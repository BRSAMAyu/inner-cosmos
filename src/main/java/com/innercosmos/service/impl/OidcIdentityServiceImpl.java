package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.config.OidcProperties;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserIdentity;
import com.innercosmos.mapper.UserIdentityMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.OidcIdentityService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class OidcIdentityServiceImpl implements OidcIdentityService {
    private static final OAuth2Error INVALID_IDENTITY = new OAuth2Error("invalid_token");
    private final UserIdentityMapper identityMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OidcProperties properties;

    public OidcIdentityServiceImpl(UserIdentityMapper identityMapper, UserMapper userMapper,
                                   PasswordEncoder passwordEncoder, OidcProperties properties) {
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public User resolve(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        if (issuer.isBlank() || subject == null || subject.isBlank()) {
            throw rejected("OIDC token is missing issuer or subject");
        }
        if (issuer.length() > 255 || subject.length() > 255) {
            throw rejected("OIDC issuer or subject exceeds the supported identity boundary");
        }
        UserIdentity identity = find(issuer, subject);
        if (identity == null) {
            if (!properties.isAutoProvision()) {
                throw rejected("OIDC identity is not linked");
            }
            identity = provision(jwt, issuer, subject);
        }
        User user = userMapper.selectById(identity.userId);
        if (user == null || !"ACTIVE".equals(user.status)) {
            throw rejected("OIDC identity is inactive");
        }
        identity.lastAuthenticatedAt = LocalDateTime.now();
        identity.emailSnapshot = verifiedEmail(jwt);
        identityMapper.updateById(identity);
        user.lastLoginAt = LocalDateTime.now();
        userMapper.updateById(user);
        return user;
    }

    private UserIdentity provision(Jwt jwt, String issuer, String subject) {
        User user = new User();
        user.username = externalUsername(issuer, subject);
        user.passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        user.nickname = claim(jwt, "name", claim(jwt, "preferred_username", "Inner Cosmos User", 64), 64);
        user.email = verifiedEmail(jwt);
        user.role = "USER";
        user.status = "ACTIVE";
        user.lastLoginAt = LocalDateTime.now();
        userMapper.insert(user);

        UserIdentity identity = new UserIdentity();
        identity.userId = user.id;
        identity.issuer = issuer;
        identity.subject = subject;
        identity.emailSnapshot = user.email;
        identity.lastAuthenticatedAt = LocalDateTime.now();
        identityMapper.insert(identity);
        return identity;
    }

    private UserIdentity find(String issuer, String subject) {
        return identityMapper.selectOne(new QueryWrapper<UserIdentity>()
                .eq("issuer", issuer)
                .eq("subject", subject));
    }

    private static String verifiedEmail(Jwt jwt) {
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) return null;
        return claim(jwt, "email", null, 128);
    }

    private static String claim(Jwt jwt, String name, String fallback, int maxLength) {
        String value = jwt.getClaimAsString(name);
        return value == null || value.isBlank() ? fallback : value.substring(0, Math.min(value.length(), maxLength));
    }

    private static String externalUsername(String issuer, String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((issuer + "\n" + subject).getBytes(StandardCharsets.UTF_8));
            return "oidc_" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static OAuth2AuthenticationException rejected(String description) {
        return new OAuth2AuthenticationException(INVALID_IDENTITY, description);
    }
}

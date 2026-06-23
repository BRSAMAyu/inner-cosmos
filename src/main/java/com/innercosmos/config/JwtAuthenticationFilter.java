package com.innercosmos.config;

import com.innercosmos.entity.User;
import com.innercosmos.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT-like authentication filter.
 * Reads userId from custom header (X-User-Id) for now.
 * In production, replace with actual JWT validation.
 * All API requests must have valid X-User-Id header.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;

    public JwtAuthenticationFilter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // M-010: session-based auth is the ONLY mechanism. The X-User-Id header previously
        // granted full Spring Security auth (incl. ROLE_ADMIN) from an unsigned, client-set
        // header — a latent risk the moment any @PreAuthorize is added. Removed; the session
        // bridge (LOGIN_USER_ID) is sufficient since every controller reads the session.
        var httpSession = request.getSession(false);
        if (httpSession != null) {
            Object uid = httpSession.getAttribute("LOGIN_USER_ID");
            if (uid instanceof Long userId && userId > 0) {
                User user = userMapper.selectById(userId);
                if (user != null && "ACTIVE".equals(user.status)) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    if ("ADMIN".equals(user.role)) {
                        authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_USER"),
                            new SimpleGrantedAuthority("ROLE_ADMIN")
                        );
                    }
                    var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/") || path.startsWith("/api/public/") ||
               path.startsWith("/actuator/health") || path.startsWith("/actuator/prometheus") ||
               path.startsWith("/actuator/info");
    }
}
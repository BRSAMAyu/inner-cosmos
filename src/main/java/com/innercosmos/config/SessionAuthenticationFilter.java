package com.innercosmos.config;

import com.innercosmos.common.Constants;
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
 * Bridges the authoritative server-side LOGIN_USER_ID session into Spring Security.
 * Client-controlled identity headers and bearer-token lookalikes are intentionally ignored.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final UserMapper userMapper;

    public SessionAuthenticationFilter(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null) {
            Object uid = session.getAttribute(Constants.SESSION_USER_KEY);
            if (uid instanceof Long userId && userId > 0) {
                User user = userMapper.selectById(userId);
                if (user != null && "ACTIVE".equals(user.status)) {
                    List<SimpleGrantedAuthority> authorities = "ADMIN".equals(user.role)
                            ? List.of(new SimpleGrantedAuthority("ROLE_USER"),
                                    new SimpleGrantedAuthority("ROLE_ADMIN"))
                            : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user, null, authorities));
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/csrf")
                || path.startsWith("/api/public/")
                || path.startsWith("/actuator/health")
                || path.startsWith("/actuator/prometheus")
                || path.startsWith("/actuator/info");
    }
}

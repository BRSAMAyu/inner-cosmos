package com.innercosmos.service;

import com.innercosmos.entity.User;
import org.springframework.security.oauth2.jwt.Jwt;

public interface OidcIdentityService {
    User resolve(Jwt jwt);
}

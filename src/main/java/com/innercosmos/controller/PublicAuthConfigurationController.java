package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.config.OidcProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Non-secret native-client bootstrap data. No client secret exists for the public client. */
@RestController
@RequestMapping("/api/public/auth")
public class PublicAuthConfigurationController {
    private final OidcProperties properties;

    public PublicAuthConfigurationController(OidcProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/mobile-oidc")
    public ApiResponse<Map<String, Object>> mobileOidc() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.isEnabled());
        data.put("flow", "authorization_code");
        data.put("pkceRequired", true);
        data.put("codeChallengeMethod", "S256");
        if (properties.isEnabled()) {
            data.put("issuer", properties.getIssuerUri());
            data.put("authorizationEndpoint", properties.getAuthorizationUri());
            data.put("tokenEndpoint", properties.getTokenUri());
            data.put("clientId", properties.getClientId());
            data.put("redirectUri", properties.getRedirectUri());
            data.put("scopes", properties.getScopes());
        }
        return ApiResponse.ok(data);
    }
}

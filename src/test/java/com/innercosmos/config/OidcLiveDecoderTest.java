package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional local-stack contract test. The token is supplied only through the test process. */
class OidcLiveDecoderTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "INNER_COSMOS_TEST_ACCESS_TOKEN", matches = ".+")
    void localKeycloakTokenPassesTheProductionDecoderContract() {
        OidcProperties properties = new OidcProperties();
        properties.setIssuerUri("http://10.0.2.2:8081/realms/inner-cosmos");
        properties.setJwkSetUri("http://127.0.0.1:8081/realms/inner-cosmos/protocol/openid-connect/certs");
        properties.setAudience("inner-cosmos-api");

        var jwt = new OidcSecurityConfiguration().oidcJwtDecoder(properties)
                .decode(System.getenv("INNER_COSMOS_TEST_ACCESS_TOKEN"));

        assertThat(jwt.getIssuer().toString()).isEqualTo(properties.getIssuerUri());
        assertThat(jwt.getAudience()).contains(properties.getAudience());
        assertThat(jwt.getSubject()).as("stable OIDC subject").isNotBlank();
    }
}

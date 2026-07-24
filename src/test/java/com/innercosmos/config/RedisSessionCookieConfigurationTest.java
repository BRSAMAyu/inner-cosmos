package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisSessionCookieConfigurationTest {

    @Test
    void publicDemoCanEmitSecureSameSiteNoneSessionCookie() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("server.servlet.session.cookie.same-site", "none");
        CookieSerializer serializer = new RedisSessionConfiguration().sessionCookieSerializer(environment);
        MockHttpServletResponse response = new MockHttpServletResponse();

        serializer.writeCookieValue(new CookieSerializer.CookieValue(
                new MockHttpServletRequest(), response, "opaque-session-id"));

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("SESSION=")
                .contains("; Path=/")
                .contains("; Secure")
                .contains("; HttpOnly")
                .contains("; SameSite=None");
    }

    @Test
    void invalidSameSitePolicyFailsClosed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.servlet.session.cookie.same-site", "anything");

        assertThatThrownBy(() -> new RedisSessionConfiguration().sessionCookieSerializer(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SameSite");
    }
}

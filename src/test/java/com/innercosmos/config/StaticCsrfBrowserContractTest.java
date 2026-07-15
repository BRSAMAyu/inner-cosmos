package com.innercosmos.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StaticCsrfBrowserContractTest {

    @Test
    void browserMutationsUseTheCentralCsrfAwareTransport() throws IOException {
        String app = resource("static/js/app.js");
        assertThat(app)
                .contains("async ensureCsrfToken(force = false)")
                .contains("async secureFetch(path, options = {})")
                .contains("headers.set(csrf.headerName, csrf.token)")
                .contains("IC.secureFetch(\"/api/asr/transcribe\"");

        assertThat(resource("static/js/api.js"))
                .contains("IC.secureFetch(path, {");
        assertThat(resource("static/pages/aurora-chat.html"))
                .contains("IC.secureFetch(\"/api/aurora/stream-stage\"")
                .contains("IC.secureFetch(\"/api/aurora/turns/\" + encodeURIComponent(turnId) + \"/stop\"")
                .contains("IC.secureFetch(\"/api/aurora/message-rich\"");
        assertThat(resource("static/pages/dashboard.html"))
                .contains("IC.secureFetch(\"/api/aurora/proactive/check\"");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("classpath resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

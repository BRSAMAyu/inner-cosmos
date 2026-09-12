package com.innercosmos.ai.gateway;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * CP-17 egress domain allowlist: every real-provider base URL is validated at wiring time —
 * an unknown host fails closed (startup error), never an unapproved outbound route. The
 * default allowlist covers the project's configured providers plus loopback for dev/test;
 * commercial deployments override it with the contracted host set.
 */
@Component
public class GatewayEgressGuard {

    private final List<String> allowedHostSuffixes;

    public GatewayEgressGuard(
            @Value("${inner-cosmos.gateway.allowed-egress-hosts:}") String configured) {
        List<String> defaults = List.of(
                "open.bigmodel.cn", "api.xiaomimimo.com", "token-plan-cn.xiaomimimo.com",
                "api.minimaxi.com", "api.minimax.chat",
                "api.deepseek.com", "generativelanguage.googleapis.com",
                "localhost", "127.0.0.1");
        if (configured == null || configured.isBlank()) {
            this.allowedHostSuffixes = defaults;
        } else {
            this.allowedHostSuffixes = List.of(configured.split(","))
                    .stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    /** Throws on any host outside the allowlist (fail closed, unknown = rejected). */
    public void validate(String baseUrl, String providerLabel) {
        String host = hostOf(baseUrl);
        for (String allowed : allowedHostSuffixes) {
            String normalized = allowed.toLowerCase(Locale.ROOT);
            if (host.equals(normalized) || host.endsWith("." + normalized)) {
                return;
            }
        }
        throw new IllegalStateException("Provider base URL host is not on the egress allowlist ("
                + providerLabel + "): " + host + " — refusing unapproved outbound route. "
                + "Add the contracted host to inner-cosmos.gateway.allowed-egress-hosts.");
    }

    private static String hostOf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Provider base URL is blank — refusing outbound route");
        }
        try {
            String host = URI.create(baseUrl.trim()).getHost();
            if (host == null) {
                throw new IllegalStateException("Unparseable provider base URL: " + baseUrl);
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (Exception malformed) {
            throw new IllegalStateException("Unparseable provider base URL: " + baseUrl, malformed);
        }
    }
}

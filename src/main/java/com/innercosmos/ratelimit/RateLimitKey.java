package com.innercosmos.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Keeps user IDs and client IPs out of Redis key names and operational tooling. */
public final class RateLimitKey {
    private RateLimitKey() {
    }

    public static String forSubject(String scope, String subject) {
        if (scope == null || !scope.matches("[a-z-]+") || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("A valid rate-limit scope and subject are required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8));
            return scope + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}

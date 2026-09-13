package com.innercosmos.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * CP-47 callback signature verification, fail-closed by design: a blank/unconfigured
 * secret rejects EVERYTHING (an unverified payment callback must never touch the ledger),
 * the HMAC binds the timestamp into the signed payload so a captured signature cannot be
 * replayed on a different body, and the freshness window bounds replay of the exact bytes.
 * Comparison is constant-time.
 */
@Component
public class PaymentCallbackVerifier {

    private static final Duration FRESHNESS = Duration.ofMinutes(5);

    private final String secret;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentCallbackVerifier(
            @Value("${inner-cosmos.payments.callback-secret:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    public PaymentCallbackVerifier(String secret, Clock clock) {
        this.secret = secret == null ? "" : secret;
        this.clock = clock;
    }

    public boolean verify(String timestamp, String canonicalBody, String signatureHex) {
        if (secret.isBlank() || timestamp == null || canonicalBody == null || signatureHex == null) {
            return false;
        }
        Instant at;
        try {
            at = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (Math.abs(Duration.between(at, clock.instant()).toSeconds()) > FRESHNESS.toSeconds()) {
            return false;
        }
        byte[] expected = hmac(timestamp + "." + canonicalBody);
        byte[] provided;
        try {
            provided = hex(signatureHex.trim().toLowerCase());
        } catch (Exception ignored) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static byte[] hex(String value) {
        if (value.length() % 2 != 0) throw new IllegalArgumentException("odd hex");
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}

package com.innercosmos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Component
public class PushTokenProtector {
    private static final byte[] AAD = "inner-cosmos:push-token:v1".getBytes(StandardCharsets.UTF_8);
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public PushTokenProtector(@Value("${inner-cosmos.push.token-encryption-key:}") String encodedKey) {
        byte[] candidate = null;
        if (encodedKey != null && !encodedKey.isBlank()) {
            try { candidate = Base64.getDecoder().decode(encodedKey); }
            catch (IllegalArgumentException ignored) { /* unavailable is handled as an external credential gate */ }
        }
        key = candidate != null && candidate.length == 32 ? candidate : null;
    }

    public boolean available() { return key != null; }

    public String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }

    public Optional<String> protect(String token) {
        if (key == null || token == null || token.isBlank()) return Optional.empty();
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Optional.of(Base64.getEncoder().encodeToString(payload));
        } catch (Exception e) { throw new IllegalStateException("Unable to protect push token", e); }
    }

    public String reveal(String protectedToken) {
        if (key == null) throw new IllegalStateException("EXTERNAL_CREDENTIAL_GATE: push token encryption key is unavailable");
        try {
            byte[] payload = Base64.getDecoder().decode(protectedToken);
            if (payload.length < 29) throw new IllegalArgumentException("invalid token envelope");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Unable to reveal protected push token", e); }
    }
}

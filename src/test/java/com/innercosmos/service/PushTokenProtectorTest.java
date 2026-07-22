package com.innercosmos.service;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class PushTokenProtectorTest {
    private static PushTokenProtector protector() {
        return new PushTokenProtector(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test void encryptsAndAuthenticatesWithoutReturningTheRawToken() {
        PushTokenProtector protector = protector();
        String raw = "device-token-that-must-never-be-logged";
        String encrypted = protector.protect(raw).orElseThrow();
        assertNotEquals(raw, encrypted);
        assertEquals(raw, protector.reveal(encrypted));
        assertEquals(64, protector.hash(raw).length());
    }

    @Test void rejectsTamperedCiphertextAndTreatsMissingKeyAsCredentialGate() {
        PushTokenProtector protector = protector();
        byte[] payload = Base64.getDecoder().decode(protector.protect("token").orElseThrow());
        payload[payload.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> protector.reveal(Base64.getEncoder().encodeToString(payload)));
        PushTokenProtector unavailable = new PushTokenProtector("");
        assertFalse(unavailable.available());
        assertTrue(unavailable.protect("token").isEmpty());
    }
}

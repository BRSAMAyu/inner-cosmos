package com.innercosmos.service.identity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Dev/test verification channel. The challenge answer encodes the outcome the test wants
 * (BIRTH:1998-04-12 verifies an adult; MINOR:2010-01-01 verifies a minor; anything else
 * fails), so the full upgrade chain — including the CP-08 minor intercept — is exercisable
 * end to end without an external provider. Production profiles must NOT use this channel;
 * IdentityVerificationService fails closed when the configured channel has no provider.
 */
@Component
public class SandboxAgeVerificationProvider implements AgeVerificationProvider {

    public static final String NAME = "sandbox";

    private static final long CHALLENGE_TTL_MINUTES = 10;
    private static final String ADULT_PREFIX = "BIRTH:";
    private static final String MINOR_PREFIX = "MINOR:";

    private final java.util.Set<String> issued = ConcurrentHashMap.newKeySet();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AgeChallenge issueChallenge(Long userId) {
        String reference = "SBX-" + UUID.randomUUID();
        issued.add(reference);
        return new AgeChallenge(reference,
                "沙箱核验：回答 BIRTH:YYYY-MM-DD（成年）或 MINOR:YYYY-MM-DD（未成年）",
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(CHALLENGE_TTL_MINUTES));
    }

    @Override
    public AgeOutcome verify(Long userId, String providerReference, String answer) {
        if (!issued.contains(providerReference)) {
            return new AgeOutcome(false, null, "核验码不存在或已失效");
        }
        String trimmed = answer == null ? "" : answer.trim();
        LocalDate birthDate = parseDate(trimmed, ADULT_PREFIX);
        if (birthDate != null) {
            return new AgeOutcome(true, birthDate, null);
        }
        LocalDate minorBirth = parseDate(trimmed, MINOR_PREFIX);
        if (minorBirth != null) {
            return new AgeOutcome(true, minorBirth, null);
        }
        return new AgeOutcome(false, null, "核验回答无效");
    }

    private static LocalDate parseDate(String answer, String prefix) {
        if (!answer.startsWith(prefix)) {
            return null;
        }
        try {
            return LocalDate.parse(answer.substring(prefix.length()));
        } catch (Exception malformed) {
            return null;
        }
    }
}

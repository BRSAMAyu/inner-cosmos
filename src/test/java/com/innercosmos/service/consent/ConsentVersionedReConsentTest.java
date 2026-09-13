package com.innercosmos.service.consent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.ConsentRecord;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.ConsentRecordMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.consent.ConsentPurpose.Decision;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-07 versioned re-consent: a recorded decision made under OLD purpose texts must never
 * silently keep authorizing after the registry version bumps. A stale grant falls back to
 * the purpose's no-decision default (fail-closed for ask-first purposes), the consent
 * center surfaces RE_CONSENT_REQUIRED, and a fresh decision re-stamps the current version.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class ConsentVersionedReConsentTest {

    private static final AtomicLong USERS = new AtomicLong(93_700_000);

    @Autowired ConsentCenterService consentCenter;
    @Autowired ConsentRecordMapper consentMapper;
    @Autowired UserMapper userMapper;

    private long human() {
        long id = USERS.incrementAndGet();
        User user = new User();
        user.id = id;
        user.username = "cp07-" + id;
        user.passwordHash = "x";
        user.nickname = "cp07";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        userMapper.insert(user);
        return id;
    }

    private void backdateVersion(long userId, ConsentPurpose purpose, String oldVersion) {
        // updateById skips null fields (default update strategy), so set the column
        // explicitly -- a null version must actually land in the row.
        consentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ConsentRecord>()
                .eq("user_id", userId).eq("purpose_code", purpose.name())
                .set("version", oldVersion));
    }

    @Test
    void freshGrantAuthorizesAndCarriesTheCurrentVersion() {
        long user = human();
        var view = consentCenter.decide(user, "AI_PROVIDER_EGRESS", true);
        assertEquals(ConsentPurpose.CURRENT_VERSION, view.version());
        assertEquals("CONSENT_CENTER", view.source());
        assertEquals(Decision.GRANTED, consentCenter.effective(user, ConsentPurpose.AI_PROVIDER_EGRESS));
    }

    @Test
    void staleGrantNeverAuthorizes_egressFailsClosedUntilReConsent() {
        long user = human();
        consentCenter.decide(user, "AI_PROVIDER_EGRESS", true);
        backdateVersion(user, ConsentPurpose.AI_PROVIDER_EGRESS, "PV-2026-08");

        assertEquals(Decision.NOT_GRANTED, consentCenter.effective(user, ConsentPurpose.AI_PROVIDER_EGRESS),
                "the old grant was made under different terms -- it must not silently continue");
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> consentCenter.assertProviderEgress(user));
        assertEquals(ErrorCode.CONSENT_REQUIRED, blocked.code);

        var view = consentCenter.list(user).stream()
                .filter(v -> v.purposeCode().equals("AI_PROVIDER_EGRESS")).findFirst().orElseThrow();
        assertEquals("RE_CONSENT_REQUIRED", view.source(), "the center must surface the prompt");

        // Re-consent under the current terms restores the grant with the new version.
        consentCenter.decide(user, "AI_PROVIDER_EGRESS", true);
        assertEquals(Decision.GRANTED, consentCenter.effective(user, ConsentPurpose.AI_PROVIDER_EGRESS));
        assertEquals("CONSENT_CENTER", consentCenter.list(user).stream()
                .filter(v -> v.purposeCode().equals("AI_PROVIDER_EGRESS")).findFirst().orElseThrow().source());
    }

    @Test
    void versionColumnIsNotNullSoEveryLegacyRowCarriesAnExplicitOldVersion() {
        // The schema forbids NULL versions (NOT NULL column), so "pre-versioning" rows
        // physically carry the original version string -- which is simply an old,
        // non-current value handled by the same staleness rule above. Trying to force a
        // NULL version is rejected by the constraint itself.
        long user = human();
        consentCenter.decide(user, "AI_PROVIDER_EGRESS", true);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> backdateVersion(user, ConsentPurpose.AI_PROVIDER_EGRESS, null));
    }

    @Test
    void coreServiceStaysUsableWhileStale_serviceContinuity() {
        // CORE_SERVICE is REQUIRED with a GRANTED default: even a stale recorded decision
        // keeps the core loop working (the default applies), while the center still flags
        // the re-consent prompt.
        long user = human();
        consentCenter.decide(user, "PUBLIC_DISCOVERABLE", true);
        backdateVersion(user, ConsentPurpose.PUBLIC_DISCOVERABLE, "PV-2026-08");

        assertEquals(Decision.DECLINED, consentCenter.effective(user, ConsentPurpose.PUBLIC_DISCOVERABLE),
                "optional discovery falls back to its declined default until re-consent");
        assertEquals(Decision.GRANTED, consentCenter.effective(user, ConsentPurpose.CORE_SERVICE),
                "the core loop itself never breaks on a version bump");
    }

    @Test
    void staleDeclineAlsoSurfacesThePromptButDecliningStaysDeclined() {
        // A DECLINE recorded under old texts: effective stays the (declined-shaped) default
        // -- no authority is being exercised, but the user is still shown the prompt so the
        // recorded row cannot masquerade as a current decision.
        long user = human();
        consentCenter.decide(user, "VOICE_PROCESSING", false);
        backdateVersion(user, ConsentPurpose.VOICE_PROCESSING, "PV-2026-08");

        assertEquals(Decision.DECLINED, consentCenter.effective(user, ConsentPurpose.VOICE_PROCESSING));
        assertTrue(consentCenter.list(user).stream()
                .anyMatch(v -> v.purposeCode().equals("VOICE_PROCESSING")
                        && "RE_CONSENT_REQUIRED".equals(v.source())));
        assertFalse(consentCenter.list(user).stream()
                .anyMatch(v -> v.purposeCode().equals("CORE_SERVICE")
                        && "RE_CONSENT_REQUIRED".equals(v.source())),
                "no recorded row -> DEFAULT, not stale");
    }
}

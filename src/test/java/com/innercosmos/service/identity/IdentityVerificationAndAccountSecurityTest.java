package com.innercosmos.service.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.IdentityVerification;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.IdentityVerificationMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.UserService;
import com.innercosmos.service.identity.IdentityVerificationService.ChallengeView;
import com.innercosmos.service.identity.IdentityVerificationService.StatusView;
import com.innercosmos.service.minor.MinorProtectionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CP-13 acceptance slice: the VERIFIED_ID upgrade chain (single-shot consume, expiry,
 * replay and cross-account guards, minor-intercept linkage, fail-closed channel) plus
 * whole-account device revocation and freeze/unfreeze with audit.
 */
@SpringBootTest
class IdentityVerificationAndAccountSecurityTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private IdentityVerificationService verificationService;
    @Autowired
    private AccountSecurityService accountSecurityService;
    @Autowired
    private MinorProtectionService minorProtectionService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private IdentityVerificationMapper verificationMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private com.innercosmos.mapper.AccountSecurityEventMapper securityEventMapper;
    @Autowired
    private SandboxAgeVerificationProvider sandboxProvider;

    private User adult() {
        LocalDate birth = LocalDate.now(SHANGHAI).minusYears(25);
        RegisterRequest request = new RegisterRequest();
        request.username = "cp13-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = birth.toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    @Test
    void verifiedAdultUpgradesAgeGateMethod() {
        User user = adult();
        ChallengeView challenge = verificationService.initiate(user.id);
        assertNotNull(challenge.providerReference());
        assertEquals("SELF_DECLARED", userMapper.selectById(user.id).ageGateMethod);

        LocalDate verifiedBirth = LocalDate.now(SHANGHAI).minusYears(30);
        StatusView status = verificationService.confirm(user.id,
                challenge.providerReference(), "BIRTH:" + verifiedBirth);
        assertEquals("VERIFIED_ID", status.ageGateMethod());
        assertEquals(verifiedBirth.toString(), status.birthDate());
        assertEquals("VERIFIED_ID", userMapper.selectById(user.id).ageGateMethod);
        assertEquals(verifiedBirth, userMapper.selectById(user.id).birthDate);
        assertTrue(status.history().stream().anyMatch(row -> "VERIFIED".equals(row.status())));

        // Re-initiating an already-verified account is refused (no double verification).
        assertThrows(BusinessException.class, () -> verificationService.initiate(user.id));
    }

    @Test
    void confirmIsSingleShotReplayAndCrossAccountSafe() {
        User user = adult();
        User other = adult();
        ChallengeView challenge = verificationService.initiate(user.id);

        // Another user probing the reference learns nothing (not-found, not a hint).
        BusinessException crossed = assertThrows(BusinessException.class, () ->
                verificationService.confirm(other.id, challenge.providerReference(),
                        "BIRTH:1996-05-05"));
        assertEquals(ErrorCode.NOT_FOUND, crossed.code);

        verificationService.confirm(user.id, challenge.providerReference(), "BIRTH:1994-02-02");
        BusinessException replay = assertThrows(BusinessException.class, () ->
                verificationService.confirm(user.id, challenge.providerReference(), "BIRTH:1994-02-02"));
        assertEquals(ErrorCode.CONFLICT, replay.code);
    }

    @Test
    void expiredChallengeIsRejectedAndReinitiationExpiresPendingOnes() {
        User user = adult();
        ChallengeView first = verificationService.initiate(user.id);
        verificationMapper.update(null, new UpdateWrapper<IdentityVerification>()
                .eq("provider_reference", first.providerReference())
                .set("expires_at", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        BusinessException expired = assertThrows(BusinessException.class, () ->
                verificationService.confirm(user.id, first.providerReference(), "BIRTH:1995-03-03"));
        assertEquals(ErrorCode.CONFLICT, expired.code);

        // Re-initiate: the stale PENDING row flips to EXPIRED, only one live challenge.
        ChallengeView second = verificationService.initiate(user.id);
        IdentityVerification stale = verificationMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<IdentityVerification>()
                        .eq("provider_reference", first.providerReference()));
        assertEquals("EXPIRED", stale.status);
        assertNotNull(second.providerReference());
        assertFalse(second.providerReference().equals(first.providerReference()));
    }

    @Test
    void verifiedMinorLinksIntoMinorIntercept() {
        User user = adult();
        ChallengeView challenge = verificationService.initiate(user.id);
        LocalDate minorBirth = LocalDate.now(SHANGHAI).minusYears(15);
        StatusView status = verificationService.confirm(user.id,
                challenge.providerReference(), "MINOR:" + minorBirth);
        assertEquals(MinorProtectionService.STATUS_MINOR_RESTRICTED,
                userMapper.selectById(user.id).status);
        assertEquals("SELF_DECLARED", status.ageGateMethod(),
                "a verified minor is never upgraded to VERIFIED_ID");
        assertThrows(BusinessException.class,
                () -> minorProtectionService.assertAdultAccess(user.id));
    }

    @Test
    void wrongAnswerIsRejectedWithReason() {
        User user = adult();
        ChallengeView challenge = verificationService.initiate(user.id);
        StatusView status = verificationService.confirm(user.id,
                challenge.providerReference(), "whatever");
        assertEquals("REJECTED", status.latestStatus());
        assertNotNull(status.latestFailureReason());
    }

    @Test
    void nonSandboxChannelFailsClosed() {
        User user = adult();
        IdentityVerificationService closed = new IdentityVerificationServiceImpl(
                verificationMapper, securityEventMapper, userMapper,
                minorProtectionService, sandboxProvider, "operator-sms");
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> closed.initiate(user.id));
        assertEquals(ErrorCode.FORBIDDEN, blocked.code);
        assertTrue(blocked.getMessage().contains("不允许降级"));
    }

    @Test
    void revokeAllDevicesFreezeAndSnapshotAreAudited() {
        User user = adult();
        jdbc.update("""
                INSERT INTO tb_device_registration
                  (user_id,installation_id,platform,transport,app_version,locale,timezone,
                   enabled,revoked,last_seen_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, user.id, "cp13-dev-" + System.nanoTime(), "ANDROID", "FCM",
                "1.0.0", "zh-CN", "Asia/Shanghai", true, false,
                LocalDateTime.now(ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO tb_device_registration
                  (user_id,installation_id,platform,transport,app_version,locale,timezone,
                   enabled,revoked,last_seen_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, user.id, "cp13-dev2-" + System.nanoTime(), "IOS", "APNS",
                "1.0.0", "zh-CN", "Asia/Shanghai", true, false,
                LocalDateTime.now(ZoneOffset.UTC));

        AccountSecurityService.SecuritySnapshot before = accountSecurityService.snapshot(user.id);
        assertEquals(2, before.activeDevices());
        assertEquals(2, accountSecurityService.revokeAllDevices(user.id));
        AccountSecurityService.SecuritySnapshot after = accountSecurityService.snapshot(user.id);
        assertEquals(0, after.activeDevices());
        assertTrue(after.events().stream().anyMatch(row -> "DEVICES_REVOKED_ALL".equals(row.action())));

        // Freeze stops login immediately; unfreeze restores it; both are audited.
        assertTrue(accountSecurityService.freeze(user.id, 1L, "疑似被盗"));
        LoginRequest login = new LoginRequest();
        login.username = user.username;
        login.password = "password123";
        BusinessException frozenLogin = assertThrows(BusinessException.class,
                () -> userService.login(login));
        assertEquals(ErrorCode.FORBIDDEN, frozenLogin.code);
        assertFalse(accountSecurityService.freeze(user.id, 1L, "重复冻结"));

        assertTrue(accountSecurityService.unfreeze(user.id, 1L, "复核通过"));
        assertNotNull(userService.login(login).id);
        AccountSecurityService.SecuritySnapshot unfrozen = accountSecurityService.snapshot(user.id);
        assertTrue(unfrozen.events().stream().anyMatch(row -> "ACCOUNT_FROZEN".equals(row.action())));
        assertTrue(unfrozen.events().stream().anyMatch(row -> "ACCOUNT_UNFROZEN".equals(row.action())));

        // Minor-restricted accounts cannot be frozen/unfrozen through this path.
        User minorCase = adult();
        minorProtectionService.flagMinor(minorCase.id, "测试");
        assertThrows(BusinessException.class,
                () -> accountSecurityService.freeze(minorCase.id, 1L, "x"));
        assertFalse(accountSecurityService.unfreeze(minorCase.id, 1L, "x"));
    }
}

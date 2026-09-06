package com.innercosmos.service.identity;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.AccountSecurityEvent;
import com.innercosmos.entity.IdentityVerification;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AccountSecurityEventMapper;
import com.innercosmos.mapper.IdentityVerificationMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.identity.AgeVerificationProvider.AgeChallenge;
import com.innercosmos.service.identity.AgeVerificationProvider.AgeOutcome;
import com.innercosmos.service.minor.MinorProtectionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityVerificationServiceImpl implements IdentityVerificationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityVerificationServiceImpl.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final IdentityVerificationMapper verificationMapper;
    private final AccountSecurityEventMapper securityEventMapper;
    private final UserMapper userMapper;
    private final MinorProtectionService minorProtectionService;
    private final SandboxAgeVerificationProvider sandboxProvider;
    private final String configuredChannel;

    public IdentityVerificationServiceImpl(
            IdentityVerificationMapper verificationMapper,
            AccountSecurityEventMapper securityEventMapper,
            UserMapper userMapper,
            MinorProtectionService minorProtectionService,
            SandboxAgeVerificationProvider sandboxProvider,
            @Value("${inner-cosmos.identity.age-provider:sandbox}") String configuredChannel) {
        this.verificationMapper = verificationMapper;
        this.securityEventMapper = securityEventMapper;
        this.userMapper = userMapper;
        this.minorProtectionService = minorProtectionService;
        this.sandboxProvider = sandboxProvider;
        this.configuredChannel = configuredChannel == null || configuredChannel.isBlank()
                ? "sandbox" : configuredChannel.trim().toLowerCase();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChallengeView initiate(Long userId) {
        User user = requireHumanUser(userId);
        if ("VERIFIED_ID".equals(user.ageGateMethod)) {
            throw new BusinessException(ErrorCode.CONFLICT, "账户已完成实名年龄核验，无需重复核验");
        }
        AgeVerificationProvider provider = requireProvider();
        // One live challenge at a time: older pending rows expire immediately.
        verificationMapper.update(null, new UpdateWrapper<IdentityVerification>()
                .eq("user_id", userId).eq("status", "PENDING").isNull("consumed_at")
                .set("status", "EXPIRED").set("updated_at", now()));
        AgeChallenge challenge = provider.issueChallenge(userId);
        IdentityVerification row = new IdentityVerification();
        row.userId = userId;
        row.method = "VERIFIED_ID";
        row.provider = provider.name();
        row.providerReference = challenge.providerReference();
        row.status = "PENDING";
        row.expiresAt = challenge.expiresAt();
        verificationMapper.insert(row);
        audit(userId, "AGE_CHALLENGE_ISSUED", null,
                "provider=" + provider.name() + " channel=" + configuredChannel);
        return new ChallengeView(challenge.providerReference(), challenge.instructions(),
                challenge.expiresAt().toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StatusView confirm(Long userId, String providerReference, String answer) {
        requireHumanUser(userId);
        AgeVerificationProvider provider = requireProvider();
        IdentityVerification row = verificationMapper.selectOne(
                new QueryWrapper<IdentityVerification>()
                        .eq("provider_reference", providerReference));
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "核验记录不存在");
        }
        if (!row.userId.equals(userId)) {
            // Crossed-account probe: never reveal whether the reference exists for others.
            throw new BusinessException(ErrorCode.NOT_FOUND, "核验记录不存在");
        }
        if (row.consumedAt != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该核验已使用过，请发起新的核验");
        }
        if (!"PENDING".equals(row.status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "该核验已结束（" + row.status + "）");
        }
        if (row.expiresAt.isBefore(now())) {
            row.status = "EXPIRED";
            row.failureReason = "核验码过期";
            verificationMapper.updateById(row);
            throw new BusinessException(ErrorCode.CONFLICT, "核验码已过期，请重新发起");
        }

        AgeOutcome outcome = provider.verify(userId, providerReference, answer);
        LocalDateTime consumed = now();
        row.consumedAt = consumed;
        if (!outcome.verified() || outcome.birthDate() == null) {
            row.status = "REJECTED";
            row.failureReason = outcome.failureReason();
            verificationMapper.updateById(row);
            audit(userId, "AGE_VERIFICATION_REJECTED", null, String.valueOf(outcome.failureReason()));
            return statusOf(userId);
        }

        row.status = "VERIFIED";
        row.verifiedBirthDate = outcome.birthDate();
        row.verifiedAt = consumed;
        verificationMapper.updateById(row);

        if (ageAt(outcome.birthDate()) < 18) {
            // Verified minor: stop the adult service immediately (CP-08 intercept linkage).
            minorProtectionService.flagMinor(userId,
                    "实名核验出生日期未满18：" + outcome.birthDate());
            audit(userId, "AGE_VERIFICATION_MINOR_INTERCEPT", null,
                    "verifiedBirthDate=" + outcome.birthDate());
            log.info("identity verification moved user {} to minor-restricted", userId);
            return statusOf(userId);
        }

        User user = userMapper.selectById(userId);
        user.birthDate = outcome.birthDate();
        user.ageGateMethod = "VERIFIED_ID";
        userMapper.updateById(user);
        audit(userId, "AGE_VERIFICATION_UPGRADED", null,
                "provider=" + provider.name() + " birthDate=" + outcome.birthDate());
        return statusOf(userId);
    }

    @Override
    public StatusView statusOf(Long userId) {
        User user = requireHumanUser(userId);
        List<IdentityVerification> rows = verificationMapper.selectList(
                new QueryWrapper<IdentityVerification>()
                        .eq("user_id", userId).orderByDesc("id").last("LIMIT 10"));
        IdentityVerification latest = rows.isEmpty() ? null : rows.get(0);
        return new StatusView(
                user.ageGateMethod,
                user.birthDate == null ? null : user.birthDate.toString(),
                latest == null ? null : latest.status,
                latest == null ? null : latest.failureReason,
                rows.stream().map(row -> new HistoryRow(
                        row.method, row.provider, row.status,
                        row.verifiedBirthDate == null ? null : row.verifiedBirthDate.toString(),
                        row.createdAt == null ? null : row.createdAt.toString())).toList());
    }

    /**
     * Fail-closed channel resolution: a non-sandbox channel without a contracted provider
     * is an error, never a silent downgrade to the sandbox (blueprint recovery clause).
     */
    private AgeVerificationProvider requireProvider() {
        if ("sandbox".equals(configuredChannel)) {
            return sandboxProvider;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "身份核验通道（" + configuredChannel + "）尚未接入：不允许降级为弱验证。"
                        + "请在通道合同核验完成后再开放实名年龄核验。");
    }

    private User requireHumanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "尚未登录");
        }
        if (!"HUMAN".equals(user.accountKind)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内部账户不参与实名核验");
        }
        if (!Constants.STATUS_ACTIVE.equals(user.status)
                && !MinorProtectionService.STATUS_MINOR_RESTRICTED.equals(user.status)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户当前状态不可发起实名核验");
        }
        return user;
    }

    private static int ageAt(LocalDate birthDate) {
        return java.time.Period.between(birthDate, LocalDate.now(SHANGHAI)).getYears();
    }

    private void audit(Long userId, String action, Long actorId, String detail) {
        AccountSecurityEvent event = new AccountSecurityEvent();
        event.userId = userId;
        event.action = action;
        event.actorId = actorId;
        event.detail = detail;
        securityEventMapper.insert(event);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}

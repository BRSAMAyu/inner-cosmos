package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.WakeIntentMapper;
import com.innercosmos.service.NotificationService;
import com.innercosmos.service.WakeIntentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WakeIntentServiceImpl implements WakeIntentService {
    private final WakeIntentMapper mapper;
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public WakeIntentServiceImpl(WakeIntentMapper mapper, JdbcTemplate jdbc,
                                 NotificationService notifications) {
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @Override
    public WakeIntent schedule(Long userId, String purpose, String reasonForUser, String content,
                               LocalDateTime earliestAt, LocalDateTime preferredAt, LocalDateTime latestAt,
                               String timezone, String payloadRef) {
        if (userId == null || preferredAt == null) throw bad("preferredAt is required");
        LocalDateTime earliestLocal = earliestAt == null ? preferredAt : earliestAt;
        LocalDateTime latestLocal = latestAt == null ? preferredAt.plusHours(6) : latestAt;
        validateWindow(earliestLocal, preferredAt, latestLocal);
        ZoneId zone = validZone(timezone);
        LocalDateTime earliest = utc(earliestLocal, zone);
        LocalDateTime preferredUtc = utc(preferredAt, zone);
        LocalDateTime latest = utc(latestLocal, zone);
        WakeIntent intent = new WakeIntent();
        intent.userId = userId;
        intent.purpose = requireText(purpose, "purpose");
        intent.reasonForUser = requireText(reasonForUser, "reasonForUser");
        intent.content = requireText(content, "content");
        intent.earliestAt = earliest;
        intent.preferredAt = preferredUtc;
        intent.latestAt = latest;
        intent.timezone = zone.getId();
        intent.preconditionsJson = "{}";
        intent.cancelConditionsJson = "{}";
        intent.payloadRef = payloadRef;
        intent.status = "PLANNED";
        intent.decisionPolicyVersion = POLICY_VERSION;
        mapper.insert(intent);
        return intent;
    }

    @Override
    public List<WakeIntent> listActive(Long userId) {
        return mapper.selectList(new QueryWrapper<WakeIntent>()
            .eq("user_id", userId)
            .in("status", List.of("PLANNED", "CLAIMED"))
            .orderByAsc("preferred_at"));
    }

    @Override
    @Transactional
    public WakeIntent cancel(Long userId, Long intentId) {
        WakeIntent owned = owned(userId, intentId);
        int changed = jdbc.update("UPDATE tb_wake_intent SET status='CANCELLED', cancelled_at=?, " +
                "outcome='CANCELLED_BY_USER', outcome_reason='user_cancelled', claim_token=NULL, " +
                "claimed_by=NULL, claim_until=NULL, updated_at=? WHERE id=? AND user_id=? AND status IN ('PLANNED','CLAIMED')",
            nowUtc(), nowUtc(), intentId, userId);
        if (changed == 0) throw bad("wake intent is no longer active");
        owned.status = "CANCELLED";
        owned.cancelledAt = nowUtc();
        owned.outcome = "CANCELLED_BY_USER";
        owned.outcomeReason = "user_cancelled";
        owned.claimToken = null;
        owned.claimedBy = null;
        owned.claimUntil = null;
        return owned;
    }

    @Override
    @Transactional
    public WakeIntent reschedule(Long userId, Long intentId, LocalDateTime earliestAt,
                                 LocalDateTime preferredAt, LocalDateTime latestAt) {
        WakeIntent owned = owned(userId, intentId);
        LocalDateTime earliestLocal = earliestAt == null ? preferredAt : earliestAt;
        LocalDateTime latestLocal = latestAt == null ? preferredAt.plusHours(6) : latestAt;
        validateWindow(earliestLocal, preferredAt, latestLocal);
        ZoneId zone = validZone(owned.timezone);
        LocalDateTime earliest = utc(earliestLocal, zone);
        LocalDateTime preferredUtc = utc(preferredAt, zone);
        LocalDateTime latest = utc(latestLocal, zone);
        int changed = jdbc.update("UPDATE tb_wake_intent SET earliest_at=?, preferred_at=?, latest_at=?, " +
                "status='PLANNED', claim_token=NULL, claimed_by=NULL, claim_until=NULL, outcome=NULL, " +
                "outcome_reason=NULL, updated_at=? WHERE id=? AND user_id=? AND status IN ('PLANNED','CLAIMED')",
            earliest, preferredUtc, latest, nowUtc(), intentId, userId);
        if (changed == 0) throw bad("wake intent is no longer active");
        owned.earliestAt = earliest;
        owned.preferredAt = preferredUtc;
        owned.latestAt = latest;
        owned.status = "PLANNED";
        owned.claimToken = null;
        owned.claimedBy = null;
        owned.claimUntil = null;
        owned.outcome = null;
        owned.outcomeReason = null;
        return owned;
    }

    @Override
    public List<WakeIntent> claimDue(String workerId, int batchSize, Duration lease) {
        LocalDateTime now = nowUtc();
        List<Long> candidates = jdbc.queryForList("SELECT id FROM tb_wake_intent WHERE preferred_at<=? AND " +
                "earliest_at<=? AND latest_at>=? AND (status='PLANNED' OR (status='CLAIMED' AND claim_until<?)) " +
                "ORDER BY preferred_at,id LIMIT ?", Long.class, now, now, now, now, Math.max(1, batchSize));
        List<WakeIntent> claimed = new ArrayList<>();
        for (Long id : candidates) {
            String token = UUID.randomUUID().toString();
            int changed = jdbc.update("UPDATE tb_wake_intent SET status='CLAIMED', claim_token=?, claimed_by=?, " +
                    "claim_until=?, updated_at=? WHERE id=? AND earliest_at<=? AND preferred_at<=? AND latest_at>=? " +
                    "AND (status='PLANNED' OR (status='CLAIMED' AND claim_until<?))",
                token, workerId, now.plus(lease), now, id, now, now, now, now);
            if (changed == 1) claimed.add(mapper.selectById(id));
        }
        return claimed;
    }

    @Override
    public boolean delay(WakeIntent claimed, LocalDateTime nextPreferredAt, String reason) {
        if (claimed == null || claimed.id == null || claimed.claimToken == null) return false;
        return jdbc.update("UPDATE tb_wake_intent SET status='PLANNED', preferred_at=?, claim_token=NULL, " +
                "claimed_by=NULL, claim_until=NULL, outcome='DELAY', outcome_reason=?, updated_at=? " +
                "WHERE id=? AND status='CLAIMED' AND claim_token=?",
            nextPreferredAt, reason, nowUtc(), claimed.id, claimed.claimToken) == 1;
    }

    @Override
    public boolean finish(WakeIntent claimed, String outcome, String reason) {
        if (claimed == null || claimed.id == null || claimed.claimToken == null) return false;
        return jdbc.update("UPDATE tb_wake_intent SET status='FIRED', fired_at=?, outcome=?, outcome_reason=?, " +
                "claim_token=NULL, claimed_by=NULL, claim_until=NULL, updated_at=? " +
                "WHERE id=? AND status='CLAIMED' AND claim_token=?",
            nowUtc(), outcome, reason, nowUtc(), claimed.id, claimed.claimToken) == 1;
    }

    @Override
    @Transactional
    public boolean finishWithNotification(WakeIntent claimed, String outcome, String reason,
                                          String title, String content) {
        if (!finish(claimed, outcome, reason)) return false;
        notifications.notifyOnce(claimed.userId, "AURORA_RETURN", title, content,
            claimed.id, "WAKE_INTENT");
        return true;
    }

    @Override
    public int expirePastDue() {
        LocalDateTime now = nowUtc();
        return jdbc.update("UPDATE tb_wake_intent SET status='EXPIRED', outcome='DROP', outcome_reason='latest_at_elapsed', " +
            "claim_token=NULL, claimed_by=NULL, claim_until=NULL, updated_at=? WHERE latest_at<? " +
            "AND (status='PLANNED' OR (status='CLAIMED' AND claim_until<?))", now, now, now);
    }

    private WakeIntent owned(Long userId, Long id) {
        WakeIntent intent = mapper.selectById(id);
        if (intent == null || !userId.equals(intent.userId)) throw new BusinessException(ErrorCode.NOT_FOUND, "wake intent not found");
        return intent;
    }

    private static void validateWindow(LocalDateTime earliest, LocalDateTime preferred, LocalDateTime latest) {
        if (preferred == null || earliest == null || latest == null || earliest.isAfter(preferred) || preferred.isAfter(latest)) {
            throw bad("expected earliestAt <= preferredAt <= latestAt");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw bad(field + " is required");
        return value.trim();
    }

    private static BusinessException bad(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private static ZoneId validZone(String timezone) {
        String value = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone;
        try { return ZoneId.of(value); }
        catch (RuntimeException invalid) { throw bad("invalid timezone"); }
    }

    private static LocalDateTime utc(LocalDateTime local, ZoneId zone) {
        return local.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}

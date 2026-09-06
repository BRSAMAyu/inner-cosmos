package com.innercosmos.service.metric;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.entity.AnalysisConsent;
import com.innercosmos.entity.CommercialMetricEvent;
import com.innercosmos.entity.CommercialMetricRollup;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.AnalysisConsentMapper;
import com.innercosmos.mapper.CommercialMetricEventMapper;
import com.innercosmos.mapper.CommercialMetricRollupMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class MetricEventServiceImpl implements MetricEventService {

    /** Rollup pseudo-metric keeping original K2 cohort sizes after account deletion. */
    public static final String K2_ACTIVATION_ROLLUP = "K2_ACTIVATION";

    private static final Logger log = LoggerFactory.getLogger(MetricEventServiceImpl.class);
    private static final ZoneId ANCHOR_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_PROP_VALUE_LENGTH = 200;

    private final CommercialMetricEventMapper eventMapper;
    private final CommercialMetricRollupMapper rollupMapper;
    private final AnalysisConsentMapper consentMapper;
    private final UserMapper userMapper;

    public MetricEventServiceImpl(CommercialMetricEventMapper eventMapper,
                                  CommercialMetricRollupMapper rollupMapper,
                                  AnalysisConsentMapper consentMapper,
                                  UserMapper userMapper) {
        this.eventMapper = eventMapper;
        this.rollupMapper = rollupMapper;
        this.consentMapper = consentMapper;
        this.userMapper = userMapper;
    }

    @Override
    public CommercialMetricEvent record(MetricCode code, Long userId, Instant occurredAt,
                                        String contextType, String contextId,
                                        String dimA, String dimB, Map<String, Object> props) {
        if (code == null || occurredAt == null) {
            throw new IllegalArgumentException("metric code and occurrence time are required");
        }
        Map<String, Object> safeProps = sanitizeProps(code, props);

        if (userId != null) {
            User user = userMapper.selectById(userId);
            // Test-account isolation: only real, active human accounts enter the metric store.
            // Seed/demo/sandbox/system traffic never pollutes commercial numerators.
            if (user == null || !"HUMAN".equals(user.accountKind) || !"ACTIVE".equals(user.status)) {
                log.debug("metric {} skipped for non-human/inactive account {}", code, userId);
                return null;
            }
            if (code.requiresAnalysisConsent && !analysisConsentGranted(userId)) {
                log.debug("metric {} skipped: analysis consent declined by {}", code, userId);
                return null;
            }
        }

        LocalDate anchorDate = LocalDate.ofInstant(occurredAt, ANCHOR_ZONE);
        CommercialMetricEvent row = new CommercialMetricEvent();
        row.eventKey = buildEventKey(code, userId, contextType, contextId, occurredAt);
        row.metricCode = code.name();
        row.pathway = code.pathway;
        row.userId = userId;
        row.occurredAtUtc = java.time.LocalDateTime.ofInstant(occurredAt,
                java.time.ZoneOffset.UTC);
        row.anchorWeek = anchorDate.get(WeekFields.ISO.weekBasedYear())
                + "-W" + String.format("%02d", anchorDate.get(WeekFields.ISO.weekOfWeekBasedYear()));
        row.anchorDay = anchorDate.toString();
        row.contextType = truncate(contextType, 32);
        row.contextId = truncate(contextId, 64);
        row.dimA = truncate(dimA, 32);
        row.dimB = truncate(dimB, 64);
        row.props = JsonUtils.toJson(safeProps);
        row.analysisConsentVersion = DEFAULT_CONSENT_VERSION;
        row.ingestSource = "SERVER_CONFIRMED";
        row.anonymized = false;
        try {
            eventMapper.insert(row);
            return row;
        } catch (DuplicateKeyException duplicate) {
            // At-least-once emitters retry safely: the natural key already landed.
            log.debug("metric {} deduplicated by event key", code);
            return null;
        }
    }

    /**
     * Account deletion contract: the user's rows leave the event store entirely (deleted
     * users must not remain individually trackable), but per-metric-per-week aggregate
     * counts — plus the K2 activation-cohort marker and rights affected-record totals —
     * are merged into tb_commercial_metric_rollup so historical denominators and the
     * original cohort sizes stay stable.
     */
    @Override
    public int anonymizeUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        java.util.List<CommercialMetricEvent> rows = eventMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CommercialMetricEvent>()
                        .eq("user_id", userId));
        if (rows.isEmpty()) {
            return 0;
        }
        String activationWeek = rows.stream()
                .filter(r -> MetricCode.PRIVATE_DIALOG_COMPLETED.name().equals(r.metricCode))
                .min(java.util.Comparator.comparing(r -> r.anchorDay))
                .map(r -> r.anchorWeek)
                .orElse(null);

        Map<String, long[]> counts = new java.util.HashMap<>();
        for (CommercialMetricEvent row : rows) {
            long[] slot = counts.computeIfAbsent(row.metricCode + "@" + row.anchorWeek,
                    k -> new long[2]);
            slot[0]++;
            if (MetricCode.RIGHTS_ACTION_COMPLETED.name().equals(row.metricCode)) {
                slot[1] += propLong(row.props, "affectedCount");
            }
        }
        for (Map.Entry<String, long[]> entry : counts.entrySet()) {
            String[] key = entry.getKey().split("@", 2);
            mergeRollup(key[0], key[1], entry.getValue()[0], entry.getValue()[1]);
        }
        if (activationWeek != null) {
            mergeRollup(K2_ACTIVATION_ROLLUP, activationWeek, 1, 0);
        }
        eventMapper.delete(new UpdateWrapper<CommercialMetricEvent>().eq("user_id", userId));
        return rows.size();
    }

    private void mergeRollup(String metricCode, String anchorWeek, long count, long affected) {
        CommercialMetricRollup existing = rollupMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CommercialMetricRollup>()
                        .eq("metric_code", metricCode).eq("anchor_week", anchorWeek));
        if (existing == null) {
            CommercialMetricRollup created = new CommercialMetricRollup();
            created.metricCode = metricCode;
            created.anchorWeek = anchorWeek;
            created.anonymizedCount = count;
            created.affectedTotal = affected;
            rollupMapper.insert(created);
        } else {
            existing.anonymizedCount += count;
            existing.affectedTotal += affected;
            rollupMapper.updateById(existing);
        }
    }

    private static long propLong(String propsJson, String key) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON
                    .readTree(propsJson == null ? "{}" : propsJson);
            return node.hasNonNull(key) ? node.get(key).asLong(0L) : 0L;
        } catch (Exception malformed) {
            return 0L;
        }
    }

    @Override
    public boolean analysisConsentGranted(Long userId) {
        AnalysisConsent consent = consentMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AnalysisConsent>()
                        .eq("user_id", userId));
        // No explicit refusal means the default product-necessary aggregate policy applies.
        // CP-07 replaces this default with the full versioned consent contract.
        return consent == null || !"DECLINED".equals(consent.status);
    }

    /**
     * Structural P0 guard: only allowlisted scalar keys survive, values are stringified,
     * length-capped and null/blank-dropped. Raw conversation, letter or safety text has
     * no path into this table.
     */
    private Map<String, Object> sanitizeProps(MetricCode code, Map<String, Object> props) {
        Map<String, Object> safe = new HashMap<>();
        if (props == null) {
            return safe;
        }
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!code.allowedProps.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "prop key not allowlisted for metric " + code + ": " + entry.getKey());
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String rendered = value instanceof String s ? s : String.valueOf(value);
            if (rendered.length() > MAX_PROP_VALUE_LENGTH) {
                rendered = rendered.substring(0, MAX_PROP_VALUE_LENGTH);
            }
            if (!rendered.isBlank()) {
                safe.put(entry.getKey(), rendered);
            }
        }
        return safe;
    }

    private String buildEventKey(MetricCode code, Long userId, String contextType,
                                 String contextId, Instant occurredAt) {
        String identity = userId == null ? "platform" : String.valueOf(userId);
        String context = (contextType == null ? "-" : contextType)
                + ":" + (contextId == null ? "-" : contextId);
        return code.name() + "|" + identity + "|" + context + "|" + occurredAt.toEpochMilli();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

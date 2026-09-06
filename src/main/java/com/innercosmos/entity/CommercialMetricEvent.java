package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-03 commercial-cn metric event. One row per server-confirmed product fact feeding
 * K1/K2/K3 and the G-SAFE/G-TRUST gate metrics. See
 * docs/commercialization/ledger/metric-dictionary.md for the per-metric SQL contracts.
 *
 * <p>Privacy contracts enforced at write time by {@code MetricEventService}:
 * props never contain P0 content (per-metric key allowlist), timestamps are UTC with
 * precomputed Asia/Shanghai week/day anchors, and account deletion nulls {@code userId}
 * and scrubs props while keeping the row so historical denominators do not shrink.</p>
 */
@TableName("tb_commercial_metric_event")
public class CommercialMetricEvent extends BaseEntity {
    /** Natural idempotency key: metric|user|context|occurredAtMillis. */
    public String eventKey;
    public String metricCode;
    /** PRIVATE, CONNECTED or PLATFORM; null only for legacy rows. */
    public String pathway;
    /** Null after anonymization (account deletion). */
    public Long userId;
    /** Business occurrence time in UTC. */
    public LocalDateTime occurredAtUtc;
    /** Asia/Shanghai ISO week, e.g. 2026-W36. */
    public String anchorWeek;
    /** Asia/Shanghai local date, e.g. 2026-09-06. */
    public String anchorDay;
    public String contextType;
    public String contextId;
    /** Generic dimension: risk level for SAFETY_INCIDENT, action for RIGHTS_ACTION_COMPLETED. */
    public String dimA;
    /** Generic dimension: risk type for SAFETY_INCIDENT. */
    public String dimB;
    /** P0-free JSON of allowlisted scalar props. */
    public String props;
    public String analysisConsentVersion;
    /** SERVER_CONFIRMED or BACKFILL (late/refund corrections). */
    public String ingestSource;
    public Boolean anonymized;
}

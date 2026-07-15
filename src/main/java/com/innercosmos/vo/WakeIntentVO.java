package com.innercosmos.vo;

import com.innercosmos.entity.WakeIntent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** User-safe view: omits worker lease tokens and converts stored UTC instants to local wall time. */
public record WakeIntentVO(Long id, String purpose, String reasonForUser, String content,
                           LocalDateTime earliestAt, LocalDateTime preferredAt, LocalDateTime latestAt,
                           String timezone, String status, String decisionPolicyVersion,
                           Long contextSessionId, Long supersedesIntentId, String userFeedback) {
    public static WakeIntentVO from(WakeIntent intent) {
        ZoneId zone;
        try { zone = ZoneId.of(intent.timezone); }
        catch (RuntimeException ignored) { zone = ZoneId.of("Asia/Shanghai"); }
        return new WakeIntentVO(intent.id, intent.purpose, intent.reasonForUser, intent.content,
            local(intent.earliestAt, zone), local(intent.preferredAt, zone), local(intent.latestAt, zone),
            zone.getId(), intent.status, intent.decisionPolicyVersion, intent.contextSessionId,
            intent.supersedesIntentId, intent.userFeedback);
    }

    private static LocalDateTime local(LocalDateTime utc, ZoneId zone) {
        return utc == null ? null : utc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime();
    }
}

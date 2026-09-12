package com.innercosmos.service.moderation;

import com.innercosmos.entity.ModerationCase;
import java.util.List;

/**
 * CP-36 case backend: reports become prioritized SLA-tracked cases; assignment, resolution
 * and appeal are audited; the reporter's identity stays behind an authorization flag in
 * moderator views (protect-the-reporter isolation).
 */
public interface ModerationCaseService {

    /** Open (or return the existing) case for a freshly recorded report. */
    ModerationCase onReport(Long reportId, String targetType, Long targetId, String reason);

    ModerationCase assign(Long caseId, Long moderatorId);

    /** Resolve or dismiss; wrong sanctions are reversible and stay audited. */
    ModerationCase resolve(Long caseId, Long moderatorId, boolean dismiss, String resolution);

    /** The reported party (or their proxy) appeals a resolution. */
    ModerationCase appeal(Long caseId, String note);

    /** Queue ordered by priority then SLA due time. */
    List<ModerationCase> queue(String statusFilter, int limit);

    /** Breach statistics: how many RESOLVED cases closed after their SLA clock. */
    record SlaStats(long totalResolved, long breached) {
    }

    SlaStats slaStats();

    /** Moderator view with reporter isolation: no reporter identity without authorization. */
    record CaseView(Long id, String targetType, Long targetId, String priority, String status,
                    String assigneeId, String slaDueAt, String resolution, String appealNote,
                    String reporterIdentity) {
    }

    List<CaseView> views(String statusFilter, int limit, boolean reporterIdentityAuthorized);
}

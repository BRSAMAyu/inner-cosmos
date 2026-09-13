package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.AdminActionLog;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.ModelConfig;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.service.AdminService;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.vo.AdminOverviewVO;
import com.innercosmos.vo.UserProfileVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController extends BaseController {
    private final AdminService adminService;
    private final com.innercosmos.service.minor.MinorProtectionService minorProtectionService;
    private final com.innercosmos.service.identity.AccountSecurityService accountSecurityService;
    private final com.innercosmos.service.moderation.ModerationCaseService moderationCaseService;
    // CP-40 DLQ dashboard: the outbox repository only exists when inner-cosmos.events.outbox.enabled=true
    // (default false), so it must be resolved lazily — a hard dependency would break every context
    // that starts AdminController without the outbox.
    private final org.springframework.beans.factory.ObjectProvider<com.innercosmos.event.reliable.JdbcOutboxRepository> outboxRepositories;

    public AdminController(AdminService adminService,
                           com.innercosmos.service.minor.MinorProtectionService minorProtectionService,
                           com.innercosmos.service.identity.AccountSecurityService accountSecurityService,
                           com.innercosmos.service.moderation.ModerationCaseService moderationCaseService,
                           org.springframework.beans.factory.ObjectProvider<com.innercosmos.event.reliable.JdbcOutboxRepository> outboxRepositories) {
        this.adminService = adminService;
        this.minorProtectionService = minorProtectionService;
        this.accountSecurityService = accountSecurityService;
        this.moderationCaseService = moderationCaseService;
        this.outboxRepositories = outboxRepositories;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserProfileVO>> users(HttpSession session) {
        requireAdmin(session);
        List<UserProfileVO> result = adminService.users().stream()
                .map(UserProfileVO::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    /**
     * CP-08 minor intercept: move an account into MINOR_RESTRICTED (adult companion/social
     * surfaces stop; safety resources and the appeal path stay available).
     */
    @org.springframework.web.bind.annotation.PostMapping("/users/{id}/minor-flag")
    public ApiResponse<java.util.Map<String, Object>> flagMinor(@org.springframework.web.bind.annotation.PathVariable Long id,
                                                                @org.springframework.web.bind.annotation.RequestBody(required = false) java.util.Map<String, String> body,
                                                                HttpSession session) {
        requireAdmin(session);
        boolean changed = minorProtectionService.flagMinor(id,
                body == null ? null : body.get("reason"));
        return ApiResponse.ok(java.util.Map.of("userId", id, "restricted", changed));
    }

    /** CP-13: freeze an account (login stops immediately); audit-trailed. */
    @org.springframework.web.bind.annotation.PostMapping("/users/{id}/freeze")
    public ApiResponse<java.util.Map<String, Object>> freezeUser(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) java.util.Map<String, String> body,
            HttpSession session) {
        requireAdmin(session);
        boolean changed = accountSecurityService.freeze(id, currentUserId(session),
                body == null ? null : body.get("reason"));
        return ApiResponse.ok(java.util.Map.of("userId", id, "frozen", changed));
    }

    /** CP-13: unfreeze a frozen account (minor-restricted accounts are handled by appeals). */
    @org.springframework.web.bind.annotation.PostMapping("/users/{id}/unfreeze")
    public ApiResponse<java.util.Map<String, Object>> unfreezeUser(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            HttpSession session) {
        requireAdmin(session);
        boolean changed = accountSecurityService.unfreeze(id, currentUserId(session), null);
        return ApiResponse.ok(java.util.Map.of("userId", id, "unfrozen", changed));
    }

    /** CP-36: the moderation queue, ordered by priority then SLA due time. */
    @GetMapping("/moderation/cases")
    public ApiResponse<java.util.List<com.innercosmos.service.moderation.ModerationCaseService.CaseView>> moderationCases(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false")
            boolean reporterIdentityAuthorized,
            HttpSession session) {
        requireAdmin(session);
        // Reporter identity needs a separate, auditable authorization decision; the default
        // moderator view never carries it (protect-the-reporter isolation).
        return ApiResponse.ok(moderationCaseService.views(status, limit, reporterIdentityAuthorized));
    }

    @org.springframework.web.bind.annotation.PostMapping("/moderation/cases/{caseId}/assign")
    public ApiResponse<com.innercosmos.entity.ModerationCase> assignModerationCase(
            @org.springframework.web.bind.annotation.PathVariable Long caseId, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(moderationCaseService.assign(caseId, currentUserId(session)));
    }

    public record ModerationResolveRequest(boolean dismiss, String resolution) {
    }

    @org.springframework.web.bind.annotation.PostMapping("/moderation/cases/{caseId}/resolve")
    public ApiResponse<com.innercosmos.entity.ModerationCase> resolveModerationCase(
            @org.springframework.web.bind.annotation.PathVariable Long caseId,
            @org.springframework.web.bind.annotation.RequestBody ModerationResolveRequest request,
            HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(moderationCaseService.resolve(
                caseId, currentUserId(session), request.dismiss(), request.resolution()));
    }

    /** CP-08: pending minor-intercept appeals for human review. */
    @GetMapping("/minor-appeals")
    public ApiResponse<java.util.List<com.innercosmos.entity.MinorAppeal>> minorAppeals(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(minorProtectionService.pendingAppeals());
    }

    /** CP-08: decide an appeal; accepting restores the misjudged adult account. */
    @org.springframework.web.bind.annotation.PostMapping("/minor-appeals/{appealId}/resolve")
    public ApiResponse<com.innercosmos.entity.MinorAppeal> resolveMinorAppeal(
            @org.springframework.web.bind.annotation.PathVariable Long appealId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body,
            HttpSession session) {
        requireAdmin(session);
        Long adminId = currentUserId(session);
        boolean accept = Boolean.parseBoolean(body.get("accept"));
        return ApiResponse.ok(minorProtectionService.resolve(appealId, accept, adminId, body.get("note")));
    }

    @GetMapping("/capsules")
    public ApiResponse<List<EchoCapsule>> capsules(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword,
                                                   HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(adminService.capsules(status, keyword));
    }

    @GetMapping("/reports")
    public ApiResponse<List<ReportRecord>> reports(@RequestParam(required = false) String status, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(adminService.reports(status));
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewVO> overview(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(adminService.overview());
    }

    @PostMapping("/capsules/{id}/hide")
    public ApiResponse<Void> hideCapsule(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        requireAdmin(session);
        adminService.hideCapsule(currentUserId(session), id, reason(body));
        return ApiResponse.ok(null);
    }

    @PostMapping("/capsules/{id}/restore")
    public ApiResponse<Void> restoreCapsule(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        requireAdmin(session);
        adminService.restoreCapsule(currentUserId(session), id, reason(body));
        return ApiResponse.ok(null);
    }

    @PostMapping("/reports/{id}/resolve")
    public ApiResponse<Void> resolveReport(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        requireAdmin(session);
        adminService.resolveReport(currentUserId(session), id, body.get("action"), reason(body));
        return ApiResponse.ok(null);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminActionLog>> auditLogs(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(adminService.auditLogs());
    }

    @PostMapping("/users/{id}/disable")
    public ApiResponse<Void> disableUser(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        adminService.disableUser(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/{id}/enable")
    public ApiResponse<Void> enableUser(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        adminService.enableUser(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/safety-events")
    public ApiResponse<List<SafetyEvent>> safetyEvents(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(adminService.safetyEvents());
    }

    @GetMapping("/model-config")
    public ApiResponse<List<ModelConfig>> modelConfig(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(adminService.modelConfigs());
    }

    @PostMapping("/model-config")
    public ApiResponse<Void> updateModelConfig(@RequestBody ModelConfig config, HttpSession session) {
        requireAdmin(session);
        adminService.updateModelConfig(config);
        return ApiResponse.ok(null);
    }

    private String reason(Map<String, String> body) {
        if (body == null) return "";
        return body.getOrDefault("reason", "");
    }

    // ===================== CP-40: outbox dead-letter (DLQ) dashboard =====================
    // Read/replay surface over the transactional outbox's DEAD rows (poison events that exhausted
    // their retries and events with no registered handler). Presentation is limited to rows that
    // really exist in tb_outbox_event; an empty dead-letter queue shows as an honest empty page.
    // The segment follows the same session-based requireAdmin gate as every other admin endpoint
    // above (Spring Security only enforces "authenticated" for /api/**).

    /** GET /api/admin/outbox/dead?limit=&offset= — current DEAD rows with failure forensics. */
    @GetMapping("/outbox/dead")
    public ApiResponse<Map<String, Object>> outboxDeadLetters(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            HttpSession session) {
        requireAdmin(session);
        com.innercosmos.event.reliable.JdbcOutboxRepository outbox = outboxRepositories.getIfAvailable();
        if (outbox == null) {
            // Outbox disabled (JDBC_OUTBOX_ENABLED=false, the default): report that honestly
            // instead of pretending there is an empty queue behind a running one.
            return ApiResponse.ok(Map.of("enabled", false, "total", 0L, "entries", List.of()));
        }
        int cappedLimit = Math.min(Math.max(limit, 1), 200);
        List<com.innercosmos.event.reliable.OutboxDeadLetter> entries = outbox.findDead(cappedLimit, offset);
        return ApiResponse.ok(Map.of(
                "enabled", true,
                "total", outbox.deadCount(),
                "limit", cappedLimit,
                "offset", Math.max(offset, 0),
                "entries", entries));
    }

    /**
     * POST /api/admin/outbox/dead/{eventId}/replay — requeue one DEAD event (status back to
     * PENDING, attempts zeroed, available now). Idempotency semantics: the update only ever
     * transitions a row that is currently DEAD, so a repeat call for an already-replayed or
     * otherwise live event is rejected with 409 CONFLICT (and an unknown eventId with 404)
     * instead of silently resetting live state; duplicate side effects after a replay remain
     * impossible through the tb_inbox_receipt consumer dedup. Replay requeues the row — whether
     * it is then processed successfully is the worker's honest outcome, not this endpoint's.
     */
    @PostMapping("/outbox/dead/{eventId}/replay")
    public ApiResponse<Map<String, Object>> replayOutboxDeadLetter(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID eventId,
            HttpSession session) {
        requireAdmin(session);
        com.innercosmos.event.reliable.JdbcOutboxRepository outbox = outboxRepositories.getIfAvailable();
        if (outbox == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "事务性 outbox 未启用，无死信可重放");
        }
        boolean replayed = outbox.replayDead(eventId);
        if (!replayed) {
            String status = outbox.statusOf(eventId);
            if (status == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "outbox 事件不存在: " + eventId);
            }
            throw new BusinessException(ErrorCode.CONFLICT,
                    "outbox 事件当前状态为 " + status + "，仅 DEAD 状态可重放");
        }
        return ApiResponse.ok(Map.of(
                "eventId", eventId.toString(),
                "status", outbox.statusOf(eventId)));
    }
}

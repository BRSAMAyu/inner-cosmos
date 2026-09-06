package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.AdminActionLog;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.ModelConfig;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.service.AdminService;
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

    public AdminController(AdminService adminService,
                           com.innercosmos.service.minor.MinorProtectionService minorProtectionService,
                           com.innercosmos.service.identity.AccountSecurityService accountSecurityService) {
        this.adminService = adminService;
        this.minorProtectionService = minorProtectionService;
        this.accountSecurityService = accountSecurityService;
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
}

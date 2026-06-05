package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.AdminActionLog;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.ModelConfig;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.AdminService;
import com.innercosmos.service.UserService;
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
    private final UserService userService;

    public AdminController(AdminService adminService, UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    private void requireAdmin(HttpSession session) {
        Long userId = currentUserId(session);
        User user = userService.current(userId);
        if (!Constants.ROLE_ADMIN.equals(user.role)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要管理员权限");
        }
    }

    @GetMapping("/users")
    public ApiResponse<List<UserProfileVO>> users(HttpSession session) {
        requireAdmin(session);
        List<UserProfileVO> result = adminService.users().stream()
                .map(UserProfileVO::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
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

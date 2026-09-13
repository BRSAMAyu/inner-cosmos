package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.DialogSessionUpdateRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.DialogService;
import com.innercosmos.vo.DialogSessionSummaryVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dialog/session")
public class DialogController extends BaseController {

    /** CP-18 honest cross-session continuity; optional for legacy direct construction. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.innercosmos.service.continuity.SessionContinuityService sessionContinuityService;

    /**
     * CP-18: the honest opening context for the user's next conversation — the SUPPLY
     * endpoint of the opening card. CP-18 §2-13: when the owner withdrew opening
     * visibility, serve the explicit withdrawn marker with zero prior material. The
     * withdrawal is a display choice only; continuity facts keep being recorded.
     */
    @GetMapping("/continuity")
    public ApiResponse<com.innercosmos.service.continuity.SessionContinuityService.OpeningContext> continuity(
            HttpSession session) {
        Long userId = currentUserId(session);
        if (!sessionContinuityService.visibility(userId).openingVisible()) {
            return ApiResponse.ok(
                    com.innercosmos.service.continuity.SessionContinuityService.OpeningContext.withdrawn());
        }
        return ApiResponse.ok(sessionContinuityService.openingContext(userId));
    }

    /** CP-18 §2-13: the owner's current opening-visibility switch (default open). */
    @GetMapping("/continuity/visibility")
    public ApiResponse<com.innercosmos.service.continuity.SessionContinuityService.VisibilityState> continuityVisibility(
            HttpSession session) {
        return ApiResponse.ok(sessionContinuityService.visibility(currentUserId(session)));
    }

    /** CP-18 §2-13: toggle the opening-visibility switch. Owner-only by session identity. */
    @PutMapping("/continuity/visibility")
    public ApiResponse<com.innercosmos.service.continuity.SessionContinuityService.VisibilityState> setContinuityVisibility(
            @RequestBody Map<String, Boolean> body, HttpSession session) {
        Boolean openingVisible = body == null ? null : body.get("openingVisible");
        if (openingVisible == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "openingVisible 不能为空");
        }
        return ApiResponse.ok(sessionContinuityService.setOpeningVisible(
                currentUserId(session), openingVisible));
    }
    private final DialogService dialogService;

    public DialogController(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @PostMapping("/create")
    public ApiResponse<DialogSession> create(@RequestBody SessionCreateRequest request, HttpSession session) {
        return ApiResponse.ok(dialogService.create(currentUserId(session), request));
    }

    @GetMapping
    public ApiResponse<List<DialogSessionSummaryVO>> sessions(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            HttpSession session) {
        return ApiResponse.ok(dialogService.sessions(
                currentUserId(session), beforeId, limit, includeArchived));
    }

    @GetMapping("/current")
    public ApiResponse<DialogSessionSummaryVO> current(HttpSession session) {
        return ApiResponse.ok(dialogService.current(currentUserId(session)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DialogSessionSummaryVO> get(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(dialogService.get(currentUserId(session), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DialogSessionSummaryVO> update(
            @PathVariable Long id,
            @RequestBody DialogSessionUpdateRequest request,
            HttpSession session) {
        return ApiResponse.ok(dialogService.update(currentUserId(session), id, request));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<DialogMessage>> messages(@PathVariable Long id, HttpSession session) {
        dialogService.verifyOwnership(currentUserId(session), id);
        return ApiResponse.ok(dialogService.messages(id));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<DialogSession> finish(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(dialogService.finish(currentUserId(session), id));
    }
}

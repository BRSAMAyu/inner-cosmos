package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.UserCorrection;
import com.innercosmos.entity.ClaimPropagation;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.UserCorrectionService;
import com.innercosmos.vo.CorrectionConfirmationVO;
import com.innercosmos.vo.CorrectionImpactVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RUN-005 — the disruptive feedback loop: let the user authoritatively correct
 * Aurora's understanding of them ("这不太是我"). Corrections are stored and re-enter
 * the Aurora prompt with precedence over inferences, so the model visibly adapts.
 *
 * Free-form self-understanding corrections default to a fixed target so they satisfy
 * the NOT NULL columns (target_id, field_name) without forcing the client to know the
 * schema: targetType=AURORA_UNDERSTANDING, targetId=0, fieldName=self_understanding.
 */
@RestController
@RequestMapping("/api/aurora/corrections")
public class UserCorrectionController extends BaseController {
    private static final String DEFAULT_TARGET_TYPE = "AURORA_UNDERSTANDING";
    private static final Long DEFAULT_TARGET_ID = 0L;
    private static final String DEFAULT_FIELD = "self_understanding";
    private static final int LIST_LIMIT = 20;

    private final UserCorrectionService userCorrectionService;

    public UserCorrectionController(UserCorrectionService userCorrectionService) {
        this.userCorrectionService = userCorrectionService;
    }

    @PostMapping("/preview")
    public ApiResponse<CorrectionImpactVO> preview(@RequestBody CorrectionCommand command,
                                                   HttpSession session) {
        return ApiResponse.ok(userCorrectionService.preview(currentUserId(session), command));
    }

    @PostMapping("/confirm")
    public ApiResponse<CorrectionConfirmationVO> confirm(@RequestBody CorrectionCommand command,
                                                         HttpSession session) {
        return ApiResponse.ok(userCorrectionService.confirm(currentUserId(session), command));
    }

    @GetMapping("/claims")
    public ApiResponse<List<UnderstandingClaim>> claims(@RequestParam(required = false) String claimKey,
                                                        HttpSession session) {
        return ApiResponse.ok(userCorrectionService.claimHistory(currentUserId(session), claimKey));
    }

    @GetMapping("/{id}/propagation")
    public ApiResponse<List<ClaimPropagation>> propagation(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(userCorrectionService.propagation(currentUserId(session), id));
    }

    @PostMapping
    public ApiResponse<UserCorrection> record(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        String newValue = trimToNull(body.get("newValue"));
        if (newValue == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请告诉我哪里说得不对");
        }
        String oldValue = trimToNull(body.get("oldValue"));
        String reason = trimToNull(body.get("reason"));
        String fieldName = orDefault(trimToNull(body.get("fieldName")), DEFAULT_FIELD);
        // RUN-006: a portrait-dimension calibration ("Aurora 眼中的你" page) tags itself
        // PORTRAIT_DIM so the prompt routes it into the soft-coexist block; free-form
        // chat corrections keep the authoritative AURORA_UNDERSTANDING default.
        String targetType = orDefault(trimToNull(body.get("targetType")), DEFAULT_TARGET_TYPE);
        UserCorrection saved = userCorrectionService.recordCorrection(
                userId, targetType, DEFAULT_TARGET_ID, fieldName, oldValue, newValue, reason);
        return ApiResponse.ok(saved);
    }

    @GetMapping
    public ApiResponse<List<UserCorrection>> recent(HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(userCorrectionService.recentCorrections(userId, LIST_LIMIT));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        userCorrectionService.deleteCorrection(userId, id);
        return ApiResponse.ok(null);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}

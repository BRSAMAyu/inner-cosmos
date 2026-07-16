package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.dto.CapsuleVisibilityRequest;
import com.innercosmos.dto.CapsuleSandboxRequest;
import com.innercosmos.dto.CapsuleSandboxFeedbackRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.service.DataMaskingService;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.CapsuleSandboxService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.CapsuleSandboxFeedback;
import com.innercosmos.vo.CapsuleFidelitySummaryVO;
import com.innercosmos.vo.CapsulePreviewVO;
import com.innercosmos.vo.CapsuleSandboxVO;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/capsule", "/api/v1/capsule"})
public class CapsuleController extends BaseController {
    private final CapsuleService capsuleService;
    private final DataMaskingService dataMaskingService;
    private final CapsuleGenomeService genomeService;
    private final CapsuleSandboxService sandboxService;
    private final DataUseGrantService dataUseGrantService;

    public CapsuleController(CapsuleService capsuleService, DataMaskingService dataMaskingService,
                             CapsuleGenomeService genomeService, CapsuleSandboxService sandboxService,
                             DataUseGrantService dataUseGrantService) {
        this.capsuleService = capsuleService;
        this.dataMaskingService = dataMaskingService;
        this.genomeService = genomeService;
        this.sandboxService = sandboxService;
        this.dataUseGrantService = dataUseGrantService;
    }

    @GetMapping("/my")
    public ApiResponse<List<EchoCapsule>> my(HttpSession session) {
        return ApiResponse.ok(capsuleService.myCapsules(currentUserId(session)));
    }

    @PostMapping("/create-from-memory")
    public ApiResponse<EchoCapsule> create(@RequestBody CapsuleCreateRequest request, HttpSession session) {
        return ApiResponse.ok(capsuleService.createFromMemory(currentUserId(session), request));
    }

    @PostMapping("/create-simulator")
    public ApiResponse<EchoCapsule> createSimulator(@RequestBody CapsuleCreateRequest request, HttpSession session) {
        return ApiResponse.ok(capsuleService.createSimulatorCapsule(currentUserId(session), request));
    }

    @GetMapping("/{id}")
    public ApiResponse<EchoCapsule> detail(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(capsuleService.getOwnedCapsule(currentUserId(session), id));
    }

    @PostMapping("/{id}/visibility")
    public ApiResponse<EchoCapsule> visibility(@PathVariable Long id,
                                               @RequestBody CapsuleVisibilityRequest request,
                                               HttpSession session) {
        return ApiResponse.ok(capsuleService.updateVisibility(
                currentUserId(session),
                id,
                request.visibilityStatus,
                request.isPublic
        ));
    }

    @PostMapping("/preview-from-memory")
    public ApiResponse<CapsulePreviewVO> previewFromMemory(@RequestBody Map<String, Object> body, HttpSession session) {
        Long userId = currentUserId(session);
        Object rawMemoryIds = body.get("memoryIds");
        List<Long> memoryIds = parseMemoryIds(rawMemoryIds);
        String privacyLevel = (String) body.getOrDefault("privacyLevel", "STRICT");
        List<String> allowTopics = (List<String>) body.getOrDefault("allowTopics", List.of());
        List<String> blockedTopics = (List<String>) body.getOrDefault("blockedTopics", List.of());
        return ApiResponse.ok(dataMaskingService.previewFromMemory(userId, memoryIds, privacyLevel, allowTopics, blockedTopics));
    }

    @PostMapping("/user-mirror/preview")
    public ApiResponse<CapsulePreviewVO> previewUserMirror(HttpSession session) {
        return ApiResponse.ok(capsuleService.previewUserMirror(currentUserId(session)));
    }

    @PostMapping("/{id}/context")
    public ApiResponse<EchoCapsule> updateContext(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body,
                                                  HttpSession session) {
        return ApiResponse.ok(capsuleService.updateContext(currentUserId(session), id, body));
    }

    @GetMapping("/{id}/context-preview")
    public ApiResponse<Map<String, Object>> contextPreview(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(capsuleService.contextPreview(currentUserId(session), id));
    }

    @GetMapping("/{id}/boundary")
    public ResponseEntity<ApiResponse<CapsuleBoundary>> getBoundary(@PathVariable Long id, HttpSession session) {
        CapsuleBoundary boundary = capsuleService.getBoundary(currentUserId(session), id); // M-023
        int version = boundary == null || boundary.version == null ? 1 : boundary.version;
        return ResponseEntity.ok().eTag(String.valueOf(version)).body(ApiResponse.ok(boundary));
    }

    @PostMapping("/{id}/boundary")
    public ResponseEntity<ApiResponse<CapsuleBoundary>> updateBoundary(
            @PathVariable Long id, @RequestBody CapsuleBoundary boundary,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpSession session, HttpServletRequest request) {
        Long userId = currentUserId(session);
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/")
                && (ifMatch == null || ifMatch.isBlank())) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "If-Match is required for v1 boundary updates");
        }
        Integer expectedVersion = parseVersion(ifMatch);
        CapsuleBoundary updated = capsuleService.updateBoundary(userId, id, boundary, expectedVersion);
        int version = updated.version == null ? 1 : updated.version;
        return ResponseEntity.ok().eTag(String.valueOf(version)).body(ApiResponse.ok(updated));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archiveCapsule(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        capsuleService.archiveCapsule(userId, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/genome-history")
    public ApiResponse<List<CapsuleGenomeVersion>> genomeHistory(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(genomeService.history(currentUserId(session), id));
    }

    @GetMapping("/{id}/data-use-grants")
    public ApiResponse<List<DataUseGrant>> dataUseGrants(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(dataUseGrantService.history(currentUserId(session), id));
    }

    @PostMapping("/{id}/data-use-grants/{grantId}/revoke")
    public ApiResponse<DataUseGrant> revokeDataUseGrant(@PathVariable Long id, @PathVariable Long grantId,
                                                        @RequestBody(required = false) Map<String, Object> body,
                                                        HttpSession session) {
        Object rawReason = body == null ? null : body.get("reason");
        String reason = rawReason == null ? "owner revoked" : String.valueOf(rawReason);
        return ApiResponse.ok(dataUseGrantService.revoke(currentUserId(session), id, grantId, reason));
    }

    @PostMapping("/{id}/genome/recompile")
    public ApiResponse<CapsuleGenomeVersion> recompileGenome(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> body,
                                                              HttpSession session) {
        Object raw = body.get("memoryIds");
        List<Long> ids = parseMemoryIds(raw);
        return ApiResponse.ok(capsuleService.recompileGenome(currentUserId(session), id, ids));
    }

    @PostMapping("/{id}/sandbox/respond")
    public ApiResponse<CapsuleSandboxVO> sandbox(@PathVariable Long id,
                                                  @Valid @RequestBody CapsuleSandboxRequest request,
                                                  HttpSession session) {
        return ApiResponse.ok(sandboxService.respond(currentUserId(session), id, request.question.trim()));
    }

    @PostMapping("/{id}/sandbox/feedback")
    public ApiResponse<CapsuleSandboxFeedback> sandboxFeedback(
            @PathVariable Long id,
            @Valid @RequestBody CapsuleSandboxFeedbackRequest request,
            HttpSession session) {
        return ApiResponse.ok(sandboxService.recordFeedback(currentUserId(session), id, request));
    }

    @GetMapping("/{id}/sandbox/feedback")
    public ApiResponse<List<CapsuleSandboxFeedback>> sandboxFeedback(
            @PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(sandboxService.feedback(currentUserId(session), id));
    }

    @GetMapping("/{id}/sandbox/fidelity")
    public ApiResponse<List<CapsuleFidelitySummaryVO>> sandboxFidelity(
            @PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(sandboxService.fidelitySummary(currentUserId(session), id));
    }

    private Integer parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return null;
        String value = ifMatch.trim();
        if (value.startsWith("W/")) value = value.substring(2);
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        try { return Integer.valueOf(value); }
        catch (NumberFormatException invalid) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST,
                    "If-Match must contain the numeric boundary version");
        }
    }

    private List<Long> parseMemoryIds(Object raw) {
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "memoryIds不能为空");
        }
        List<Long> ids = values.stream().filter(Number.class::isInstance)
                .map(Number.class::cast).map(Number::longValue).toList();
        if (ids.size() != values.size()) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.BAD_REQUEST, "memoryIds必须全部为数字");
        }
        return ids;
    }
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.dto.CapsuleVisibilityRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.service.DataMaskingService;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.vo.CapsulePreviewVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/capsule")
public class CapsuleController extends BaseController {
    private final CapsuleService capsuleService;
    private final DataMaskingService dataMaskingService;
    private final CapsuleGenomeService genomeService;

    public CapsuleController(CapsuleService capsuleService, DataMaskingService dataMaskingService,
                             CapsuleGenomeService genomeService) {
        this.capsuleService = capsuleService;
        this.dataMaskingService = dataMaskingService;
        this.genomeService = genomeService;
    }

    @GetMapping("/my")
    public ApiResponse<List<EchoCapsule>> my(HttpSession session) {
        return ApiResponse.ok(capsuleService.myCapsules(currentUserId(session)));
    }

    @PostMapping("/create-from-memory")
    public ApiResponse<EchoCapsule> create(@RequestBody CapsuleCreateRequest request, HttpSession session) {
        return ApiResponse.ok(capsuleService.createFromMemory(currentUserId(session), request));
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
        if (rawMemoryIds == null) {
            return ApiResponse.fail("BAD_REQUEST", "memoryIds不能为空");
        }
        List<Long> memoryIds = ((List<Number>) rawMemoryIds).stream().map(Number::longValue).collect(Collectors.toList());
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
    public ApiResponse<CapsuleBoundary> getBoundary(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(capsuleService.getBoundary(currentUserId(session), id)); // M-023
    }

    @PostMapping("/{id}/boundary")
    public ApiResponse<Void> updateBoundary(@PathVariable Long id, @RequestBody CapsuleBoundary boundary, HttpSession session) {
        Long userId = currentUserId(session);
        capsuleService.updateBoundary(userId, id, boundary);
        return ApiResponse.ok(null);
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

    @PostMapping("/{id}/genome/recompile")
    public ApiResponse<CapsuleGenomeVersion> recompileGenome(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> body,
                                                              HttpSession session) {
        Object raw = body.get("memoryIds");
        if (!(raw instanceof List<?> values)) return ApiResponse.fail("BAD_REQUEST", "memoryIds不能为空");
        List<Long> ids = values.stream().filter(Number.class::isInstance)
                .map(Number.class::cast).map(Number::longValue).toList();
        if (ids.size() != values.size()) return ApiResponse.fail("BAD_REQUEST", "memoryIds必须全部为数字");
        return ApiResponse.ok(capsuleService.recompileGenome(currentUserId(session), id, ids));
    }
}

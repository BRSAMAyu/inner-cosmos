package com.innercosmos.service;

import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.vo.CapsulePreviewVO;
import java.util.List;
import java.util.Map;

public interface CapsuleService {
    EchoCapsule createFromMemory(Long userId, CapsuleCreateRequest request);

    /**
     * The isolated Simulator capability contract (对齐文档/16 Campaign C): compiles a Genome
     * for testing/research purposes only, from memories the owner has explicitly marked
     * SIMULATOR_AUTHORIZED (a distinct consent scope from normal capsule authorization — no
     * default reuse of a personal capsule's already-authorized memories). The result is
     * permanently private: it can never be published, matched, or reached by real visitor
     * persona chat (enforced in updateVisibility/plazaCapsules/PersonaChatService).
     */
    EchoCapsule createSimulatorCapsule(Long userId, CapsuleCreateRequest request);

    EchoCapsule getOwnedCapsule(Long userId, Long capsuleId);

    EchoCapsule updateVisibility(Long userId, Long capsuleId, String visibilityStatus, Boolean isPublic);

    List<EchoCapsule> myCapsules(Long userId);

    List<EchoCapsule> plazaCapsules();

    List<Map<String, Object>> matchedCapsules(Long userId);

    List<Map<String, Object>> matchedCapsules(Long userId, ResonanceMatchStrategy strategy);

    CapsulePreviewVO previewUserMirror(Long userId);

    EchoCapsule updateContext(Long userId, Long capsuleId, Map<String, Object> body);

    Map<String, Object> contextPreview(Long userId, Long capsuleId);

    CapsuleBoundary getBoundary(Long userId, Long capsuleId);

    void updateBoundary(Long userId, Long capsuleId, CapsuleBoundary boundary);

    void archiveCapsule(Long userId, Long capsuleId);

    Double markLanded(Long userId, Long capsuleId);

    CapsuleGenomeVersion recompileGenome(Long userId, Long capsuleId, List<Long> memoryIds);
}

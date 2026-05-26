package com.innercosmos.service;

import com.innercosmos.vo.CapsulePreviewVO;

import java.util.List;

public interface DataMaskingService {
    CapsulePreviewVO previewFromMemory(Long userId, List<Long> memoryIds,
                                        String privacyLevel, List<String> allowTopics, List<String> blockedTopics);

    String maskText(String raw, String privacyLevel);
}

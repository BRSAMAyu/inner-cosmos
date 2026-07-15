package com.innercosmos.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record StarfieldSceneVO(
        String mode,
        String modeExplanation,
        List<StarfieldVO> stars,
        List<StarfieldVO> accessibleList,
        Map<String, String> legend,
        LocalDateTime generatedAt) {}

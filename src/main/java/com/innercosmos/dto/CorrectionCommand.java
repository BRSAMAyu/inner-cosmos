package com.innercosmos.dto;

public record CorrectionCommand(
        String targetType,
        Long targetId,
        String fieldName,
        String oldValue,
        String newValue,
        String reason) {}

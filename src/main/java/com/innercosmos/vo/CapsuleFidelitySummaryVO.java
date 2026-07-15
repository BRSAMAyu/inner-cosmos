package com.innercosmos.vo;

public record CapsuleFidelitySummaryVO(
        Long genomeVersionId,
        Integer versionNo,
        int totalRatings,
        int likeMeCount,
        int notMeCount,
        int factWrongCount,
        int tooExposedCount,
        int toneWrongCount,
        Double fidelityScore) {}

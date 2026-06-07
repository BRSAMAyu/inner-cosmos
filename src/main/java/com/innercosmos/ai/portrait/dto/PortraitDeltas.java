package com.innercosmos.ai.portrait.dto;

import java.util.List;

public record PortraitDeltas(
        List<Delta> deltas,
        List<RuptureSignal> ruptures,
        List<NewFact> newFacts
) {
    public record Delta(String dim, String valueJson, double confidence, List<String> evidenceTurnIds) {}
    public record RuptureSignal(String event, String userFeedback) {}
    public record NewFact(String factType, String factValue, double confidence) {}
}
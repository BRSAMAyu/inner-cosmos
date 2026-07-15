package com.innercosmos.dto;

import java.util.List;

public record MemoryRetrievalQuery(
        String query,
        String task,
        List<String> allowedLayers,
        Integer maxResults,
        Integer tokenBudget,
        boolean includeContradicted) {}

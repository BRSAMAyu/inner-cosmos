package com.innercosmos.service;

import com.innercosmos.entity.MemoryCard;

import java.util.List;
import java.util.Map;

public interface MemoryEmbeddingIndexService {
    Map<Long, Double> similarities(Long userId, String query, List<MemoryCard> allowedCurrentCards);

    RebuildResult rebuildMissing(int batchSize);

    long pendingCount();

    record RebuildResult(int selected, int indexed, int failed, long remaining) {}
}

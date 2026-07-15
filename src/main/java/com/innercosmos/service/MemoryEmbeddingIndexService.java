package com.innercosmos.service;

import com.innercosmos.entity.MemoryCard;

import java.util.List;
import java.util.Map;

public interface MemoryEmbeddingIndexService {
    Map<Long, Double> similarities(Long userId, String query, List<MemoryCard> allowedCurrentCards);
}

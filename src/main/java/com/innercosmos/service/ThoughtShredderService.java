package com.innercosmos.service;

import com.innercosmos.entity.MemoryCard;
import java.util.List;

public interface ThoughtShredderService {
    MemoryCard process(Long userId, String rawText);

    List<MemoryCard> history(Long userId);

    void settle(Long userId, Long id);

    void delete(Long userId, Long id);
}

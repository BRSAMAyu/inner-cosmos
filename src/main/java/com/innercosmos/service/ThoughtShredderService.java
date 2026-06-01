package com.innercosmos.service;

import com.innercosmos.entity.MemoryCard;
import com.innercosmos.vo.ShredderResultVO;
import java.util.List;

public interface ThoughtShredderService {
    ShredderResultVO process(Long userId, String rawText, String originalHandlingMode);

    List<MemoryCard> history(Long userId);

    void settle(Long userId, Long id);

    void delete(Long userId, Long id);
}

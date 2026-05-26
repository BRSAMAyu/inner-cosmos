package com.innercosmos.service;

import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.vo.DailyRecordVO;
import com.innercosmos.vo.StarfieldDetailVO;
import com.innercosmos.vo.StarfieldVO;
import java.util.List;

public interface MemoryService {
    MemoryCard extractFromSession(Long userId, Long sessionId);

    List<MemoryCard> listCards(Long userId);

    List<StarfieldVO> starfield(Long userId);

    DailyRecordVO latestDailyRecord(Long userId);

    List<String> topGravitySummaries(Long userId, int limit);

    StarfieldDetailVO starfieldDetail(Long userId, Long cardId);

    void updateImportance(Long userId, Long cardId, Double importance);

    void archiveCard(Long userId, Long cardId);

    List<DailyRecord> listDailyRecords(Long userId);

    void acceptDailyRecord(Long userId, Long recordId);
}

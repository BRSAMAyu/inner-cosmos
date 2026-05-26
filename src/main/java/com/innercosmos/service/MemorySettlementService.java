package com.innercosmos.service;

import com.innercosmos.vo.DailyRecordVO;

public interface MemorySettlementService {
    void settleSession(Long userId, Long sessionId);

    DailyRecordVO generateDailyRecord(Long userId, Long sessionId);

    void updateThemeAggregation(Long userId);
}

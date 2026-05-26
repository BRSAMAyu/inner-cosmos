package com.innercosmos.service;

import com.innercosmos.entity.DailyRecord;
import com.innercosmos.vo.DashboardVO;
import java.util.List;

public interface DashboardService {
    DashboardVO summary(Long userId);

    List<DailyRecord> recentRecords(Long userId, int days);
}

package com.innercosmos.service;

import java.util.Map;

public interface UnderstandingOverviewService {
    Map<String, Object> overview(Long userId, int rangeDays);
}

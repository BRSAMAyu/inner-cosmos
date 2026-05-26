package com.innercosmos.service;

import com.innercosmos.entity.DialogSummary;

public interface DialogSummaryService {
    DialogSummary summarize(Long userId, Long sessionId);

    DialogSummary getLatest(Long sessionId);
}

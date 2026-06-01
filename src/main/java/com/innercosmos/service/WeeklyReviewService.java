package com.innercosmos.service;

import com.innercosmos.entity.WeeklyReview;

public interface WeeklyReviewService {
    WeeklyReview generateWeeklyReview(Long userId);
    WeeklyReview latest(Long userId);
}

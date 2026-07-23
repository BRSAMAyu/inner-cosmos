package com.innercosmos.service;

import com.innercosmos.vo.WeeklyReviewV2VO;

import java.time.LocalDate;

/**
 * G2.ARCH-MODULES: gives {@code DailyRecordController} a service interface to depend on instead of
 * the concrete {@code com.innercosmos.service.impl.WeeklyReviewV2ServiceImpl} class.
 */
public interface WeeklyReviewV2Service {

    /** Generate a V2 weekly review for the current (Monday-Sunday) week. */
    WeeklyReviewV2VO generate(Long userId);

    /** Generate a V2 weekly review for a specific date range. */
    WeeklyReviewV2VO generateForRange(Long userId, LocalDate weekStartDate, LocalDate weekEndDate);

    /** Get the latest V2 review for a user (returns a V2 VO even if only a legacy V1 row exists). */
    WeeklyReviewV2VO latest(Long userId);

    /** Persist a V2 review (upsert into the existing tb_weekly_review table). */
    WeeklyReviewV2VO save(WeeklyReviewV2VO vo);
}

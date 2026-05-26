package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("tb_weekly_review")
public class WeeklyReview extends BaseEntity {
    public Long userId;
    public LocalDate weekStartDate;
    public LocalDate weekEndDate;
    public String dominantTheme;
    public String themeSummary;
    public String emotionTrend;
    public Integer completedTodos;
    public Integer totalTodos;
    public String gravityChangeSummary;
    public String auroraObservation;
    public String status;
}

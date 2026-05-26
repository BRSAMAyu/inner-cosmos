package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("tb_daily_record")
public class DailyRecord extends BaseEntity {
    public Long userId;
    public LocalDate recordDate;
    public Long sourceSessionId;
    public String theme;
    public String eventSummary;
    public String emotionWeather;
    public String cognitiveSummary;
    public String todoSummary;
    public String auroraSummary;
    public Boolean capsuleSuggested;
    public Boolean userAccepted;
    public String status;
}

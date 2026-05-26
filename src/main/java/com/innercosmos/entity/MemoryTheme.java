package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_memory_theme")
public class MemoryTheme extends BaseEntity {
    public Long userId;
    public String themeName;
    public String themeSummary;
    public String themeType;
    public String keywords;
    public Integer memoryCount;
    public Double averageGravity;
    public LocalDateTime lastTouchedAt;
    public String status;
}

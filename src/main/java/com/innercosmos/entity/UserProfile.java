package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_user_profile")
public class UserProfile extends BaseEntity {
    public Long userId;
    public String auroraName;
    public String auroraTone;
    public String preferredInputType;
    public String socialReachabilityStatus;
    public String bio;
    public Integer reflectionDepth;
    public Boolean allowMemoryRecall;
    public String quietHoursStart;
    public String quietHoursEnd;
    public Integer proactiveSensitivity;
    public Boolean allowMultiMessage;
    public Boolean focusModeEnabled;
    public String focusWindowsJson;
    public String currentEnvironmentLabel;
    public Boolean weatherAwarenessEnabled;
    public Boolean timeAwarenessEnabled;
}

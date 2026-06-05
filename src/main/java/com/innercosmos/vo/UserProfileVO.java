package com.innercosmos.vo;

import com.innercosmos.entity.User;
import com.innercosmos.entity.UserProfile;

public class UserProfileVO {
    public Long id;
    public String username;
    public String nickname;
    public String role;

    /* Profile settings */
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

    public static UserProfileVO from(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.id = user.id;
        vo.username = user.username;
        vo.nickname = user.nickname;
        vo.role = user.role;
        return vo;
    }

    public static UserProfileVO from(User user, UserProfile profile) {
        UserProfileVO vo = from(user);
        if (profile != null) {
            vo.auroraName = profile.auroraName;
            vo.auroraTone = profile.auroraTone;
            vo.preferredInputType = profile.preferredInputType;
            vo.socialReachabilityStatus = profile.socialReachabilityStatus;
            vo.bio = profile.bio;
            vo.reflectionDepth = profile.reflectionDepth;
            vo.allowMemoryRecall = profile.allowMemoryRecall;
            vo.quietHoursStart = profile.quietHoursStart;
            vo.quietHoursEnd = profile.quietHoursEnd;
            vo.proactiveSensitivity = profile.proactiveSensitivity;
            vo.allowMultiMessage = profile.allowMultiMessage;
            vo.focusModeEnabled = profile.focusModeEnabled;
            vo.focusWindowsJson = profile.focusWindowsJson;
            vo.currentEnvironmentLabel = profile.currentEnvironmentLabel;
            vo.weatherAwarenessEnabled = profile.weatherAwarenessEnabled;
            vo.timeAwarenessEnabled = profile.timeAwarenessEnabled;
        }
        return vo;
    }
}

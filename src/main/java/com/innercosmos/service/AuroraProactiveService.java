package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class AuroraProactiveService {
    private static final Logger log = LoggerFactory.getLogger(AuroraProactiveService.class);

    private final UserProfileMapper userProfileMapper;

    public AuroraProactiveService(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    public ProactiveGreeting evaluate(Long userId, LocalDateTime now, Long hoursSinceLastSession) {
        if (userId == null) return null;
        UserProfile profile = loadProfile(userId);
        if (profile != null && inQuietHours(profile, now.toLocalTime())) {
            return null;
        }
        if (hoursSinceLastSession != null && hoursSinceLastSession < 24) {
            return null;
        }
        Long hours = hoursSinceLastSession == null ? 24L : hoursSinceLastSession;
        return new ProactiveGreeting(userId, composeGreeting(hours), hours, now);
    }

    private boolean inQuietHours(UserProfile profile, LocalTime now) {
        if (profile.quietHoursStart == null || profile.quietHoursEnd == null) {
            return false;
        }
        try {
            LocalTime start = LocalTime.parse(profile.quietHoursStart);
            LocalTime end = LocalTime.parse(profile.quietHoursEnd);
            if (start.isBefore(end) || start.equals(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            return !now.isBefore(start) || now.isBefore(end);
        } catch (Exception e) {
            return false;
        }
    }

    private String composeGreeting(Long hoursSinceLastSession) {
        long days = Math.max(1, hoursSinceLastSession / 24);
        if (days >= 7) return String.format("已经 %d 天没见了。最近一切还好吗？想说点什么吗？", days);
        if (days >= 3) return "好几天没聊了。我还记得你之前提过的一些事，那些后来怎么样了？";
        return "今天有什么想说的吗？我在这里。";
    }

    private UserProfile loadProfile(Long userId) {
        try {
            QueryWrapper<UserProfile> query = new QueryWrapper<>();
            query.eq("user_id", userId).last("LIMIT 1");
            return userProfileMapper.selectOne(query);
        } catch (Exception e) {
            log.warn("Failed to load profile for proactive greeting: {}", e.getMessage());
            return null;
        }
    }

    public static class ProactiveGreeting {
        public Long userId;
        public String greeting;
        public Long hoursSinceLastSession;
        public LocalDateTime generatedAt;

        public ProactiveGreeting() {}

        public ProactiveGreeting(Long userId, String greeting, Long hoursSinceLastSession, LocalDateTime generatedAt) {
            this.userId = userId;
            this.greeting = greeting;
            this.hoursSinceLastSession = hoursSinceLastSession;
            this.generatedAt = generatedAt;
        }
    }
}

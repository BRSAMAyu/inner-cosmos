package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.service.RhythmGuardService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class RhythmGuardServiceImpl implements RhythmGuardService {
    private final DialogSessionMapper sessionMapper;
    private final DialogMessageMapper messageMapper;

    public RhythmGuardServiceImpl(DialogSessionMapper sessionMapper, DialogMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public String checkRhythm(Long userId, Long sessionId) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return "CONTINUE";
        }

        // Count messages in session
        QueryWrapper<DialogMessage> msgQuery = new QueryWrapper<>();
        msgQuery.eq("session_id", sessionId).eq("speaker", "USER");
        long userMessageCount = messageMapper.selectCount(msgQuery);

        // Check session duration
        long durationMinutes = 0;
        if (session.startedAt != null) {
            durationMinutes = Duration.between(session.startedAt, LocalDateTime.now()).toMinutes();
        }

        // Check time of day
        int hour = LocalTime.now().getHour();

        // Decision logic
        if (userMessageCount > 20 || durationMinutes > 60) {
            return "REST";
        }
        if (userMessageCount > 15 || durationMinutes > 30) {
            return "SETTLE";
        }
        if (userMessageCount > 10 || durationMinutes > 20) {
            return "SLOW_DOWN";
        }
        if (hour >= 23 || hour < 5) {
            return "SLOW_DOWN";
        }
        return "CONTINUE";
    }

    @Override
    public boolean shouldSuggestSettle(Long userId, Long sessionId) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }

        // Check message count
        QueryWrapper<DialogMessage> msgQuery = new QueryWrapper<>();
        msgQuery.eq("session_id", sessionId).eq("speaker", "USER");
        long userMessageCount = messageMapper.selectCount(msgQuery);
        if (userMessageCount > 15) {
            return true;
        }

        // Check duration
        if (session.startedAt != null) {
            long durationMinutes = Duration.between(session.startedAt, LocalDateTime.now()).toMinutes();
            if (durationMinutes > 30) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getSessionAdvice(Long userId) {
        int hour = LocalTime.now().getHour();

        if (hour >= 23 || hour < 5) {
            return "已经很晚了,也许今天可以先把感受放下,明天再慢慢整理.";
        }
        if (hour >= 5 && hour < 8) {
            return "清晨的思绪往往很清晰,这是一个好好整理想法的好时间.";
        }
        if (hour >= 8 && hour < 12) {
            return "上午的精力充沛,适合做一些需要专注的事情.";
        }
        if (hour >= 12 && hour < 14) {
            return "午后可以稍微放松一下,和 Aurora 随意聊聊也可以.";
        }
        if (hour >= 14 && hour < 18) {
            return "下午的时间,如果有什么想法冒出来,可以试着抓住它.";
        }
        if (hour >= 18 && hour < 21) {
            return "傍晚是一个适合回顾今天的时间.";
        }
        return "夜晚适合安静地梳理内心的声音.";
    }
}

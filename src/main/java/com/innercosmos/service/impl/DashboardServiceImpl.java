package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.*;
import com.innercosmos.mapper.*;
import com.innercosmos.service.DashboardService;
import com.innercosmos.vo.DashboardVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {
    private final MemoryCardMapper memoryCardMapper;
    private final TodoItemMapper todoItemMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final SlowLetterMapper letterMapper;
    private final AiInteractionLogMapper aiLogMapper;
    private final EmotionTraceMapper emotionTraceMapper;
    private final DailyRecordMapper dailyRecordMapper;

    public DashboardServiceImpl(MemoryCardMapper memoryCardMapper,
                                TodoItemMapper todoItemMapper,
                                EchoCapsuleMapper capsuleMapper,
                                SlowLetterMapper letterMapper,
                                AiInteractionLogMapper aiLogMapper,
                                EmotionTraceMapper emotionTraceMapper,
                                DailyRecordMapper dailyRecordMapper) {
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.capsuleMapper = capsuleMapper;
        this.letterMapper = letterMapper;
        this.aiLogMapper = aiLogMapper;
        this.emotionTraceMapper = emotionTraceMapper;
        this.dailyRecordMapper = dailyRecordMapper;
    }

    @Override
    public DashboardVO summary(Long userId) {
        DashboardVO vo = new DashboardVO();
        vo.greeting = "你可以从一句话开始。今天不需要立刻变好，只需要先被看见。";
        vo.memoryCount = memoryCardMapper.selectCount(new QueryWrapper<MemoryCard>().eq("user_id", userId));
        vo.capsuleCount = capsuleMapper.selectCount(new QueryWrapper<EchoCapsule>().eq("owner_user_id", userId));
        vo.unreadLetterCount = letterMapper.selectCount(new QueryWrapper<SlowLetter>().eq("receiver_user_id", userId).in("status", "DELIVERED", "SENT", "FLYING"));
        vo.aiLogCount = aiLogMapper.selectCount(new QueryWrapper<AiInteractionLog>().eq("user_id", userId));
        vo.highGravityMemories = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>().eq("user_id", userId).orderByDesc("emotional_gravity").last("LIMIT 3"));
        vo.todos = todoItemMapper.selectList(new QueryWrapper<TodoItem>().eq("user_id", userId).ne("status", "DONE").orderByDesc("id").last("LIMIT 5"));
        vo.recommendations = capsuleMapper.selectList(new QueryWrapper<EchoCapsule>().eq("is_public", true).eq("visibility_status", "PUBLIC").orderByDesc("echo_energy").last("LIMIT 3"));

        EmotionTrace trace = emotionTraceMapper.selectOne(new QueryWrapper<EmotionTrace>().eq("user_id", userId).orderByDesc("id").last("LIMIT 1"));
        vo.emotionWeather = trace == null ? "尚未记录天气" : trace.weatherType + " / " + trace.emotionName;
        vo.lastSummary = vo.highGravityMemories.isEmpty() ? "完成一次 Aurora 对话后，这里会出现今日摘要。" : vo.highGravityMemories.get(0).summary;
        return vo;
    }

    @Override
    public List<DailyRecord> recentRecords(Long userId, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        QueryWrapper<DailyRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId).ge("record_date", since).orderByDesc("record_date");
        return dailyRecordMapper.selectList(query);
    }
}

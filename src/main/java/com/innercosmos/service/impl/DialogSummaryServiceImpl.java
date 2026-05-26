package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.DialogSummary;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.DialogSummaryMapper;
import com.innercosmos.service.DialogSummaryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DialogSummaryServiceImpl implements DialogSummaryService {
    private final DialogSummaryMapper dialogSummaryMapper;
    private final DialogMessageMapper messageMapper;
    private final DialogSessionMapper sessionMapper;

    public DialogSummaryServiceImpl(DialogSummaryMapper dialogSummaryMapper,
                                     DialogMessageMapper messageMapper,
                                     DialogSessionMapper sessionMapper) {
        this.dialogSummaryMapper = dialogSummaryMapper;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
    }

    @Override
    public DialogSummary summarize(Long userId, Long sessionId) {
        // Read messages from session
        QueryWrapper<DialogMessage> msgQuery = new QueryWrapper<>();
        msgQuery.eq("session_id", sessionId).orderByAsc("id");
        List<DialogMessage> messages = messageMapper.selectList(msgQuery);

        String raw = messages.stream()
                .filter(m -> "USER".equals(m.speaker))
                .map(m -> m.textContent)
                .reduce("", (a, b) -> a + "\n" + b);

        // Generate keyword-based summary
        String summaryText = generateSummary(raw);
        String keyTopics = extractKeyTopics(raw);
        String emotionTone = inferEmotionTone(raw);

        DialogSummary summary = new DialogSummary();
        summary.sessionId = sessionId;
        summary.userId = userId;
        summary.summaryText = summaryText;
        summary.keyTopics = keyTopics;
        summary.emotionTone = emotionTone;
        summary.messageCountAtSummary = messages.size();
        dialogSummaryMapper.insert(summary);

        return summary;
    }

    @Override
    public DialogSummary getLatest(Long sessionId) {
        QueryWrapper<DialogSummary> query = new QueryWrapper<>();
        query.eq("session_id", sessionId).orderByDesc("id").last("LIMIT 1");
        return dialogSummaryMapper.selectOne(query);
    }

    private String generateSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return "用户完成了一次与 Aurora 的对话。";
        }

        List<String> parts = new ArrayList<>();

        if (raw.contains("作业") || raw.contains("考试")) {
            parts.add("用户谈到了学业相关的事情");
        }
        if (raw.contains("朋友") || raw.contains("同学") || raw.contains("关系")) {
            parts.add("用户提到了人际关系方面的感受");
        }
        if (raw.contains("累") || raw.contains("压力") || raw.contains("烦")) {
            parts.add("用户表达了一些压力和疲惫的感受");
        }
        if (raw.contains("开心") || raw.contains("高兴")) {
            parts.add("用户分享了积极的情绪体验");
        }
        if (raw.contains("想") || raw.contains("觉得")) {
            parts.add("用户在思考一些事情的意义");
        }

        if (parts.isEmpty()) {
            String compact = raw.replaceAll("\\s+", " ").trim();
            return "用户表达了自己的想法：" + (compact.length() > 80 ? compact.substring(0, 80) + "..." : compact);
        }

        return String.join("；", parts) + "。";
    }

    private String extractKeyTopics(String raw) {
        List<String> topics = new ArrayList<>();
        if (raw.contains("作业") || raw.contains("考试")) topics.add("学业");
        if (raw.contains("朋友") || raw.contains("同学")) topics.add("社交");
        if (raw.contains("家人")) topics.add("家庭");
        if (raw.contains("未来") || raw.contains("以后")) topics.add("未来规划");
        if (raw.contains("压力") || raw.contains("累")) topics.add("压力管理");
        if (raw.contains("开心") || raw.contains("高兴")) topics.add("积极情绪");
        if (topics.isEmpty()) topics.add("日常对话");
        return topics.toString();
    }

    private String inferEmotionTone(String raw) {
        if (raw == null || raw.isBlank()) return "平静";
        if (raw.contains("开心") || raw.contains("高兴")) return "积极";
        if (raw.contains("孤独") || raw.contains("难过")) return "低落";
        if (raw.contains("烦") || raw.contains("不舒服")) return "烦躁";
        if (raw.contains("累") || raw.contains("压力")) return "疲惫";
        if (raw.contains("担心") || raw.contains("害怕")) return "焦虑";
        return "平静";
    }
}

package com.innercosmos.service.impl;

import com.innercosmos.entity.SafetyEvent;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.mapper.SafetyEventMapper;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.SafetyResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SafetyServiceImpl implements SafetyService {
    private final SafetyEventMapper safetyEventMapper;

    private static final List<String> CRISIS_KEYWORDS = List.of("自杀", "杀人", "人肉", "威胁");
    private static final List<String> ABUSE_KEYWORDS = List.of("辱骂", "歧视", "骚扰", "暴力");

    public SafetyServiceImpl(SafetyEventMapper safetyEventMapper) {
        this.safetyEventMapper = safetyEventMapper;
    }

    @Override
    public void checkText(Long userId, Long sessionId, String text) {
        if (text == null) {
            return;
        }
        for (String keyword : CRISIS_KEYWORDS) {
            if (text.contains(keyword)) {
                SafetyEvent event = new SafetyEvent();
                event.userId = userId;
                event.sessionId = sessionId;
                event.riskType = "CRISIS_KEYWORD";
                event.riskLevel = "HIGH";
                event.matchedRule = keyword;
                event.handledAction = "RESOURCE_PAGE";
                safetyEventMapper.insert(event);
                throw new SafetyBlockedException("内容触发安全边界，请先查看支持资源页。");
            }
        }
    }

    @Override
    public List<String> resources() {
        return List.of(
                "如果你正处于紧急危险中，请立即联系当地急救或可信赖的现实支持者。",
                "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。",
                "你可以先离开屏幕，喝水，呼吸，并联系一个真实的人。"
        );
    }

    @Override
    public SafetyResult check(String text, Long userId, Long sessionId) {
        SafetyResult result = new SafetyResult();
        if (text == null || text.isBlank()) {
            result.riskLevel = "LOW";
            result.riskType = "NONE";
            result.blockModelCall = false;
            return result;
        }
        for (String keyword : CRISIS_KEYWORDS) {
            if (text.contains(keyword)) {
                SafetyEvent event = new SafetyEvent();
                event.userId = userId;
                event.sessionId = sessionId;
                event.riskType = "CRISIS_KEYWORD";
                event.riskLevel = "HIGH";
                event.matchedRule = keyword;
                event.handledAction = "RESOURCE_PAGE";
                safetyEventMapper.insert(event);

                result.riskLevel = "HIGH";
                result.riskType = "CRISIS_KEYWORD";
                result.matchedRule = keyword;
                result.handledAction = "RESOURCE_PAGE";
                result.blockModelCall = true;
                result.safeMessage = "你提到的内容触发了一些安全边界。如果你正处于紧急危险中，请立即联系当地急救或可信赖的现实支持者。你可以先离开屏幕，喝水，呼吸，并联系一个真实的人。";
                return result;
            }
        }
        for (String keyword : ABUSE_KEYWORDS) {
            if (text.contains(keyword)) {
                result.riskLevel = "MEDIUM";
                result.riskType = "ABUSE_KEYWORD";
                result.matchedRule = keyword;
                result.handledAction = "FLAG";
                result.blockModelCall = false;
                return result;
            }
        }
        result.riskLevel = "LOW";
        result.riskType = "NONE";
        result.blockModelCall = false;
        return result;
    }
}

package com.innercosmos.service;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;

import java.util.Locale;

public enum ResonanceMatchStrategy {
    MIRROR("相似共鸣", "寻找与你此刻经历相近的回声"),
    COMPLEMENT("有意义的互补", "遇见你当前轨迹里较少出现的视角"),
    GROWTH_EDGE("成长边缘", "从当前压力走向可能提供支撑的方向"),
    SERENDIPITY("温和偶遇", "在安全边界内为意外理解留一点空间"),
    CONTEXTUAL("阶段同行", "优先回应你此刻画像与阶段中的主题");

    public final String label;
    public final String description;

    ResonanceMatchStrategy(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public static ResonanceMatchStrategy parse(String raw) {
        try {
            return valueOf(raw == null ? "MIRROR" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未知的共鸣策略");
        }
    }
}

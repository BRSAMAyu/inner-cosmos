package com.innercosmos.service;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;

import java.util.Locale;

/**
 * CP-32 三模式召回的请求级模式偏好。
 *
 * <ul>
 *   <li>{@link #BALANCED}（默认，综合）：每个候选按其最强模式的得分参与排序，候选携带各自
 *       的 {@link ResonanceMode} 标签；</li>
 *   <li>{@link #SIMILAR} / {@link #COMPLEMENTARY} / {@link #UNEXPECTED}：指定模式时按该模式的
 *       得分排序——该模式零信号的候选退居补充位（resonant 仍可能因既有主题重合为 true，但
 *       标签始终如实反映其真实主导模式）；</li>
 *   <li>{@link #NONE}：显式关闭三模式层，完全保持既有 strategy 排序行为（兼容逃生口）。</li>
 * </ul>
 */
public enum ResonanceModePreference {
    BALANCED,
    SIMILAR,
    COMPLEMENTARY,
    UNEXPECTED,
    NONE;

    public static ResonanceModePreference parse(String raw) {
        try {
            return valueOf(raw == null || raw.isBlank() ? "BALANCED" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "未知的模式偏好");
        }
    }
}

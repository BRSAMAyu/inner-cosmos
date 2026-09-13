package com.innercosmos.vo;

import com.innercosmos.service.ResonanceMode;
import com.innercosmos.service.ResonanceModeAssessor;

import java.util.List;
import java.util.Map;

/**
 * CP-32 匹配候选的结构化解释（进入 /api/plaza/matches 候选项的 modeExplanation 字段）。
 *
 * <p>诚实性契约：reasons 只列促成该候选的具体共同点/互补点/跨域点，全部可由
 * {@link ResonanceModeAssessor} 的输入信号复原；信号不足时 mode 为 null、reasons 为空、
 * confidence = "insufficient_signal"，绝不用「灵魂契合」类话术补位。scoreBreakdown 是主导
 * 模式的得分构成（组件名 → 分值）；三档模式得分始终全量透出，便于消费方核对模式偏好下的
 * 排序依据（指定模式偏好时排序用对应模式得分，而标签/breakdown 始终反映真实主导模式）。
 */
public class ResonanceMatchExplanationVO {
    /** SIMILAR / COMPLEMENTARY / UNEXPECTED；信号不足时为 null（配合 insufficient_signal）。 */
    public String mode;
    public String modeLabel;
    /** 模式定义，描述信号本身，不承诺匹配效果。 */
    public String modeDefinition;
    /** 促成该候选的具体信号（真实计数/真实主题域/真实词），克制、可核对。 */
    public List<String> reasons;
    /** 主导模式的得分构成：组件名 → 分值。 */
    public Map<String, Double> scoreBreakdown;
    /** 信号充分度：sufficient / weak / insufficient_signal。 */
    public String confidence;
    /** 三档模式得分全量透出（无论主导是哪档），供核对偏好排序。 */
    public Double similarScore;
    public Double complementaryScore;
    public Double unexpectedScore;

    public static ResonanceMatchExplanationVO from(ResonanceModeAssessor.ModeAssessment assessment) {
        ResonanceMatchExplanationVO vo = new ResonanceMatchExplanationVO();
        if (assessment == null) {
            vo.reasons = List.of();
            vo.scoreBreakdown = Map.of();
            vo.confidence = ResonanceModeAssessor.CONFIDENCE_INSUFFICIENT;
            vo.similarScore = 0.0;
            vo.complementaryScore = 0.0;
            vo.unexpectedScore = 0.0;
            return vo;
        }
        ResonanceMode mode = assessment.mode();
        vo.mode = mode == null ? null : mode.name();
        vo.modeLabel = mode == null ? null : mode.label;
        vo.modeDefinition = mode == null ? null : mode.definition;
        vo.reasons = assessment.reasons();
        vo.scoreBreakdown = assessment.scoreBreakdown();
        vo.confidence = assessment.confidence();
        vo.similarScore = assessment.similarScore();
        vo.complementaryScore = assessment.complementaryScore();
        vo.unexpectedScore = assessment.unexpectedScore();
        return vo;
    }
}

package com.innercosmos.service;

/**
 * CP-32 三模式召回：每个匹配候选按真实可计算信号获得的模式标签。
 *
 * <p>这是叠加在既有 {@link ResonanceMatchStrategy} 排序层之上的一个正交维度：strategy 回答
 * 「这次请求用哪种排序视角」，mode 回答「这个候选是因为什么类型的信号被召回的」。标签与得分
 * 只来自已存在的本地确定性信号（主题域词表、画像维度、语义相似度、情绪标签词、记录时间桶），
 * 不引入任何营销式「契合度」话术；信号不足时标注 insufficient_signal，不编造理由。
 *
 * <p>三种模式的信号定义（详见 {@link ResonanceModeAssessor}）：
 * <ul>
 *   <li>{@link #SIMILAR}：双方共同出现的主题域数量为主，画像印证与语义相近做加成；</li>
 *   <li>{@link #COMPLEMENTARY}：定向组合信号——你当前轨迹中的压力主题 × 对方内容里你目前
 *       缺少的支撑主题（桥接对沿用既有 GROWTH_EDGE 的三组方向，不新增玄学映射）；</li>
 *   <li>{@link #UNEXPECTED}：双方主题域完全不重合，但存在跨域潜力信号（共同的情绪痕迹
 *       词汇、相近的记录时段）。</li>
 * </ul>
 */
public enum ResonanceMode {
    SIMILAR("相似", "由双方共同出现的主题域驱动（画像印证与语义相近作加成）"),
    COMPLEMENTARY("互补", "由定向组合驱动：你的压力主题 × 对方内容中你目前缺少的支撑主题"),
    UNEXPECTED("意外", "双方主题域不重合，但存在跨域信号（共同情绪痕迹词汇、相近的记录时段）");

    public final String label;
    /** 面向用户的模式定义，措辞描述信号本身，不承诺匹配效果。 */
    public final String definition;

    ResonanceMode(String label, String definition) {
        this.label = label;
        this.definition = definition;
    }
}

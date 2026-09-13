package com.innercosmos.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * CP-32 三模式信号层：纯函数、确定性、无 LLM、无外部调用。
 *
 * <p>输入全部是匹配链路中已经存在的本地信号（与 CapsuleServiceImpl 的 userThemeProfile /
 * capsuleThemeProfile / portraitFamilies / paraphraseFamilies / semanticSignal 同源），本类只做
 * 归一、组合与解释，不引入新的数据依赖。解释字段的诚实性约束：每条 reason 必须能从输入信号
 * 复原（共同主题的数量、桥接对、情绪词、时间桶都是真实计数），没有信号就给
 * {@code insufficient_signal}，绝不输出「灵魂契合」式的效果承诺。
 *
 * <p><b>三种模式的信号定义与归一（全部为 [0,1] 上的加性得分）：</b>
 * <ul>
 *   <li><b>SIMILAR</b>（相似）：每个双方共同出现的主题域 +0.34；画像印证（查看者画像维度中的
 *       主题域也出现在对方公开内容中）+0.15；语义相近（既有 semanticSignal ≥ 0.05，沿用
 *       CapsuleServiceImpl.SEMANTIC_REASON_THRESHOLD 的「信号足够才解释」惯例）+0.20；总分
 *       封顶 1.0。</li>
 *   <li><b>COMPLEMENTARY</b>（互补）：定向桥接 查看者压力主题 → 对方支撑主题，每条 +0.30，
 *       封顶 0.60。桥接对沿用既有 GROWTH_EDGE 策略已经确立的三组方向（任务压力→希望期待、
 *       情绪承压→认知探索、自我评价→关系牵动），不新增映射。桥接只在「查看者确实有该压力
 *       主题、对方确实有该支撑主题、且查看者当前没有该支撑主题」时成立——最后一项保证它
 *       是互补而不是相似。没有任何桥接成立时该模式如实得 0 分（降权为无信号）。</li>
 *   <li><b>UNEXPECTED</b>（意外）：前提是双方主题域完全不重合（严格词表 + 释义词表的并集都
 *       无交集），否则候选总能被 SIMILAR 解释，不进入本模式。在此前提下两条跨域信号：
 *       情绪痕迹重合（查看者记忆情绪标签词 × 对方公开标签的交集非空）+0.40；记录时段相近
 *       （查看者带时间戳记忆的多数桶 ≥2 条且严格多于另一桶，且对方共鸣体创建时间落在同一
 *       桶）+0.30；封顶 0.55。时间桶仅两档：夜间 22:00–05:59、日间 06:00–21:59。</li>
 * </ul>
 *
 * <p><b>主导模式与置信度：</b>主导模式取三档得分的最大者，同分时按 SIMILAR &gt;
 * COMPLEMENTARY &gt; UNEXPECTED 的固定优先级（相似信号最硬）。置信度（信号充分度）：
 * SIMILAR 有共同主题域为 sufficient，仅画像/语义为 weak；COMPLEMENTARY ≥2 条桥接为
 * sufficient、1 条为 weak（定向组合是启发式而非双方实测互动）；UNEXPECTED 恒为 weak
 * （情绪词与时间桶都是单一代理信号）。三档全 0 时 mode 为 null、reasons 为空、置信度
 * insufficient_signal。
 */
public final class ResonanceModeAssessor {

    public static final String CONFIDENCE_SUFFICIENT = "sufficient";
    public static final String CONFIDENCE_WEAK = "weak";
    public static final String CONFIDENCE_INSUFFICIENT = "insufficient_signal";

    public static final String NIGHT_BUCKET = "夜间";
    public static final String DAY_BUCKET = "日间";

    /** 与 CapsuleServiceImpl.FAMILY_ORDER 保持一致的稳定次序（同包重复声明，保持本类无状态）。 */
    static final List<String> FAMILY_ORDER = List.of(
            "任务压力", "关系牵动", "情绪承压", "认知探索", "自我评价", "希望期待");

    /** 桥接方向沿用既有 GROWTH_EDGE 策略的三组「压力 → 可能支撑」方向，不新增映射。 */
    private record Bridge(String demand, String supply) {}

    private static final List<Bridge> COMPLEMENT_BRIDGES = List.of(
            new Bridge("任务压力", "希望期待"),
            new Bridge("情绪承压", "认知探索"),
            new Bridge("自我评价", "关系牵动"));

    private static final double SIMILAR_FAMILY_UNIT = 0.34;
    private static final double SIMILAR_PORTRAIT_UNIT = 0.15;
    private static final double SIMILAR_SEMANTIC_UNIT = 0.20;
    private static final double SIMILAR_CAP = 1.0;
    private static final double COMPLEMENT_BRIDGE_UNIT = 0.30;
    private static final double COMPLEMENT_CAP = 0.60;
    private static final double UNEXPECTED_EMOTION_UNIT = 0.40;
    private static final double UNEXPECTED_RHYTHM_UNIT = 0.30;
    private static final double UNEXPECTED_CAP = 0.55;
    /** 与 CapsuleServiceImpl.SEMANTIC_REASON_THRESHOLD 同值：语义信号小到这个程度就不解释。 */
    private static final double SEMANTIC_CONTRIBUTION_FLOOR = 0.05;
    /** 时段信号要求查看者至少有 2 条带时间戳的记忆落在主导桶。 */
    private static final int RHYTHM_MIN_MEMORIES = 2;

    private ResonanceModeAssessor() {}

    /** 查看者侧信号：与主题画像同源的一次性聚合（同一批至多 24 条高重力记忆）。 */
    public record ViewerSignals(Map<String, Integer> themeProfile,
                                Set<String> portraitFamilies,
                                Map<String, Integer> emotionTagFreq,
                                Map<String, Integer> hourBucketCounts) {}

    /**
     * 候选（共鸣体）侧信号。themeProfile 为严格词表主题频次，paraphraseFamilies 为释义词表
     * 补充命中的主题域，publicTags 为对方公开标签，createdHour 为共鸣体创建时间的小时数
     * （无时间戳传 null），semanticSignal 为既有语义相似度加成（未配置 provider 或非
     * MIRROR 策略时为 0）。
     */
    public record CapsuleSignals(Map<String, Integer> themeProfile,
                                 Set<String> paraphraseFamilies,
                                 Set<String> publicTags,
                                 Integer createdHour,
                                 double semanticSignal) {
        /** 严格词表 + 释义词表的并集——既有 matchTier 覆盖率使用的同一家族集合。 */
        public Set<String> effectiveFamilies() {
            Set<String> families = new LinkedHashSet<>();
            if (themeProfile != null) {
                families.addAll(themeProfile.keySet());
            }
            if (paraphraseFamilies != null) {
                families.addAll(paraphraseFamilies);
            }
            return families;
        }
    }

    /**
     * 单个候选的三模式评估结果。relevance(preference) 返回该偏好下实际参与排序的得分：
     * BALANCED/null 取主导模式得分，指定模式取该模式得分（可能为 0——此时候选退居补充位，
     * 标签仍如实保留其真实主导模式）。
     */
    public record ModeAssessment(ResonanceMode mode,
                                 double similarScore,
                                 double complementaryScore,
                                 double unexpectedScore,
                                 List<String> reasons,
                                 Map<String, Double> scoreBreakdown,
                                 String confidence) {

        static ModeAssessment insufficient() {
            return new ModeAssessment(null, 0.0, 0.0, 0.0,
                    List.of(), Map.of(), CONFIDENCE_INSUFFICIENT);
        }

        private double dominantScore() {
            return Math.max(similarScore, Math.max(complementaryScore, unexpectedScore));
        }

        public double relevance(ResonanceModePreference preference) {
            if (preference == null || preference == ResonanceModePreference.BALANCED
                    || preference == ResonanceModePreference.NONE) {
                return dominantScore();
            }
            return switch (preference) {
                case SIMILAR -> similarScore;
                case COMPLEMENTARY -> complementaryScore;
                case UNEXPECTED -> unexpectedScore;
                default -> dominantScore();
            };
        }
    }

    /** 22:00–05:59 记为 {@link #NIGHT_BUCKET}，06:00–21:59 记为 {@link #DAY_BUCKET}。 */
    public static String hourBucket(int hour) {
        boolean night = hour >= 22 || hour < 6;
        return night ? NIGHT_BUCKET : DAY_BUCKET;
    }

    public static ModeAssessment assess(ViewerSignals viewer, CapsuleSignals capsule) {
        if (viewer == null || capsule == null
                || viewer.themeProfile() == null || viewer.themeProfile().isEmpty()) {
            // 查看者还没有可用的轨迹信号——不足以解释任何模式，宁可承认也不编造。
            return ModeAssessment.insufficient();
        }

        Set<String> capsuleFamilies = capsule.effectiveFamilies();

        // ---- SIMILAR：共同主题域 + 画像印证 + 语义相近 ----
        List<String> similarReasons = new ArrayList<>();
        Map<String, Double> similarBreakdown = new LinkedHashMap<>();
        List<Map.Entry<String, Integer>> shared = new ArrayList<>();
        for (Map.Entry<String, Integer> e : viewer.themeProfile().entrySet()) {
            if (capsuleFamilies.contains(e.getKey())) {
                shared.add(e);
            }
        }
        shared.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(e -> FAMILY_ORDER.indexOf(e.getKey())));
        double similar = 0.0;
        for (Map.Entry<String, Integer> e : shared) {
            similar += SIMILAR_FAMILY_UNIT;
            similarReasons.add("共同主题：" + e.getKey() + "（你的记忆中出现" + e.getValue() + "次）");
            similarBreakdown.put("共同主题·" + e.getKey(), SIMILAR_FAMILY_UNIT);
        }
        List<String> portraitShared = new ArrayList<>();
        for (String fam : FAMILY_ORDER) {
            if (viewer.portraitFamilies() != null && viewer.portraitFamilies().contains(fam)
                    && capsuleFamilies.contains(fam)) {
                portraitShared.add(fam);
            }
        }
        if (!portraitShared.isEmpty()) {
            similar += SIMILAR_PORTRAIT_UNIT;
            similarReasons.add("画像印证：『" + String.join("、", portraitShared) + "』也在你当前画像中");
            similarBreakdown.put("画像印证", SIMILAR_PORTRAIT_UNIT);
        }
        if (capsule.semanticSignal() >= SEMANTIC_CONTRIBUTION_FLOOR) {
            similar += SIMILAR_SEMANTIC_UNIT;
            similarReasons.add("语义相近：语义相似度对得分有实际贡献");
            similarBreakdown.put("语义相近", SIMILAR_SEMANTIC_UNIT);
        }
        similar = Math.min(SIMILAR_CAP, similar);

        // ---- COMPLEMENTARY：查看者压力主题 × 对方支撑主题（查看者当前缺少该支撑主题） ----
        List<String> complementReasons = new ArrayList<>();
        Map<String, Double> complementBreakdown = new LinkedHashMap<>();
        for (Bridge bridge : COMPLEMENT_BRIDGES) {
            boolean demandPresent = viewer.themeProfile().containsKey(bridge.demand());
            boolean supplyPresent = capsuleFamilies.contains(bridge.supply());
            boolean supplyNovelToViewer = !viewer.themeProfile().containsKey(bridge.supply());
            if (demandPresent && supplyPresent && supplyNovelToViewer) {
                complementReasons.add("互补方向：你的『" + bridge.demand() + "』主题 × 对方的『"
                        + bridge.supply() + "』主题");
                complementBreakdown.put("互补·" + bridge.demand() + "→" + bridge.supply(),
                        COMPLEMENT_BRIDGE_UNIT);
            }
        }
        double complementary = Math.min(COMPLEMENT_CAP, complementBreakdown.size() * COMPLEMENT_BRIDGE_UNIT);

        // ---- UNEXPECTED：主题域完全不重合为前提，情绪痕迹 / 记录时段两条跨域信号 ----
        List<String> unexpectedReasons = new ArrayList<>();
        Map<String, Double> unexpectedBreakdown = new LinkedHashMap<>();
        double unexpected = 0.0;
        if (shared.isEmpty()) {
            Set<String> emotionOverlap = new TreeSet<>();
            if (viewer.emotionTagFreq() != null && capsule.publicTags() != null) {
                for (Map.Entry<String, Integer> e : viewer.emotionTagFreq().entrySet()) {
                    if (capsule.publicTags().contains(e.getKey())) {
                        emotionOverlap.add(e.getKey());
                    }
                }
            }
            if (!emotionOverlap.isEmpty()) {
                unexpected += UNEXPECTED_EMOTION_UNIT;
                List<String> tags = emotionOverlap.stream().limit(2).toList();
                unexpectedReasons.add("跨域信号：主题域不同，但双方都带有『"
                        + String.join("』『", tags) + "』的情绪痕迹");
                unexpectedBreakdown.put("情绪痕迹重合", UNEXPECTED_EMOTION_UNIT);
            }
            String rhythm = rhythmSignal(viewer.hourBucketCounts(), capsule.createdHour());
            if (rhythm != null) {
                unexpected += UNEXPECTED_RHYTHM_UNIT;
                unexpectedReasons.add(rhythm);
                unexpectedBreakdown.put("记录时段相近", UNEXPECTED_RHYTHM_UNIT);
            }
            unexpected = Math.min(UNEXPECTED_CAP, unexpected);
        }

        // ---- 主导模式：最大者，同分按 SIMILAR > COMPLEMENTARY > UNEXPECTED ----
        if (similar == 0.0 && complementary == 0.0 && unexpected == 0.0) {
            return ModeAssessment.insufficient();
        }
        ResonanceMode mode;
        List<String> reasons;
        Map<String, Double> breakdown;
        String confidence;
        if (similar >= complementary && similar >= unexpected) {
            mode = ResonanceMode.SIMILAR;
            reasons = similarReasons;
            breakdown = similarBreakdown;
            confidence = shared.isEmpty() ? CONFIDENCE_WEAK : CONFIDENCE_SUFFICIENT;
        } else if (complementary >= unexpected) {
            mode = ResonanceMode.COMPLEMENTARY;
            reasons = complementReasons;
            breakdown = complementBreakdown;
            confidence = complementBreakdown.size() >= 2 ? CONFIDENCE_SUFFICIENT : CONFIDENCE_WEAK;
        } else {
            mode = ResonanceMode.UNEXPECTED;
            reasons = unexpectedReasons;
            breakdown = unexpectedBreakdown;
            confidence = CONFIDENCE_WEAK;
        }
        return new ModeAssessment(mode, similar, complementary, unexpected,
                List.copyOf(reasons), breakdown, confidence);
    }

    /**
     * 记录时段信号：查看者带时间戳记忆的主导桶（≥2 条且严格多于另一桶）与共鸣体创建桶一致时
     * 成立。返回可直接展示的 reason 文案（含真实计数），不成立返回 null。共鸣体创建时间是
     * 单一时间戳——文案如实写「创建于」，不夸大为对方的长期节律。
     */
    private static String rhythmSignal(Map<String, Integer> hourBucketCounts, Integer createdHour) {
        if (hourBucketCounts == null || hourBucketCounts.isEmpty() || createdHour == null) {
            return null;
        }
        int night = hourBucketCounts.getOrDefault(NIGHT_BUCKET, 0);
        int day = hourBucketCounts.getOrDefault(DAY_BUCKET, 0);
        String dominant;
        int dominantCount;
        int total;
        if (night > day) {
            dominant = NIGHT_BUCKET;
            dominantCount = night;
            total = night + day;
        } else if (day > night) {
            dominant = DAY_BUCKET;
            dominantCount = day;
            total = night + day;
        } else {
            return null;
        }
        if (dominantCount < RHYTHM_MIN_MEMORIES) {
            return null;
        }
        if (!dominant.equals(hourBucket(createdHour))) {
            return null;
        }
        return "跨域信号：你的记忆多在" + dominant + "形成（" + dominantCount + "/" + total
                + "条），对方共鸣体也创建于" + dominant;
    }
}

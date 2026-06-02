package com.innercosmos.ai.lexicon;

import java.util.HashMap;
import java.util.Map;

/**
 * Chinese sentiment lexicon for pseudo-semantic analysis in Mock mode.
 * Maps Chinese words to sentiment scores (-5 to +5).
 * Used by PseudoSemanticAnalyzer to infer user intent and emotion.
 */
public final class ChineseSentimentLexicon {

    private static final Map<String, Integer> LEXICON = new HashMap<>();

    static {
        // === Negative emotions (CRISIS level) -5 to -4 ===
        LEXICON.put("自杀", -5);
        LEXICON.put("轻生", -5);
        LEXICON.put("不想活", -5);
        LEXICON.put("寻死", -5);
        LEXICON.put("结束生命", -5);
        LEXICON.put("从没出生", -5);
        LEXICON.put("想消失", -5);
        LEXICON.put("活着好累", -5);
        LEXICON.put("死了一了百了", -5);
        LEXICON.put("了结自己", -5);
        LEXICON.put("自残", -5);
        LEXICON.put("割腕", -5);
        LEXICON.put("跳楼", -5);

        // === Negative emotions (SEVERE) -4 to -3 ===
        LEXICON.put("绝望", -4);
        LEXICON.put("崩溃", -4);
        LEXICON.put("撑不住", -4);
        LEXICON.put("受够", -4);
        LEXICON.put("活不下去", -4);
        LEXICON.put("熬不下去", -4);
        LEXICON.put("生不如死", -4);
        LEXICON.put("痛苦", -3);
        LEXICON.put("煎熬", -3);
        LEXICON.put("折磨", -3);
        LEXICON.put("窒息", -3);
        LEXICON.put("压抑", -3);
        LEXICON.put("绝望", -3);
        LEXICON.put("无助", -3);
        LEXICON.put("孤单", -3);
        LEXICON.put("孤独", -3);
        LEXICON.put("没人懂", -3);
        LEXICON.put("一个人", -2);

        // === Negative emotions (MODERATE) -3 to -2 ===
        LEXICON.put("难过", -3);
        LEXICON.put("伤心", -3);
        LEXICON.put("委屈", -3);
        LEXICON.put("失望", -3);
        LEXICON.put("沮丧", -3);
        LEXICON.put("低落", -2);
        LEXICON.put("消沉", -2);
        LEXICON.put("郁闷", -2);
        LEXICON.put("惆怅", -2);
        LEXICON.put("惘然", -2);
        LEXICON.put("落寞", -2);
        LEXICON.put("凄凉", -2);
        LEXICON.put("悲凉", -2);

        // === Negative emotions (MILD) -2 to -1 ===
        LEXICON.put("烦", -2);
        LEXICON.put("烦躁", -2);
        LEXICON.put("讨厌", -2);
        LEXICON.put("厌恶", -2);
        LEXICON.put("反感", -2);
        LEXICON.put("不舒服", -2);
        LEXICON.put("不爽", -2);
        LEXICON.put("恼火", -2);
        LEXICON.put("生气", -2);
        LEXICON.put("愤怒", -2);
        LEXICON.put("气", -2);
        LEXICON.put("恼怒", -2);
        LEXICON.put("愤懑", -2);
        LEXICON.put("恼恨", -2);
        LEXICON.put("恨", -2);

        // === Stress/Pressure emotions ===
        LEXICON.put("累", -2);
        LEXICON.put("疲惫", -2);
        LEXICON.put("精疲力竭", -3);
        LEXICON.put("筋疲力尽", -3);
        LEXICON.put("累垮", -3);
        LEXICON.put("压力", -2);
        LEXICON.put("压力大", -3);
        LEXICON.put("紧张", -2);
        LEXICON.put("焦虑", -3);
        LEXICON.put("担心", -2);
        LEXICON.put("担忧", -2);
        LEXICON.put("忧心", -2);
        LEXICON.put("不安", -2);
        LEXICON.put("慌", -2);
        LEXICON.put("恐慌", -3);
        LEXICON.put("害怕", -2);
        LEXICON.put("恐惧", -3);
        LEXICON.put("惊恐", -3);
        LEXICON.put("慌乱", -2);
        LEXICON.put("忐忑", -2);
        LEXICON.put("忧虑", -2);

        // === Self-evaluation (negative) ===
        LEXICON.put("不行", -2);
        LEXICON.put("没用", -3);
        LEXICON.put("废物", -4);
        LEXICON.put("垃圾", -3);
        LEXICON.put("失败", -2);
        LEXICON.put("失败者", -3);
        LEXICON.put("没做好", -2);
        LEXICON.put("做不好", -2);
        LEXICON.put("做不到", -2);
        LEXICON.put("无能", -3);
        LEXICON.put("笨", -2);
        LEXICON.put("蠢", -2);
        LEXICON.put("傻", -1);
        LEXICON.put("糟糕", -2);
        LEXICON.put("差劲", -2);
        LEXICON.put("糟糕透顶", -3);

        // === Procrastination/avoidance ===
        LEXICON.put("拖延", -2);
        LEXICON.put("拖", -1);
        LEXICON.put("逃避", -2);
        LEXICON.put("躲", -1);
        LEXICON.put("不想做", -2);
        LEXICON.put("不想动", -1);
        LEXICON.put("不想面对", -2);
        LEXICON.put("推迟", -1);
        LEXICON.put("拖延症", -2);

        // === Task/Academic stress ===
        LEXICON.put("作业", -1);
        LEXICON.put("考试", -1);
        LEXICON.put("任务", -1);
        LEXICON.put("项目", -1);
        LEXICON.put("ddl", -1);
        LEXICON.put("截止", -1);
        LEXICON.put("交", 0);
        LEXICON.put("提交", 0);
        LEXICON.put("复习", 0);

        // === Conflict/Relationship issues ===
        LEXICON.put("吵架", -3);
        LEXICON.put("冲突", -2);
        LEXICON.put("争执", -2);
        LEXICON.put("矛盾", -2);
        LEXICON.put("不和", -2);
        LEXICON.put("闹翻", -3);
        LEXICON.put("冷战", -3);
        LEXICON.put("分手", -4);
        LEXICON.put("离", -3);

        // === Positive emotions (HIGH) +4 to +5 ===
        LEXICON.put("开心", +4);
        LEXICON.put("高兴", +4);
        LEXICON.put("快乐", +4);
        LEXICON.put("幸福", +5);
        LEXICON.put("满足", +4);
        LEXICON.put("欣慰", +3);
        LEXICON.put("喜悦", +4);
        LEXICON.put("愉快", +4);
        LEXICON.put("欢乐", +4);
        LEXICON.put("欣喜", +4);
        LEXICON.put("雀跃", +4);

        // === Positive emotions (MODERATE) +3 ===
        LEXICON.put("轻松", +3);
        LEXICON.put("放松", +3);
        LEXICON.put("舒坦", +3);
        LEXICON.put("舒服", +2);
        LEXICON.put("舒适", +2);
        LEXICON.put("惬意", +3);
        LEXICON.put("自在", +3);
        LEXICON.put("安适", +2);
        LEXICON.put("安定", +2);

        // === Positive emotions (MILD) +2 ===
        LEXICON.put("不错", +2);
        LEXICON.put("挺好", +2);
        LEXICON.put("很好", +3);
        LEXICON.put("还可以", +1);
        LEXICON.put("顺利", +2);
        LEXICON.put("成功", +3);
        LEXICON.put("完成了", +2);
        LEXICON.put("做到了", +2);
        LEXICON.put("解决", +1);
        LEXICON.put("搞定", +1);

        // === Calm/Neutral ===
        LEXICON.put("平静", 0);
        LEXICON.put("安静", 0);
        LEXICON.put("宁静", 0);
        LEXICON.put("平和", 0);
        LEXICON.put("淡然", 0);
        LEXICON.put("坦然", 0);
        LEXICON.put("心安", 0);

        // === Hope/Forward-looking ===
        LEXICON.put("希望", +2);
        LEXICON.put("期待", +2);
        LEXICON.put("盼望", +2);
        LEXICON.put("憧憬", +3);
        LEXICON.put("向往", +2);
        LEXICON.put("渴望", +1);
        LEXICON.put("梦想", +2);

        // === Relief/Release ===
        LEXICON.put("释然", +3);
        LEXICON.put("解脱", +3);
        LEXICON.put("放下", +2);
        LEXICON.put("释怀", +3);
        LEXICON.put("豁然开朗", +4);

        // === Connection/Relationship ===
        LEXICON.put("朋友", 0);
        LEXICON.put("同学", 0);
        LEXICON.put("家人", 0);
        LEXICON.put("家人", 0);
        LEXICON.put("亲人", +1);
        LEXICON.put("爱人", +2);
        LEXICON.put("伴侣", +2);
        LEXICON.put("老师", 0);
        LEXICON.put("同事", 0);
        LEXICON.put("伙伴", +1);
        LEXICON.put("支持", +2);
        LEXICON.put("理解", +2);
        LEXICON.put("陪伴", +2);
        LEXICON.put("关心", +1);
        LEXICON.put("在乎", +1);
        LEXICON.put("爱护", +2);

        // === Cognitive states ===
        LEXICON.put("混乱", -1);
        LEXICON.put("乱", -1);
        LEXICON.put("糊涂", -1);
        LEXICON.put("迷茫", -2);
        LEXICON.put("困惑", -1);
        LEXICON.put("不解", -1);
        LEXICON.put("疑问", 0);
        LEXICON.put("疑惑", 0);
        LEXICON.put("想不通", -1);
        LEXICON.put("理不清", -1);
        LEXICON.put("不知道", 0);
        LEXICON.put("不清楚", 0);

        // === Need/Desire ===
        LEXICON.put("需要", 0);
        LEXICON.put("想要", 0);
        LEXICON.put("希望", +1);
        LEXICON.put("期望", 0);
        LEXICON.put("渴望", +1);
        LEXICON.put("愿望", +1);
        LEXICON.put("渴望", +1);

        // === Energy states ===
        LEXICON.put("有精神", +2);
        LEXICON.put("精力充沛", +3);
        LEXICON.put("充满活力", +3);
        LEXICON.put("振奋", +3);
        LEXICON.put("鼓舞", +2);
        LEXICON.put("兴奋", +2);
        LEXICON.put("激动", +2);
        LEXICON.put("疲惫", -2);
        LEXICON.put("困", -1);
        LEXICON.put("倦", -1);
        LEXICON.put("乏", -1);
        LEXICON.put("累", -2);
    }

    private ChineseSentimentLexicon() {}

    /**
     * Get sentiment score for a word. Returns 0 if word not found.
     * Score range: -5 (crisis/negative) to +5 (positive)
     */
    public static int getScore(String word) {
        if (word == null || word.isBlank()) return 0;
        return LEXICON.getOrDefault(word.trim(), 0);
    }

    /**
     * Check if word exists in lexicon.
     */
    public static boolean contains(String word) {
        if (word == null || word.isBlank()) return false;
        return LEXICON.containsKey(word.trim());
    }

    /**
     * Get all words with a given sentiment level.
     * level: "negative", "positive", "neutral", "crisis"
     */
    public static java.util.Set<String> getWordsByLevel(String level) {
        switch (level.toLowerCase()) {
            case "crisis":
                return LEXICON.entrySet().stream()
                    .filter(e -> e.getValue() <= -4)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            case "negative":
                return LEXICON.entrySet().stream()
                    .filter(e -> e.getValue() < 0)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            case "positive":
                return LEXICON.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            case "neutral":
                return LEXICON.entrySet().stream()
                    .filter(e -> e.getValue() == 0)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            default:
                return java.util.Collections.emptySet();
        }
    }

    /**
     * Get total lexicon size.
     */
    public static int size() {
        return LEXICON.size();
    }
}

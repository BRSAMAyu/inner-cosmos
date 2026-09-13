package com.innercosmos.vo;

import java.util.List;
import java.util.Map;

/**
 * CP-34: 关系互动回顾 — the honest replacement for the old relation "temperature" score.
 * The temperature score was an evaluative verdict (positive-emotion share, inflated ×1.5)
 * on a relationship; a platform that coaches introspection must not grade the user's
 * relationships. This VO carries only what actually happened, counted from real
 * tb_relation_mention rows: how often the relation appeared in the user's memories, in
 * how many distinct weeks, with which emotion tags — and nothing that says "good" or
 * "bad". The UI's fixed legend states this explicitly.
 */
public class RelationInteractionReviewVO {
    public String relationLabel;
    /** Inclusive review window [windowStart, windowEnd), server UTC. */
    public String windowStart;
    public String windowEnd;
    /** Total mentions of this relation inside the window (0 = honest empty review). */
    public long mentionCount;
    /** Distinct ISO weeks with at least one mention (cadence, non-evaluatively). */
    public long weeksActive;
    /** Emotion tag -> real occurrence count inside the window (parsed from mentions). */
    public Map<String, Long> emotionSpectrum;
    /** Most recent trigger summaries (real rows, newest first, capped). */
    public List<String> recentTriggers;

    public static RelationInteractionReviewVO empty(String relationLabel, String windowStart, String windowEnd) {
        RelationInteractionReviewVO vo = new RelationInteractionReviewVO();
        vo.relationLabel = relationLabel;
        vo.windowStart = windowStart;
        vo.windowEnd = windowEnd;
        vo.mentionCount = 0;
        vo.weeksActive = 0;
        vo.emotionSpectrum = Map.of();
        vo.recentTriggers = List.of();
        return vo;
    }
}

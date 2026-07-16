package com.innercosmos.ai.claim;

import com.innercosmos.entity.DialogMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, provider-independent engine that derives {@link ClaimCandidate}s from the user's
 * own utterances in a conversation. It is precision-first: it only proposes a candidate when the
 * text is a clear first-person self-predication, and deliberately rejects questions, hypotheticals,
 * reported speech (others' statements) and momentary one-off feelings, because a fabricated
 * understanding is worse than a missing one. It is the deterministic fallback beneath the
 * real-provider extraction path and the machine-verifiable floor measured by the claim-precision
 * evaluation gate.
 */
public final class ClaimCandidateExtractor {

    private static final Set<String> QUESTION_MARKERS = Set.of("？", "?", "是不是", "要不要", "能不能",
            "该不该", "是否", "吗", "呢");
    private static final Set<String> HYPOTHETICAL_MARKERS = Set.of("如果", "假如", "要是", "万一", "假设");
    // Explicit third-party attribution phrases — precision-safe (avoids matching 「对我来说」/「我觉得」).
    private static final List<String> REPORTED_SPEECH_PHRASES = List.of("妈说", "妈总说", "爸说", "爸总说",
            "同事觉得", "同事说", "别人觉得", "别人说", "他说", "她说", "他觉得", "她觉得", "朋友说",
            "朋友觉得", "老板说", "同学说", "家里人说", "在别人眼里", "领导说");
    private static final Set<String> INTERROGATIVE_OBJECTS = Set.of("什么", "啥", "哪", "谁", "多少",
            "怎么", "怎样", "如何");
    private static final Set<String> EMOTION_WORDS = Set.of("紧张", "焦虑", "难过", "生气", "害怕", "低落",
            "烦躁", "紧绷", "委屈", "崩溃", "抑郁", "恐惧", "不安", "担心", "愤怒", "沮丧", "孤独", "慌");
    private static final Set<String> RECURRENCE_MARKERS = Set.of("总是", "经常", "老是", "动不动", "每次",
            "一直", "常常", "总会");
    private static final List<String> LEADING_FILLERS = List.of("自己", "一段", "一名", "一个", "一种",
            "一份", "的", "了");

    private ClaimCandidateExtractor() {
    }

    /**
     * @param messages the turn/session messages in chronological order; only {@code USER} messages
     *                 with a non-blank {@code textContent} and a non-null id are considered.
     * @return zero or more candidates, de-duplicated by claim key with provenance merged.
     */
    public static List<ClaimCandidate> extract(List<DialogMessage> messages) {
        if (messages == null || messages.isEmpty()) return List.of();
        Map<String, Acc> byKey = new LinkedHashMap<>();
        for (DialogMessage message : messages) {
            if (message == null || message.id == null) continue;
            if (!"USER".equalsIgnoreCase(message.speaker)) continue;
            String text = message.textContent == null ? "" : message.textContent.trim();
            if (text.isEmpty()) continue;
            if (isNonClaim(text)) continue;
            detect(text, message.id, byKey);
        }
        List<ClaimCandidate> out = new ArrayList<>();
        for (Acc acc : byKey.values()) {
            out.add(acc.toCandidate());
        }
        return out;
    }

    /** Precision gates: never turn a question, a hypothetical or someone else's words into a claim. */
    private static boolean isNonClaim(String text) {
        for (String marker : QUESTION_MARKERS) if (text.contains(marker)) return true;
        for (String marker : HYPOTHETICAL_MARKERS) if (text.contains(marker)) return true;
        for (String phrase : REPORTED_SPEECH_PHRASES) if (text.contains(phrase)) return true;
        return false;
    }

    private static void detect(String text, long messageId, Map<String, Acc> byKey) {
        // Priority order: uncertainty and boundary are the most specific; preference/habit/value/
        // need/fact follow. Emotion patterns require both a recurrence marker and an emotion word so
        // momentary one-off feelings ("我今天特别累") never become a claim.
        int idx;

        if ((idx = text.indexOf("不确定")) >= 0) {
            String value = clean(text.substring(idx + 3));
            if (isConcrete(value)) {
                add(byKey, ClaimTypes.UNCERTAINTY, value, ClaimAuthority.SINGLE_EXPLICIT, 0.4, messageId, "不确定" + value, true);
            }
            return;
        }
        for (String trigger : List.of("受不了", "不能接受", "无法忍受", "最讨厌别人", "别碰我", "不许")) {
            if ((idx = text.indexOf(trigger)) >= 0) {
                String value = clean(text.substring(idx + trigger.length()));
                if (isConcrete(value)) {
                    add(byKey, ClaimTypes.BOUNDARY, value, ClaimAuthority.SINGLE_EXPLICIT, 0.6, messageId, trigger + value, false);
                }
                return;
            }
        }
        // TREND before EMOTION: "我最近越来越焦虑" is a change-over-time claim, not a momentary feeling.
        for (String trigger : List.of("越来越", "比以前更", "比以前", "渐渐变得", "慢慢变得", "越发")) {
            if ((idx = text.indexOf(trigger)) >= 0) {
                String value = clean(text.substring(idx + trigger.length()));
                if (isConcrete(value)) {
                    add(byKey, ClaimTypes.TREND, value, ClaimAuthority.SINGLE_EXPLICIT, 0.55, messageId, trigger + value, false);
                }
                return;
            }
        }
        if (hasRecurrence(text) && containsAny(text, EMOTION_WORDS)) {
            String value = clean(stripLeadingSubject(text));
            if (isConcrete(value)) {
                add(byKey, ClaimTypes.EMOTION_PATTERN, value, ClaimAuthority.REPEATED_BEHAVIOR, 0.6, messageId, value, false);
            }
            return;
        }
        for (String trigger : List.of("最喜欢", "喜欢", "喜爱", "偏爱", "热爱", "最爱", "讨厌", "不喜欢")) {
            if ((idx = text.indexOf(trigger)) >= 0) {
                String value = clean(text.substring(idx + trigger.length()));
                if (isConcrete(value)) {
                    add(byKey, ClaimTypes.PREFERENCE, value, ClaimAuthority.SINGLE_EXPLICIT, 0.55, messageId, trigger + value, false);
                } else {
                    // Implicit object (e.g. "...我一直都很喜欢"): fold into an existing preference the
                    // user already named, escalating it to repeated-explicit evidence.
                    mergeRepeat(byKey, ClaimTypes.PREFERENCE, text, messageId);
                }
                return;
            }
        }
        for (String trigger : List.of("每天", "每周", "每晚", "每年", "习惯", "总会", "都会去", "经常")) {
            if ((idx = text.indexOf(trigger)) >= 0) {
                String value = clean(text.substring(idx + trigger.length()));
                if (isConcrete(value)) {
                    add(byKey, ClaimTypes.HABIT, value, ClaimAuthority.REPEATED_BEHAVIOR, 0.6, messageId, trigger + value, false);
                }
                return;
            }
        }
        if (text.contains("重要") || text.contains("看重") || text.contains("在乎")) {
            String value = clean(stripLeadingSubject(text));
            if (isConcrete(value)) {
                add(byKey, ClaimTypes.VALUE, value, ClaimAuthority.SINGLE_EXPLICIT, 0.6, messageId, value, false);
            }
            return;
        }
        for (String trigger : List.of("需要", "想要", "希望", "渴望")) {
            if ((idx = text.indexOf(trigger)) >= 0) {
                String value = clean(text.substring(idx + trigger.length()));
                if (isConcrete(value)) {
                    add(byKey, ClaimTypes.NEED, value, ClaimAuthority.SINGLE_EXPLICIT, 0.55, messageId, trigger + value, false);
                }
                return;
            }
        }
        int workIdx = text.indexOf("工作");
        if (workIdx > 0) {
            int inIdx = text.lastIndexOf("在", workIdx);
            if (inIdx >= 0 && inIdx + 1 < workIdx) {
                String value = clean(text.substring(inIdx + 1, workIdx));
                if (isConcrete(value)) {
                    add(byKey, ClaimTypes.FACT, value, ClaimAuthority.SINGLE_EXPLICIT, 0.6, messageId, value + "工作", false);
                    return;
                }
            }
        }
        if ((idx = text.indexOf("我是")) >= 0 && !text.startsWith("不是", idx + 2)) {
            String value = clean(text.substring(idx + 2));
            if (isConcrete(value)) {
                add(byKey, ClaimTypes.FACT, value, ClaimAuthority.SINGLE_EXPLICIT, 0.6, messageId, "我是" + value, false);
            }
        }
    }

    private static boolean hasRecurrence(String text) {
        if (containsAny(text, RECURRENCE_MARKERS)) return true;
        int one = text.indexOf('一');
        return one >= 0 && text.indexOf('就', one) > one; // 「一……就……」pattern
    }

    private static boolean containsAny(String text, Set<String> needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    /** A value is concrete when it is non-blank and not a bare interrogative placeholder. */
    private static boolean isConcrete(String value) {
        if (value == null || value.isBlank()) return false;
        for (String q : INTERROGATIVE_OBJECTS) if (value.equals(q) || value.startsWith(q)) return false;
        return true;
    }

    private static String stripLeadingSubject(String text) {
        String value = text;
        for (String prefix : List.of("对我来说", "我觉得", "我认为", "我", "说真的，", "说真的")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
                break;
            }
        }
        return value;
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String filler : LEADING_FILLERS) {
                if (value.startsWith(filler)) {
                    value = value.substring(filler.length());
                    changed = true;
                }
            }
        }
        while (!value.isEmpty() && "。，！？、；.,!?; ".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1);
        }
        return value.trim();
    }

    private static void add(Map<String, Acc> byKey, String type, String value, String authority,
                            double confidence, long messageId, String evidence, boolean uncertain) {
        String key = type + ":" + value.replaceAll("[\\s。，！？、；.,!?;]", "");
        Acc acc = byKey.get(key);
        if (acc == null) {
            acc = new Acc(type, key, value, authority, confidence, evidence, uncertain);
            acc.ids.add(messageId);
            byKey.put(key, acc);
            return;
        }
        if (!acc.ids.contains(messageId)) acc.ids.add(messageId);
        acc.escalateRepeated();
    }

    private static void mergeRepeat(Map<String, Acc> byKey, String type, String text, long messageId) {
        for (Acc acc : byKey.values()) {
            if (acc.type.equals(type) && text.contains(acc.value)) {
                if (!acc.ids.contains(messageId)) acc.ids.add(messageId);
                acc.escalateRepeated();
                return;
            }
        }
    }

    private static final class Acc {
        final String type;
        final String key;
        final String value;
        String authority;
        double confidence;
        final String evidence;
        final boolean uncertain;
        final List<Long> ids = new ArrayList<>();

        Acc(String type, String key, String value, String authority, double confidence,
            String evidence, boolean uncertain) {
            this.type = type;
            this.key = key;
            this.value = value;
            this.authority = authority;
            this.confidence = confidence;
            this.evidence = evidence;
            this.uncertain = uncertain;
        }

        void escalateRepeated() {
            this.authority = ClaimAuthority.REPEATED_EXPLICIT;
            this.confidence = Math.max(this.confidence, 0.8);
        }

        ClaimCandidate toCandidate() {
            return new ClaimCandidate(type, key, value, authority, confidence, List.copyOf(ids), evidence, uncertain);
        }
    }
}

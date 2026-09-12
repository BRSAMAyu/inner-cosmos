package com.innercosmos.ai.retrieval;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CP-22 hard time-window semantics for retrieval. A query carrying a MEASURABLE recency
 * expression ("这周", "最近三个月", "上个月"...) is a hard constraint: memories whose effective
 * timestamp falls outside the window are excluded from the candidate set entirely, not merely
 * down-ranked. A bare, unmeasurable "最近" deliberately does NOT open a window — in natural
 * speech it often describes the recalling ("我最近老是想起以前…"), not the memory, and
 * hard-filtering on it would silently hide exactly the older material the user wants back.
 * Bare recency keeps its existing soft freshness signal in the scorer.
 */
public final class TimeWindowParser {

    private static final Pattern EXPLICIT_WINDOW = Pattern.compile(
            "最近(\\d+|[一二两三四五六七八九十]+)\\s*(天|日|周|星期|个月|月|年)"
                    + "|(今[天晚]|这[一]?[周星期]+|本[周月]|上[个]?[周月]|今年|最近[一两三]?个?月|最近[一两三]?年)");

    private TimeWindowParser() {
    }

    /** Maximum age in days a memory may have for this query, or null when no hard window applies. */
    public static Integer parseMaxAgeDays(String query) {
        if (query == null || query.isBlank()) return null;
        Matcher measured = Pattern.compile(
                "最近(\\d+|[一二两三四五六七八九十]+)\\s*(天|日|周|星期|个月|月|年)").matcher(query);
        if (measured.find()) {
            int amount = parseAmount(measured.group(1));
            return switch (measured.group(2)) {
                case "天", "日" -> amount;
                case "周", "星期" -> amount * 7;
                case "个月", "月" -> amount * 31;
                default -> amount * 366; // 年
            };
        }
        Matcher fixed = EXPLICIT_WINDOW.matcher(query);
        if (!fixed.find()) return null;
        String token = fixed.group();
        if (token.contains("年")) return 366;                     // 今年/最近一年 (checked before 今)
        if (token.contains("今")) return 1;                       // 今天/今晚
        if (token.startsWith("这") || token.startsWith("本")) {
            return token.contains("月") ? 31 : 7;                 // 这周/本周/本月
        }
        if (token.startsWith("上")) {
            return token.contains("月") ? 62 : 14;                // 上个月/上周
        }
        return 31;                                                 // 最近一个月/最近几个月
    }

    private static int parseAmount(String raw) {
        if (raw.chars().allMatch(Character::isDigit)) return Integer.parseInt(raw);
        int total = 0;
        int current = 0;
        for (char ch : raw.toCharArray()) {
            int digit = switch (ch) {
                case '一' -> 1; case '两', '二' -> 2; case '三' -> 3; case '四' -> 4; case '五' -> 5;
                case '六' -> 6; case '七' -> 7; case '八' -> 8; case '九' -> 9; default -> -1;
            };
            if (digit > 0) {
                current = digit;
            } else if (ch == '十') {
                total += (current == 0 ? 1 : current) * 10;
                current = 0;
            }
        }
        return total + current;
    }
}

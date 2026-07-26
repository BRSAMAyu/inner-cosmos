package com.innercosmos.ai.action;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-precision bilingual grammar for user-authorized Aurora actions.
 *
 * <p>This parser turns natural language into data only. It never performs an action and deliberately
 * ignores vague or inferred wishes. The resulting intent must be persisted and explicitly confirmed
 * by the owner before {@link AuroraNaturalActionService} may execute it.</p>
 */
@Component
public class AuroraNaturalActionParser {
    public static final String REMEMBER = "REMEMBER";
    public static final String REMINDER = "REMINDER";
    public static final String PROFILE_SETTING = "PROFILE_SETTING";

    private static final Pattern ZH_REMINDER = Pattern.compile(
            "^(?:请)?(?:在)?(?<when>(?:\\d+\\s*(?:分钟|小时)后|明天[^，,。；;]*|明早[^，,。；;]*|今晚[^，,。；;]*|"
                    + "(?:今天)?(?:上午|下午|晚上)\\s*\\d{1,2}(?:[:：点]\\d{0,2})?))\\s*提醒我\\s*(?<content>.+)$");
    private static final Pattern EN_RELATIVE_REMINDER = Pattern.compile(
            "^(?:please\\s+)?remind me\\s+(?<when>in\\s+\\d+\\s+(?:minutes?|hours?))\\s+(?:to\\s+)?(?<content>.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_TOMORROW_REMINDER = Pattern.compile(
            "^(?:please\\s+)?remind me\\s+(?<when>tomorrow(?:\\s+at)?\\s+\\d{1,2}(?::\\d{2})?(?:\\s*[ap]m)?)"
                    + "\\s+(?:to\\s+)?(?<content>.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_TRAILING_TIME_REMINDER = Pattern.compile(
            "^(?:please\\s+)?remind me\\s+(?:to\\s+)?(?<content>.+?)\\s+"
                    + "(?<when>tomorrow(?:\\s+at)?\\s+\\d{1,2}(?::\\d{2})?(?:\\s*[ap]m)?|"
                    + "at\\s+\\d{1,2}(?::\\d{2})?(?:\\s*[ap]m)?)$",
            Pattern.CASE_INSENSITIVE);

    public Decision parse(String rawMessage, String previousUserMessage, String timezone) {
        String message = trim(rawMessage);
        if (message == null) return Decision.none();
        boolean english = isMostlyEnglish(message);

        Decision reminder = reminder(message, timezone, english);
        if (reminder.recognized()) return reminder;

        Decision remember = remember(message, previousUserMessage, english);
        if (remember.recognized()) return remember;

        Decision setting = setting(message, english);
        if (setting.recognized()) return setting;
        return Decision.none();
    }

    private Decision remember(String message, String previousUserMessage, boolean english) {
        String lower = message.toLowerCase(Locale.ROOT);
        boolean cue = message.matches("^(?:请|麻烦你|帮我)?记住(?:[：:].*|这件事|这个|刚才(?:我)?说的(?:话|内容)?|.+)$")
                || lower.matches("^(?:please\\s+)?remember(?:\\s+this|\\s+that\\s+.+|\\s+what i just said|[\\s:：].+)$");
        if (!cue) return Decision.none();

        String content = explicitRememberContent(message, english);
        if (content == null || isRememberReference(content)) content = trim(previousUserMessage);
        if (content == null) {
            return Decision.clarify(english
                    ? "Tell me what you want kept, then I’ll show you the private memory draft before saving it."
                    : "告诉我想记住的具体内容，我会先把私密记忆草稿给你确认，再保存。");
        }
        content = bound(content, 4000);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("title", memoryTitle(content, english));
        payload.put("content", content);
        return Decision.intent(new ActionIntent(REMEMBER, payload,
                english ? "Keep this as a private, traceable memory: “" + bound(content, 120) + "”"
                        : "把这件事保存为仅你可见、可追溯的记忆：“" + bound(content, 120) + "”",
                english));
    }

    private Decision reminder(String message, String timezone, boolean english) {
        Matcher matcher = ZH_REMINDER.matcher(message);
        if (!matcher.matches()) {
            matcher = EN_RELATIVE_REMINDER.matcher(message);
            if (!matcher.matches()) {
                matcher = EN_TOMORROW_REMINDER.matcher(message);
                if (!matcher.matches()) matcher = EN_TRAILING_TIME_REMINDER.matcher(message);
            }
        }
        if (matcher.matches()) {
            String when = trim(matcher.group("when"));
            String content = trim(matcher.group("content"));
            if (content != null) content = content.replaceFirst("^(?:要|去|to)\\s*", "").trim();
            if (english && content != null) content = content.replaceFirst("[.!?]+$", "").trim();
            if (when == null || content == null) return reminderClarification(english);
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("when", bound(when, 120));
            payload.put("purpose", bound(content, 160));
            payload.put("content", bound(content, 4000));
            payload.put("timezone", trim(timezone) == null ? "Asia/Singapore" : bound(timezone.trim(), 64));
            String englishTimePhrase = when.toLowerCase(Locale.ROOT).startsWith("in ") ? " " + when : " at " + when;
            return Decision.intent(new ActionIntent(REMINDER, payload,
                    english ? "Schedule a real reminder for “" + bound(content, 120) + "”" + englishTimePhrase
                            : "在“" + when + "”真正提醒你：“" + bound(content, 120) + "”",
                    english));
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (message.contains("提醒我") || lower.contains("remind me")) return reminderClarification(english);
        return Decision.none();
    }

    private Decision setting(String message, boolean english) {
        String compact = message.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        String setting = null;
        String value = null;
        String label = null;

        if (containsAny(compact, "记忆读取", "读取我的记忆", "调用我的记忆", "回忆我的记忆",
                "memoryrecall", "usemymemories", "readmymemories")) {
            setting = "allowMemoryRecall";
            value = enabledValue(compact);
            label = english ? "memory recall" : "记忆回顾";
        } else if (containsAny(compact, "天气感知", "感知天气", "weatherawareness", "senseweather")) {
            setting = "weatherAwarenessEnabled";
            value = enabledValue(compact);
            label = english ? "weather awareness" : "天气感知";
        } else if (containsAny(compact, "时间感知", "感知时间", "timeawareness", "sensetime")) {
            setting = "timeAwarenessEnabled";
            value = enabledValue(compact);
            label = english ? "time awareness" : "时间感知";
        } else if (containsAny(compact, "每次只说一条", "每次只说一句", "只发一条", "singlemessage",
                "onemessageatatime")) {
            setting = "allowMultiMessage";
            value = "false";
            label = english ? "one message at a time" : "每次只说一条";
        } else if (containsAny(compact, "可以多说几条", "分几条说", "多条消息", "multiplemessages",
                "severalmessages")) {
            setting = "allowMultiMessage";
            value = "true";
            label = english ? "multi-message replies" : "分条回应";
        } else if (containsAny(compact, "关闭专注模式", "退出专注模式", "disablefocusmode", "turnofffocusmode")) {
            setting = "focusModeEnabled";
            value = "false";
            label = english ? "focus mode" : "专注模式";
        } else if (containsAny(compact, "开启专注模式", "打开专注模式", "enablefocusmode", "turnonfocusmode")) {
            setting = "focusModeEnabled";
            value = "true";
            label = english ? "focus mode" : "专注模式";
        } else if (containsAny(compact, "少主动一点", "降低主动性", "不要太主动", "lessproactive",
                "reduceproactivity")) {
            setting = "proactiveSensitivity";
            value = "1";
            label = english ? "lower proactivity" : "较低主动性";
        } else if (containsAny(compact, "更主动一点", "提高主动性", "主动一些", "moreproactive",
                "increaseproactivity")) {
            setting = "proactiveSensitivity";
            value = "4";
            label = english ? "higher proactivity" : "较高主动性";
        }

        if (setting == null || value == null) return Decision.none();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("setting", setting);
        payload.put("value", value);
        payload.put("label", label);
        String enabled = "true".equals(value) ? (english ? "enable " : "开启")
                : "false".equals(value) ? (english ? "disable " : "关闭") : "";
        return Decision.intent(new ActionIntent(PROFILE_SETTING, payload,
                english ? "Change Aurora’s authorized setting: " + enabled + label
                        : "调整 Aurora 的可授权设置：" + enabled + label,
                english));
    }

    private static String enabledValue(String compact) {
        if (containsAny(compact, "不要", "不再", "关闭", "停止", "禁用", "disable", "turnoff", "stop")) {
            return "false";
        }
        if (containsAny(compact, "允许", "可以", "开启", "打开", "启用", "enable", "turnon", "allow")) {
            return "true";
        }
        return null;
    }

    private static String explicitRememberContent(String message, boolean english) {
        String value = message;
        if (english) {
            value = value.replaceFirst("(?i)^(?:please\\s+)?remember\\s*(?::|that\\s+)?", "");
        } else {
            value = value.replaceFirst("^(?:请|麻烦你|帮我)?记住\\s*[：:]?\\s*", "");
        }
        return trim(value);
    }

    private static boolean isRememberReference(String value) {
        if (value == null) return true;
        String compact = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return containsAny(compact, "这件事", "这个", "刚才说的", "刚才我说的", "刚才的内容",
                "this", "whatijustsaid");
    }

    private static String memoryTitle(String content, boolean english) {
        String first = content.replaceAll("\\s+", " ").split("[。！？.!?;；\\n]", 2)[0].trim();
        if (first.isBlank()) return english ? "Something I asked Aurora to remember" : "我请 Aurora 记住的事";
        return bound(first, 80);
    }

    private static Decision reminderClarification(boolean english) {
        return Decision.clarify(english
                ? "I can set a real reminder, but I need both the time and what to remind you about — for example, “Remind me in 2 hours to check the build.”"
                : "我可以设置真实提醒，但需要时间和内容都明确，例如：“2 小时后提醒我检查构建结果。”");
    }

    private static boolean isMostlyEnglish(String value) {
        return value.codePoints().noneMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String bound(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    public record ActionIntent(String type, Map<String, String> payload, String summary, boolean english) {}

    public record Decision(boolean recognized, ActionIntent intent, String clarification) {
        public static Decision none() { return new Decision(false, null, null); }
        public static Decision intent(ActionIntent intent) { return new Decision(true, intent, null); }
        public static Decision clarify(String clarification) { return new Decision(true, null, clarification); }
    }
}

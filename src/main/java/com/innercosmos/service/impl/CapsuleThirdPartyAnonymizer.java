package com.innercosmos.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Derives a public-safe, stable third-party representation for one compiled Genome snapshot.
 * It never mutates the owner's private UnderstandingClaim.
 */
@Component
public class CapsuleThirdPartyAnonymizer {

    private static final Pattern ROLE_NAME = Pattern.compile(
            "(朋友|同事|同学|室友|伴侣|对象|老师|领导|老板)(?:叫|是|名叫)?"
                    + "([\\p{IsHan}]{1,3}?)(?=今天|昨天|最近|说|觉得|和|在|，|。|、|\\s|$)");
    private static final Pattern LITTLE_NAME = Pattern.compile("小[\\p{IsHan}]");

    public Session beginSnapshot() {
        return new Session();
    }

    public static final class Session {
        private final Map<String, String> aliases = new LinkedHashMap<>();
        private final Map<String, AtomicInteger> counters = new LinkedHashMap<>();

        public String anonymize(String raw) {
            if (raw == null || raw.isBlank()) return "";
            String value = raw
                    .replace("我妈妈", "家人").replace("我妈", "家人")
                    .replace("妈妈", "家人").replace("母亲", "家人")
                    .replace("我爸爸", "家人").replace("我爸", "家人")
                    .replace("爸爸", "家人").replace("父亲", "家人");

            Matcher roleMatcher = ROLE_NAME.matcher(value);
            StringBuffer roleSafe = new StringBuffer();
            while (roleMatcher.find()) {
                String role = roleMatcher.group(1);
                String entity = roleMatcher.group();
                String name = roleMatcher.group(2);
                String alias = aliases.get(name);
                if (alias == null) alias = aliases.computeIfAbsent(entity, ignored -> nextAlias(role));
                aliases.putIfAbsent(name, alias);
                roleMatcher.appendReplacement(roleSafe, Matcher.quoteReplacement(alias));
            }
            roleMatcher.appendTail(roleSafe);

            Matcher littleName = LITTLE_NAME.matcher(roleSafe.toString());
            StringBuffer safe = new StringBuffer();
            while (littleName.find()) {
                String entity = littleName.group();
                String alias = aliases.computeIfAbsent(entity, ignored -> nextAlias("朋友"));
                littleName.appendReplacement(safe, Matcher.quoteReplacement(alias));
            }
            littleName.appendTail(safe);
            return safe.toString();
        }

        private String nextAlias(String role) {
            String normalized = switch (role) {
                case "朋友", "同学", "室友" -> "一位朋友";
                case "同事", "领导", "老板" -> "一位同事";
                case "老师" -> "一位老师";
                case "伴侣", "对象" -> "伴侣";
                default -> "一位相关的人";
            };
            int sequence = counters.computeIfAbsent(normalized, ignored -> new AtomicInteger()).incrementAndGet();
            return sequence == 1 ? normalized : normalized + " " + (char) ('A' + Math.min(sequence - 2, 25));
        }
    }
}

package com.innercosmos.ai.lexicon;

import java.util.HashSet;
import java.util.Set;

/**
 * Chinese stopwords for pseudo-semantic analysis.
 * These words should be filtered out before sentiment analysis.
 */
public final class ChineseStopwords {

    private static final Set<String> STOPWORDS = new HashSet<>();

    static {
        // === Pronouns ===
        STOPWORDS.add("我");
        STOPWORDS.add("我的");
        STOPWORDS.add("你");
        STOPWORDS.add("你的");
        STOPWORDS.add("他");
        STOPWORDS.add("他的");
        STOPWORDS.add("她");
        STOPWORDS.add("她的");
        STOPWORDS.add("它");
        STOPWORDS.add("它的");
        STOPWORDS.add("我们");
        STOPWORDS.add("咱们");
        STOPWORDS.add("你们");
        STOPWORDS.add("他们");
        STOPWORDS.add("她们");
        STOPWORDS.add("自己");
        STOPWORDS.add("这");
        STOPWORDS.add("这个");
        STOPWORDS.add("那");
        STOPWORDS.add("那个");
        STOPWORDS.add("哪个");
        STOPWORDS.add("什么");
        STOPWORDS.add("某些");
        STOPWORDS.add("其它");
        STOPWORDS.add("其它");
        STOPWORDS.add("大家");
        STOPWORDS.add("某人");

        // === Prepositions/Conjunctions ===
        STOPWORDS.add("的");
        STOPWORDS.add("地");
        STOPWORDS.add("得");
        STOPWORDS.add("和");
        STOPWORDS.add("跟");
        STOPWORDS.add("与");
        STOPWORDS.add("及");
        STOPWORDS.add("或");
        STOPWORDS.add("还是");
        STOPWORDS.add("但是");
        STOPWORDS.add("可是");
        STOPWORDS.add("然而");
        STOPWORDS.add("不过");
        STOPWORDS.add("只是");
        STOPWORDS.add("因为");
        STOPWORDS.add("由于");
        STOPWORDS.add("所以");
        STOPWORDS.add("因此");
        STOPWORDS.add("于是");
        STOPWORDS.add("如果");
        STOPWORDS.add("要是");
        STOPWORDS.add("假如");
        STOPWORDS.add("倘若");
        STOPWORDS.add("只要");
        STOPWORDS.add("除非");
        STOPWORDS.add("无论");
        STOPWORDS.add("不管");
        STOPWORDS.add("即使");
        STOPWORDS.add("尽管");
        STOPWORDS.add("虽然");
        STOPWORDS.add("为了");
        STOPWORDS.add("给");
        STOPWORDS.add("对");
        STOPWORDS.add("向");
        STOPWORDS.add("往");
        STOPWORDS.add("在");
        STOPWORDS.add("于");
        STOPWORDS.add("从");
        STOPWORDS.add("自");
        STOPWORDS.add("由");
        STOPWORDS.add("关于");
        STOPWORDS.add("至于");
        STOPWORDS.add("按照");
        STOPWORDS.add("依照");
        STOPWORDS.add("按照");

        // === Verbs (auxiliary) ===
        STOPWORDS.add("是");
        STOPWORDS.add("不是");
        STOPWORDS.add("是吗");
        STOPWORDS.add("是不是");
        STOPWORDS.add("有");
        STOPWORDS.add("没有");
        STOPWORDS.add("会");
        STOPWORDS.add("不会");
        STOPWORDS.add("能");
        STOPWORDS.add("不能");
        STOPWORDS.add("可以");
        STOPWORDS.add("不可以");
        STOPWORDS.add("可能");
        STOPWORDS.add("应该");
        STOPWORDS.add("必须");
        STOPWORDS.add("得");
        STOPWORDS.add("要");
        STOPWORDS.add("想");
        STOPWORDS.add("做");
        STOPWORDS.add("说");
        STOPWORDS.add("看");
        STOPWORDS.add("听");
        STOPWORDS.add("觉得");
        STOPWORDS.add("认为");
        STOPWORDS.add("以为");
        STOPWORDS.add("发现");
        STOPWORDS.add("感觉");
        STOPWORDS.add("感到");
        STOPWORDS.add("变得");
        STOPWORDS.add("成为");
        STOPWORDS.add("来");
        STOPWORDS.add("去");
        STOPWORDS.add("走");
        STOPWORDS.add("跑");
        STOPWORDS.add("回来");
        STOPWORDS.add("回去");

        // === Time/Place ===
        STOPWORDS.add("今天");
        STOPWORDS.add("昨天");
        STOPWORDS.add("前天");
        STOPWORDS.add("明天");
        STOPWORDS.add("后天");
        STOPWORDS.add("现在");
        STOPWORDS.add("刚才");
        STOPWORDS.add("之前");
        STOPWORDS.add("以后");
        STOPWORDS.add("后来");
        STOPWORDS.add("一直");
        STOPWORDS.add("总是");
        STOPWORDS.add("经常");
        STOPWORDS.add("有时");
        STOPWORDS.add("偶尔");
        STOPWORDS.add("从来");
        STOPWORDS.add("永远");
        STOPWORDS.add("曾经");
        STOPWORDS.add("已经");
        STOPWORDS.add("还没");
        STOPWORDS.add("还没有");
        STOPWORDS.add("这里");
        STOPWORDS.add("那里");
        STOPWORDS.add("哪里");
        STOPWORDS.add("哪儿");
        STOPWORDS.add("这里");
        STOPWORDS.add("那儿");

        // === Numbers/Quantifiers ===
        STOPWORDS.add("一");
        STOPWORDS.add("二");
        STOPWORDS.add("三");
        STOPWORDS.add("四");
        STOPWORDS.add("五");
        STOPWORDS.add("六");
        STOPWORDS.add("七");
        STOPWORDS.add("八");
        STOPWORDS.add("九");
        STOPWORDS.add("十");
        STOPWORDS.add("百");
        STOPWORDS.add("千");
        STOPWORDS.add("万");
        STOPWORDS.add("几");
        STOPWORDS.add("有些");
        STOPWORDS.add("很多");
        STOPWORDS.add("少许");
        STOPWORDS.add("少许");
        STOPWORDS.add("一点");
        STOPWORDS.add("些");
        STOPWORDS.add("个");
        STOPWORDS.add("位");
        STOPWORDS.add("件");
        STOPWORDS.add("条");
        STOPWORDS.add("只");
        STOPWORDS.add("些");
        STOPWORDS.add("次");

        // === Adverbs (neutral) ===
        STOPWORDS.add("很");
        STOPWORDS.add("太");
        STOPWORDS.add("更");
        STOPWORDS.add("最");
        STOPWORDS.add("非常");
        STOPWORDS.add("特别");
        STOPWORDS.add("尤其");
        STOPWORDS.add("相当");
        STOPWORDS.add("比较");
        STOPWORDS.add("有点");
        STOPWORDS.add("稍微");
        STOPWORDS.add("稍稍");
        STOPWORDS.add("略");
        STOPWORDS.add("完全");
        STOPWORDS.add("十分");
        STOPWORDS.add("好");
        STOPWORDS.add("真");
        STOPWORDS.add("确实");
        STOPWORDS.add("实在");
        STOPWORDS.add("简直");
        STOPWORDS.add("简直");
        STOPWORDS.add("一直");
        STOPWORDS.add("一直");
        STOPWORDS.add("也");
        STOPWORDS.add("都");
        STOPWORDS.add("还");
        STOPWORDS.add("就");
        STOPWORDS.add("才");
        STOPWORDS.add("已");
        STOPWORDS.add("又");
        STOPWORDS.add("再");
        STOPWORDS.add("还是");
        STOPWORDS.add("只");
        STOPWORDS.add("光");
        STOPWORDS.add("单");
        STOPWORDS.add("仅仅");
        STOPWORDS.add("不");
        STOPWORDS.add("没");
        STOPWORDS.add("别");
        STOPWORDS.add("无");

        // === Question words ===
        STOPWORDS.add("吗");
        STOPWORDS.add("呢");
        STOPWORDS.add("吧");
        STOPWORDS.add("啊");
        STOPWORDS.add("呀");
        STOPWORDS.add("哦");
        STOPWORDS.add("啦");
        STOPWORDS.add("嘛");
        STOPWORDS.add("哈");
        STOPWORDS.add("哇");
        STOPWORDS.add("哪");
        STOPWORDS.add("谁");
        STOPWORDS.add("怎么");
        STOPWORDS.add("怎样");
        STOPWORDS.add("如何");
        STOPWORDS.add("为何");
        STOPWORDS.add("几时");
        STOPWORDS.add("多");
        STOPWORDS.add("多少");

        // === Others ===
        STOPWORDS.add("这样");
        STOPWORDS.add("那样");
        STOPWORDS.add("如何");
        STOPWORDS.add("如此");
        STOPWORDS.add("同样");
        STOPWORDS.add("一样");
        STOPWORDS.add("所谓");
        STOPWORDS.add("等等");
        STOPWORDS.add("等等");
        STOPWORDS.add("之类");
        STOPWORDS.add之类的");
        STOPWORDS.add("总之");
        STOPWORDS.add("总的来说");
        STOPWORDS.add("基本上");
        STOPWORDS.add("一般");
    }

    private ChineseStopwords() {}

    /**
     * Check if a word is a stopword.
     */
    public static boolean isStopword(String word) {
        if (word == null || word.isBlank()) return false;
        return STOPWORDS.contains(word.trim());
    }

    /**
     * Get all stopwords.
     */
    public static Set<String> getAll() {
        return new HashSet<>(STOPWORDS);
    }

    /**
     * Get total stopword count.
     */
    public static int size() {
        return STOPWORDS.size();
    }
}

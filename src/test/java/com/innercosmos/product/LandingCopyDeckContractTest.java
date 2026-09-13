package com.innercosmos.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-53 landing copy deck contract over
 * docs/commercialization/product/landing-copy.deck.yml. The blueprint acceptance
 * (§7 CP-53, L815) is "落地页说得出具体任务、AI身份和隐私边界，安装／注册／取消路径真实"
 * plus "示例与用户真实证言区分" and the recovery clause "不从用户倾诉自动生成营销内容".
 * Honesty discipline mirrors the sibling product ledgers: section status only moves
 * between DRAFT and PENDING_VERIFICATION — OPERATOR_APPROVED is an operator-only
 * external-receipt state that must never appear as a field value here, install/cancel
 * paths stay PENDING_VERIFICATION until the operator verifies them against the real
 * release (they currently point at the ephemeral classroom demo tunnel + Debug APK),
 * and no real user testimonial exists yet, so every quote is a labelled synthetic
 * example.
 */
class LandingCopyDeckContractTest {

    private static final Path DECK = Path.of("docs", "commercialization", "product", "landing-copy.deck.yml");

    private static final Set<String> ALLOWED_STATUSES = Set.of("DRAFT", "PENDING_VERIFICATION");
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "hero", "what-it-is", "privacy-boundary", "install-path", "trust-notes", "evidence-discipline");

    /** 空泛陪伴话术黑名单：hero 价值主张不得以情绪空话替代具体任务（CP-53 验收）。 */
    private static final List<String> VAGUE_PROMISE_WORDS = List.of(
            "懂你", "最懂", "灵魂伴侣", "心灵伴侣", "贴心闺蜜", "完美恋人");
    /** 具体任务词：hero 必须落在本产品可演示的真实任务词上。 */
    private static final List<String> CONCRETE_TASK_TERMS = List.of(
            "倾诉", "沉淀", "记忆卡片", "待办", "情绪轨迹", "回看", "复盘", "脱敏");

    /** "不是"+（真人/医疗/诊断）至少两处 —— AI 身份声明的最低诚实线。 */
    private record NegationClaim(String concept, Pattern pattern) {
    }

    private static final List<NegationClaim> AI_IDENTITY_NEGATIONS = List.of(
            new NegationClaim("真人", Pattern.compile("不是[^。；;，,]{0,4}真人")),
            new NegationClaim("医疗", Pattern.compile("(不是|不属于)[^。；;，,]{0,6}医疗")),
            new NegationClaim("诊断", Pattern.compile("(不做|不提供|不是)[^。；;，,]{0,6}诊断")));

    private static final Pattern SECTION = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern ITEM = Pattern.compile("^  - (\\w+):\\s*(.*)$");
    private static final Pattern FIELD = Pattern.compile("^    (\\w+):\\s*(.*)$");

    private record Item(Map<String, String> fields) {
        String f(String key) {
            return fields.get(key);
        }
    }

    @Test
    void deckCoversSixRequiredSectionsWithHonestStatusesOnly() throws IOException {
        List<Item> sections = parse(DECK, "sections");
        List<String> failures = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        assertTrue(sections.size() >= REQUIRED_SECTIONS.size(), "章节至少覆盖 " + REQUIRED_SECTIONS);
        for (Item section : sections) {
            String id = section.f("id");
            if (!REQUIRED_SECTIONS.contains(id)) {
                failures.add("未知章节 " + id);
            }
            if (!seen.add(id)) {
                failures.add("章节重复 " + id);
            }
            if (isBlank(section.f("title"))) {
                failures.add(id + ": 缺少 title");
            }
            String status = section.f("status");
            if (!ALLOWED_STATUSES.contains(status)) {
                failures.add(id + ": status \"" + status + "\" 不在 " + ALLOWED_STATUSES);
            }
        }
        for (String required : REQUIRED_SECTIONS) {
            if (!seen.contains(required)) {
                failures.add("缺少章节 " + required);
            }
        }
        assertTrue(failures.isEmpty(), "landing deck 结构 violations: " + failures);
    }

    @Test
    void noFieldValueAnywhereClaimsOperatorApproval() throws IOException {
        // OPERATOR_APPROVED 只能由 operator 以外部核对回执落位（本文件之外）；
        // agent/测试自行填写任何字段的该值都是伪造完成态。
        List<String> failures = new ArrayList<>();
        for (Item item : parse(DECK, "sections")) {
            collectApprovalClaims("sections/" + item.f("id"), item, failures);
        }
        for (Item item : parse(DECK, "assets")) {
            collectApprovalClaims("assets/" + item.f("asset_id"), item, failures);
        }
        assertTrue(failures.isEmpty(), "OPERATOR_APPROVED 违规出现: " + failures);
    }

    @Test
    void heroPromisesConcreteTasksInsteadOfVagueCompanionTalk() throws IOException {
        Item hero = sectionById("hero");
        String text = joinedValues(hero);

        int distinctTasks = 0;
        for (String term : CONCRETE_TASK_TERMS) {
            if (text.contains(term)) {
                distinctTasks++;
            }
        }
        assertTrue(distinctTasks >= 3,
                "hero 必须说得出具体任务（至少命中 3 个任务词，候选 " + CONCRETE_TASK_TERMS
                        + "），实际命中 " + distinctTasks);

        List<String> failures = new ArrayList<>();
        for (Item section : parse(DECK, "sections")) {
            for (Map.Entry<String, String> field : section.fields().entrySet()) {
                String value = field.getValue();
                if (value == null) {
                    continue;
                }
                for (String word : VAGUE_PROMISE_WORDS) {
                    if (value.contains(word)) {
                        failures.add(section.f("id") + "/" + field.getKey()
                                + ": 空泛陪伴话术 \"" + word + "\" 不得出现在落地页文案");
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "空泛话术黑名单 violations: " + failures);
    }

    @Test
    void aiIdentityStatementNegatesHumanAndMedicalAtLeastTwice() throws IOException {
        String text = joinedValues(sectionById("what-it-is"));
        List<String> matched = new ArrayList<>();
        for (NegationClaim claim : AI_IDENTITY_NEGATIONS) {
            if (claim.pattern().matcher(text).find()) {
                matched.add(claim.concept());
            }
        }
        assertTrue(matched.size() >= 2,
                "AI 身份声明必须以\"不是\"明确否定 真人/医疗/诊断 至少两处，实际否定: " + matched);
    }

    @Test
    void privacyBoundaryCarriesAllThreeLines() throws IOException {
        Item privacy = sectionById("privacy-boundary");
        List<String> failures = new ArrayList<>();

        String line1 = privacy.f("line_1");
        if (isBlank(line1) || !line1.contains("对话") || !(line1.contains("P0") || line1.contains("仅"))) {
            failures.add("line_1 必须声明：原始对话（P0）仅本人可见");
        }
        String line2 = privacy.f("line_2");
        if (isBlank(line2) || !line2.contains("共鸣体") || !line2.contains("授权")) {
            failures.add("line_2 必须声明：共鸣体仅携带授权后的抽象信息");
        }
        String line3 = privacy.f("line_3");
        if (isBlank(line3) || !line3.contains("数据") || !line3.contains("付费墙")) {
            failures.add("line_3 必须声明：数据权利无付费墙");
        }
        assertTrue(failures.isEmpty(), "隐私边界三行 violations: " + failures);
    }

    @Test
    void installRegistrationAndCancellationPathsExistAndStayPendingVerification() throws IOException {
        Item install = sectionById("install-path");
        List<String> failures = new ArrayList<>();

        for (String field : List.of("web_entry", "android_download", "registration", "cancellation")) {
            if (isBlank(install.f(field))) {
                failures.add("install-path 缺少 " + field);
            }
        }
        if (!"PENDING_VERIFICATION".equals(install.f("status"))) {
            failures.add("install-path status 必须保持 PENDING_VERIFICATION"
                    + "（operator 按真实发布逐项核对前不得自动宣称已验证）");
        }
        String web = install.f("web_entry");
        if (!isBlank(web) && !web.contains("/app/aurora/")) {
            failures.add("Web 入口必须写当前真实地址 /app/aurora/");
        }
        String apk = install.f("android_download");
        if (!isBlank(apk) && !apk.toLowerCase().contains("apk")) {
            failures.add("Android 下载描述必须指向真实 APK 下载路径");
        }
        String registration = install.f("registration");
        if (!isBlank(registration) && !registration.contains("18")) {
            failures.add("注册路径必须声明 18+ 年龄门槛");
        }
        String cancellation = install.f("cancellation");
        if (!isBlank(cancellation) && (!cancellation.contains("账户设置") || !cancellation.contains("删除账户"))) {
            failures.add("取消路径必须指向真实入口：账户设置 → 删除账户");
        }
        assertTrue(failures.isEmpty(), "安装/注册/取消路径 violations: " + failures);
    }

    @Test
    void evidenceDisciplineSeparatesSyntheticExamplesAndCommitsNoAutoMarketing() throws IOException {
        String text = joinedValues(sectionById("evidence-discipline"));
        List<String> failures = new ArrayList<>();

        if (!text.contains("不从用户倾诉自动生成营销内容")) {
            failures.add("缺少 CP-53 恢复条款承诺：不从用户倾诉自动生成营销内容");
        }
        if (!text.contains("当前无任何真实用户证言")) {
            failures.add("必须如实声明：当前无任何真实用户证言");
        }
        if (!text.contains("AI 合成") || !text.contains("非真实用户")) {
            failures.add("所有示例必须带「AI 合成、非真实用户」标注");
        }
        if (!text.contains("撤下")) {
            failures.add("缺少违规或误导素材立即撤下并记录影响的恢复条款");
        }
        assertTrue(failures.isEmpty(), "示例/证言区分 violations: " + failures);
    }

    @Test
    void assetRegistryKeepsAuthorizationBlankAndAdLabelMandatory() throws IOException {
        List<Item> assets = parse(DECK, "assets");
        List<String> failures = new ArrayList<>();

        assertTrue(assets.size() >= 3,
                "素材登记至少覆盖 落地页视觉/应用截图/分享卡模板 三类，实际 " + assets.size());
        for (Item asset : assets) {
            String where = "assets/" + asset.f("asset_id");
            if (isBlank(asset.f("asset_id"))) {
                failures.add("素材条目缺少 asset_id");
                continue;
            }
            if (!isBlank(asset.f("authorized"))) {
                failures.add(where + ": authorized 必须为 null（operator 外部授权回执落位前不得对外使用）");
            }
            if (!"true".equals(asset.f("ad_label_required"))) {
                failures.add(where + ": ad_label_required 必须为 true（进入付费/推广渠道必须带广告标识）");
            }
        }
        assertTrue(failures.isEmpty(), "素材登记 violations: " + failures);
    }

    // ---------- helpers ----------

    private static void collectApprovalClaims(String where, Item item, List<String> failures) {
        for (Map.Entry<String, String> field : item.fields().entrySet()) {
            String value = field.getValue();
            if (value != null && value.contains("OPERATOR_APPROVED")) {
                failures.add(where + "/" + field.getKey()
                        + " 出现 OPERATOR_APPROVED —— 该态只能由 operator 以外部回执落位");
            }
        }
    }

    private Item sectionById(String id) throws IOException {
        for (Item section : parse(DECK, "sections")) {
            if (id.equals(section.f("id"))) {
                return section;
            }
        }
        throw new IllegalStateException("landing deck 缺少章节 " + id);
    }

    private static String joinedValues(Item item) {
        StringBuilder text = new StringBuilder();
        for (String value : item.fields().values()) {
            if (value != null) {
                text.append(value).append('\n');
            }
        }
        return text.toString();
    }

    // ---------- tiny YAML subset parser (same discipline as the sibling product contracts) ----------

    private static List<Item> parse(Path file, String section) throws IOException {
        List<Item> items = new ArrayList<>();
        boolean inSection = false;
        Item current = null;
        for (String line : Files.readString(file, StandardCharsets.UTF_8).split("\n")) {
            Matcher sec = SECTION.matcher(line);
            if (sec.find()) {
                if (current != null) items.add(current);
                current = null;
                inSection = section.equals(sec.group(1));
                continue;
            }
            if (!inSection) continue;
            Matcher item = ITEM.matcher(line);
            if (item.find()) {
                if (current != null) items.add(current);
                current = new Item(new LinkedHashMap<>());
                current.fields().put(item.group(1), stripQuotes(item.group(2)));
                continue;
            }
            Matcher field = FIELD.matcher(line);
            if (field.find() && current != null) {
                current.fields().put(field.group(1), stripQuotes(field.group(2)));
            }
        }
        if (current != null) items.add(current);
        return items;
    }

    private static String stripQuotes(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equals(value);
    }
}

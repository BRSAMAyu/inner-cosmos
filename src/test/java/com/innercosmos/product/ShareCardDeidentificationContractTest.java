package com.innercosmos.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-53 share-card de-identification contract. The blueprint (§7 CP-53, L814) demands
 * "分享卡去身份预览" — but the repository currently has NO share-card implementation
 * (no share-card render path under web/src or static/, no share endpoint in the
 * controllers), so this is a forward-looking contract over
 * docs/commercialization/product/share-card.schema.md: the payload schema is frozen
 * now (de-identified summary fields only, forbidden identity/raw-content field
 * blacklist, mandatory permanent syntheticLabel), together with the
 * "去身份预览先于发布" commitment. A sentinel test re-verifies the absence premise:
 * the moment a share-card implementation file appears (CP-31), this contract must be
 * upgraded to assert de-identification on the real rendered output — 输出不含用户名/
 * 昵称/真实记忆原文/P0 词，合成示例永久标注 AI。
 */
class ShareCardDeidentificationContractTest {

    private static final Path SCHEMA = Path.of("docs", "commercialization", "product", "share-card.schema.md");

    /** 任务冻结的禁止字段黑名单下限：身份与原始内容四类。 */
    private static final List<String> REQUIRED_FORBIDDEN_FIELDS = List.of(
            "username", "nickname", "rawMemory", "dialogExcerpt");
    /** schema 当前冻结的完整黑名单（扩充须同步改本契约）。 */
    private static final Set<String> FROZEN_FORBIDDEN_FIELDS = Set.of(
            "username", "nickname", "rawMemory", "dialogExcerpt",
            "userId", "realName", "phone", "email", "avatarUrl");

    private static final Pattern HEADING = Pattern.compile("^##\\s.*$");
    private static final Pattern FENCED_FIELD = Pattern.compile("^- `([A-Za-z]\\w*)`");

    @Test
    void schemaDocExistsAndCarriesDeidentifiedPreviewCommitments() throws IOException {
        assertTrue(Files.exists(SCHEMA),
                "缺少 docs/commercialization/product/share-card.schema.md（CP-53 分享卡去身份前瞻契约）");
        String text = Files.readString(SCHEMA, StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();

        if (!text.contains("尚无分享卡实现")) {
            failures.add("schema 必须如实声明：当前仓库尚无分享卡实现（前瞻契约，不是已完成功能）");
        }
        if (!text.contains("前瞻")) {
            failures.add("schema 必须标明前瞻契约性质");
        }
        if (!text.contains("去身份预览先于发布")) {
            failures.add("缺少核心承诺：去身份预览先于发布");
        }
        if (!text.contains("不从用户倾诉自动生成营销内容")) {
            failures.add("缺少 CP-53 恢复条款：不从用户倾诉自动生成营销内容");
        }
        if (!text.contains("P0")) {
            failures.add("必须写明分享卡禁止携带 P0 原始对话");
        }
        assertTrue(failures.isEmpty(), "share-card schema 承诺 violations: " + failures);
    }

    @Test
    void syntheticLabelIsMandatoryAndPermanent() throws IOException {
        String text = Files.readString(SCHEMA, StandardCharsets.UTF_8);
        List<String> failures = new ArrayList<>();

        if (!text.contains("syntheticLabel")) {
            failures.add("schema 必须定义必含字段 syntheticLabel");
        }
        // 恒为 true：合成标注是永久性的，不存在关闭选项。
        if (!Pattern.compile("syntheticLabel[^\\n]{0,40}true").matcher(text).find()) {
            failures.add("syntheticLabel 必须声明恒为 true");
        }
        if (!text.contains("不得移除") && !text.contains("去不掉")) {
            failures.add("必须写明渲染层不得移除合成标注（无关闭选项）");
        }
        assertTrue(failures.isEmpty(), "syntheticLabel violations: " + failures);
    }

    @Test
    void forbiddenFieldBlacklistIsCompleteAndDisjointFromAllowedFields() throws IOException {
        String text = Files.readString(SCHEMA, StandardCharsets.UTF_8);
        List<String> forbidden = bulletedFields(text, "禁止字段");
        List<String> allowed = bulletedFields(text, "允许字段");
        List<String> failures = new ArrayList<>();

        for (String required : REQUIRED_FORBIDDEN_FIELDS) {
            if (!forbidden.contains(required)) {
                failures.add("禁止字段黑名单缺少 " + required);
            }
        }
        if (!new LinkedHashSet<>(forbidden).equals(FROZEN_FORBIDDEN_FIELDS)) {
            failures.add("禁止字段黑名单与契约冻结集合不一致: " + forbidden + "（扩充黑名单须同步改本契约测试）");
        }
        if (!allowed.contains("syntheticLabel")) {
            failures.add("允许字段白名单必须包含 syntheticLabel");
        }
        for (String field : forbidden) {
            if (allowed.contains(field)) {
                failures.add("字段 " + field + " 同时出现在允许与禁止清单");
            }
        }
        assertTrue(failures.isEmpty(), "禁止字段黑名单 violations: " + failures);
    }

    @Test
    void noShareCardImplementationExistsYetSoContractStaysForwardLooking() throws IOException {
        // 实现哨兵：当前仓库不应存在分享卡实现文件。CP-31 落地当天，本断言会失败并
        // 提醒把本前瞻契约升级为对真实渲染产物的去身份断言。
        List<String> hits = new ArrayList<>();
        List<Path> roots = List.of(
                Path.of("web", "src"),
                Path.of("src", "main", "java"),
                Path.of("src", "main", "resources", "static"));
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString().toLowerCase())
                        .filter(name -> name.contains("sharecard")
                                || name.contains("share-card")
                                || name.contains("share_card"))
                        .forEach(hits::add);
            }
        }
        assertTrue(hits.isEmpty(),
                "分享卡实现文件已出现: " + hits + " —— 请把本前瞻契约升级为对真实渲染产物的去身份断言："
                        + "输出不含用户名/昵称/真实记忆原文/P0 词，合成示例永久标注 AI，去身份预览先于发布");
    }

    // ---------- helpers ----------

    /** 提取 markdown 某二级标题（headingMarker 子串匹配）下 "- `field`" 形式的字段清单。 */
    private static List<String> bulletedFields(String markdown, String headingMarker) {
        List<String> fields = new ArrayList<>();
        boolean inSection = false;
        for (String line : markdown.split("\n")) {
            if (HEADING.matcher(line).matches()) {
                inSection = line.contains(headingMarker);
                continue;
            }
            if (!inSection) {
                continue;
            }
            Matcher field = FENCED_FIELD.matcher(line.trim());
            if (field.find()) {
                fields.add(field.group(1));
            }
        }
        return fields;
    }
}

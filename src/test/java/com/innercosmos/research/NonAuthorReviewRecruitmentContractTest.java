package com.innercosmos.research;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-23 next_action ("非作者评审招募材料(CP-12联动)") contract for
 * docs/commercialization/research/cp23-non-author-review-recruitment.md.
 * The material must: quote the vision mission verbatim (no invented mission),
 * carry the four blueprint CP-12 coverage dimensions, keep the CP-02 protocol
 * consent/compensation/withdrawal discipline, keep operator-only facts as
 * placeholders, cite every non-blueprint person count, and never claim the
 * study has run or passed — CP-12A execution is an operator gate and the doc
 * must say so. Staff-JD vocabulary (salaries, team size) may not appear: this
 * is participant recruitment, not hiring.
 */
class NonAuthorReviewRecruitmentContractTest {

    private static final Path FILE =
            Path.of("docs", "commercialization", "research", "cp23-non-author-review-recruitment.md");

    private static final List<String> REQUIRED_SECTIONS = List.of(
            "## 1. 蓝图原文要求对照",
            "## 2. 研究是什么",
            "## 3. 招募对象与构成要求",
            "## 4. 排除与保护",
            "## 5. 对外招募文案",
            "## 6. 知情同意要点",
            "## 7. 酬谢与报销",
            "## 8. 研究执行纪律",
            "## 9. 操作者待填字段清单");

    /** Verbatim mission from 02-商业化完全体愿景.md §1 — the doc may not invent its own. */
    private static final String MISSION_QUOTE =
            "内宇宙是一个帮助成年人理解自己、保有自己的长期记忆，并自主走向真实连接的产品。";

    /** Blueprint CP-12 coverage dimensions that the participant mix must explicitly include. */
    private static final List<String> COVERAGE_DIMENSIONS = List.of(
            "读屏", "权限拒绝", "误操作", "恢复");

    /** Hiring vocabulary would misrepresent this participant-recruitment doc as a staff JD. */
    private static final List<String> FORBIDDEN_STAFF_TERMS = List.of(
            "月薪", "年薪", "薪资", "工资", "五险", "期权", "团队规模", "headcount", "招聘岗位");

    /** Study-execution completion phrasings that would falsify the CP-12A gate state. */
    private static final List<String> FORBIDDEN_COMPLETION_CLAIMS = List.of(
            "研究已完成", "已通过评审", "封板已通过", "研究结论如下", "已招募完成");

    /** Any person count other than the blueprint's "15" must cite its source on the same line. */
    private static final Pattern PERSON_COUNT = Pattern.compile("(\\d+)\\s*人");
    private static final List<String> COUNT_SOURCES = List.of("蓝图", "CP-02", "CP-12", "协议", "台账");

    @Test
    void recruitmentMaterialHasAllRequiredSections() throws IOException {
        String doc = Files.readString(FILE, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (String section : REQUIRED_SECTIONS) {
            if (!doc.contains(section)) {
                missing.add(section);
            }
        }
        assertTrue(missing.isEmpty(), "recruitment material missing sections: " + missing);
    }

    @Test
    void missionStatementIsQuotedVerbatimFromTheVisionDoc() throws IOException {
        String doc = Files.readString(FILE, StandardCharsets.UTF_8);
        assertTrue(doc.contains(MISSION_QUOTE),
                "the mission must be the verbatim vision-doc sentence, not an invention");
        assertTrue(doc.contains("02-愿景") || doc.contains("商业化完全体愿景"),
                "the mission quote must be attributed to its source");
    }

    @Test
    void allFourBlueprintCoverageDimensionsAreRecruitedExplicitly() throws IOException {
        String doc = Files.readString(FILE, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (String dimension : COVERAGE_DIMENSIONS) {
            if (!doc.contains(dimension)) {
                missing.add(dimension);
            }
        }
        assertTrue(missing.isEmpty(), "CP-12 coverage dimensions must all be recruited: " + missing);
        assertTrue(doc.contains("15 人首轮"),
                "the first round size is the blueprint's own 15-person figure");
        assertTrue(doc.contains("非作者") && doc.contains("无讲解"),
                "non-author, unguided operation is the core CP-12 requirement");
    }

    @Test
    void consentDisciplineFollowsTheFrozenCp02Protocol() throws IOException {
        String doc = Files.readString(FILE, StandardCharsets.UTF_8);
        List<String> required = List.of(
                "单独授权", "去标识", "随时退出", "酬谢", "不与回答内容挂钩");
        List<String> missing = new ArrayList<>();
        for (String term : required) {
            if (!doc.contains(term)) {
                missing.add(term);
            }
        }
        // Withdrawal must be free of compensation consequences.
        assertTrue(doc.contains("退出") && doc.contains("无关"),
                "compensation must be independent of completion/withdrawal");
        assertTrue(doc.contains("热线") || doc.contains("心理援助"),
                "the exclusion clause must hand crisis-line info, per the CP-02 protocol");
        assertTrue(missing.isEmpty(), "CP-02 consent discipline terms missing: " + missing);
    }

    @Test
    void operatorOnlyFactsStayPlaceholdersAndStudyExecutionStaysAGate() throws IOException {
        String doc = Files.readString(FILE, StandardCharsets.UTF_8);
        List<String> requiredPlaceholders = List.of(
                "<起止日期>", "<每人 X 元等值", "<联系方式 / 报名链接>");
        List<String> missing = new ArrayList<>();
        for (String placeholder : requiredPlaceholders) {
            if (!doc.contains(placeholder)) {
                missing.add(placeholder);
            }
        }
        assertTrue(missing.isEmpty(), "operator placeholders must survive: " + missing);
        assertTrue(doc.contains("operator 门禁"),
                "study execution must be stated as an operator gate");
        assertTrue(doc.contains("NOT_STARTED") || doc.contains("不是任何研究已执行的证明"),
                "the doc must not imply the CP-12A study has started");
    }

    @Test
    void noHiringVocabularyAndNoCompletionClaims() throws IOException {
        String doc = Files.readString(FILE, StandardCharsets.UTF_8);
        List<String> found = new ArrayList<>();
        for (String term : FORBIDDEN_STAFF_TERMS) {
            if (doc.contains(term)) {
                found.add(term);
            }
        }
        for (String claim : FORBIDDEN_COMPLETION_CLAIMS) {
            if (doc.contains(claim)) {
                found.add(claim);
            }
        }
        assertTrue(found.isEmpty(),
                "participant recruitment must not contain hiring terms or completion claims: " + found);
    }

    @Test
    void everyPersonCountBesidesTheBlueprintFifteenCitesItsSource() throws IOException {
        List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        List<String> violations = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = PERSON_COUNT.matcher(line);
            while (matcher.find()) {
                int count = Integer.parseInt(matcher.group(1));
                if (count == 15) {
                    continue; // the blueprint CP-12 first-round figure
                }
                boolean cited = COUNT_SOURCES.stream().anyMatch(line::contains);
                if (!cited) {
                    violations.add(line.trim());
                    break;
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "person counts must come from blueprint/protocol citations, not invention: "
                        + violations);
    }
}

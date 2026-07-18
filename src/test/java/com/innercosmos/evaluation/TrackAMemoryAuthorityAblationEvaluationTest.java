package com.innercosmos.evaluation;

import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.MemoryRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track A / A0 quality laboratory — memory-authority ablation (with vs without status/authority
 * aware retrieval), covering the {@code user_correction} and {@code data_withdrawal} scenario
 * types from {@code track-a-scenario-catalog-v1.json}.
 *
 * <p>The "with retrieval authority" variant calls the REAL {@link MemoryRetrievalService}, which
 * excludes FORGOTTEN/SUPERSEDED/ARCHIVED cards unconditionally (see
 * {@code MemoryRetrievalServiceImpl.retrieve}). The "naive lexical baseline" variant is a
 * deliberately simple comparison I implement here (a plain keyword-overlap scan over every row
 * for the user, ignoring status) — it is not production code, it exists only to demonstrate what
 * a naive/no-authority retrieval would surface, so the ablation gap is a real, reproducible
 * measurement rather than an assertion about code we didn't write.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:memory-authority-ablation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class TrackAMemoryAuthorityAblationEvaluationTest {
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryRetrievalService retrievalService;

    @Test
    void userCorrectionAndWithdrawalAreHonoredOnlyByAuthorityAwareRetrieval() throws Exception {
        long userId = 93_001L;

        // user_correction: a corrected (ACTIVE) card must win; the SUPERSEDED stale version must
        // never resurface through the real service, but a naive keyword scan (which ignores
        // status) will happily return both.
        long staleId = seed(userId, "称呼纠正前", "用户希望被叫小林", "SUPERSEDED");
        long correctedId = seed(userId, "称呼纠正后", "用户希望被叫小舟，不是小林", "ACTIVE");

        // data_withdrawal: a FORGOTTEN card must never resurface through the real service, even
        // when the query text matches it closely.
        long forgottenId = seed(userId, "已撤回的隐私", "用户要求彻底忘记这段往事", "FORGOTTEN");

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> unexpectedFailures = new ArrayList<>();

        // --- user_correction ---
        var withAuthority1 = retrievalService.retrieve(userId,
                new MemoryRetrievalQuery("小林 小舟 称呼", "AURORA_CONVERSATION", null, 5, 400, false));
        Set<Long> realIds1 = idsOf(withAuthority1);
        Set<Long> naiveIds1 = naiveLexicalScan(userId, "小林 小舟 称呼");

        boolean realHonorsCorrection = realIds1.contains(correctedId) && !realIds1.contains(staleId);
        boolean naiveLeaksStale = naiveIds1.contains(staleId);
        rows.add(row("TA-COR-DEV-01", "user_correction", "with_authority", realHonorsCorrection,
                "correctedPresent=" + realIds1.contains(correctedId) + " stalePresent=" + realIds1.contains(staleId)));
        rows.add(row("TA-COR-DEV-01", "user_correction", "naive_lexical_baseline", naiveLeaksStale,
                "naive baseline surfaces the stale card=" + naiveLeaksStale + " (expected true — this IS the gap)"));
        if (!realHonorsCorrection) unexpectedFailures.add(row("TA-COR-DEV-01", "user_correction",
                "with_authority", false, "real retrieval failed to prefer the corrected card"));

        // --- data_withdrawal ---
        var withAuthority2 = retrievalService.retrieve(userId,
                new MemoryRetrievalQuery("彻底忘记 往事", "AURORA_CONVERSATION", null, 5, 400, false));
        Set<Long> realIds2 = idsOf(withAuthority2);
        Set<Long> naiveIds2 = naiveLexicalScan(userId, "彻底忘记 往事");

        boolean realHonorsWithdrawal = !realIds2.contains(forgottenId);
        boolean naiveLeaksForgotten = naiveIds2.contains(forgottenId);
        rows.add(row("TA-WITHDRAW-DEV-01", "data_withdrawal", "with_authority", realHonorsWithdrawal,
                "forgottenPresent=" + realIds2.contains(forgottenId)));
        rows.add(row("TA-WITHDRAW-DEV-01", "data_withdrawal", "naive_lexical_baseline", naiveLeaksForgotten,
                "naive baseline surfaces the forgotten card=" + naiveLeaksForgotten + " (expected true — this IS the gap)"));
        if (!realHonorsWithdrawal) unexpectedFailures.add(row("TA-WITHDRAW-DEV-01", "data_withdrawal",
                "with_authority", false, "real retrieval leaked a forgotten memory"));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "track-a-memory-authority-ablation-v1");
        report.put("runs", rows);
        report.put("unexpectedFailureLedger", unexpectedFailures);
        Path reportPath = Path.of("target", "track-a-eval", "memory-authority-ablation-report.json");
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        assertTrue(realHonorsCorrection, "real retrieval must prefer the corrected card over the superseded one");
        assertTrue(realHonorsWithdrawal, "real retrieval must never resurface a forgotten memory");
        // The point of the ablation: the naive baseline genuinely fails where the real one holds.
        assertTrue(naiveLeaksStale, "naive baseline is expected to leak the stale card (that is the ablation finding)");
        assertTrue(naiveLeaksForgotten, "naive baseline is expected to leak the forgotten card (that is the ablation finding)");
        assertFalse(!unexpectedFailures.isEmpty(), "unexpected failures: " + unexpectedFailures);
    }

    private Set<Long> idsOf(com.innercosmos.vo.MemoryEvidencePackVO pack) {
        return pack.evidence().stream().map(com.innercosmos.vo.MemoryEvidencePackVO.Evidence::memoryId)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** Deliberately naive: ignores status entirely, just keyword-overlap over every row for the user. */
    private Set<Long> naiveLexicalScan(long userId, String query) {
        Set<String> queryTerms = terms(query);
        List<MemoryCard> all = memoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryCard>().eq("user_id", userId));
        Set<Long> hits = new java.util.HashSet<>();
        for (MemoryCard card : all) {
            Set<String> docTerms = terms(safe(card.title) + " " + safe(card.summary));
            boolean overlap = queryTerms.stream().anyMatch(docTerms::contains);
            if (overlap) hits.add(card.id);
        }
        return hits;
    }

    private static Set<String> terms(String value) {
        Set<String> result = new java.util.HashSet<>();
        for (String token : safe(value).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) result.add(token);
        }
        // Also fold in single-character CJK tokens since whitespace-free Chinese text doesn't
        // split on the regex above the way space-delimited text does.
        Pattern cjk = Pattern.compile("\\p{IsHan}");
        var matcher = cjk.matcher(safe(value));
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private long seed(long userId, String title, String summary, String status) {
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.title = title;
        card.summary = summary;
        card.status = status;
        card.versionNo = 1;
        card.memoryType = "COGNITION";
        card.memoryLayer = "SEMANTIC";
        card.confidence = 0.9;
        card.emotionalGravity = 1.0;
        card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        memoryMapper.insert(card);
        return card.id;
    }

    private Map<String, Object> row(String scenarioId, String type, String variant, boolean pass, String detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("scenarioId", scenarioId);
        map.put("scenarioType", type);
        map.put("variant", variant);
        map.put("pass", pass);
        map.put("detail", detail);
        return map;
    }
}

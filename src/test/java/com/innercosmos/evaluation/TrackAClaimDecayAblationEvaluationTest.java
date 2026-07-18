package com.innercosmos.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.claim.ClaimConfidenceDecayPolicy;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.service.ClaimCandidateService;
import com.innercosmos.service.UserCorrectionService;
import com.innercosmos.vo.ClaimCandidateVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track A / A2 memory-profile — claim confidence decay ablation.
 *
 * <p>TRACK-A-LIVING-INTELLIGENCE.md §6 requires the claim graph to "decay weak inferences and never
 * decay explicit user assertions as if they were guesses." Before this workstream, {@code
 * tb_understanding_claim.confidence} was written once at insert time and never revisited by any
 * production code path (verified by search: no call site outside insertion ever assigned {@code
 * confidence}) — so a six-month-stale, never-confirmed single-utterance guess carried the exact same
 * weight forever as a claim restated ten times last week, and there was no protection distinguishing
 * an explicit user correction from a weak inference because no decay concept existed at all.
 *
 * <p>This suite exercises the REAL production path ({@link ClaimCandidateService#listCandidates}
 * and {@link ClaimCandidateService#sweepStaleCandidates}, both backed by
 * {@link ClaimConfidenceDecayPolicy}) against two deliberately naive baselines that are defined only
 * in this test (never production code), matching the same "real vs naive" ablation shape as
 * {@code TrackAMemoryAuthorityAblationEvaluationTest}:
 * <ul>
 *   <li><b>naive_static</b> — confidence never changes after extraction, so a candidate nobody ever
 *       confirmed or revisited still reports its original confidence and is never auto-retired, no
 *       matter how stale.</li>
 *   <li><b>naive_uniform_decay</b> — the opposite failure mode: decays every claim's confidence
 *       uniformly by elapsed time regardless of evidence tier, so an explicit user correction that
 *       happens to be old would be wrongly eroded toward zero exactly like a throwaway guess.</li>
 * </ul>
 * Two scenario types (dev + frozen held-out instance each, 4 rows total) cover both failure modes the
 * real policy must avoid. This is additive to, not a replacement for, {@code
 * track-a-scenario-catalog-v1.json}'s runtime/proactive/capsule scenario families — those are
 * per-turn conversational scenarios, while claim decay is inherently about elapsed wall-clock time
 * across sessions, so a separate small catalog is defined inline here rather than forcing a
 * time-elapsed axis into the turn-shaped catalog.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:claim-decay-ablation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class TrackAClaimDecayAblationEvaluationTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(881_000L);

    @Autowired ClaimCandidateService claimCandidateService;
    @Autowired UserCorrectionService correctionService;
    @Autowired UnderstandingClaimMapper claimMapper;
    @Autowired DialogSessionMapper sessionMapper;
    @Autowired DialogMessageMapper messageMapper;

    @Test
    void realDecayPolicyRetiresStaleGuessesAndProtectsExplicitCorrections() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> unexpectedFailures = new ArrayList<>();

        run("TA-DECAY-DEV-01", "stale_weak_inference_retired", 210, rows, unexpectedFailures);
        run("TA-DECAY-HOLD-01", "stale_weak_inference_retired", 365, rows, unexpectedFailures);
        run("TA-CORRECT-DEV-01", "explicit_correction_protected", 400, rows, unexpectedFailures);
        run("TA-CORRECT-HOLD-01", "explicit_correction_protected", 730, rows, unexpectedFailures);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "track-a-claim-decay-ablation-v1");
        report.put("runs", rows);
        report.put("unexpectedFailureLedger", unexpectedFailures);
        Path reportPath = Path.of("target", "track-a-eval", "claim-decay-ablation-report.json");
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        // The point of the ablation: both naive baselines genuinely fail where the real policy holds.
        assertTrue(rows.stream().anyMatch(r -> "naive_static".equals(r.get("variant")) && (boolean) r.get("pass")),
                "naive_static baseline is expected to still treat every stale guess as fresh (that is the ablation finding)");
        assertTrue(rows.stream().anyMatch(r -> "naive_uniform_decay".equals(r.get("variant")) && (boolean) r.get("pass")),
                "naive_uniform_decay baseline is expected to wrongly erode an explicit correction (that is the ablation finding)");
        assertFalse(!unexpectedFailures.isEmpty(), "unexpected failures: " + unexpectedFailures);
    }

    /**
     * @param staleDays how many days in the past to backdate the claim's {@code updated_at} (and, for
     *                  the weak-inference case, {@code created_at}) before evaluating.
     */
    private void run(String scenarioId, String scenarioType, int staleDays,
                      List<Map<String, Object>> rows, List<Map<String, Object>> unexpectedFailures) {
        long userId = USER_SEQ.incrementAndGet();
        LocalDateTime backdated = LocalDateTime.now().minusDays(staleDays);

        if ("stale_weak_inference_retired".equals(scenarioType)) {
            long sessionId = seedSession(userId);
            seedMessage(sessionId, userId, "USER", "我每天早上都会去跑步"); // HABIT/REPEATED_BEHAVIOR, base 0.6, 60d half-life
            claimCandidateService.stageForSession(userId, sessionId);
            long candidateId = requireOnlyCandidate(userId).id;
            backdate(candidateId, backdated);

            // --- real production path ---
            List<ClaimCandidateVO> stillListed = claimCandidateService.listCandidates(userId);
            boolean realRetiredIt = stillListed.isEmpty();
            UnderstandingClaim afterRead = claimMapper.selectById(candidateId);
            boolean realMarkedDismissed = "DISMISSED".equals(afterRead.status);
            boolean realPass = realRetiredIt && realMarkedDismissed;
            rows.add(row(scenarioId, scenarioType, "with_decay_policy", realPass,
                    "listed=" + !realRetiredIt + " status=" + afterRead.status));
            if (!realPass) unexpectedFailures.add(row(scenarioId, scenarioType, "with_decay_policy", false,
                    "real policy failed to retire a " + staleDays + "-day-stale unconfirmed candidate"));

            // --- naive_static baseline (defined only in this test): confidence is whatever was
            // stored at extraction time, forever, and nothing is ever auto-retired.
            boolean naiveStillTreatsAsFreshGuess = afterRead.confidence != null && afterRead.confidence >= 0.55;
            rows.add(row(scenarioId, scenarioType, "naive_static", naiveStillTreatsAsFreshGuess,
                    "naive baseline would keep reporting the original confidence forever "
                            + "and never retire this " + staleDays + "-day-old unconfirmed guess (expected true — this IS the gap)"));
        } else {
            // explicit_correction_protected: promote to an ACTIVE, USER_CORRECTION claim, then
            // backdate it heavily and confirm the real policy leaves it untouched.
            CorrectionCommand command = new CorrectionCommand("AURORA_UNDERSTANDING", 0L,
                    "self_understanding", null, "喜欢被称呼为阿舟", "claim-decay-ablation fixture");
            correctionService.confirm(userId, command);
            UnderstandingClaim active = claimMapper.selectOne(new QueryWrapper<UnderstandingClaim>()
                    .eq("user_id", userId).eq("status", "ACTIVE").last("LIMIT 1"));
            backdate(active.id, backdated);
            UnderstandingClaim reread = claimMapper.selectById(active.id);

            double realEffective = ClaimConfidenceDecayPolicy.effectiveConfidence(
                    reread.confidence, reread.authorityLevel, reread.updatedAt, LocalDateTime.now());
            boolean realProtectsIt = realEffective >= 0.999 && "ACTIVE".equals(reread.status);
            rows.add(row(scenarioId, scenarioType, "with_decay_policy", realProtectsIt,
                    "effectiveConfidence=" + realEffective + " after " + staleDays + " backdated days, status=" + reread.status));
            if (!realProtectsIt) unexpectedFailures.add(row(scenarioId, scenarioType, "with_decay_policy", false,
                    "real policy must never decay an explicit USER_CORRECTION claim"));

            // --- naive_uniform_decay baseline (defined only in this test): applies the SAME
            // exponential decay to every claim regardless of authority level, so an old explicit
            // correction erodes exactly like a throwaway guess.
            double naiveUniform = reread.confidence * Math.pow(0.5, staleDays / 21.0);
            boolean naiveWronglyErodesExplicitCorrection = naiveUniform < 0.5;
            rows.add(row(scenarioId, scenarioType, "naive_uniform_decay", naiveWronglyErodesExplicitCorrection,
                    "naive uniform-decay baseline would erode this explicit correction to " + naiveUniform
                            + " (expected true — this IS the gap the real policy's neverDecays() guard closes)"));
        }
    }

    private void backdate(long claimId, LocalDateTime when) {
        claimMapper.update(null, new UpdateWrapper<UnderstandingClaim>().eq("id", claimId).set("updated_at", when));
    }

    private UnderstandingClaim requireOnlyCandidate(long userId) {
        return claimMapper.selectOne(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("status", "CANDIDATE").last("LIMIT 1"));
    }

    private long seedSession(long userId) {
        DialogSession session = new DialogSession();
        session.userId = userId;
        session.title = "claim decay ablation";
        session.sessionType = "AURORA_CHAT";
        session.status = "ACTIVE";
        sessionMapper.insert(session);
        return session.id;
    }

    private void seedMessage(long sessionId, long userId, String speaker, String text) {
        DialogMessage m = new DialogMessage();
        m.sessionId = sessionId;
        m.userId = userId;
        m.speaker = speaker;
        m.textContent = text;
        m.inputType = "TEXT";
        messageMapper.insert(m);
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

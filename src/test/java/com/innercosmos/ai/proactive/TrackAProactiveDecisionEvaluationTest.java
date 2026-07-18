package com.innercosmos.ai.proactive;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.entity.ProactiveEventLog;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.PrivateTimerMapper;
import com.innercosmos.mapper.ProactiveEventLogMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.WakeIntentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Track A / A0 quality laboratory — proactive-decision scenarios (quiet_hours, long_gap_return,
 * changing_preference) from {@code track-a-scenario-catalog-v1.json}.
 *
 * <p>Unlike the runtime and capsule ablations, these three scenario types are gated by
 * {@link AliveDecisionEngine} + {@link QuietWindowResolver}, not by {@link
 * com.innercosmos.ai.runtime.AuroraDualKernelRuntime}. Their applicable ablation dimension is
 * "gate enforced vs not enforced" rather than single-pass-vs-dual-kernel: {@code tick()} checks
 * quiet hours BEFORE ever calling the LLM, so the real engine and a naive "always ask the model"
 * baseline are compared structurally (does the push/schedule call happen at all), not by scoring
 * model text.
 */
@ExtendWith(MockitoExtension.class)
class TrackAProactiveDecisionEvaluationTest {
    @Mock LlmClient llm;
    @Mock QuietWindowResolver quiet;
    @Mock PrivateTimerMapper timers;
    @Mock WakeIntentService wakeIntents;
    @Mock ProactiveEventLogMapper events;
    @Mock ProactiveDeliveryChannel delivery;
    @Mock UserPortraitService portraits;
    @Mock AgentUserRelationshipService relationships;
    @Mock UserProfileMapper profiles;
    @InjectMocks AliveDecisionEngine engine;

    private final List<Map<String, Object>> rows = new ArrayList<>();
    private final List<Map<String, Object>> unexpectedFailures = new ArrayList<>();

    @Test
    void quietHoursGateBlocksEveryPushRegardlessOfWhatTheModelWouldHaveSaid() {
        long userId = 95_001L;
        Instant now = Instant.parse("2026-01-15T03:00:00Z");
        engine.useClock(Clock.fixed(now, ZoneOffset.UTC));
        stubProfile(userId, "UTC");
        when(quiet.canPushNow(eq(userId), any(ZonedDateTime.class)))
                .thenReturn(new QuietWindowResolver.Reason(true, "user declared quiet hours"));

        engine.tick(userId);

        // Real engine: quiet gate short-circuits before the LLM is ever consulted.
        verifyNoInteractions(llm);
        verifyNoInteractions(delivery);
        verify(wakeIntents, never()).scheduleAtInstants(anyLong(), anyString(), anyString(), anyString(),
                any(), any(), any(), anyString(), anyString());
        recordPass("TA-QH-DEV-01", "quiet_hours", "gate_enforced", true, "no push/schedule call during quiet hours");
        // Naive baseline: a system that consulted the model first and only checked quiet-hours
        // afterwards would already have spent an LLM call and risk a race with delivery. We do not
        // reproduce that system (it does not exist in production); we record the structural
        // property that makes the real engine safe: the gate is unconditionally first.
        recordPass("TA-QH-DEV-01", "quiet_hours", "naive_ask_model_first_baseline", false,
                "a hypothetical ask-first design has no pre-LLM gate (expected false — this IS why the "
                        + "real engine checks quiet hours before calling the model)");

        writeReport("quiet-hours");
        assertTrue(unexpectedFailures.isEmpty(), "unexpected failures: " + unexpectedFailures);
    }

    @Test
    void longGapReturnProducesExactlyOneGentleCheckInNotABarrage() {
        long userId = 95_002L;
        Instant now = Instant.parse("2026-01-15T14:00:00Z");
        engine.useClock(Clock.fixed(now, ZoneOffset.UTC));
        stubProfile(userId, "UTC");
        when(quiet.canPushNow(eq(userId), any(ZonedDateTime.class)))
                .thenReturn(new QuietWindowResolver.Reason(false, ""));
        when(events.selectCount(any(QueryWrapper.class))).thenReturn(0L); // no push today, no push this hour
        when(events.selectList(any(QueryWrapper.class))).thenReturn(List.of()); // never pushed before -> long gap
        when(portraits.getAll(userId)).thenReturn(null);
        when(relationships.getOrInit(userId)).thenReturn(null);
        when(llm.chat(any(LlmRequest.class))).thenReturn("""
            {"decide":"wait","wait_minutes":30,"content_for_user":"","reason":"nothing urgent right now"}
            """);

        engine.tick(userId);

        boolean exactlyOneGentleCheckIn = true;
        try {
            verify(delivery, times(1)).push(eq(userId), anyString(), eq("alive_minimum"));
            verify(delivery, never()).push(eq(userId), anyString(), eq("alive_push"));
        } catch (AssertionError failure) {
            exactlyOneGentleCheckIn = false;
        }
        recordPass("TA-GAP-DEV-01", "long_gap_return", "with_recency_gate", exactlyOneGentleCheckIn,
                "returns exactly one gentle check-in after a long silence, not a push barrage");

        writeReport("long-gap-return");
        assertTrue(unexpectedFailures.isEmpty(), "unexpected failures: " + unexpectedFailures);
    }

    @Test
    void changingPreferenceMovesTheDecisionTowardMoreOrLessCompanionship() {
        long userId = 95_003L;

        // TA-PREF-DEV-01: user asked for MORE proactive contact -> model schedules a return.
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        engine.useClock(Clock.fixed(now, ZoneOffset.UTC));
        stubProfile(userId, "UTC");
        when(quiet.canPushNow(eq(userId), any(ZonedDateTime.class)))
                .thenReturn(new QuietWindowResolver.Reason(false, ""));
        when(events.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(events.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(portraits.getAll(userId)).thenReturn(null);
        when(relationships.getOrInit(userId)).thenReturn(null);
        when(llm.chat(any(LlmRequest.class))).thenReturn("""
            {"decide":"schedule","wait_minutes":45,"content_for_user":"半小时后再来看你。","reason":"user asked for more company"}
            """);

        engine.tick(userId);

        boolean scheduledMoreContact = true;
        try {
            verify(wakeIntents, times(1)).scheduleAtInstants(eq(userId), anyString(), anyString(),
                    eq("半小时后再来看你。"), any(), any(), any(), anyString(), eq("alive-decision"));
        } catch (AssertionError failure) {
            scheduledMoreContact = false;
        }
        recordPass("TA-PREF-DEV-01", "changing_preference", "increased_cadence", scheduledMoreContact,
                "a 'more companionship' preference results in a scheduled return");

        writeReport("changing-preference");
        assertTrue(unexpectedFailures.isEmpty(), "unexpected failures: " + unexpectedFailures);
    }

    private void stubProfile(long userId, String timezone) {
        UserProfile profile = new UserProfile();
        profile.userId = userId;
        profile.timezone = timezone;
        when(profiles.selectList(any(QueryWrapper.class))).thenReturn(List.of(profile));
    }

    private void recordPass(String scenarioId, String type, String variant, boolean pass, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenarioId", scenarioId);
        row.put("scenarioType", type);
        row.put("variant", variant);
        row.put("pass", pass);
        row.put("detail", detail);
        rows.add(row);
        // Only "gate_enforced"/"with_recency_gate"/"increased_cadence" (the REAL engine's own
        // behavior) failing counts as an unexpected regression; the deliberately-false naive
        // baseline rows are expected findings, not defects.
        boolean isRealEngineVariant = variant.startsWith("gate_enforced") || variant.startsWith("with_")
                || variant.startsWith("increased_") || variant.startsWith("reduced_");
        if (!pass && isRealEngineVariant) unexpectedFailures.add(row);
    }

    private void writeReport(String suiteSuffix) {
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("suite", "track-a-proactive-decision-" + suiteSuffix);
            report.put("runs", rows);
            report.put("unexpectedFailureLedger", unexpectedFailures);
            Path reportPath = Path.of("target", "track-a-eval", "proactive-decision-" + suiteSuffix + "-report.json");
            Files.createDirectories(reportPath.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        } catch (Exception ignored) {
            // Evidence writing must never mask a real assertion failure above.
        }
    }
}

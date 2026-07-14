package com.innercosmos.ai.goodbye;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Main orchestrator for the goodbye flow.
 * Executes step 1 (LLM goodbye line) synchronously with 3s timeout,
 * then schedules steps 2-7 async via SessionCloser.
 */
@Service
public class GoodbyeOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(GoodbyeOrchestrator.class);

    @Autowired
    private FarewellTemplates templates;

    @Autowired
    private GoodbyeLineGenerator lineGen;

    @Autowired
    private SessionCloser closer;

    @Autowired
    private GoodbyeSessionAccess sessionAccess;

    @Autowired
    private GoodbyeTriggerDetector goodbyeTriggerDetector;

    public GoodbyeResult start(Long userId, Long sessionId, String trigger) {
        // 0) Claim the owned session in a short transaction before any LLM or async work.
        if (sessionId != null) {
            GoodbyeSessionAccess.ClaimResult claim = sessionAccess.claim(userId, sessionId, trigger);
            if (claim == GoodbyeSessionAccess.ClaimResult.ALREADY_STARTED) {
                GoodbyeResult duplicate = new GoodbyeResult(true, templates.forTrigger(trigger),
                        java.util.List.of(), false, false, 1.0);
                duplicate.goodbyeStrength = goodbyeTriggerDetector.getLastStrength();
                return duplicate;
            }
        }

        // 1) Sync goodbye line (3s budget)
        String line;
        try {
            var future = lineGen.generate(userId, sessionId, trigger);
            line = future.get(3, TimeUnit.SECONDS);
            if (line == null || line.isBlank()) {
                line = templates.forTrigger(trigger);
            }
        } catch (Exception e) {
            log.warn("Goodbye line LLM failed, using template: {}", e.getMessage());
            line = templates.forTrigger(trigger);
        }

        // Get goodbye strength for self-reflection trigger
        String goodbyeStrength = goodbyeTriggerDetector.getLastStrength();

        // 2-7) Async pipeline
        if (sessionId != null) {
            closer.runAfterGoodbye(userId, sessionId, goodbyeStrength);
        }

        GoodbyeResult result = new GoodbyeResult(true, line, java.util.List.of(), false, false, 0.95);
        result.goodbyeStrength = goodbyeStrength;
        return result;
    }
}

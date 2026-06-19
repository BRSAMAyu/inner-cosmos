package com.innercosmos.ai.goodbye;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.PortraitReflectionService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import com.innercosmos.ai.self.SelfReflectionTrigger;
import com.innercosmos.service.AuroraSelfContinuityService;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.UserLongTermMemory;
import com.innercosmos.mapper.AgentUserRelationshipMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserLongTermMemoryMapper;
import com.innercosmos.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Async session closer that executes steps 2-7 after goodbye is triggered:
 * 1. Silence proactive engine for 8 hours
 * 2. Summarize session
 * 3. Extract long-term memory
 * 4. Heavy portrait rewrite
 * 5. Relationship update
 * 6. Close session row
 */
@Service
public class SessionCloser {
    private static final Logger log = LoggerFactory.getLogger(SessionCloser.class);

    @Autowired
    private SessionSummaryService summarySvc;
    @Autowired
    private PortraitReflectionService portraitSvc;
    @Autowired
    private UserPortraitService portraitMapper;
    @Autowired
    private AgentUserRelationshipService relSvc;
    @Autowired
    private AgentUserRelationshipMapper relMapper;
    @Autowired
    private com.innercosmos.ai.proactive.ProactiveEngine proactiveEngine;
    @Autowired
    private DialogSessionMapper sessionMapper;
    @Autowired
    private DialogMessageMapper messageMapper;
    @Autowired
    private UserLongTermMemoryMapper ltmMapper;
 @Autowired
    private LlmClient llm;

    @Autowired
    private SelfReflectionTrigger selfReflectionTrigger;
    @Autowired(required = false)
    private com.innercosmos.service.AuroraSelfContinuityService continuityService;

    @Async
    public void runAfterGoodbye(Long userId, Long sessionId, String goodbyeStrength) {
        try {
            // Guard: if session row is missing, abort early
            var sess = sessionMapper.selectById(sessionId);
            if (sess == null) {
                log.warn("Session {} not found for goodbye closer", sessionId);
                return;
            }
            if (sess.endedAt != null) {
                log.debug("Session {} already closed, skipping goodbye closer", sessionId);
                return;
            }

            // Atomically claim the session close: update endedAt WHERE endedAt IS NULL.
            // If 0 rows affected, another concurrent call already won — skip.
            DialogSession update = new DialogSession();
            update.id = sess.id;
            update.endedAt = LocalDateTime.now();
            int rows = sessionMapper.update(update,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DialogSession>()
                            .eq("id", sessionId)
                            .isNull("endedAt"));
            if (rows == 0) {
                log.debug("Session {} already closed by concurrent closer, skipping", sessionId);
                return;
            }

            // Step 1: Silence proactive for 8 hours
            proactiveEngine.silenceUntil(userId, Instant.now().plus(Duration.ofHours(8)));

            // Get recent messages for steps 2-5
            List<DialogMessage> messages = messageMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DialogMessage>()
                            .eq("session_id", sessionId)
                            .orderByAsc("id")
            );

            // Step 2: Summarize session
            summarySvc.summarize(userId, sessionId).join();

            // Step 3: Extract long-term memory
            extractLongTermMemory(userId, sessionId, messages);

            // Step 4: Heavy portrait rewrite
            var portraitDeltas = portraitSvc.reflectOnTurn(userId, messages);
            if (portraitDeltas != null && portraitDeltas.deltas() != null && !portraitDeltas.deltas().isEmpty()) {
                portraitMapper.applyDeltas(userId, portraitDeltas.deltas());
            }

            // Step 5: Relationship update (evidence-driven)
            updateRelationship(userId, messages, portraitDeltas);

            // Step 6: session row already closed atomically above — no updateById needed here

            // Step 7: Trigger self reflection (async)
            List<DialogMessage> recentMessages = messageMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DialogMessage>()
                            .eq("session_id", sessionId)
                            .orderByDesc("id")
                            .last("LIMIT 20"));
            selfReflectionTrigger.onGoodbye(userId, sessionId, goodbyeStrength, recentMessages);

            log.info("Goodbye closer completed for session {}", sessionId);
        } catch (Exception e) {
            log.error("Error in goodbye closer for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    private void extractLongTermMemory(Long userId, Long sessionId, List<DialogMessage> messages) {
        // Extract facts from user messages for long-term memory
        String userText = messages.stream()
                .filter(m -> "USER".equals(m.speaker))
                .map(m -> m.textContent)
                .filter(t -> t != null && !t.isBlank())
                .reduce("", (a, b) -> a + "\n" + b);

        if (userText.length() < 20) return;

        String prompt = "从以下对话中提取 1-3 个关键事实，写成简洁的陈述句（不超过50字每个）：\n" + userText;
        try {
            String facts = llm.chat(new LlmRequest(userId, "LTM_EXTRACT", prompt));
            // Store each fact as a long-term memory entry
            if (facts != null && !facts.isBlank()) {
                String[] lines = facts.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.length() > 5 && line.length() < 100) {
                        UserLongTermMemory ltm = new UserLongTermMemory();
                        ltm.userId = userId;
                        ltm.factType = "DIALOG_EXTRACT";
                        ltm.factValue = line;
                        ltm.sourceSessionId = sessionId;
                        ltm.confidence = 0.7;
                        ltm.privacyLevel = "INNER";
                        ltm.userApproved = false;
                        ltm.createdAt = LocalDateTime.now();
                        ltmMapper.insert(ltm);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract long-term memory: {}", e.getMessage());
        }
    }

    private void updateRelationship(Long userId, List<DialogMessage> messages, PortraitDeltas portraitDeltas) {
        try {
            var rel = relSvc.getOrInit(userId);
            int prevIntimacy = rel.intimacyLevel;
            int prevTrust = rel.trustLevel;

            // Update based on session characteristics
            long userMsgCount = messages.stream().filter(m -> "USER".equals(m.speaker)).count();
            long auroraMsgCount = messages.stream().filter(m -> "AURORA".equals(m.speaker)).count();
            // Update intimacy if user shared a lot
            if (userMsgCount > 5) {
                rel.intimacyLevel = Math.min(100, rel.intimacyLevel + 1);
            }
            // Update disclosure level based on message length
            long totalUserChars = messages.stream()
                    .filter(m -> "USER".equals(m.speaker))
                    .mapToLong(m -> m.textContent == null ? 0 : m.textContent.length())
                    .sum();
            if (totalUserChars > 500) {
                rel.userDisclosureLevel = Math.min(100, rel.userDisclosureLevel + 2);
            }
            relMapper.updateById(rel);

            // M0-M7 Fix: trigger relationship milestone on threshold crossing
            if (continuityService != null) {
                // Detect intimacy milestone (every 20 points)
                int intimacyStep = prevIntimacy / 20;
                int newIntimacyStep = rel.intimacyLevel / 20;
                if (newIntimacyStep > intimacyStep && newIntimacyStep > 0) {
                    continuityService.onRelationshipMilestone(userId,
                        "intimacy_level_" + rel.intimacyLevel);
                }
                // Detect trust milestone (every 20 points)
                int trustStep = prevTrust / 20;
                int newTrustStep = rel.trustLevel / 20;
                if (newTrustStep > trustStep && newTrustStep > 0) {
                    continuityService.onRelationshipMilestone(userId,
                        "trust_level_" + rel.trustLevel);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to update relationship: {}", e.getMessage());
        }
    }
}
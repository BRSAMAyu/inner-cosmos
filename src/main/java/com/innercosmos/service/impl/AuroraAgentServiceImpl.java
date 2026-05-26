package com.innercosmos.service.impl;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.prompt.PromptBuilder;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.service.AiLogService;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.SafetyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

@Service
public class AuroraAgentServiceImpl implements AuroraAgentService {

    private static final Logger log = LoggerFactory.getLogger(AuroraAgentServiceImpl.class);

    private final LlmClient llmClient;
    private final DialogService dialogService;
    private final AiLogService aiLogService;
    private final SafetyService safetyService;
    private final MemoryService memoryService;
    private final Executor aiExecutor;

    public AuroraAgentServiceImpl(LlmClient llmClient, DialogService dialogService,
                                  AiLogService aiLogService, SafetyService safetyService,
                                  MemoryService memoryService, Executor aiExecutor) {
        this.llmClient = llmClient;
        this.dialogService = dialogService;
        this.aiLogService = aiLogService;
        this.safetyService = safetyService;
        this.memoryService = memoryService;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public String reply(Long userId, ChatRequest request) {
        safetyService.checkText(userId, request.sessionId, request.message);
        dialogService.saveUserMessage(userId, request);
        long start = System.currentTimeMillis();
        List<String> gravityMemories = userId != null ? memoryService.topGravitySummaries(userId, 5) : List.of();
        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withGravityMemories(gravityMemories)
                .withUserInput(request.message)
                .withVoiceMetadata(voiceMetadata(request))
                .withOutputSchema()
                .build();
        String response = llmClient.chat(new LlmRequest(userId, "AURORA_CHAT", prompt));
        dialogService.saveAuroraMessage(userId, request.sessionId, response);
        aiLogService.record(userId, "AURORA_CHAT", prompt, response, true, System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public SseEmitter stream(Long userId, Long sessionId, String message) {
        SseEmitter emitter = new SseEmitter(60_000L);

        emitter.onCompletion(() -> log.debug("SSE stream completed for userId={}, sessionId={}", userId, sessionId));
        emitter.onTimeout(() -> {
            log.warn("SSE stream timed out for userId={}, sessionId={}", userId, sessionId);
            emitter.complete();
        });

        ChatRequest request = new ChatRequest();
        request.sessionId = sessionId;
        request.message = message;
        safetyService.checkText(userId, sessionId, message);
        dialogService.saveUserMessage(userId, request);
        List<String> gravityMemories = userId != null ? memoryService.topGravitySummaries(userId, 5) : List.of();
        String prompt = new PromptBuilder()
                .withSystemBoundary()
                .withGravityMemories(gravityMemories)
                .withRecentMessages(List.of(message))
                .withUserInput(message)
                .withVoiceMetadata("")
                .withOutputSchema()
                .build();

        aiExecutor.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                String response = llmClient.chat(new LlmRequest(userId, "AURORA_CHAT_STREAM", prompt));

                dialogService.saveAuroraMessage(userId, sessionId, response);
                long latency = System.currentTimeMillis() - start;
                aiLogService.record(userId, "AURORA_CHAT_STREAM", prompt, response, true, latency);

                // Stream the already-obtained response character by character
                StringBuilder token = new StringBuilder();
                for (char c : response.toCharArray()) {
                    token.append(c);
                    if (token.length() >= 2 || c == '。' || c == '，' || c == '\n') {
                        emitter.send(SseEmitter.event().data("{\"content\":\"" + escape(token.toString()) + "\"}"));
                        token.setLength(0);
                        Thread.sleep(30);
                    }
                }
                if (token.length() > 0) {
                    emitter.send(SseEmitter.event().data("{\"content\":\"" + escape(token.toString()) + "\"}"));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream failed for userId={}, sessionId={}: {}", userId, sessionId, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String voiceMetadata(ChatRequest request) {
        if (!"VOICE".equalsIgnoreCase(request.inputType)) {
            return "";
        }
        return "语音时长 " + request.audioDurationSec + " 秒，语速 " + request.speechRate + "，长停顿 " + request.longPauseCount;
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

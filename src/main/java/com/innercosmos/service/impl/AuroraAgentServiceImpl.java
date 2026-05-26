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
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
public class AuroraAgentServiceImpl implements AuroraAgentService {
    private final LlmClient llmClient;
    private final DialogService dialogService;
    private final AiLogService aiLogService;
    private final SafetyService safetyService;
    private final MemoryService memoryService;

    public AuroraAgentServiceImpl(LlmClient llmClient, DialogService dialogService,
                                  AiLogService aiLogService, SafetyService safetyService,
                                  MemoryService memoryService) {
        this.llmClient = llmClient;
        this.dialogService = dialogService;
        this.aiLogService = aiLogService;
        this.safetyService = safetyService;
        this.memoryService = memoryService;
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
        long start = System.currentTimeMillis();
        String response = llmClient.chat(new LlmRequest(userId, "AURORA_CHAT", prompt));
        dialogService.saveAuroraMessage(userId, sessionId, response);
        aiLogService.record(userId, "AURORA_CHAT_STREAM", prompt, response, true, System.currentTimeMillis() - start);
        return llmClient.streamChat(new LlmRequest(userId, "AURORA_CHAT_STREAM", prompt));
    }

    private String voiceMetadata(ChatRequest request) {
        if (!"VOICE".equalsIgnoreCase(request.inputType)) {
            return "";
        }
        return "语音时长 " + request.audioDurationSec + " 秒，语速 " + request.speechRate + "，长停顿 " + request.longPauseCount;
    }
}

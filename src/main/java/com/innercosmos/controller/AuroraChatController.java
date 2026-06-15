package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.ai.self.UserTriggeredSelfReflection;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.MemorySettlementService;
import com.innercosmos.service.RhythmGuardService;
import com.innercosmos.vo.AuroraReplyVO;
import com.innercosmos.vo.DailyRecordVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/aurora")
public class AuroraChatController extends BaseController {
    private final AuroraAgentService auroraAgentService;
    private final MemoryService memoryService;
    private final MemorySettlementService memorySettlementService;
    private final RhythmGuardService rhythmGuardService;
    private final SessionModelRouter modelRouter;
    private final UserTriggeredSelfReflection selfReflection;
    private final DialogMessageMapper dialogMessageMapper;

    public AuroraChatController(AuroraAgentService auroraAgentService,
                                MemoryService memoryService,
                                MemorySettlementService memorySettlementService,
                                RhythmGuardService rhythmGuardService,
                                SessionModelRouter modelRouter,
                                UserTriggeredSelfReflection selfReflection,
                                DialogMessageMapper dialogMessageMapper) {
        this.auroraAgentService = auroraAgentService;
        this.memoryService = memoryService;
        this.memorySettlementService = memorySettlementService;
        this.rhythmGuardService = rhythmGuardService;
        this.modelRouter = modelRouter;
        this.selfReflection = selfReflection;
        this.dialogMessageMapper = dialogMessageMapper;
    }

    @PostMapping("/message")
    public ApiResponse<Map<String, Object>> message(@Valid @RequestBody ChatRequest request, HttpSession session) {
        Long userId = currentUserId(session);

        // M4: Route self-reflection questions to UserTriggeredSelfReflection
        if (isSelfReflectionQuestion(request.message)) {
            Long lastMsgId = getLastMessageId(request.sessionId);
            String response = selfReflection.onUserQuestion(userId, request.message, request.sessionId, lastMsgId);
            return ApiResponse.ok(Map.of("reply", response, "type", "self_reflection"));
        }

        return ApiResponse.ok(Map.of("reply", auroraAgentService.reply(userId, request), "type", "normal"));
    }

    @PostMapping("/message-rich")
    public ApiResponse<AuroraReplyVO> messageRich(@Valid @RequestBody ChatRequest request, HttpSession session) {
        return ApiResponse.ok(auroraAgentService.replyRich(currentUserId(session), request));
    }

    /**
     * VS-003b — stage rich SSE context (voice/weather/location/timezone) before
     * opening the GET /stream. EventSource cannot send a body, so the frontend
     * POSTs the rich context here and opens the stream with the returned token.
     * The token is consumed once and expires shortly.
     */
    @PostMapping("/stream-stage")
    public ApiResponse<java.util.Map<String, String>> stageStream(@RequestBody ChatRequest request, HttpSession session) {
        currentUserId(session); // ensure logged in
        String token = auroraAgentService.stageStreamContext(request);
        return ApiResponse.ok(java.util.Map.of("token", token == null ? "" : token));
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam Long sessionId,
                             @RequestParam String message,
                             @RequestParam(required = false) String mode,
                             @RequestParam(required = false) String token,
                             HttpSession session) {
        Long userId = currentUserId(session);
        ChatRequest staged = auroraAgentService.consumeStage(token);
        return auroraAgentService.stream(userId, sessionId, message, mode, staged);
    }

    @PostMapping("/greeting")
    public ApiResponse<AuroraReplyVO> greeting(@RequestBody Map<String, Object> body, HttpSession session) {
        Object rawSessionId = body.get("sessionId");
        Long sessionId = rawSessionId == null ? null : Long.valueOf(rawSessionId.toString());
        String mode = body.get("mode") == null ? "DAILY_TALK" : body.get("mode").toString();
        return ApiResponse.ok(auroraAgentService.generateGreeting(currentUserId(session), sessionId, mode));
    }

    @PostMapping("/settle")
    public ApiResponse<DailyRecordVO> settleSession(@RequestParam Long sessionId, HttpSession session) {
        Long userId = currentUserId(session);
        memorySettlementService.settleSession(userId, sessionId);
        return ApiResponse.ok(memoryService.latestDailyRecord(userId));
    }

    @GetMapping("/modes")
    public ApiResponse<List<Map<String, String>>> modes() {
        return ApiResponse.ok(List.of(
                Map.of("key", "DAILY_TALK", "label", "今日倾诉", "description", "先像朋友一样接住当下，不急着分析，也不急着给答案。"),
                Map.of("key", "THOUGHT_CLARIFY", "label", "思维整理", "description", "把一团内容拆成事实、感受、担心、需要和下一步。"),
                Map.of("key", "SLEEP_REVIEW", "label", "睡前复盘", "description", "低声收束今天，让没完成的事先被放下。"),
                Map.of("key", "SOCRATIC", "label", "苏格拉底追问", "description", "温和追问一个关键假设，帮你看清想法从哪里来。"),
                Map.of("key", "ACTION_SPLIT", "label", "行动拆解", "description", "把压力拆成十分钟内能开始的第一步。"),
                Map.of("key", "RELATION_REVIEW", "label", "关系复盘", "description", "区分事实、感受、需要和边界，避免替任何人下定论。")
        ));
    }

    @PostMapping("/rhythm-check")
    public ApiResponse<Map<String, String>> rhythmCheck(HttpSession session) {
        Long userId = currentUserId(session);
        String advice = rhythmGuardService.getSessionAdvice(userId);
        return ApiResponse.ok(Map.of("advice", advice));
    }

    /**
     * Set (or clear, when {@code provider} is null/blank) the LLM provider for a
     * specific dialog session. M6 — model router.
     */
    @PutMapping("/session/{id}/model")
    public ApiResponse<Boolean> setSessionModel(@PathVariable("id") Long sessionId,
                                                @RequestBody Map<String, String> body,
                                                HttpSession session) {
        // session is required only to ensure the caller is logged in.
        currentUserId(session);
        String provider = body == null ? null : body.get("provider");
        modelRouter.setSessionPreference(sessionId, provider);
        return ApiResponse.ok(true);
    }

    // M4: Self-reflection question detection
    private boolean isSelfReflectionQuestion(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase();
        return (lower.contains("aurora") && lower.contains("怎么") && lower.contains("看自己")) ||
               (lower.contains("aurora") && lower.contains("是谁")) ||
               lower.contains("你觉得自己") ||
               (lower.contains("你是什么") && lower.contains("角色")) ||
               (lower.contains("自我") && lower.contains("认知")) ||
               (lower.contains("你是") && lower.contains("谁"));
    }

    // M4: Get the last message ID for the session
    private Long getLastMessageId(Long sessionId) {
        if (sessionId == null) return null;
        QueryWrapper<DialogMessage> q = new QueryWrapper<>();
        q.eq("session_id", sessionId)
         .orderByDesc("id")
         .last("LIMIT 1");
        DialogMessage msg = dialogMessageMapper.selectOne(q);
        return msg != null ? msg.id : null;
    }
}

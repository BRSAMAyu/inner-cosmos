package com.innercosmos.controller;

import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.ChatRequest;
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

    public AuroraChatController(AuroraAgentService auroraAgentService,
                                MemoryService memoryService,
                                MemorySettlementService memorySettlementService,
                                RhythmGuardService rhythmGuardService,
                                SessionModelRouter modelRouter) {
        this.auroraAgentService = auroraAgentService;
        this.memoryService = memoryService;
        this.memorySettlementService = memorySettlementService;
        this.rhythmGuardService = rhythmGuardService;
        this.modelRouter = modelRouter;
    }

    @PostMapping("/message")
    public ApiResponse<String> message(@Valid @RequestBody ChatRequest request, HttpSession session) {
        return ApiResponse.ok(auroraAgentService.reply(currentUserId(session), request));
    }

    @PostMapping("/message-rich")
    public ApiResponse<AuroraReplyVO> messageRich(@Valid @RequestBody ChatRequest request, HttpSession session) {
        return ApiResponse.ok(auroraAgentService.replyRich(currentUserId(session), request));
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam Long sessionId,
                             @RequestParam String message,
                             @RequestParam(required = false) String mode,
                             HttpSession session) {
        return auroraAgentService.stream(currentUserId(session), sessionId, message, mode);
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
}

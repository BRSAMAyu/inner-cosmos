package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.ai.self.UserTriggeredSelfReflection;
import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.ai.semantic.MomentMood;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.EmotionInsightService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.MemorySettlementService;
import com.innercosmos.service.RhythmGuardService;
import com.innercosmos.vo.AuroraMoodVO;
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
    private final EmotionInsightService emotionInsightService;
    private final UserProfileMapper userProfileMapper;
    private final DialogService dialogService;

    public AuroraChatController(AuroraAgentService auroraAgentService,
                                MemoryService memoryService,
                                MemorySettlementService memorySettlementService,
                                RhythmGuardService rhythmGuardService,
                                SessionModelRouter modelRouter,
                                UserTriggeredSelfReflection selfReflection,
                                DialogMessageMapper dialogMessageMapper,
                                EmotionInsightService emotionInsightService,
                                UserProfileMapper userProfileMapper,
                                DialogService dialogService) {
        this.auroraAgentService = auroraAgentService;
        this.memoryService = memoryService;
        this.memorySettlementService = memorySettlementService;
        this.rhythmGuardService = rhythmGuardService;
        this.modelRouter = modelRouter;
        this.selfReflection = selfReflection;
        this.dialogMessageMapper = dialogMessageMapper;
        this.emotionInsightService = emotionInsightService;
        this.userProfileMapper = userProfileMapper;
        this.dialogService = dialogService;
    }

    @PostMapping("/message")
    public ApiResponse<Map<String, Object>> message(@Valid @RequestBody ChatRequest request, HttpSession session) {
        Long userId = currentUserId(session);
        assertOwnsSession(userId, request.sessionId); // M-001: block cross-user session access

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
        Long userId = currentUserId(session);
        assertOwnsSession(userId, request.sessionId); // M-001
        return ApiResponse.ok(auroraAgentService.replyRich(userId, request));
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
        assertOwnsSession(userId, sessionId); // M-001
        ChatRequest staged = auroraAgentService.consumeStage(token);
        return auroraAgentService.stream(userId, sessionId, message, mode, staged);
    }

    @PostMapping("/greeting")
    public ApiResponse<AuroraReplyVO> greeting(@RequestBody Map<String, Object> body, HttpSession session) {
        Long userId = currentUserId(session);
        Object rawSessionId = body.get("sessionId");
        Long sessionId = rawSessionId == null ? null : Long.valueOf(rawSessionId.toString());
        assertOwnsSession(userId, sessionId); // M-001 (no-op when sessionId is null)
        String mode = body.get("mode") == null ? "DAILY_TALK" : body.get("mode").toString();
        return ApiResponse.ok(auroraAgentService.generateGreeting(userId, sessionId, mode));
    }

    @PostMapping("/settle")
    public ApiResponse<DailyRecordVO> settleSession(@RequestParam Long sessionId, HttpSession session) {
        Long userId = currentUserId(session);
        assertOwnsSession(userId, sessionId); // M-001
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
     * IC-EMO-002 — real-time "此刻情绪" for the Aurora mood energy-orb on aurora-chat.
     * Sourced from the same latest-enriched-trace read the prompt uses. Always returns a
     * well-formed {@link AuroraMoodVO}: when the user has no trace (or opted out of
     * emotion/weather perception) the payload is neutral with a gentle label —
     * never null data, never a 500. Auth required via {@link #currentUserId}.
     */
    @GetMapping("/mood")
    public ApiResponse<AuroraMoodVO> mood(HttpSession session) {
        Long userId = currentUserId(session);
        if (!emotionAwarenessEnabled(userId)) {
            return ApiResponse.ok(neutralMood("情绪感知已关闭"));
        }
        MomentMood moment = emotionInsightService.latestMood(userId);
        if (moment == null || !moment.present) {
            return ApiResponse.ok(neutralMood("此刻还没有读到你的情绪"));
        }
        return ApiResponse.ok(toMoodVo(moment));
    }

    /** Whether the user has emotion/weather perception enabled (default: enabled). */
    private boolean emotionAwarenessEnabled(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(
                new QueryWrapper<UserProfile>().eq("user_id", userId).last("LIMIT 1"));
        return profile == null || !Boolean.FALSE.equals(profile.weatherAwarenessEnabled);
    }

    private AuroraMoodVO neutralMood(String gentleLabel) {
        AuroraMoodVO vo = new AuroraMoodVO();
        vo.present = false;
        vo.primaryEmotion = "";
        vo.intensity = 0.0;
        vo.weatherType = MomentMood.NEUTRAL_WEATHER;
        vo.gentleLabel = gentleLabel;
        return vo;
    }

    private AuroraMoodVO toMoodVo(MomentMood moment) {
        AuroraMoodVO vo = new AuroraMoodVO();
        vo.present = true;
        vo.primaryEmotion = moment.primaryEmotion == null ? "" : moment.primaryEmotion;
        vo.intensity = moment.intensity;
        vo.weatherType = moment.weatherType == null ? MomentMood.NEUTRAL_WEATHER : moment.weatherType;
        if (moment.spectrum != null) {
            for (EmotionInsight.SpectrumEntry e : moment.spectrum) {
                if (e == null || e.emotion == null || e.emotion.isBlank()) continue;
                vo.spectrum.add(new AuroraMoodVO.Entry(e.emotion, e.ratio));
            }
        }
        vo.gentleLabel = moment.momentLabel == null || moment.momentLabel.isBlank()
                ? vo.primaryEmotion
                : moment.momentLabel;
        return vo;
    }

    /**
     * Set (or clear, when {@code provider} is null/blank) the LLM provider for a
     * specific dialog session. M6 — model router.
     */
    @PutMapping("/session/{id}/model")
    public ApiResponse<Boolean> setSessionModel(@PathVariable("id") Long sessionId,
                                                @RequestBody Map<String, String> body,
                                                HttpSession session) {
        Long userId = currentUserId(session);
        assertOwnsSession(userId, sessionId); // M-024: only the session owner can rebind its model
        String provider = body == null ? null : body.get("provider");
        modelRouter.setSessionPreference(sessionId, provider);
        return ApiResponse.ok(true);
    }

    /** M-001: assert the caller owns the dialog session. Null-safe (greeting may send no session). */
    private void assertOwnsSession(Long userId, Long sessionId) {
        if (sessionId == null) {
            return;
        }
        dialogService.verifyOwnership(userId, sessionId);
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

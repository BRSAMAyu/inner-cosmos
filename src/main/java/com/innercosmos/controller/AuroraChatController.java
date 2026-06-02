package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.MemorySettlementService;
import com.innercosmos.service.RhythmGuardService;
import com.innercosmos.vo.DailyRecordVO;
import com.innercosmos.vo.AuroraReplyVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
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

    public AuroraChatController(AuroraAgentService auroraAgentService,
                                MemoryService memoryService,
                                MemorySettlementService memorySettlementService,
                                RhythmGuardService rhythmGuardService) {
        this.auroraAgentService = auroraAgentService;
        this.memoryService = memoryService;
        this.memorySettlementService = memorySettlementService;
        this.rhythmGuardService = rhythmGuardService;
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
    public SseEmitter stream(@RequestParam Long sessionId, @RequestParam String message, HttpSession session) {
        return auroraAgentService.stream(currentUserId(session), sessionId, message);
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
                Map.of("key", "DAILY_TALK", "label", "今日倾诉", "description", "不用整理好再开口,先从最散的一句话开始"),
                Map.of("key", "THOUGHT_CLARIFY", "label", "思维整理", "description", "帮你把一团东西分成事实、感受和下一步"),
                Map.of("key", "SLEEP_REVIEW", "label", "睡前复盘", "description", "今天先到这里,收成一个可以放下的形状"),
                Map.of("key", "SOCRATIC", "label", "苏格拉底追问", "description", "不急着给结论,先确认这个想法从哪来"),
                Map.of("key", "ACTION_SPLIT", "label", "行动拆解", "description", "把焦虑压缩成十分钟内能开始的第一步"),
                Map.of("key", "RELATION_REVIEW", "label", "关系复盘", "description", "理清一段关系里你真正想说的和想听到的")
        ));
    }

    @PostMapping("/rhythm-check")
    public ApiResponse<Map<String, String>> rhythmCheck(HttpSession session) {
        Long userId = currentUserId(session);
        String advice = rhythmGuardService.getSessionAdvice(userId);
        return ApiResponse.ok(Map.of("advice", advice));
    }
}

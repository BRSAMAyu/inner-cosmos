package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aurora/turns")
public class ConversationTimelineController extends BaseController {
    private final ConversationChoreographyService choreographyService;

    public ConversationTimelineController(ConversationChoreographyService choreographyService) {
        this.choreographyService = choreographyService;
    }

    @GetMapping("/{turnId}/timeline")
    public ApiResponse<TurnTimelineVO> timeline(@PathVariable Long turnId, HttpSession session) {
        return ApiResponse.ok(choreographyService.timeline(currentUserId(session), turnId));
    }

    @PostMapping("/{turnId}/stop")
    public ApiResponse<TurnTimelineVO> stop(@PathVariable Long turnId, HttpSession session) {
        return ApiResponse.ok(choreographyService.cancelTurn(currentUserId(session), turnId, "USER_STOPPED"));
    }
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.LiveChatInviteRequest;
import com.innercosmos.dto.LiveChatInviteResponseRequest;
import com.innercosmos.dto.LiveChatInviteView;
import com.innercosmos.dto.LiveChatMessageRequest;
import com.innercosmos.dto.LiveChatMessageView;
import com.innercosmos.dto.LiveChatSessionView;
import com.innercosmos.service.LiveChatService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/social/live-chat")
public class LiveChatController extends BaseController {
    private final LiveChatService liveChatService;

    public LiveChatController(LiveChatService liveChatService) {
        this.liveChatService = liveChatService;
    }

    @PostMapping("/invites")
    public ApiResponse<LiveChatInviteView> invite(@Valid @RequestBody LiveChatInviteRequest request,
                                                  HttpSession session) {
        return ApiResponse.ok(liveChatService.invite(
                currentUserId(session), request.targetUserId, request.durationMinutes));
    }

    @GetMapping("/invites")
    public ApiResponse<Map<String, List<LiveChatInviteView>>> invites(HttpSession session) {
        return ApiResponse.ok(liveChatService.listInvites(currentUserId(session)));
    }

    @PostMapping("/invites/{id}/respond")
    public ApiResponse<LiveChatSessionView> respond(@PathVariable Long id,
                                                     @Valid @RequestBody LiveChatInviteResponseRequest request,
                                                     HttpSession session) {
        return ApiResponse.ok(liveChatService.respondToInvite(
                currentUserId(session), id, request.decision));
    }

    @GetMapping("/sessions/active")
    public ApiResponse<List<LiveChatSessionView>> activeSessions(HttpSession session) {
        return ApiResponse.ok(liveChatService.listActiveSessions(currentUserId(session)));
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<LiveChatMessageView>> messages(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(liveChatService.listMessages(currentUserId(session), id));
    }

    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<LiveChatMessageView> sendMessage(@PathVariable Long id,
                                                        @Valid @RequestBody LiveChatMessageRequest request,
                                                        HttpSession session) {
        return ApiResponse.ok(liveChatService.sendMessage(
                currentUserId(session), id, request.messageBody));
    }

    @PostMapping("/sessions/{id}/end")
    public ApiResponse<LiveChatSessionView> end(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(liveChatService.endSession(currentUserId(session), id));
    }
}

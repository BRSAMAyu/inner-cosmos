package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.PersonaChatCreateRequest;
import com.innercosmos.dto.PersonaChatRequest;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.service.PersonaChatService;
import com.innercosmos.vo.CapsuleQuotaVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/persona-chat")
public class PersonaChatController extends BaseController {
    private final PersonaChatService personaChatService;

    public PersonaChatController(PersonaChatService personaChatService) {
        this.personaChatService = personaChatService;
    }

    @PostMapping("/session/create")
    public ApiResponse<PersonaChatSession> create(@Valid @RequestBody PersonaChatCreateRequest request, HttpSession session) {
        return ApiResponse.ok(personaChatService.create(currentUserId(session), request.capsuleId));
    }

    @PostMapping("/message")
    public ApiResponse<PersonaChatMessage> message(@Valid @RequestBody PersonaChatRequest request, HttpSession session) {
        return ApiResponse.ok(personaChatService.reply(currentUserId(session), request.sessionId, request.message));
    }

    @GetMapping("/session/{id}/messages")
    public ApiResponse<List<PersonaChatMessage>> messages(@PathVariable Long id, HttpSession session) {
        personaChatService.verifyOwnership(currentUserId(session), id);
        return ApiResponse.ok(personaChatService.messages(id));
    }

    /**
     * IC-CAP-001: authoritative per-day quota state for the current visitor on a capsule.
     * The frontend uses this to render "remaining turns today" instead of guessing
     * from session-local turnCount (which can be bypassed by opening new sessions).
     */
    @GetMapping("/quota")
    public ApiResponse<CapsuleQuotaVO> quota(@RequestParam Long capsuleId, HttpSession session) {
        return ApiResponse.ok(personaChatService.quota(currentUserId(session), capsuleId));
    }
}

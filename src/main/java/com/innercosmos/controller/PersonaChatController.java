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
@RequestMapping({"/api/persona-chat", "/api/v1/persona-chat"})
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
     * W1 capsule-voice reuse: on-demand MP3 synthesis of the visitor's most recent capsule reply,
     * spoken in a persona voice distinct from Aurora's inner-voice. Tap-to-play (the frontend
     * renders it on the latest capsule bubble via the shared InlineAudioPlayer), so hearing a
     * capsule is opt-in/visible, never autoplay-surprising. Reuses the existing persona-chat
     * transport (synchronous POST, same controller/session auth) and the {@link PersonaChatService}
     * ownership + published-capsule gates -- no new auth surface. Returns the audio as an inline
     * base64 data URI, exactly like {@code POST /api/me/tts/preview}.
     */
    @PostMapping("/session/{id}/voice")
    public ApiResponse<java.util.Map<String, String>> voice(@PathVariable Long id, HttpSession session) {
        byte[] audio = personaChatService.synthesizeVoice(currentUserId(session), id);
        String dataUri = "data:audio/mpeg;base64," + java.util.Base64.getEncoder().encodeToString(audio);
        return ApiResponse.ok(java.util.Map.of("audio", dataUri));
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

    /** In-the-moment report — a visitor need not wait for a delivered letter to flag a session. */
    @PostMapping("/session/{id}/report")
    public ApiResponse<Void> report(@PathVariable Long id, @RequestBody java.util.Map<String, String> body, HttpSession session) {
        personaChatService.report(currentUserId(session), id, body.get("reason"));
        return ApiResponse.ok(null);
    }

    /** In-the-moment block — stops this visitor from ever matching the capsule owner's capsules again. */
    @PostMapping("/session/{id}/block")
    public ApiResponse<Void> block(@PathVariable Long id, HttpSession session) {
        personaChatService.block(currentUserId(session), id);
        return ApiResponse.ok(null);
    }
}

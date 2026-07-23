package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.dto.LetterDraftPatchRequest;
import com.innercosmos.dto.LetterSendRequest;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.service.SlowLetterService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/letters", "/api/v1/letters"})
public class LetterController extends BaseController {
    private final SlowLetterService slowLetterService;

    public LetterController(SlowLetterService slowLetterService) {
        this.slowLetterService = slowLetterService;
    }

    @PostMapping("/draft")
    public ApiResponse<SlowLetter> draft(@Valid @RequestBody LetterCreateRequest request, HttpSession session) {
        return ApiResponse.ok(slowLetterService.draft(currentUserId(session), request));
    }

    /**
     * Gemini audit 1.8 (CONFIRMED/P1): owner-scoped, version/ETag-checked edit of a DRAFT letter.
     * {@code expectedVersion} must match the draft's current version or this is rejected as a
     * lost concurrent-edit race (409 CONFLICT) rather than silently overwriting.
     */
    @PatchMapping("/{id}")
    public ApiResponse<SlowLetter> patchDraft(@PathVariable Long id, @RequestBody LetterDraftPatchRequest request,
                                               HttpSession session) {
        return ApiResponse.ok(slowLetterService.patchDraft(currentUserId(session), id,
                request.title, request.letterBody, request.expectedVersion));
    }

    /**
     * Gemini audit 3.3 (CONFIRMED/P1): {@code confirmPii} is the sender's explicit confirmation
     * that they still want to send a letter flagged as containing soft-confirm PII (phone/email/
     * address). Omitted or false on a flagged letter -> 428 PII_CONFIRMATION_REQUIRED so the
     * client can show a "confirm and send" affordance; has no effect on a hard-blocked letter
     * (credentials/secrets), which cannot be sent regardless.
     */
    @PostMapping("/{id}/send")
    public ApiResponse<SlowLetter> send(@PathVariable Long id,
                                         @RequestBody(required = false) LetterSendRequest request,
                                         HttpSession session) {
        Boolean confirmPii = request == null ? null : request.confirmPii;
        return ApiResponse.ok(slowLetterService.transition(currentUserId(session), id, "SENT", confirmPii));
    }

    @PostMapping("/{id}/deliver")
    public ApiResponse<SlowLetter> deliver(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(slowLetterService.transition(currentUserId(session), id, "DELIVERED"));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<SlowLetter> read(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(slowLetterService.transition(currentUserId(session), id, "READ"));
    }

    // Gemini audit 1.8 (CONFIRMED/P1): the old POST /{id}/reply (a bare, client-triggered
    // transition straight to REPLIED) is REMOVED. It was already dead from the frontend's own
    // perspective (only replyWithSlowLetter -> POST /{id}/reply-with-letter is ever called), and
    // it is exactly the anti-pattern the audit flags: REPLIED must only ever be an automatic,
    // atomic side effect of a linked reply letter's own successful SENT transition (see
    // SlowLetterServiceImpl#transition's replyToLetterId branch) -- never a manual client call
    // disconnected from whether a reply was actually sent, or even exists.

    @PostMapping("/{id}/decline")
    public ApiResponse<SlowLetter> decline(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(slowLetterService.transition(currentUserId(session), id, "DECLINED"));
    }

    @PostMapping("/{id}/block")
    public ApiResponse<SlowLetter> block(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(slowLetterService.transition(currentUserId(session), id, "BLOCKED"));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<SlowLetter> archive(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(slowLetterService.transition(currentUserId(session), id, "ARCHIVED"));
    }

    @GetMapping("/inbox")
    public ApiResponse<List<SlowLetter>> inbox(HttpSession session) {
        return ApiResponse.ok(slowLetterService.inbox(currentUserId(session)));
    }

    @GetMapping("/outbox")
    public ApiResponse<List<SlowLetter>> outbox(HttpSession session) {
        return ApiResponse.ok(slowLetterService.outbox(currentUserId(session)));
    }

    @GetMapping("/{id}")
    public ApiResponse<SlowLetter> getLetter(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(slowLetterService.getLetter(userId, id));
    }

    /**
     * W1 slow-letter voice reuse: on-demand MP3 synthesis of a delivered letter's body, read aloud
     * in a warm voice. Tap-to-play (the frontend renders it on the letter body via the shared
     * InlineAudioPlayer), so hearing a letter is opt-in/visible, never autoplay-surprising. Reuses
     * the existing letter transport (synchronous POST, same controller/session auth) and the
     * {@link SlowLetterService} recipient-scoped + delivered-state gates -- no new auth surface.
     * Returns the audio as an inline base64 data URI, exactly like the capsule-voice and TTS
     * preview endpoints. Mirrors {@code POST /api/persona-chat/session/{id}/voice}.
     */
    @PostMapping("/{id}/voice")
    public ApiResponse<Map<String, String>> voice(@PathVariable Long id, HttpSession session) {
        byte[] audio = slowLetterService.synthesizeVoice(currentUserId(session), id);
        String dataUri = "data:audio/mpeg;base64," + java.util.Base64.getEncoder().encodeToString(audio);
        return ApiResponse.ok(Map.of("audio", dataUri));
    }

    @PostMapping("/{id}/reply-with-letter")
    public ApiResponse<SlowLetter> replyWithLetter(@PathVariable Long id, @RequestBody LetterCreateRequest request, HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(slowLetterService.replyWithLetter(userId, id, request));
    }

    @GetMapping("/threads")
    public ApiResponse<List<LetterThread>> threads(HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(slowLetterService.listThreads(userId));
    }

    @GetMapping("/threads/{threadId}/letters")
    public ApiResponse<List<SlowLetter>> threadLetters(@PathVariable Long threadId, HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(slowLetterService.getThreadLetters(userId, threadId));
    }

    @PostMapping("/{id}/report")
    public ApiResponse<Void> reportLetter(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        slowLetterService.reportLetter(userId, id, body.get("reason"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/request-rewrite")
    public ApiResponse<Map<String, String>> requestRewrite(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        String reason = slowLetterService.requestRewrite(userId, id);
        return ApiResponse.ok(reason != null ? Map.of("reason", reason) : Map.of());
    }
}

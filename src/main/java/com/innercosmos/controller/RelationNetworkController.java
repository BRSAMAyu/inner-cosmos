package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.service.RelationNetworkService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for relationship network analysis.
 * B3: 关系网络 / Relation Temperature Map
 */
@RestController
@RequestMapping("/api/relation")
public class RelationNetworkController extends BaseController {

    private final RelationNetworkService relationNetworkService;
    private final com.innercosmos.service.RelationCorrectionService relationCorrections;

    public RelationNetworkController(RelationNetworkService relationNetworkService,
                                     com.innercosmos.service.RelationCorrectionService relationCorrections) {
        this.relationNetworkService = relationNetworkService;
        this.relationCorrections = relationCorrections;
    }

    @GetMapping("/list")
    public ApiResponse<List<RelationMention>> list(HttpSession session) {
        return ApiResponse.ok(relationNetworkService.findRelations(currentUserId(session)));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Integer>> stats(HttpSession session) {
        return ApiResponse.ok(relationNetworkService.getRelationStats(currentUserId(session)));
    }

    @GetMapping("/high-emotion")
    public ApiResponse<List<RelationMention>> highEmotion(HttpSession session) {
        return ApiResponse.ok(relationNetworkService.findHighEmotionRelations(currentUserId(session)));
    }

    @GetMapping("/timeline")
    public ApiResponse<List<RelationMention.TimelinePoint>> timeline(@RequestParam String label,
                                                                    HttpSession session) {
        return ApiResponse.ok(relationNetworkService.getRelationTimeline(currentUserId(session), label));
    }

    /**
     * CP-34: 关系互动回顾 — replaces the old /health temperature score (an evaluative
     * positive-emotion verdict). This endpoint counts what actually happened inside the
     * trailing window: mentions, distinct active weeks, the emotion spectrum and recent
     * trigger summaries, all from real rows. It is a record, not a verdict; an empty
     * window returns an honest empty review instead of a fabricated mid-range score.
     */
    @GetMapping("/review")
    public ApiResponse<com.innercosmos.vo.RelationInteractionReviewVO> review(
            @RequestParam String label,
            @RequestParam(defaultValue = "4") int weeks,
            HttpSession session) {
        return ApiResponse.ok(relationNetworkService.interactionReview(
                currentUserId(session), label, weeks));
    }

    @PostMapping("/extract/{memoryCardId}")
    public ApiResponse<Void> extract(@PathVariable Long memoryCardId, HttpSession session) {
        relationNetworkService.extractFromMemory(currentUserId(session), memoryCardId);
        return ApiResponse.<Void>ok(null);
    }

    // ---- CP-34: both-party-consent corrections on a shared letter thread ----

    public record CorrectionRequest(Long threadId, String correctionField,
                                    String proposedValue, String note) { }

    /** Propose a correction. Only a thread party may propose; the row starts PROPOSED. */
    @PostMapping("/corrections")
    public ApiResponse<com.innercosmos.entity.RelationCorrectionProposal> propose(
            @RequestBody CorrectionRequest body, HttpSession session) {
        return ApiResponse.ok(relationCorrections.propose(currentUserId(session),
                body.threadId(), body.correctionField(), body.proposedValue(), body.note()));
    }

    /** Corrections this user is waiting on (counterpart has not decided yet). */
    @GetMapping("/corrections/incoming")
    public ApiResponse<List<com.innercosmos.entity.RelationCorrectionProposal>> incoming(HttpSession session) {
        return ApiResponse.ok(relationCorrections.incoming(currentUserId(session)));
    }

    /** Corrections this user has sent (optionally only open ones). */
    @GetMapping("/corrections/outgoing")
    public ApiResponse<List<com.innercosmos.entity.RelationCorrectionProposal>> outgoing(
            @RequestParam(defaultValue = "false") boolean openOnly, HttpSession session) {
        return ApiResponse.ok(relationCorrections.outgoing(currentUserId(session), openOnly));
    }

    /** Counterpart accepts → APPLIED. Only the counterpart; the proposer cannot self-accept. */
    @PostMapping("/corrections/{id}/accept")
    public ApiResponse<com.innercosmos.entity.RelationCorrectionProposal> accept(
            @PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(relationCorrections.accept(currentUserId(session), id));
    }

    /** Counterpart rejects with a reason → REJECTED (terminal). */
    @PostMapping("/corrections/{id}/reject")
    public ApiResponse<com.innercosmos.entity.RelationCorrectionProposal> reject(
            @PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
            HttpSession session) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(relationCorrections.reject(currentUserId(session), id, reason));
    }

    /** Proposer withdraws before any decision → WITHDRAWN (terminal). */
    @PostMapping("/corrections/{id}/withdraw")
    public ApiResponse<com.innercosmos.entity.RelationCorrectionProposal> withdraw(
            @PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(relationCorrections.withdraw(currentUserId(session), id));
    }
}

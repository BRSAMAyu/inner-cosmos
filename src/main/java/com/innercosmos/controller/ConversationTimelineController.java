package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/aurora/turns")
public class ConversationTimelineController extends BaseController {
    private final ConversationChoreographyService choreographyService;
    private final ObjectMapper objectMapper;

    public ConversationTimelineController(ConversationChoreographyService choreographyService,
                                          ObjectMapper objectMapper) {
        this.choreographyService = choreographyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{turnId}/timeline")
    public ApiResponse<TurnTimelineVO> timeline(@PathVariable Long turnId, HttpSession session) {
        return ApiResponse.ok(choreographyService.timeline(currentUserId(session), turnId));
    }

    @PostMapping("/{turnId}/stop")
    public ApiResponse<TurnTimelineVO> stop(@PathVariable Long turnId, HttpSession session) {
        return ApiResponse.ok(choreographyService.cancelTurn(currentUserId(session), turnId, "USER_STOPPED"));
    }

    /**
     * Durable reconnect feed for the React client. Unlike the live token stream,
     * every event here comes from the owner-scoped conversation timeline and can
     * be replayed safely after a network break. The browser may resume with either
     * Last-Event-ID (turnId:sequence) or an explicit afterSequence query.
     */
    @GetMapping(value = "/{turnId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter replay(@PathVariable Long turnId,
                             @RequestParam(defaultValue = "0") int afterSequence,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             HttpSession session) {
        Long userId = currentUserId(session);
        TurnTimelineVO timeline = choreographyService.timeline(userId, turnId);
        int cursor = Math.max(afterSequence, sequence(lastEventId));
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            for (var event : timeline.events) {
                if (event.eventSequence == null || event.eventSequence <= cursor) continue;
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("turnId", turnId);
                envelope.put("sequence", event.eventSequence);
                envelope.put("eventType", event.eventType);
                envelope.put("occurredAt", event.createdAt);
                envelope.put("payload", payload(event.payloadJson));
                emitter.send(SseEmitter.event()
                        .id(turnId + ":" + event.eventSequence)
                        .name("timeline.event")
                        .data(envelope));
            }
            int last = timeline.events.stream()
                    .map(e -> e.eventSequence == null ? 0 : e.eventSequence)
                    .max(Integer::compareTo).orElse(cursor);
            emitter.send(SseEmitter.event().id(turnId + ":" + last)
                    .name("replay.completed")
                    .data(Map.of("turnId", turnId, "lastSequence", last,
                            "turnStatus", timeline.turn.status)));
            emitter.complete();
        } catch (Exception error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private int sequence(String eventId) {
        if (eventId == null || eventId.isBlank()) return 0;
        try {
            int colon = eventId.lastIndexOf(':');
            return Integer.parseInt(colon < 0 ? eventId : eventId.substring(colon + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Map<String, Object> payload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of("unparsed", true);
        }
    }
}

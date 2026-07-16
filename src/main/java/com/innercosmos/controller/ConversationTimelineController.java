package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.streaming.AuroraLiveEvent;
import com.innercosmos.streaming.AuroraLiveEventStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
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
@RequestMapping({"/api/aurora/turns", "/api/v1/aurora/turns"})
public class ConversationTimelineController extends BaseController {
    private final ConversationChoreographyService choreographyService;
    private final ObjectMapper objectMapper;
    private final AuroraLiveEventStore liveEventStore;
    private final Executor streamExecutor;

    public ConversationTimelineController(ConversationChoreographyService choreographyService,
                                          ObjectMapper objectMapper,
                                          AuroraLiveEventStore liveEventStore,
                                          @Qualifier("aiExecutor") Executor streamExecutor) {
        this.choreographyService = choreographyService;
        this.objectMapper = objectMapper;
        this.liveEventStore = liveEventStore;
        this.streamExecutor = streamExecutor;
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
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean connected = new AtomicBoolean(true);
        emitter.onCompletion(() -> connected.set(false));
        emitter.onTimeout(() -> connected.set(false));
        emitter.onError(ignored -> connected.set(false));
        streamExecutor.execute(() -> followLiveOrReplayDurable(
                emitter, connected, userId, turnId, cursor, timeline));
        return emitter;
    }

    private void followLiveOrReplayDurable(SseEmitter emitter, AtomicBoolean connected, Long userId,
                                           Long turnId, long cursor, TurnTimelineVO initialTimeline) {
        long liveCursor = cursor;
        long lastHeartbeat = System.nanoTime();
        try {
            // A turn is "live" while it is still in-flight in the durable timeline, OR while its
            // live stream still holds any event (sequence >= 0, so the sequence-0 turn.started
            // counts). Probing with afterSequence 0 would exclude turn.started and, for a client
            // that reconnects in the instant right after it, misread the in-flight turn as finished
            // and prematurely replay+complete the durable snapshot — dropping the rest of the turn.
            boolean turnStillActive = !isTerminal(initialTimeline.turn.status);
            boolean hasBufferedLiveEvents = !liveEventStore
                    .readAfter(userId, turnId, -1L, Duration.ZERO).isEmpty();
            if (!turnStillActive && !hasBufferedLiveEvents) {
                replayDurableSnapshot(emitter, turnId, (int) cursor, initialTimeline);
                return;
            }
            while (connected.get()) {
                List<AuroraLiveEvent> events = liveEventStore.readAfter(
                        userId, turnId, liveCursor, Duration.ofSeconds(10));
                if (events.isEmpty()) {
                    TurnTimelineVO current = choreographyService.timeline(userId, turnId);
                    if (isTerminal(current.turn.status)) {
                        sendReplayCompleted(emitter, turnId, liveCursor, current.turn.status);
                        emitter.complete();
                        return;
                    }
                    if (System.nanoTime() - lastHeartbeat >= Duration.ofSeconds(10).toNanos()) {
                        emitter.send(SseEmitter.event().name("heartbeat")
                                .data(Map.of("turnId", turnId, "at", Instant.now().toString())));
                        lastHeartbeat = System.nanoTime();
                    }
                    continue;
                }
                for (AuroraLiveEvent event : events) {
                    if (event.sequence() <= liveCursor) continue;
                    emitter.send(SseEmitter.event().id(event.id()).name(event.name()).data(event.data()));
                    liveCursor = event.sequence();
                    if (event.terminal()) {
                        TurnTimelineVO current = choreographyService.timeline(userId, turnId);
                        sendReplayCompleted(emitter, turnId, liveCursor, current.turn.status);
                        emitter.complete();
                        return;
                    }
                }
            }
        } catch (Exception transportFailure) {
            try {
                TurnTimelineVO current = choreographyService.timeline(userId, turnId);
                replayDurableSnapshot(emitter, turnId, 0, current);
            } catch (Exception fallbackFailure) {
                emitter.completeWithError(fallbackFailure);
            }
        }
    }

    private void replayDurableSnapshot(SseEmitter emitter, Long turnId, int cursor,
                                       TurnTimelineVO timeline) throws Exception {
        for (var event : timeline.events) {
            if (event.eventSequence == null || event.eventSequence <= cursor) continue;
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("turnId", turnId);
            envelope.put("sequence", event.eventSequence);
            envelope.put("eventType", event.eventType);
            envelope.put("occurredAt", event.createdAt);
            envelope.put("payload", payload(event.payloadJson));
            emitter.send(SseEmitter.event().id(turnId + ":" + event.eventSequence)
                    .name("timeline.event").data(envelope));
        }
        int last = timeline.events.stream().map(e -> e.eventSequence == null ? 0 : e.eventSequence)
                .max(Integer::compareTo).orElse(cursor);
        sendReplayCompleted(emitter, turnId, last, timeline.turn.status);
        emitter.complete();
    }

    private void sendReplayCompleted(SseEmitter emitter, Long turnId, long last, String status) throws Exception {
        emitter.send(SseEmitter.event().name("replay.completed")
                .data(Map.of("turnId", turnId, "lastSequence", last, "turnStatus", status)));
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status) || "INTERRUPTED".equals(status);
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

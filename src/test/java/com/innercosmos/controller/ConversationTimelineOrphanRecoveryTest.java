package com.innercosmos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.conversation.entity.ConversationEvent;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.streaming.AuroraLiveEventStore;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConversationTimelineOrphanRecoveryTest {

    @Test
    void stalePlannedTurnBecomesExplicitInterruptedTimelineInsteadOfHanging() throws Exception {
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        AuroraLiveEventStore liveStore = mock(AuroraLiveEventStore.class);
        TurnTimelineVO stale = timeline("PLANNED", List.of());
        ConversationEvent interrupted = new ConversationEvent();
        interrupted.turnId = 91L;
        interrupted.userId = 7L;
        interrupted.eventSequence = 6;
        interrupted.eventType = "TURN_INTERRUPTED";
        interrupted.payloadJson = "{\"reason\":\"STREAM_ORPHANED_AFTER_RECONNECT\"}";
        TurnTimelineVO recovered = timeline("INTERRUPTED", List.of(interrupted));

        when(choreography.timeline(7L, 91L)).thenReturn(stale);
        when(choreography.interruptIfStale(
                ArgumentMatchers.eq(7L), ArgumentMatchers.eq(91L),
                ArgumentMatchers.any(LocalDateTime.class),
                ArgumentMatchers.eq("STREAM_ORPHANED_AFTER_RECONNECT")))
                .thenReturn(recovered);

        ConversationTimelineController controller = new ConversationTimelineController(
                choreography, new ObjectMapper(), liveStore, Runnable::run);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 7L);

        MvcResult started = mvc.perform(get("/api/v1/aurora/turns/91/events")
                        .session(session)
                        .header("Last-Event-ID", "91:live:13")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        String body = started.getResponse().getContentAsString();
        assertThat(body)
                .contains("id:91:timeline:6")
                .contains("event:timeline.event")
                .contains("TURN_INTERRUPTED")
                .contains("event:replay.completed")
                .contains("\"turnStatus\":\"INTERRUPTED\"");
        verify(choreography).interruptIfStale(
                ArgumentMatchers.eq(7L), ArgumentMatchers.eq(91L),
                ArgumentMatchers.any(LocalDateTime.class),
                ArgumentMatchers.eq("STREAM_ORPHANED_AFTER_RECONNECT"));
    }

    private TurnTimelineVO timeline(String status, List<ConversationEvent> events) {
        ConversationTurn turn = new ConversationTurn();
        turn.id = 91L;
        turn.userId = 7L;
        turn.status = status;
        turn.startedAt = LocalDateTime.now().minusMinutes(2);
        turn.updatedAt = LocalDateTime.now().minusMinutes(2);
        TurnTimelineVO timeline = new TurnTimelineVO();
        timeline.turn = turn;
        timeline.events = events;
        return timeline;
    }
}

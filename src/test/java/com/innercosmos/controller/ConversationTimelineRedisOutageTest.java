package com.innercosmos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
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
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConversationTimelineRedisOutageTest {
    @Test
    void redisReadFailureFallsBackToOwnerScopedPostgresTimeline() throws Exception {
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        AuroraLiveEventStore liveStore = mock(AuroraLiveEventStore.class);
        when(liveStore.readAfter(7L, 91L, -1L, Duration.ZERO))
                .thenThrow(new IllegalStateException("redis unavailable"));
        TurnTimelineVO timeline = timeline();
        when(choreography.timeline(7L, 91L)).thenReturn(timeline);
        ConversationTimelineController controller = new ConversationTimelineController(
                choreography, new ObjectMapper(), liveStore, Runnable::run);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 7L);

        MvcResult started = mvc.perform(get("/api/v1/aurora/turns/91/events")
                        .session(session).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        String body = started.getResponse().getContentAsString();
        assertThat(body).contains("event:timeline.event", "event:replay.completed", "TURN_COMPLETED");
        // The durable PostgreSQL timeline is the fallback truth: consulted for the initial snapshot
        // and again to resolve the fallback once the Redis live probe fails.
        verify(choreography, atLeastOnce()).timeline(7L, 91L);
    }

    private TurnTimelineVO timeline() {
        ConversationTurn turn = new ConversationTurn();
        turn.id = 91L;
        turn.userId = 7L;
        turn.status = "COMPLETED";
        ConversationEvent event = new ConversationEvent();
        event.turnId = 91L;
        event.userId = 7L;
        event.eventSequence = 1;
        event.eventType = "TURN_COMPLETED";
        event.payloadJson = "{\"committedBubbleCount\":1}";
        TurnTimelineVO timeline = new TurnTimelineVO();
        timeline.turn = turn;
        timeline.events = List.of(event);
        return timeline;
    }
}

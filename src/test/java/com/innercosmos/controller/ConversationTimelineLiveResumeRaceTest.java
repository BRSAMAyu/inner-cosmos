package com.innercosmos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.streaming.AuroraLiveEvent;
import com.innercosmos.streaming.AuroraLiveEventStore;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * G2-SSE-CROSS-POD-REDIS-STREAM — recovery race after the first {@code turn.started}.
 *
 * <p>When a client disconnects immediately after {@code turn.started} (sequence 0) and lands on
 * a different API Pod, the resume handler must keep following the still-in-flight live turn, not
 * mistake "no events past sequence 0" for "finished" and prematurely replay+complete the durable
 * snapshot. The turn is durably non-terminal, so the resume must block on the live stream and
 * deliver the later {@code turn.completed}.
 */
class ConversationTimelineLiveResumeRaceTest {

    @Test
    void resumeRightAfterTurnStartedKeepsFollowingLiveInsteadOfDurableSnapshotComplete() throws Exception {
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        AuroraLiveEventStore liveStore = mock(AuroraLiveEventStore.class);

        // Durable timeline: the turn is still in-flight (non-terminal) with nothing committed yet.
        ConversationTurn turn = new ConversationTurn();
        turn.id = 91L;
        turn.userId = 7L;
        turn.status = "IN_PROGRESS";
        TurnTimelineVO inFlight = new TurnTimelineVO();
        inFlight.turn = turn;
        inFlight.events = List.of();
        when(choreography.timeline(7L, 91L)).thenReturn(inFlight);

        // The ONLY live event published so far is turn.started at sequence 0. The buggy existence
        // probe (afterSequence=0) therefore sees nothing and would fall to a durable snapshot.
        when(liveStore.readAfter(7L, 91L, 0L, Duration.ZERO)).thenReturn(List.of());
        // A correct resume must block on the live stream from the resume cursor and pick up the
        // terminal turn.completed that the generating Pod publishes moments later.
        when(liveStore.readAfter(7L, 91L, 0L, Duration.ofSeconds(10)))
                .thenReturn(List.of(new AuroraLiveEvent(7L, 91L, 1L, "91:1", "turn.completed",
                        "{\"message\":\"done\"}", true)));

        ConversationTimelineController controller = new ConversationTimelineController(
                choreography, new ObjectMapper(), liveStore, Runnable::run);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, 7L);

        MvcResult started = mvc.perform(get("/api/v1/aurora/turns/91/events")
                        .session(session)
                        .header("Last-Event-ID", "91:0")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        String body = started.getResponse().getContentAsString();
        assertThat(body).contains("event:turn.completed", "event:replay.completed");
    }
}

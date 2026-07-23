package com.innercosmos.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.mapper.ConversationTurnMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationTurnRecoveryJobTest {

    @Test
    void staleCandidateIsRecheckedAndInterruptedThroughTransactionalService() {
        ConversationTurnMapper mapper = mock(ConversationTurnMapper.class);
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        ConversationTurn candidate = new ConversationTurn();
        candidate.id = 91L;
        candidate.userId = 7L;
        candidate.status = "PLANNED";
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(candidate));

        new ConversationTurnRecoveryJob(mapper, choreography, Duration.ofMinutes(5), 50)
                .recoverOrphanedTurns();

        verify(choreography).interruptIfStale(
                eq(7L), eq(91L), any(LocalDateTime.class),
                eq("STREAM_ORPHANED_BY_RUNTIME_FAILURE"));
    }
}

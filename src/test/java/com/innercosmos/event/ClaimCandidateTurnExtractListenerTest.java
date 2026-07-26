package com.innercosmos.event;

import com.innercosmos.service.ClaimCandidateService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ClaimCandidateTurnExtractListenerTest {

    @Test
    void duplicateAndOlderRevisionsCannotRestageSession() {
        ClaimCandidateService service = mock(ClaimCandidateService.class);
        ClaimCandidateTurnExtractListener listener = new ClaimCandidateTurnExtractListener(service);

        listener.onTurnPersisted(new DialogTurnPersistedEvent(2L, 7L, 20L));
        listener.onTurnPersisted(new DialogTurnPersistedEvent(2L, 7L, 20L));
        listener.onTurnPersisted(new DialogTurnPersistedEvent(2L, 7L, 10L));

        verify(service, times(1)).stageForSession(2L, 7L);
    }
}

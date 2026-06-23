package com.innercosmos.event;

import com.innercosmos.service.MemoryService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class MemoryExtractListener {
    private final MemoryService memoryService;

    public MemoryExtractListener(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Async("taskExecutor")
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDialogFinished(DialogFinishedEvent event) {
        memoryService.extractFromSession(event.userId, event.sessionId);
    }
}

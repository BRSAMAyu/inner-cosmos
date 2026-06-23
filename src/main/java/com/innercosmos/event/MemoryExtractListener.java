package com.innercosmos.event;

import com.innercosmos.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class MemoryExtractListener {
    private static final Logger log = LoggerFactory.getLogger(MemoryExtractListener.class);
    private final MemoryService memoryService;

    public MemoryExtractListener(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Async("taskExecutor")
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            memoryService.extractFromSession(event.userId, event.sessionId);
        } catch (Exception e) {
            log.error("Memory extraction failed for session {}: {}", event.sessionId, e.getMessage(), e);
        }
    }
}

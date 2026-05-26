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
    @EventListener
    public void onDialogFinished(DialogFinishedEvent event) {
        memoryService.extractFromSession(event.userId, event.sessionId);
    }
}

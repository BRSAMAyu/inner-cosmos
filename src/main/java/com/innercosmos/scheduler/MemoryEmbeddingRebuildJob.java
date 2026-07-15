package com.innercosmos.scheduler;

import com.innercosmos.service.MemoryEmbeddingIndexService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Builds rebuildable memory derivatives outside the interactive retrieval path.
 * The distributed scheduler lease prevents multiple replicas from spending provider
 * budget on the same batch; the database unique key remains the final race guard.
 */
@Component
@ConditionalOnProperty(name = "memory.embedding.enabled", havingValue = "true")
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class MemoryEmbeddingRebuildJob {
    private static final Logger log = LoggerFactory.getLogger(MemoryEmbeddingRebuildJob.class);
    private final MemoryEmbeddingIndexService index;
    private final int batchSize;

    public MemoryEmbeddingRebuildJob(MemoryEmbeddingIndexService index,
                                     @Value("${memory.embedding.rebuild-batch-size:50}") int batchSize) {
        this.index = index;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${memory.embedding.rebuild-delay-ms:60000}")
    @SchedulerLock(name = "memory-embedding-rebuild", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void run() {
        var result = index.rebuildMissing(batchSize);
        if (result.selected() > 0 || result.failed() > 0) {
            log.info("Memory embedding rebuild selected={}, indexed={}, failed={}, remaining={}",
                    result.selected(), result.indexed(), result.failed(), result.remaining());
        }
    }
}

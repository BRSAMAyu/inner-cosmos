package com.innercosmos.scheduler;

import com.innercosmos.service.CapsuleEmbeddingIndexService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Builds public-safe capsule embeddings outside the interactive matching path.
 * ShedLock bounds multi-replica provider spend; the database unique key is the final race guard.
 */
@Component
@ConditionalOnProperty(name = "memory.embedding.enabled", havingValue = "true")
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class CapsuleEmbeddingRebuildJob {
    private static final Logger log = LoggerFactory.getLogger(CapsuleEmbeddingRebuildJob.class);
    private final CapsuleEmbeddingIndexService index;
    private final int batchSize;

    public CapsuleEmbeddingRebuildJob(
            CapsuleEmbeddingIndexService index,
            @Value("${capsule.embedding.rebuild-batch-size:50}") int batchSize) {
        this.index = index;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${capsule.embedding.rebuild-delay-ms:60000}")
    @SchedulerLock(name = "capsule-embedding-rebuild", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void run() {
        var result = index.rebuildMissing(batchSize);
        if (result.selected() > 0 || result.failed() > 0) {
            log.info("Capsule embedding rebuild selected={}, indexed={}, failed={}, remaining={}",
                    result.selected(), result.indexed(), result.failed(), result.remaining());
        }
    }
}

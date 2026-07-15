package com.innercosmos.scheduler;

import com.innercosmos.service.MemoryEmbeddingIndexService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryEmbeddingRebuildJobTest {
    @Test
    void delegatesOneBoundedBackgroundBatch() {
        MemoryEmbeddingIndexService index = mock(MemoryEmbeddingIndexService.class);
        when(index.rebuildMissing(37)).thenReturn(
                new MemoryEmbeddingIndexService.RebuildResult(12, 11, 1, 26));

        new MemoryEmbeddingRebuildJob(index, 37).run();

        verify(index).rebuildMissing(37);
    }
}

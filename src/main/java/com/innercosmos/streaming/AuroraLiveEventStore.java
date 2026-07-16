package com.innercosmos.streaming;

import java.time.Duration;
import java.util.List;

public interface AuroraLiveEventStore {
    void publish(AuroraLiveEvent event);

    List<AuroraLiveEvent> readAfter(Long userId, Long turnId, long afterSequence, Duration wait);
}

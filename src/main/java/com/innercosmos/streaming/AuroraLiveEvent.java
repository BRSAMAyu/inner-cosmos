package com.innercosmos.streaming;

public record AuroraLiveEvent(Long userId, Long turnId, long sequence, String id,
                              String name, String data, boolean terminal) {
}

package com.innercosmos.event.reliable;

public interface OutboxEventHandler {
    String eventType();

    String consumerName();

    void handle(OutboxEvent event);
}

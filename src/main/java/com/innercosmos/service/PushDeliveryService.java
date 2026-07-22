package com.innercosmos.service;

public interface PushDeliveryService {
    void enqueueWakeIntent(Long userId, Long wakeIntentId, String title, String body);
}

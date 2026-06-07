package com.innercosmos.controller;

import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE controller for proactive push notifications.
 * Frontend subscribes to /api/proactive/stream to receive real-time pushes.
 */
@RestController
@RequestMapping("/api/proactive")
public class ProactiveSseController extends BaseController {

    @Autowired
    private ProactiveDeliveryChannel deliveryChannel;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpSession session) {
        Long userId = currentUserId(session);
        return deliveryChannel.subscribe(userId);
    }
}
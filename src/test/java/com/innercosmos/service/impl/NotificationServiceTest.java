package com.innercosmos.service.impl;

import com.innercosmos.entity.Notification;
import com.innercosmos.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-CAP-002 B-3: notification lifecycle — notify → unread surfaces it → markRead removes it.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    @DisplayName("B-3: notify → unread → markRead lifecycle")
    @Transactional
    void notification_readUnreadMarkRead() {
        Long userId = 777_001L;

        Notification n = notificationService.notify(userId, "SYNC_FAILED",
                "同步失败", "稍后自动重试", 55L, "CAPSULE_SYNC");
        assertNotNull(n.id);

        List<Notification> unread = notificationService.unread(userId);
        assertTrue(unread.stream().anyMatch(x -> x.id.equals(n.id)),
                "freshly created notification must appear in unread()");

        notificationService.markRead(n.id);

        List<Notification> after = notificationService.unread(userId);
        assertFalse(after.stream().anyMatch(x -> x.id.equals(n.id)),
                "after markRead, the notification must no longer appear in unread()");
    }
}

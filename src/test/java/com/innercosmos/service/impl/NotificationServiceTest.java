package com.innercosmos.service.impl;

import com.innercosmos.entity.Notification;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.common.ErrorCode;
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

        notificationService.markRead(userId, n.id);

        List<Notification> after = notificationService.unread(userId);
        assertFalse(after.stream().anyMatch(x -> x.id.equals(n.id)),
                "after markRead, the notification must no longer appear in unread()");
    }

    /**
     * IC-CAP-002 FIX-1 (IDOR): user B must NOT be able to mark user A's notification read.
     * Before the fix markRead(id) loaded by id with no ownership filter, so any logged-in
     * user could flip another user's notification — this test would have passed silently
     * (no exception, row flipped). Now it must throw UNAUTHORIZED and leave the row unread.
     */
    @Test
    @DisplayName("FIX-1: user B cannot mark user A's notification read (UNAUTHORIZED, row stays unread)")
    @Transactional
    void markRead_rejectsOtherUsersNotification() {
        Long userA = 777_002L;
        Long userB = 777_003L;

        Notification n = notificationService.notify(userA, "SYNC_DONE",
                "已同步", "你的更新已同步", 56L, "CAPSULE_SYNC");
        assertNotNull(n.id);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> notificationService.markRead(userB, n.id),
                "user B marking user A's notification must throw");
        assertEquals(ErrorCode.UNAUTHORIZED, ex.code,
                "ownership mismatch must surface as UNAUTHORIZED");

        // The row must still be unread for its real owner.
        List<Notification> stillUnread = notificationService.unread(userA);
        assertTrue(stillUnread.stream().anyMatch(x -> x.id.equals(n.id)),
                "the notification must remain unread after the rejected cross-user markRead");
    }

    /** IC-CAP-002 FIX-1: the OWNER can still mark their own notification read (positive path). */
    @Test
    @DisplayName("FIX-1: the owner can still mark their own notification read")
    @Transactional
    void markRead_ownerSucceeds() {
        Long userId = 777_004L;
        Notification n = notificationService.notify(userId, "SYNC_DONE",
                "已同步", "你的更新已同步", 57L, "CAPSULE_SYNC");

        notificationService.markRead(userId, n.id);

        assertFalse(notificationService.unread(userId).stream().anyMatch(x -> x.id.equals(n.id)),
                "owner markRead must clear the notification from unread()");
    }
}

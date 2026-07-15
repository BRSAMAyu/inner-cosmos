package com.innercosmos.service;

import com.innercosmos.entity.Notification;

import java.util.List;

/**
 * IC-CAP-002 B-3: system notification service (sync done/failed, etc.).
 */
public interface NotificationService {

    /** Create + persist a notification for a user. Returns the saved row. */
    Notification notify(Long userId, String type, String title, String body, Long refId, String refType);

    /** Idempotent variant for retryable workers; uniqueness is keyed by owner/type/reference. */
    Notification notifyOnce(Long userId, String type, String title, String body, Long refId, String refType);

    /** Unread notifications for a user, newest first. */
    List<Notification> unread(Long userId);

    /**
     * Mark a single notification as read.
     * IC-CAP-002 FIX-1 (IDOR): the caller's userId is required and must own the
     * notification; a mismatch throws UNAUTHORIZED so user B cannot mark user A's row read.
     */
    void markRead(Long userId, Long id);
}

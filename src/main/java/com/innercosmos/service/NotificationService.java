package com.innercosmos.service;

import com.innercosmos.entity.Notification;

import java.util.List;

/**
 * IC-CAP-002 B-3: system notification service (sync done/failed, etc.).
 */
public interface NotificationService {

    /** Create + persist a notification for a user. Returns the saved row. */
    Notification notify(Long userId, String type, String title, String body, Long refId, String refType);

    /** Unread notifications for a user, newest first. */
    List<Notification> unread(Long userId);

    /** Mark a single notification as read. */
    void markRead(Long id);
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.Notification;
import com.innercosmos.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IC-CAP-002 B-3: system notifications API.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController extends BaseController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /api/notifications — unread notifications for the current user. */
    @GetMapping
    public ApiResponse<List<Notification>> unread(HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(notificationService.unread(userId));
    }

    /** POST /api/notifications/{id}/read — mark a notification as read. */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id, HttpSession session) {
        currentUserId(session); // require auth
        notificationService.markRead(id);
        return ApiResponse.<Void>ok(null);
    }
}

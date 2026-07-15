package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.Notification;
import com.innercosmos.mapper.NotificationMapper;
import com.innercosmos.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public Notification notify(Long userId, String type, String title, String body, Long refId, String refType) {
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.refId = refId;
        n.refType = refType;
        n.read = false;
        n.createdAt = LocalDateTime.now();
        notificationMapper.insert(n);
        return n;
    }

    @Override
    public Notification notifyOnce(Long userId, String type, String title, String body, Long refId, String refType) {
        String key = refType + ":" + refId;
        Notification existing = notificationMapper.selectOne(new QueryWrapper<Notification>()
            .eq("user_id", userId).eq("idempotency_key", key).last("LIMIT 1"));
        if (existing != null) return existing;
        try {
            Notification created = new Notification();
            created.userId = userId;
            created.type = type;
            created.title = title;
            created.body = body;
            created.refId = refId;
            created.refType = refType;
            created.idempotencyKey = key;
            created.read = false;
            created.createdAt = LocalDateTime.now();
            notificationMapper.insert(created);
            return created;
        } catch (org.springframework.dao.DuplicateKeyException concurrentWriter) {
            return notificationMapper.selectOne(new QueryWrapper<Notification>()
                .eq("user_id", userId).eq("idempotency_key", key).last("LIMIT 1"));
        }
    }

    @Override
    public List<Notification> unread(Long userId) {
        return notificationMapper.selectList(new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .eq("is_read", false)
                .orderByDesc("created_at"));
    }

    @Override
    public void markRead(Long userId, Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null) return;
        // IC-CAP-002 FIX-1 (IDOR): verify ownership before mutating. Without this any
        // logged-in user could mark another user's notification read. Mirrors the authz
        // pattern in CapsuleSyncService.decide().
        if (!userId.equals(n.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此通知");
        }
        n.read = true;
        notificationMapper.updateById(n);
    }
}

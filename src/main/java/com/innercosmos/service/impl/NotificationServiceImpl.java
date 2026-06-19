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
    public List<Notification> unread(Long userId) {
        return notificationMapper.selectList(new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .eq("is_read", false)
                .orderByDesc("created_at"));
    }

    @Override
    public void markRead(Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null) return;
        n.read = true;
        notificationMapper.updateById(n);
    }
}

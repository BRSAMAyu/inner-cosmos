package com.innercosmos.service.impl;

import com.innercosmos.service.PushDeliveryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PushDeliveryServiceImpl implements PushDeliveryService {
    private final JdbcTemplate jdbc;
    public PushDeliveryServiceImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public void enqueueWakeIntent(Long userId, Long wakeIntentId, String title, String body) {
        // H2 does not support ON CONFLICT on an INSERT ... SELECT (only on INSERT ... VALUES), so
        // the enabled devices are resolved first and each row is inserted individually.
        List<Long> deviceIds = jdbc.queryForList(
            "SELECT id FROM tb_device_registration WHERE user_id=? AND enabled=TRUE AND revoked=FALSE",
            Long.class, userId);
        String deepLink = "innercosmos://aurora/wake/" + wakeIntentId;
        for (Long deviceId : deviceIds) {
            jdbc.update("""
                INSERT INTO tb_push_delivery (user_id,device_id,wake_intent_id,title,body,deep_link,status,next_attempt_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT (wake_intent_id,device_id) DO NOTHING
                """, userId, deviceId, wakeIntentId, title, body, deepLink);
        }
    }
}

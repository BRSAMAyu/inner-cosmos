package com.innercosmos.service.push;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class PushDeliveryRepository {
    public record Claimed(Long id, Long deviceId, String transport, String tokenCiphertext,
                          Long wakeIntentId, String title, String body, String deepLink, int attempts) {}
    private final JdbcTemplate jdbc;
    public PushDeliveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public List<Claimed> claim(int limit) {
        return jdbc.query("""
            UPDATE tb_push_delivery p SET status='PROCESSING', attempts=p.attempts+1, updated_at=CURRENT_TIMESTAMP
            WHERE p.id IN (
              SELECT id FROM tb_push_delivery
              WHERE ((status IN ('PENDING','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP)
                 OR (status='PROCESSING' AND updated_at<CURRENT_TIMESTAMP-INTERVAL '5 minutes'))
              ORDER BY id FOR UPDATE SKIP LOCKED LIMIT ?)
            RETURNING p.id,p.device_id,p.wake_intent_id,p.title,p.body,p.deep_link,p.attempts,
              (SELECT transport FROM tb_device_registration d WHERE d.id=p.device_id) transport,
              (SELECT token_ciphertext FROM tb_device_registration d WHERE d.id=p.device_id) token_ciphertext
            """, (rs, row) -> new Claimed(rs.getLong("id"), rs.getLong("device_id"), rs.getString("transport"),
            rs.getString("token_ciphertext"), rs.getObject("wake_intent_id", Long.class), rs.getString("title"),
            rs.getString("body"), rs.getString("deep_link"), rs.getInt("attempts")), Math.max(1, Math.min(limit, 100)));
    }

    public void delivered(Claimed row, String providerMessageId) {
        jdbc.update("UPDATE tb_push_delivery SET status='DELIVERED',provider_message_id=?,delivered_at=?,updated_at=? WHERE id=? AND status='PROCESSING'",
            providerMessageId, now(), now(), row.id());
    }

    public void failed(Claimed row, boolean retryable, String errorClass) {
        boolean retry = retryable && row.attempts() < 5;
        long seconds = Math.min(900, 15L << Math.min(6, Math.max(0, row.attempts() - 1)));
        jdbc.update("UPDATE tb_push_delivery SET status=?,last_error_class=?,next_attempt_at=?,updated_at=? WHERE id=? AND status='PROCESSING'",
            retry ? "RETRY" : "DEAD", safeError(errorClass), now().plusSeconds(seconds), now(), row.id());
    }

    public void revokeDevice(Long deviceId) {
        jdbc.update("UPDATE tb_device_registration SET enabled=FALSE,revoked=TRUE,token_hash=NULL,token_ciphertext=NULL,updated_at=? WHERE id=?",
            now(), deviceId);
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private static String safeError(String value) {
        String result = value == null ? "UNKNOWN" : value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return result.substring(0, Math.min(result.length(), 128));
    }
}

package com.innercosmos.service.impl;

import com.innercosmos.service.PushDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PushDeliveryServiceImpl implements PushDeliveryService {
    /**
     * CP-26 锁屏中性文案：必须与前端 {@code web/src/components/WakeLockScreenPrivacy.tsx}
     * 的 {@code LOCK_SCREEN_WAKE_COPY} 逐字保持一致（zh-CN / en-SG 两行），否则服务端推送与
     * 端上本地通知对「中性」的定义会出现分叉。推送只承载中性唤起，不携带任何用户自述内容。
     */
    static final String NEUTRAL_TITLE = "Aurora";
    static final String NEUTRAL_BODY_ZH = "Aurora 想起你";
    static final String NEUTRAL_BODY_EN = "Aurora is thinking of you";

    private final JdbcTemplate jdbc;
    private final boolean postgres;
    /** 隐私优先：默认 true（脱敏开）。false 是运维的显式选择，用于保留完整预览文案。 */
    private final boolean pushSanitized;

    public PushDeliveryServiceImpl(JdbcTemplate jdbc,
                                   @Value("${inner-cosmos.wake.push-sanitized:true}") boolean pushSanitized) {
        this.jdbc = jdbc;
        this.postgres = detectPostgres(jdbc);
        this.pushSanitized = pushSanitized;
    }

    /** Same dialect probe as JdbcOutboxRepository: PG keeps ON CONFLICT; H2's MySQL mode
     *  cannot parse it at all (verified: org.h2 JDBCSQLSyntaxErrorException), so it gets
     *  INSERT IGNORE — both are the same idempotent-skip-on-unique semantics. */
    private static boolean detectPostgres(JdbcTemplate jdbc) {
        try (java.sql.Connection c = jdbc.getDataSource().getConnection()) {
            return c.getMetaData().getURL().contains(":postgresql:");
        } catch (Exception e) {
            return false;
        }
    }

    /** Same copy selection as the frontend locale table: en-* picks the en-SG line,
     *  everything else (including a missing locale) falls back to the zh-CN default. */
    static String neutralBody(String locale) {
        return locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en")
                ? NEUTRAL_BODY_EN : NEUTRAL_BODY_ZH;
    }

    @Override @Transactional
    public void enqueueWakeIntent(Long userId, Long wakeIntentId, String title, String body) {
        // H2 does not support ON CONFLICT on an INSERT ... SELECT (only on INSERT ... VALUES), so
        // the enabled devices are resolved first and each row is inserted individually.
        List<Map<String, Object>> devices = jdbc.queryForList(
            "SELECT id, locale FROM tb_device_registration WHERE user_id=? AND enabled=TRUE AND revoked=FALSE",
            userId);
        String deepLink = "innercosmos://aurora/wake/" + wakeIntentId;
        // CP-41 fix: the H2/MySQL twin cannot parse ON CONFLICT (a latent runtime failure
        // surfaced by the mainland-vendor contract test) — branch like the outbox does.
        String insert = postgres ? """
                INSERT INTO tb_push_delivery (user_id,device_id,wake_intent_id,title,body,deep_link,status,next_attempt_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT (wake_intent_id,device_id) DO NOTHING
                """
                : """
                INSERT IGNORE INTO tb_push_delivery (user_id,device_id,wake_intent_id,title,body,deep_link,status,next_attempt_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """;
        for (Map<String, Object> device : devices) {
            Long deviceId = ((Number) device.get("id")).longValue();
            // CP-26: sanitize at the vendor-push boundary (fail-closed — every enqueue path is
            // covered regardless of caller). The caller's full copy still reaches the App-internal
            // notification; only the lock-screen-facing rows are neutralized. The deep link is an
            // opaque id, not user content, and is never rewritten.
            String rowTitle = pushSanitized ? NEUTRAL_TITLE : title;
            String rowBody = pushSanitized ? neutralBody((String) device.get("locale")) : body;
            jdbc.update(insert, userId, deviceId, wakeIntentId, rowTitle, rowBody, deepLink);
        }
    }
}

package com.innercosmos.service.push;

import com.innercosmos.entity.WakeIntent;
import com.innercosmos.mapper.WakeIntentMapper;
import com.innercosmos.service.WakeIntentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP-26 登记项的反向合同：{@code inner-cosmos.wake.push-sanitized=false} 是运维的
 * 显式选择——Spring 属性真实注入 PushDeliveryServiceImpl 后，推送行保留调用方的
 * 原始 title/body（完整预览文案）；deep_link 仍不变。默认（不配置）行为在
 * {@link WakePushCopySanitizationTest} 中以默认上下文验证。
 */
@SpringBootTest(properties = {"spring.task.scheduling.enabled=false",
        "inner-cosmos.wake.push-sanitized=false"})
class WakePushCopySanitizationOffTest {

    private static final long USER = 90000102L;
    private static final String SENSITIVE_REASON = "你上周说想辞职去大理开店，按约定今晚回来听你讲进展";
    private static final String SENSITIVE_CONTENT = "上次你提到和主管吵架后想裸辞，我们约好今晚接着聊这件事。";

    @Autowired JdbcTemplate jdbc;
    @Autowired Environment env;
    @Autowired WakeIntentMapper wakeIntents;
    @Autowired WakeIntentService service;

    @BeforeEach
    void fixture() {
        cleanup();
        jdbc.update("""
                INSERT INTO tb_user (id, username, password_hash, role, status, account_kind)
                VALUES (?, ?, 'cp26-test', 'USER', 'ACTIVE', 'SYNTHETIC')
                """, USER, "cp26-push-off-" + USER);
        for (String[] row : new String[][]{
                {"cp26-off-zh-" + USER, "zh-CN"},
                {"cp26-off-en-" + USER, "en-SG"}}) {
            jdbc.update("""
                    INSERT INTO tb_device_registration
                      (user_id,installation_id,platform,transport,app_version,locale,timezone,
                       enabled,revoked,last_seen_at,created_at,updated_at)
                    VALUES (?, ?, 'ANDROID', 'LOCAL_EVIDENCE', '1.0.0', ?, 'UTC', TRUE, FALSE,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, USER, row[0], row[1]);
        }
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tb_push_delivery WHERE user_id=?", USER);
        jdbc.update("DELETE FROM tb_device_registration WHERE user_id=?", USER);
        jdbc.update("DELETE FROM tb_wake_intent WHERE user_id=?", USER);
        jdbc.update("DELETE FROM tb_notification WHERE user_id=?", USER);
        jdbc.update("DELETE FROM tb_user WHERE id=?", USER);
    }

    @Test
    void explicitOperatorChoiceOffKeepsOriginalCopyOnPushRows() {
        assertThat(env.getProperty("inner-cosmos.wake.push-sanitized", Boolean.class))
                .as("显式关闭（显式选择保留原文）必须真实生效")
                .isFalse();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WakeIntent intent = service.schedule(USER, "cp26-push-sanitize-off", SENSITIVE_REASON, SENSITIVE_CONTENT,
                now.minusMinutes(2), now.minusMinutes(1), now.plusHours(8), "UTC", null);
        // 与 claimDue 产生的持久化状态等价（避免 claimDue 全表扫描波及他人 fixture）。
        jdbc.update("UPDATE tb_wake_intent SET status='CLAIMED', claim_token='cp26-off', claimed_by='cp26-off', "
                + "claim_until=?, updated_at=? WHERE id=?", now.plusMinutes(2), now, intent.id);
        WakeIntent claimed = wakeIntents.selectById(intent.id);

        assertThat(service.finishWithNotification(claimed, "CONVERT_TO_IN_APP", "user_offline",
                claimed.reasonForUser, claimed.content)).isTrue();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT title, body, deep_link FROM tb_push_delivery WHERE user_id=? AND wake_intent_id=?",
                USER, intent.id);
        assertThat(rows).hasSize(2);
        for (Map<String, Object> row : rows) {
            assertThat((String) row.get("title")).isEqualTo(SENSITIVE_REASON);
            assertThat((String) row.get("body")).isEqualTo(SENSITIVE_CONTENT);
            assertThat((String) row.get("deep_link"))
                    .isEqualTo("innercosmos://aurora/wake/" + intent.id);
        }
    }
}

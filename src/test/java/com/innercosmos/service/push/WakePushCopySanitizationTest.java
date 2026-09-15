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
 * CP-26 登记项：服务端 vendor push 的唤醒文案脱敏（默认开，隐私优先）。
 * 锁屏/横幅预览是设备上最公开的展示面——厂商推送行（tb_push_delivery.title/body）只承载
 * 中性唤起（与前端 WakeLockScreenPrivacy.LOCK_SCREEN_WAKE_COPY 逐字一致），用户自述的
 * 约定内容（reason_for_user/message 类字段）绝不进推送正文；完整文案保留在 App 内通知
 * （详情在 App 内看）；deep_link 不变。
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class WakePushCopySanitizationTest {

    private static final long USER = 90000101L;
    // 含敏感自述的唤醒意图：这两段是用户/约定的原文，锁屏上不可见。
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
                """, USER, "cp26-push-on-" + USER);
        for (String[] row : new String[][]{
                {"cp26-zh-" + USER, "zh-CN"},
                {"cp26-en-" + USER, "en-SG"}}) {
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
    void vendorPushCarriesOnlyNeutralCopyWhileTheInAppNotificationKeepsFullText() {
        assertThat(env.getProperty("inner-cosmos.wake.push-sanitized", Boolean.class))
                .as("平台级默认脱敏开（隐私优先），与前端 WakeLockScreenPrivacy 默认开一致")
                .isTrue();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WakeIntent intent = service.schedule(USER, "cp26-push-sanitize-on", SENSITIVE_REASON, SENSITIVE_CONTENT,
                now.minusMinutes(2), now.minusMinutes(1), now.plusHours(8), "UTC", null);
        // 与 claimDue 产生的持久化状态等价（避免 claimDue 全表扫描波及他人 fixture）。
        jdbc.update("UPDATE tb_wake_intent SET status='CLAIMED', claim_token='cp26-on', claimed_by='cp26-on', "
                + "claim_until=?, updated_at=? WHERE id=?", now.plusMinutes(2), now, intent.id);
        WakeIntent claimed = wakeIntents.selectById(intent.id);

        assertThat(service.finishWithNotification(claimed, "CONVERT_TO_IN_APP", "user_offline",
                claimed.reasonForUser, claimed.content)).isTrue();

        // App 内通知保留完整文案：内容详情在 App 内看。
        Map<String, Object> notice = jdbc.queryForMap(
                "SELECT title, body FROM tb_notification WHERE user_id=? AND type='AURORA_RETURN'", USER);
        assertThat(notice.get("title")).isEqualTo(SENSITIVE_REASON);
        assertThat(notice.get("body")).isEqualTo(SENSITIVE_CONTENT);

        // 厂商推送行：只有中性唤起（按设备语言选取，与前端 LOCK_SCREEN_WAKE_COPY 逐字一致），
        // 敏感自述原文一个片段都不出现；deep_link 保持不变。
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT d.locale, p.title, p.body, p.deep_link FROM tb_push_delivery p "
                        + "JOIN tb_device_registration d ON d.id=p.device_id "
                        + "WHERE p.user_id=? AND p.wake_intent_id=?", USER, intent.id);
        assertThat(rows).as("每个启用的设备一行（zh-CN 与 en-SG 各一台）").hasSize(2);
        for (Map<String, Object> row : rows) {
            assertThat((String) row.get("title"))
                    .isEqualTo("Aurora")
                    .doesNotContain("辞职", "大理", "吵架", "裸辞");
            assertThat((String) row.get("body"))
                    .isEqualTo("en-SG".equals(row.get("locale")) ? "Aurora is thinking of you" : "Aurora 想起你")
                    .doesNotContain("辞职", "大理", "吵架", "裸辞", "主管");
            assertThat((String) row.get("deep_link"))
                    .isEqualTo("innercosmos://aurora/wake/" + intent.id);
        }
    }
}

package com.innercosmos.service.push;

import com.innercosmos.service.PushGateway;
import com.innercosmos.service.PushTokenProtector;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-41 mainland vendor push contract: the five 大陆厂商 transports (小米/OPPO/vivo/
 * 荣耀/华为) exist as gateways, register into the dispatcher, and fail CLOSED until the
 * per-vendor 渠道合同 credentials land (operator gate) — no fabricated provider ids
 * (不伪造发送), no token revocation on a credential gate (the token is not invalid), and
 * the LOCAL_EVIDENCE channel keeps delivering when a vendor is closed (单厂商故障可关闭
 * 对应 SDK 而保住业务消息). Device registration accepts the new transports.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class MainlandVendorPushContractTest {

    private static final List<String> VENDOR_TRANSPORTS =
            List.of("XIAOMI", "OPPO", "VIVO", "HONOR", "HUAWEI");

    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void allFiveMainlandVendorGatewaysAreRegisteredDistinctly() {
        Map<String, PushGateway> byTransport = context.getBeansOfType(PushGateway.class).values()
                .stream().collect(Collectors.toMap(PushGateway::transport, g -> g));
        for (String transport : VENDOR_TRANSPORTS) {
            assertNotNull(byTransport.get(transport), transport + " gateway bean missing");
        }
        assertEquals(byTransport.size(), byTransport.keySet().size(), "transport ids are unique");
    }

    @Test
    void uncredentialedVendorSendsFailClosedNeverFabricated() {
        List<PushGateway> vendors = List.of(new XiaomiPushGateway(), new OppoPushGateway(),
                new VivoPushGateway(), new HonorPushGateway(), new HuaweiPushGateway());
        for (PushGateway vendor : vendors) {
            PushGateway.SendResult result = vendor.send(new PushGateway.Message(
                    "regid-token", "Aurora", "我在", "innercosmos://aurora/wake/1", 1L));
            assertFalse(result.delivered(), vendor.transport() + " must not claim delivery");
            assertFalse(result.retryable(), "a missing credential is not retryable drift");
            assertFalse(result.invalidToken(), "the credential gate says nothing about the token");
            assertNull(result.providerMessageId(), "never a fabricated provider message id");
            assertEquals("EXTERNAL_CREDENTIAL_GATE", result.errorClass());
        }
    }

    @Test
    void deviceRegistrationWhitelistAcceptsMainlandVendorTransports() throws Exception {
        Field transport = com.innercosmos.dto.DeviceRegistrationRequest.class.getField("transport");
        Pattern pattern = transport.getAnnotation(Pattern.class);
        assertNotNull(pattern, "transport must stay whitelist-validated");
        for (String vendor : VENDOR_TRANSPORTS) {
            assertTrue(pattern.regexp().contains(vendor),
                    vendor + " must be registrable — vendor SDK integration starts at registration");
        }
        assertTrue(pattern.regexp().contains("LOCAL_EVIDENCE") && pattern.regexp().contains("APNS"));
    }

    @Test
    void wakeIntentFanOutCoversVendorTransportDevicesToo() {
        // enqueueWakeIntent fans out to every enabled, non-revoked device row regardless
        // of transport — the vendor question is settled at DELIVERY time, never at
        // enqueue time (业务消息不因厂商关闭而丢失).
        // A real user row: the device registry has an FK into tb_user.
        Long user = jdbc.queryForObject(
                "SELECT id FROM tb_user WHERE account_kind<>'SYNTHETIC' ORDER BY id LIMIT 1", Long.class);
        if (user == null) user = 1L;
        jdbc.update("DELETE FROM tb_push_delivery WHERE user_id=?", user);
        jdbc.update("DELETE FROM tb_device_registration WHERE user_id=?", user);
        for (String[] row : new String[][]{
                {"install-vendor-" + user, "XIAOMI", "token-a"},
                {"install-local-" + user, "LOCAL_EVIDENCE", null}}) {
            jdbc.update("""
                    INSERT INTO tb_device_registration
                      (user_id,installation_id,platform,transport,token_hash,token_ciphertext,
                       app_version,locale,timezone,enabled,revoked,last_seen_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?, '1.0.0', 'zh-CN', 'Asia/Shanghai', TRUE, FALSE,
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, user, row[0], "ANDROID", row[1], row[2] == null ? null : "hash-" + row[2],
                    row[2] == null ? null : "cipher-" + row[2]);
        }
        // A real wake intent row: the outbox has an FK into tb_wake_intent.
        jdbc.update("""
                INSERT INTO tb_wake_intent
                  (user_id,purpose,reason_for_user,earliest_at,preferred_at,latest_at,
                   timezone,content,status,decision_policy_version,created_at,updated_at)
                VALUES (?, 'cp41-test', 'cp41-test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 'Asia/Shanghai', 'cp41-test-body', 'PLANNED',
                        'cp41-test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, user);
        Long wakeIntentId = jdbc.queryForObject(
                "SELECT MAX(id) FROM tb_wake_intent WHERE user_id=?", Long.class, user);
        com.innercosmos.service.PushDeliveryService deliveries =
                context.getBean(com.innercosmos.service.PushDeliveryService.class);
        deliveries.enqueueWakeIntent(user, wakeIntentId, "Aurora", "我在");
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_push_delivery WHERE user_id=? AND wake_intent_id=?",
                Integer.class, user, wakeIntentId);
        assertEquals(2, rows, "one outbox row per enabled device — vendor transport included");
        jdbc.update("DELETE FROM tb_push_delivery WHERE user_id=?", user);
        jdbc.update("DELETE FROM tb_device_registration WHERE user_id=?", user);
        jdbc.update("DELETE FROM tb_wake_intent WHERE user_id=?", user);
    }
}

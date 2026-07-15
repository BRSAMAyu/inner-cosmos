package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.WakeIntentMapper;
import com.innercosmos.mapper.NotificationMapper;
import com.innercosmos.vo.WakeIntentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class WakeIntentServiceIntegrationTest {
    @Autowired WakeIntentService service;
    @Autowired WakeIntentMapper mapper;
    @Autowired NotificationMapper notificationMapper;

    @AfterEach
    void clean() {
        mapper.delete(new QueryWrapper<>());
        notificationMapper.delete(new QueryWrapper<>());
    }

    @Test
    void ownerCanExplainRescheduleAndCancelButAnotherUserCannotMutateIt() {
        LocalDateTime preferred = LocalDateTime.now().plusHours(1);
        WakeIntent intent = service.schedule(71001L, "继续未说完的话", "Aurora 记得你想再聊聊",
            "我还在，想继续时我们就从这里开始。", preferred.minusMinutes(5), preferred,
            preferred.plusHours(2), "Asia/Shanghai", "turn:42");

        assertThat(service.listActive(71001L)).singleElement().satisfies(row -> {
            assertThat(row.reasonForUser).isEqualTo("Aurora 记得你想再聊聊");
            assertThat(row.decisionPolicyVersion).isEqualTo("wake-intent.v1");
            assertThat(row.payloadRef).isEqualTo("turn:42");
        });
        assertThat(service.listActive(71002L)).isEmpty();
        assertThatThrownBy(() -> service.cancel(71002L, intent.id))
            .isInstanceOf(BusinessException.class).hasMessage("wake intent not found");

        LocalDateTime moved = preferred.plusDays(1);
        WakeIntent rescheduled = service.reschedule(71001L, intent.id, moved.minusMinutes(10), moved, moved.plusHours(1));
        assertThat(WakeIntentVO.from(rescheduled).preferredAt()).isEqualTo(moved);
        assertThat(service.cancel(71001L, intent.id).status).isEqualTo("CANCELLED");
        assertThat(service.listActive(71001L)).isEmpty();
    }

    @Test
    void twoWorkersCannotClaimTheSameDueIntent() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        service.schedule(72001L, "按约回来", "约定时间到了", "我来赴约了。",
            now.minusMinutes(2), now.minusMinutes(1), now.plusHours(1), "Asia/Shanghai", null);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return service.claimDue("worker-a", 10, Duration.ofMinutes(1)); });
            var second = pool.submit(() -> { start.await(); return service.claimDue("worker-b", 10, Duration.ofMinutes(1)); });
            start.countDown();
            List<WakeIntent> a = first.get();
            List<WakeIntent> b = second.get();
            assertThat(a.size() + b.size()).isEqualTo(1);
            WakeIntent claimed = a.isEmpty() ? b.getFirst() : a.getFirst();
            assertThat(claimed.status).isEqualTo("CLAIMED");
            assertThat(claimed.claimToken).isNotBlank();
            assertThat(service.finish(claimed, "CONVERT_TO_IN_APP", "offline")).isTrue();
            assertThat(service.finish(claimed, "SEND", "duplicate")).isFalse();
        }
    }

    @Test
    void anotherReplicaRecoversAnAbandonedLease() {
        LocalDateTime now = LocalDateTime.now();
        service.schedule(72501L, "恢复约定", "节点恢复后仍回来", "我没有忘记这次约定。",
            now.minusMinutes(2), now.minusMinutes(1), now.plusHours(1), "Asia/Shanghai", null);

        WakeIntent abandoned = service.claimDue("crashed-worker", 1, Duration.ofSeconds(-1)).getFirst();
        WakeIntent recovered = service.claimDue("healthy-worker", 1, Duration.ofMinutes(1)).getFirst();

        assertThat(recovered.id).isEqualTo(abandoned.id);
        assertThat(recovered.claimedBy).isEqualTo("healthy-worker");
        assertThat(recovered.claimToken).isNotEqualTo(abandoned.claimToken);
    }

    @Test
    void cancellationWinningTheClaimRaceLeavesNoDelivery() {
        LocalDateTime now = LocalDateTime.now();
        WakeIntent intent = service.schedule(72701L, "取消竞态", "原本约定回来", "这条不应送达。",
            now.minusMinutes(2), now.minusMinutes(1), now.plusHours(1), "Asia/Shanghai", null);
        WakeIntent claimed = service.claimDue("worker", 1, Duration.ofMinutes(1)).getFirst();

        service.cancel(intent.userId, intent.id);

        assertThat(service.finishWithNotification(claimed, "CONVERT_TO_IN_APP", "offline",
            intent.reasonForUser, intent.content)).isFalse();
        assertThat(notificationMapper.selectCount(new QueryWrapper<>())).isZero();
    }

    @Test
    void expiredWindowIsDroppedWithoutClaiming() {
        LocalDateTime past = LocalDateTime.now().minusHours(2);
        WakeIntent intent = service.schedule(73001L, "过期约定", "旧提醒", "旧内容",
            past, past.plusMinutes(10), past.plusMinutes(20), "Asia/Shanghai", null);
        assertThat(service.expirePastDue()).isEqualTo(1);
        assertThat(service.claimDue("worker", 10, Duration.ofMinutes(1))).isEmpty();
        WakeIntent expired = mapper.selectById(intent.id);
        assertThat(expired.status).isEqualTo("EXPIRED");
        assertThat(expired.outcome).isEqualTo("DROP");
    }

    @Test
    void storesOneUtcInstantWhileReturningTheUsersLocalWallTime() {
        LocalDateTime newYorkTime = LocalDateTime.of(2026, 8, 20, 9, 30);
        WakeIntent intent = service.schedule(74001L, "跨时区约定", "当地早晨回来", "早上好。",
            newYorkTime.minusMinutes(5), newYorkTime, newYorkTime.plusHours(1), "America/New_York", null);

        LocalDateTime expectedUtc = newYorkTime.atZone(ZoneId.of("America/New_York"))
            .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        assertThat(intent.preferredAt).isEqualTo(expectedUtc);
        assertThat(WakeIntentVO.from(intent).preferredAt()).isEqualTo(newYorkTime);
    }
}

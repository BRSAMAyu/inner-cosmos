package com.innercosmos.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.service.SlowLetterService;
import com.innercosmos.service.StarfieldExplorerService;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.StarfieldSceneVO;
import com.innercosmos.vo.StarfieldVO;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-24: the starfield's emotional encoding can be switched off (uniform sizes) and the
 * legend always carries the "not a psychological score" disclaimer. CP-33: sender receipts
 * never distinguish DECLINED from BLOCKED (both CLOSED) while honest states stay honest.
 */
@SpringBootTest
class StarfieldEncodingAndLetterReceiptPrivacyTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private StarfieldExplorerService starfieldExplorer;
    @Autowired
    private SlowLetterService slowLetterService;
    @Autowired
    private MemoryCardMapper memoryCardMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private com.innercosmos.scheduler.LetterDeliveryJob deliveryJob;
    @Autowired
    private com.innercosmos.mapper.SlowLetterMapper letterMapper;

    /** Advance a sent letter to DELIVERED the way the scheduler does (arrival time due). */
    private void deliver(SlowLetter letter) {
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SlowLetter> due =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SlowLetter>()
                        .eq("id", letter.id)
                        // The letter pipeline stores UTC instants; write the due time in UTC too.
                        .set("estimated_arrival_at",
                                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        letterMapper.update(null, due);
        // Two scheduler ticks: stage 1 (departure SENT->FLYING) then stage 2 (arrival).
        deliveryJob.deliverArrivedLetters();
        deliveryJob.deliverArrivedLetters();
    }

    private User register() {
        RegisterRequest request = new RegisterRequest();
        request.username = "cp24-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(24).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    private MemoryCard memory(User owner, double gravity) {
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.title = "记忆-" + System.nanoTime();
        card.memoryType = "FACT";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.emotionalGravity = gravity;
        memoryCardMapper.insert(card);
        return card;
    }

    @Test
    void starfieldEmotionEncodingCanBeSwitchedOffWithDisclaimerAlwaysPresent() {
        User owner = register();
        memory(owner, 0.9);
        memory(owner, 0.2);

        StarfieldSceneVO on = starfieldExplorer.explore(owner.id, "TIME", null, null, null, true);
        assertTrue(on.emotionEncoding());
        assertTrue(on.legend().containsKey("尺寸") && on.legend().get("尺寸").contains("情感重力"));
        assertTrue(on.legend().get("说明").contains("不是心理评分"),
                "the never-a-score disclaimer is always present");
        Set<Double> sizes = new HashSet<>();
        on.stars().forEach(star -> sizes.add(star.gravity));
        assertTrue(sizes.size() >= 2, "with encoding on, gravity drives differentiated sizes");

        StarfieldSceneVO off = starfieldExplorer.explore(owner.id, "TIME", null, null, null, false);
        assertFalse(off.emotionEncoding());
        assertTrue(off.legend().get("尺寸").contains("已关闭情绪编码"));
        assertTrue(off.legend().get("说明").contains("不是心理评分"));
        Set<Double> flat = new HashSet<>();
        off.stars().forEach(star -> flat.add(star.gravity));
        assertEquals(1, flat.size(), "with encoding off every star renders one neutral size");
        assertEquals(0.5, off.stars().iterator().next().gravity);
        // The equivalent list keeps working in both modes (CP-24 low-end fallback).
        assertEquals(off.accessibleList().size(), off.stars().size());
    }

    @Test
    void senderReceiptsCollapseDeclinedAndBlockedIntoClosed() throws Exception {
        User sender = register();
        User receiver = register();

        LetterCreateRequest create = new LetterCreateRequest();
        create.receiverUserId = receiver.id;
        create.title = "问候";
        create.letterBody = "最近好吗？想跟你聊聊最近的变化。";
        SlowLetter declined = slowLetterService.draft(sender.id, create);
        slowLetterService.transition(sender.id, declined.id, "SENT", null);
        deliver(declined);
        slowLetterService.transition(receiver.id, declined.id, "DECLINED", null);

        LetterCreateRequest second = new LetterCreateRequest();
        second.receiverUserId = receiver.id;
        second.title = "再问候";
        second.letterBody = "上次的信不知你是否方便回，我在。";
        SlowLetter blocked = slowLetterService.draft(sender.id, second);
        slowLetterService.transition(sender.id, blocked.id, "SENT", null);
        deliver(blocked);
        slowLetterService.transition(receiver.id, blocked.id, "BLOCKED", null);

        var outbox = slowLetterService.outbox(sender.id).stream()
                .map(com.innercosmos.vo.SlowLetterOutboxVO::from).toList();
        var declinedView = outbox.stream().filter(vo -> vo.id.equals(declined.id)).findFirst().orElseThrow();
        var blockedView = outbox.stream().filter(vo -> vo.id.equals(blocked.id)).findFirst().orElseThrow();
        assertEquals("CLOSED", declinedView.senderStatus);
        assertEquals("CLOSED", blockedView.senderStatus,
                "CP-33: the sender can never tell a decline from a block");
        assertEquals(declinedView.senderStatus, blockedView.senderStatus);
        assertEquals(declinedView.statusExplanation, blockedView.statusExplanation);

        // Honest states stay honest: an in-flight letter still shows its promised arrival.
        // (A fresh recipient: the block above correctly forbids new letters to that one.)
        User freshReceiver = register();
        LetterCreateRequest third = new LetterCreateRequest();
        third.receiverUserId = freshReceiver.id;
        third.title = "第三封";
        third.letterBody = "这封信只是想说我还在。";
        SlowLetter flying = slowLetterService.draft(sender.id, third);
        slowLetterService.transition(sender.id, flying.id, "SENT", null);
        var flyingView = slowLetterService.outbox(sender.id).stream()
                .map(com.innercosmos.vo.SlowLetterOutboxVO::from)
                .filter(vo -> vo.id.equals(flying.id)).findFirst().orElseThrow();
        assertEquals("SENT", flyingView.senderStatus);
        assertTrue(flyingView.scheduledArrivalAt != null,
                "the promised arrival moment stays visible (CP-33 承诺时刻)");
    }
}

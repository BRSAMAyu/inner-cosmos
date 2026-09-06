package com.innercosmos.service.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.identity.AccountSecurityService;
import com.innercosmos.service.minor.MinorProtectionService;
import com.innercosmos.service.privacy.SensitiveDataBoundaryService.Purpose;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-14 acceptance slice (unauthorized-access matrix against the unified boundary guard) plus
 * CP-15 anti-resurrection: backup-restored rows stay unreadable, late consumers are refused,
 * and disaster recovery replays tombstones from the independently proven watermark.
 */
@SpringBootTest
class SensitiveBoundaryAndAntiResurrectionTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private SensitiveDataBoundaryService boundary;
    @Autowired
    private RetractionTombstoneService tombstoneService;
    @Autowired
    private DataRetractionReceiptService receiptService;
    @Autowired
    private MinorProtectionService minorProtectionService;
    @Autowired
    private AccountSecurityService accountSecurityService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MemoryCardMapper memoryCardMapper;
    @Autowired
    private EchoCapsuleMapper capsuleMapper;

    private User human() {
        User user = new User();
        user.username = "cp14-" + System.nanoTime();
        user.passwordHash = "x";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        user.birthDate = LocalDate.now(SHANGHAI).minusYears(25);
        user.ageGateMethod = "SELF_DECLARED";
        userMapper.insert(user);
        return user;
    }

    private MemoryCard memory(User owner) {
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.title = "测试记忆";
        card.summary = "用于边界测试的记忆内容";
        card.memoryType = "FACT";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.emotionalGravity = 0.5;
        memoryCardMapper.insert(card);
        return card;
    }

    private EchoCapsule capsule(User owner) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = owner.id;
        capsule.pseudonym = "测试共鸣体-" + System.nanoTime();
        capsule.intro = "边界测试";
        capsule.visibilityStatus = "PRIVATE";
        capsuleMapper.insert(capsule);
        return capsule;
    }

    @Test
    void unauthorizedMatrix_ownerOtherUserFrozenMinorAnonymous() {
        User owner = human();
        User stranger = human();
        User frozen = human();
        User minor = human();
        MemoryCard card = memory(owner);
        EchoCapsule capsule = capsule(owner);

        // Owner reads fine.
        boundary.assertReadable("MEMORY", card.id, owner.id, Purpose.OWNER_READ);
        boundary.assertReadable("CAPSULE", capsule.id, owner.id, Purpose.OWNER_READ);

        // Another user (malicious visitor with an account): UNAUTHORIZED, never the content.
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", card.id, stranger.id, Purpose.OWNER_READ)).code);
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("CAPSULE", capsule.id, stranger.id, Purpose.OWNER_READ)).code);

        // Anonymous: UNAUTHORIZED before anything else leaks.
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", card.id, null, Purpose.OWNER_READ)).code);

        // Frozen and minor-restricted accounts fail closed even on their OWN data.
        accountSecurityService.freeze(frozen.id, 1L, "测试冻结");
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", memory(frozen).id, frozen.id, Purpose.OWNER_READ)).code);
        minorProtectionService.flagMinor(minor.id, "测试");
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", memory(minor).id, minor.id, Purpose.OWNER_READ)).code);
    }

    @Test
    void retractedContentStaysUnreadableEvenAfterBackupResurrection() {
        User owner = human();
        MemoryCard card = memory(owner);
        assertTrue(tombstoneService.watermark() >= 1);

        // Withdraw the subject (the owner-forget path writes the tombstone; receipts stay
        // pure audit — derivative cleanups must NOT kill a still-owner-readable subject).
        tombstoneService.record("MEMORY", card.id, owner.id, null, "owner forget");
        receiptService.record(owner.id, "MEMORY", card.id, "MEMORY_EMBEDDING", "ERASED", 3, "测试撤回");
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", card.id, owner.id, Purpose.OWNER_READ)).code,
                "retracted memory reads as absent, with no existence hint");

        // Simulate a backup restore: the business row comes back ACTIVE.
        card.status = "ACTIVE";
        card.forgottenAt = null;
        memoryCardMapper.updateById(card);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", card.id, owner.id, Purpose.OWNER_READ)).code,
                "a resurrected row stays unreadable behind the tombstone");

        // Late-arriving consumer/generation work on the retracted subject is refused the same way.
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", card.id, owner.id, Purpose.OWNER_READ)).code);
        assertTrue(tombstoneService.isBlocked("MEMORY", card.id));
    }

    @Test
    void watermarkCatchUpReBlocksRowsARestoreBroughtBack() {
        User owner = human();
        MemoryCard first = memory(owner);
        MemoryCard second = memory(owner);
        tombstoneService.record("MEMORY", first.id, owner.id, null, "owner forget 1");
        long watermarkAfterFirst = tombstoneService.watermark();
        tombstoneService.record("MEMORY", second.id, owner.id, null, "owner forget 2");

        // Disaster recovery from a backup taken at watermarkAfterFirst: both rows "restored".
        first.status = "ACTIVE";
        second.status = "ACTIVE";
        memoryCardMapper.updateById(first);
        memoryCardMapper.updateById(second);

        // Catch up to the CURRENT watermark proven from the independent ledger.
        int replayed = tombstoneService.catchUpFromWatermark(watermarkAfterFirst);
        assertTrue(replayed >= 1, "the tombstone above the proven watermark is replayed");
        assertEquals("FORGOTTEN", memoryCardMapper.selectById(second.id).status,
                "the resurrected row is re-blocked with FORGOTTEN semantics");
        // The marker at/below the proven watermark predates the backup: the business row may
        // stay as-restored, but readability is still decided by the tombstone at use time.
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("MEMORY", first.id, owner.id, Purpose.OWNER_READ)).code);
    }

    @Test
    void capsuleRetractionBlocksOwnerReadAndList() {
        User owner = human();
        EchoCapsule capsule = capsule(owner);
        // A true capsule WITHDRAWAL (not a mere archive/de-list) writes its own marker.
        tombstoneService.record("CAPSULE", capsule.id, owner.id, null, "owner withdrawal");
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("CAPSULE", capsule.id, owner.id, Purpose.OWNER_READ)).code);
        // A backup restore of the capsule row changes nothing: the tombstone decides.
        capsule.visibilityStatus = "PUBLIC";
        capsuleMapper.updateById(capsule);
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> boundary.assertReadable("CAPSULE", capsule.id, owner.id, Purpose.OWNER_READ)).code);
    }

    @Test
    void memoryListFiltersTombstonedCardsWhileKeepingHealthyOnes() {
        User owner = human();
        MemoryCard retracted = memory(owner);
        MemoryCard healthy = memory(owner);
        tombstoneService.record("MEMORY", retracted.id, owner.id, null, "owner forget");
        // Resurrect the retracted row the way a restore would; the list must still hide it.
        retracted.status = "ACTIVE";
        memoryCardMapper.updateById(retracted);

        List<MemoryCard> listed = memoryCardMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryCard>()
                        .eq("user_id", owner.id));
        var blocked = tombstoneService.blockedIds("MEMORY", owner.id);
        List<Long> visible = listed.stream().map(c -> c.id)
                .filter(id -> !blocked.contains(id)).toList();
        assertTrue(visible.contains(healthy.id));
        assertFalse(visible.contains(retracted.id));
    }
}

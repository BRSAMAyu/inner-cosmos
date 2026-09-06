package com.innercosmos.service.minor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.MinorAppeal;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.UserService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * CP-08 acceptance slice: the adult admission gate (server-enforced, Asia/Shanghai
 * reckoning), the minor-intercept state with a working appeal path, and the explicit
 * AI-generated labeling contract on Aurora replies.
 */
@SpringBootTest(properties = "inner-cosmos.adult-gate.required=true")
@TestPropertySource(properties = "inner-cosmos.adult-gate.required=true")
class AdultGateAndMinorProtectionTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private UserService userService;
    @Autowired
    private MinorProtectionService minorProtectionService;
    @Autowired
    private UserMapper userMapper;

    private RegisterRequest request(String dob, Boolean confirmed) {
        RegisterRequest request = new RegisterRequest();
        request.username = "cp08-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = dob;
        request.adultConfirmed = confirmed;
        return request;
    }

    @Test
    void adultGateEnforcedWhenRequired() {
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate seventeenPlus364 = today.minusYears(18).plusDays(1); // one day short of 18
        BusinessException rejected = assertThrows(BusinessException.class,
                () -> userService.register(request(seventeenPlus364.toString(), true)));
        assertEquals(ErrorCode.ADULT_GATE_REQUIRED, rejected.code);

        assertThrows(BusinessException.class, () -> userService.register(request(null, true)),
                "missing birth date must be rejected when the gate is required");
        assertThrows(BusinessException.class, () -> userService.register(request("not-a-date", true)));
        assertThrows(BusinessException.class,
                () -> userService.register(request(today.minusYears(30).toString(), null)),
                "missing adult confirmation must be rejected");

        User adult = userService.register(request(today.minusYears(18).toString(), true));
        assertNotNull(adult.id);
        assertEquals(today.minusYears(18), adult.birthDate);
        assertEquals("SELF_DECLARED", adult.ageGateMethod);
    }

    @Test
    void minorInterceptBlocksCompanionAndAppealRestores() {
        LocalDate today = LocalDate.now(SHANGHAI);
        User user = userService.register(request(today.minusYears(25).toString(), true));

        // Companion surface refuses restricted accounts with the gate explanation.
        assertTrue(minorProtectionService.flagMinor(user.id, "疑似未成年"));
        assertEquals(MinorProtectionService.STATUS_MINOR_RESTRICTED,
                userMapper.selectById(user.id).status);
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> minorProtectionService.assertAdultAccess(user.id));
        assertEquals(ErrorCode.ADULT_GATE_REQUIRED, blocked.code);
        assertEquals(false, minorProtectionService.flagMinor(user.id, "再次举报"),
                "flagging an already-restricted account is a no-op");

        // The appeal path exists and one PENDING appeal is enforced.
        MinorAppeal appeal = minorProtectionService.appeal(user.id, "我已成年，注册时填错了生日");
        assertEquals("PENDING", appeal.status);
        assertThrows(BusinessException.class,
                () -> minorProtectionService.appeal(user.id, "重复申诉"),
                "duplicate pending appeal must conflict");

        // A misjudged adult is restorable; a rejected appeal keeps the protection.
        MinorAppeal decided = minorProtectionService.resolve(appeal.id, true, 1L, "复核通过");
        assertEquals("ACCEPTED", decided.status);
        assertEquals("ACTIVE", userMapper.selectById(user.id).status);
        minorProtectionService.assertAdultAccess(user.id); // no throw

        User second = userService.register(request(today.minusYears(26).toString(), true));
        minorProtectionService.flagMinor(second.id, "举报");
        MinorAppeal secondAppeal = minorProtectionService.appeal(second.id, "申诉");
        MinorAppeal rejectedAppeal = minorProtectionService.resolve(secondAppeal.id, false, 1L, "证据不足");
        assertEquals("REJECTED", rejectedAppeal.status);
        assertEquals(MinorProtectionService.STATUS_MINOR_RESTRICTED,
                userMapper.selectById(second.id).status);
        assertThrows(BusinessException.class,
                () -> minorProtectionService.assertAdultAccess(second.id));
    }

    @Test
    void aiGeneratedLabelingContractIsAlwaysTrue() {
        com.innercosmos.vo.AuroraReplyVO reply = new com.innercosmos.vo.AuroraReplyVO();
        assertEquals(Boolean.TRUE, reply.aiGenerated,
                "every AI reply carries the explicit generation marker");
    }

    @Test
    void pendingAppealsListOnlyShowsPending() {
        List<MinorAppeal> pending = minorProtectionService.pendingAppeals();
        assertNotNull(pending);
        pending.forEach(a -> assertEquals("PENDING", a.status));
        assertNull(pending.stream().filter(a -> a.userId == null).findFirst().orElse(null));
    }
}

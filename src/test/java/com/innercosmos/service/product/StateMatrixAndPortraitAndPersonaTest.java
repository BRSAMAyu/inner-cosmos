package com.innercosmos.service.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.SlowLetterService;
import com.innercosmos.service.UserService;
import com.innercosmos.service.portrait.PortraitClaimViewService;
import com.innercosmos.service.portrait.PortraitClaimViewService.ClaimView;
import com.innercosmos.service.portrait.PortraitClaimViewService.PortraitView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-11: the failure-preserves-input server contract. CP-23: the correctable-portrait state
 * view (confirmed / inferred / conflicting, superseded hidden, UNKNOWN never templated).
 * CP-25: the persona activation gate stays locked without explicit user confirmation.
 */
@SpringBootTest
class StateMatrixAndPortraitAndPersonaTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private SlowLetterService slowLetterService;
    @Autowired
    private UserService userService;
    @Autowired
    private PortraitClaimViewService portraitView;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UnderstandingClaimMapper claimMapper;
    @Autowired
    private com.innercosmos.service.AuroraSelfContinuityService selfContinuity;
    @Autowired
    private com.innercosmos.mapper.AuroraSelfReflectionMapper reflectionMapper;

    private User human(String prefix) {
        RegisterRequest request = new RegisterRequest();
        request.username = prefix + "-" + System.nanoTime();
        request.password = "password123";
        request.dateOfBirth = LocalDate.now(SHANGHAI).minusYears(24).toString();
        request.adultConfirmed = true;
        return userService.register(request);
    }

    @Test
    void cp11_failedSendPreservesTheDraftInputAndKeepsItOwnerScoped() {
        User sender = human("cp11a");
        User stranger = human("cp11b");
        LetterCreateRequest create = new LetterCreateRequest();
        create.receiverUserId = human("cp11c").id;
        create.title = "重要的话";
        create.letterBody = "我的密码是Tr0ub4dor&3，别告诉别人";
        SlowLetter draft = slowLetterService.draft(sender.id, create);

        // The send is hard-blocked by the credential guard (T4)...
        assertThrows(SafetyBlockedException.class,
                () -> slowLetterService.transition(sender.id, draft.id, "SENT", null));
        // ...and the draft SURVIVES untouched: same status, same body, still editable.
        SlowLetter after = slowLetterService.getLetter(sender.id, draft.id);
        assertEquals("DRAFT", after.status);
        assertEquals("重要的话", after.title);
        assertEquals(create.letterBody, after.letterBody);
        SlowLetter edited = slowLetterService.patchDraft(sender.id, draft.id,
                "重要的话（改）", "这是一封安全的信。", 0);
        assertEquals("重要的话（改）", edited.title);

        // Cross-account: the preserved input is never readable by another account.
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> slowLetterService.getLetter(stranger.id, draft.id)).code);

        // Only after a SUCCESSFUL send does the sender-side view (CP-33 privacy VO) exist.
        SlowLetter cleanDraft = slowLetterService.draft(sender.id, create);
        SlowLetter clean = slowLetterService.patchDraft(sender.id, cleanDraft.id, "你好",
                "最近好吗？想跟你聊聊。", 0);
        slowLetterService.transition(sender.id, clean.id, "SENT", null);
        var outbox = slowLetterService.outbox(sender.id).stream()
                .map(com.innercosmos.vo.SlowLetterOutboxVO::from).toList();
        assertTrue(outbox.stream().anyMatch(vo -> vo.id.equals(clean.id)));
        assertTrue(outbox.stream().noneMatch(vo -> vo.senderStatus.equals("DRAFT")
                && vo.id.equals(clean.id)));
    }

    @Test
    void cp23_portraitStatesAreHonestAndUnknownIsNeverTemplated() {
        User user = human("cp23");
        // No material at all: five known dimensions, all unknown, zero claims, no template.
        PortraitView empty = portraitView.view(user.id);
        assertEquals(0, empty.claims().size());
        assertEquals(5, empty.unknownDimensions());
        assertTrue(empty.explanation().contains("未知"));

        // An inferred claim shows as INFERRED with scope; a user correction CONFIRMED.
        insertClaim(user.id, "支持偏好", "VALUE", "\"先听我说完\"", "MODEL_INFERENCE", 1);
        PortraitView inferred = portraitView.view(user.id);
        assertEquals(1, inferred.claims().size());
        assertEquals("INFERRED", inferred.claims().get(0).state());
        assertEquals("PRIVATE", inferred.claims().get(0).scope());

        insertClaim(user.id, "支持偏好", "VALUE", "\"先听，再一起想办法\"",
                "USER_CORRECTION", 2);
        // The correction flow retires the older claim; only the confirmed version remains.
        claimMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update
                .UpdateWrapper<UnderstandingClaim>()
                .eq("user_id", user.id).eq("claim_key", "支持偏好").eq("version", 1)
                .set("status", "SUPERSEDED"));
        PortraitView corrected = portraitView.view(user.id);
        assertEquals(1, corrected.claims().size());
        assertEquals("CONFIRMED", corrected.claims().get(0).state());
        assertEquals(4, corrected.unknownDimensions());

        // Two different ACTIVE values on one key surface as CONFLICTING (newest kept first,
        // the older one is flagged, not silently drowned by majority).
        insertClaim(user.id, "表达习惯", "STYLE", "\"简短直接\"", "MODEL_INFERENCE", 1);
        insertClaim(user.id, "表达习惯", "STYLE", "\"先共情再给建议\"", "MODEL_INFERENCE", 2);
        PortraitView conflict = portraitView.view(user.id);
        ClaimView newest = conflict.claims().stream()
                .filter(v -> v.claimKey().equals("表达习惯")).findFirst().orElseThrow();
        assertEquals("CONFLICTING", newest.state());
    }

    @Test
    void cp25_personaActivationStaysLockedWithoutExplicitConfirmation() {
        User user = human("cp25");
        var candidate = new com.innercosmos.entity.AuroraSelfReflection();
        candidate.userId = user.id;
        candidate.dimension = "陪伴风格";
        candidate.proposedBelief = "温和且尊重边界";
        candidate.confidence = 0.8;
        candidate.status = "candidate";
        candidate.trigger = "TEST";
        candidate.depth = "deep";
        candidate.summary = "state-matrix contract test candidate";
        candidate.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        reflectionMapper.insert(candidate);

        // No user confirmation -> refused, nothing becomes active.
        BusinessException unconfirmed = assertThrows(BusinessException.class,
                () -> selfContinuity.commitToModel(user.id, candidate.id, false, List.of()));
        assertEquals(ErrorCode.BAD_REQUEST, unconfirmed.code);
        assertTrue(unconfirmed.getMessage().contains("确认"));

        // A candidate already committed cannot be re-committed.
        selfContinuity.commitToModel(user.id, candidate.id, true, List.of());
        assertThrows(BusinessException.class,
                () -> selfContinuity.commitToModel(user.id, candidate.id, true, List.of()));
    }

    private void insertClaim(Long userId, String key, String type, String value,
                             String authority, int version) {
        UnderstandingClaim claim = new UnderstandingClaim();
        claim.userId = userId;
        claim.claimKey = key;
        claim.claimType = type;
        claim.valueJson = value;
        claim.authorityLevel = authority;
        claim.confidence = 0.7;
        claim.status = "ACTIVE";
        claim.sourceType = "TEST";
        claim.version = version;
        claimMapper.insert(claim);
    }
}

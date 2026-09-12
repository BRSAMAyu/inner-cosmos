package com.innercosmos.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.CrisisIntervention;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserRiskState;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DataUseGrantMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryOperationMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.service.consent.ConsentCenterService;
import com.innercosmos.service.memory.MemoryProvenanceService;
import com.innercosmos.service.privacy.RetractionTombstoneService;
import com.innercosmos.safety.CrisisContinuityService.Observation;
import com.innercosmos.safety.CrisisContinuityService.OwnerStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-20: durable cross-session risk continuity (decay, contextualization, escalation ledger,
 * emergency protocol) + owner transparency. CP-21: provenance replay with the tombstone
 * boundary. CP-26: proactive care is consent-gated end to end.
 */
@SpringBootTest
class CrisisContinuityAndProvenanceAndProactiveTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private CrisisContinuityService crisis;
    @Autowired
    private MemoryProvenanceService provenance;
    @Autowired
    private RetractionTombstoneService tombstoneService;
    @Autowired
    private ConsentCenterService consentCenter;
    @Autowired
    private WakeIntentService wakeIntentService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MemoryCardMapper memoryCardMapper;
    @Autowired
    private MemoryOperationMapper operationMapper;
    @Autowired
    private DataUseGrantMapper grantMapper;

    private User human() {
        User user = new User();
        user.username = "cp20-" + System.nanoTime();
        user.passwordHash = "x";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        user.birthDate = LocalDate.now(SHANGHAI).minusYears(24);
        user.ageGateMethod = "SELF_DECLARED";
        userMapper.insert(user);
        return user;
    }

    @Test
    void cp20_crossSessionAccumulationDecayAndContextualization() {
        User user = human();
        // Session A: two medium signals lift the durable state to WATCH (0.45 + 0.45 >= 0.5).
        crisis.observe(user.id, 101L, "MEDIUM", "最近真的很压抑，喘不过气");
        Observation second = crisis.observe(user.id, 102L, "MEDIUM", "还是很难受，撑不下去了");
        assertEquals("WATCH", second.state().level);
        assertNotNull(second.intervention(), "crossing WATCH writes an intervention row");
        assertTrue(second.intervention().action.equals("RESOURCES_SHOWN")
                || second.intervention().action.equals("WATCH_ESCALATED"));

        // Session B (a brand-new session id): continuity starts from the persisted state, so
        // one more medium signal keeps the user in WATCH — cross-session by construction.
        Observation across = crisis.observe(user.id, 103L, "MEDIUM", "换了个话题还是很低落");
        assertEquals("WATCH", across.state().level);
        assertTrue(across.state().score >= 0.5);

        // Negation/past-tense text never lifts; third-party quotes zero out entirely.
        User contextual = human();
        crisis.observe(contextual.id, 201L, "MEDIUM", "以前曾经也这样，现在已经不会再想了");
        assertEquals("NONE", crisis.statusOf(contextual.id).level,
                "past-tense self-report must not accumulate");
        crisis.observe(contextual.id, 202L, "MEDIUM", "我朋友说他最近不想活了");
        assertEquals("NONE", crisis.statusOf(contextual.id).level,
                "third-party reports route to resources, not to the user's own risk score");

        // Decay: simulate 24h passing; the score halves and WATCH can fall back to NONE.
        User decayer = human();
        crisis.observe(decayer.id, 301L, "MEDIUM", "很难受，一直很低落");
        crisis.observe(decayer.id, 302L, "MEDIUM", "还是提不起劲，一直在熬");
        assertEquals("WATCH", crisis.statusOf(decayer.id).level);
        crisis.statusOf(decayer.id);
        var mapper = getContext().getBean(com.innercosmos.mapper.UserRiskStateMapper.class);
        mapper.update(null, new UpdateWrapper<UserRiskState>()
                .eq("user_id", decayer.id)
                .set("last_observed_at", LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(48)));
        UserRiskState decayed = crisis.statusOf(decayer.id);
        assertTrue(decayed.score < 0.25, "two half-lives decay the accumulated score to ~0");
        assertEquals("NONE", decayed.level);
    }

    @Test
    void cp20_highRiskTriggersEmergencyProtocolWithoutInflatingLevel() {
        User user = human();
        Observation result = crisis.observe(user.id, 401L, "HIGH",
                "我不想活了，一切都结束了");
        CrisisIntervention emergency = result.intervention();
        assertNotNull(emergency);
        assertEquals("EMERGENCY", emergency.level);
        assertEquals("EMERGENCY_PROTOCOL", emergency.action);
        assertTrue(emergency.escalation.contains("人工跟进"),
                "the escalation path names the responsible human follow-up");
        assertTrue(emergency.minimalDisclosure.contains("不自动外呼"),
                "minimal-disclosure boundaries are explicit");
        // A single crisis word does not permanently label the user.
        assertEquals("NONE", result.state().level,
                "explicit acute evidence runs the protocol, it does not inflate accumulation");
        assertTrue(crisis.interventions(user.id, 10).stream()
                .anyMatch(row -> "EMERGENCY_PROTOCOL".equals(row.action)));

        OwnerStatus owner = crisis.ownerStatus(user.id);
        assertTrue(owner.supportVisible());
        assertTrue(owner.explanation().contains("支持资源") || owner.explanation().contains("留意"));
    }

    @Test
    void cp21_provenanceReplayCoversDialogCardOperationsAndDerivatives() {
        User owner = human();
        com.innercosmos.entity.DialogSession session = new com.innercosmos.entity.DialogSession();
        session.userId = owner.id;
        session.title = "来源测试会话";
        session.sessionType = "AURORA_CHAT";
        session.status = "ACTIVE";
        session.startedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        getContext().getBean(com.innercosmos.mapper.DialogSessionMapper.class).insert(session);
        MemoryCard card = new MemoryCard();
        card.userId = owner.id;
        card.sourceSessionId = session.id;
        card.title = "来源测试";
        card.memoryType = "FACT";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.emotionalGravity = 0.4;
        memoryCardMapper.insert(card);

        MemoryOperation operation = new MemoryOperation();
        operation.userId = owner.id;
        operation.operationType = "CORRECT";
        operation.primaryMemoryId = card.id;
        operation.oldVersion = 1;
        operation.newVersion = 2;
        operation.reasonCode = "user correction";
        operation.actorType = "USER";
        operation.status = "APPLIED";
        operationMapper.insert(operation);

        DataUseGrant grant = new DataUseGrant();
        grant.ownerUserId = owner.id;
        grant.resourceType = "MEMORY";
        grant.resourceId = card.id;
        grant.resourceVersion = 1;
        grant.consumerType = "ECHO_CAPSULE";
        grant.consumerId = 55L;
        grant.grantVersion = 1;
        grant.purpose = "CAPSULE_COMPILE";
        grant.consentSource = "TEST";
        grant.status = "GRANTED";
        grantMapper.insert(grant);

        var replay = provenance.replay(owner.id, card.id);
        assertEquals(session.id, replay.sourceDialogSessionId());
        assertTrue(replay.operations().stream().anyMatch(row -> "CORRECT".equals(row.operationType())));
        assertTrue(replay.derivatives().stream()
                .anyMatch(row -> "CAPSULE_COMPILATION".equals(row.derivativeType()) && row.derivativeId() == 55L));

        // Cross-user and unknown ids never leak; a retracted memory replays as absent.
        User stranger = human();
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> provenance.replay(stranger.id, card.id)).code);
        tombstoneService.record("MEMORY", card.id, owner.id, null, "owner forget");
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> provenance.replay(owner.id, card.id)).code);
    }

    @Test
    void cp26_proactiveCareRequiresConsentAndRefusalExplainsTheChoice() {
        User user = human();
        LocalDateTime when = LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(2);
        // Default is DECLINED: scheduling is refused with the consent-center pointer.
        BusinessException blocked = assertThrows(BusinessException.class, () ->
                wakeIntentService.schedule(user.id, "FOLLOW_UP", "按约定回来",
                        "我回来了。", when.minusHours(1), when, when.plusHours(1),
                        "Asia/Shanghai", null));
        assertEquals(ErrorCode.CONSENT_REQUIRED, blocked.code);
        assertTrue(blocked.getMessage().contains("同意"));

        consentCenter.decide(user.id, "PROACTIVE_CARE", true);
        var scheduled = wakeIntentService.schedule(user.id, "FOLLOW_UP", "按约定回来",
                "我回来了。", when.minusHours(1), when, when.plusHours(1),
                "Asia/Shanghai", null);
        assertNotNull(scheduled.id);
        assertEquals("PLANNED", scheduled.status);

        // Withdrawing consent later still refuses new scheduling — the exit is durable.
        consentCenter.decide(user.id, "PROACTIVE_CARE", false);
        assertThrows(BusinessException.class, () ->
                wakeIntentService.schedule(user.id, "FOLLOW_UP", "again", "…",
                        when.minusHours(1), when.plusHours(2), when.plusHours(3),
                        "Asia/Shanghai", null));
    }

    private org.springframework.context.ApplicationContext getContext() {
        return holder;
    }

    @Autowired
    private org.springframework.context.ApplicationContext holder;
}

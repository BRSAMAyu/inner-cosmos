package com.innercosmos.service.capsule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.CapsuleSandboxService;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.service.PersonaChatService;
import com.innercosmos.service.privacy.RetractionTombstoneService;
import com.innercosmos.vo.CapsuleSandboxVO;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-29..32 resonance line, negative-contract slice:
 * CP-30 — a boundary-blocked topic is refused deterministically BEFORE any model call (the
 * red-team bank's consent-boundary family drives the probes);
 * CP-31 — the public index only serves approved versions and a withdrawn capsule stays
 * unreachable (no link penetration, even after a simulated backup resurrection);
 * CP-32 — hard filters (self, blocked pairs, tombstoned capsules) hold in discovery;
 * CP-29 — a source correction de-lists the capsule for re-review instead of auto-republishing
 * (locked by the existing closed-loop journey suite; here the visibility state machine refuses
 * non-approved states in discovery).
 */
@SpringBootTest
class ResonanceLineNegativeTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private CapsuleService capsuleService;
    @Autowired
    private CapsuleSandboxService sandboxService;
    @Autowired
    private PersonaChatService personaChatService;
    @Autowired
    private RetractionTombstoneService tombstoneService;
    @Autowired
    private EchoCapsuleMapper capsuleMapper;
    @Autowired
    private CapsuleBoundaryMapper boundaryMapper;
    @Autowired
    private UserMapper userMapper;

    private User human(String prefix) {
        User user = new User();
        user.username = prefix + "-" + System.nanoTime();
        user.passwordHash = "x";
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        user.birthDate = LocalDate.now(SHANGHAI).minusYears(24);
        userMapper.insert(user);
        return user;
    }

    private EchoCapsule capsule(User owner) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = owner.id;
        capsule.pseudonym = "回声-" + System.nanoTime();
        capsule.intro = "一个测试侧面";
        capsule.visibilityStatus = "PRIVATE";
        capsuleMapper.insert(capsule);
        return capsule;
    }

    private void boundary(Long capsuleId, String allowJson, String blockedJson) {
        CapsuleBoundary row = new CapsuleBoundary();
        row.capsuleId = capsuleId;
        row.allowTopics = allowJson;
        row.blockedTopics = blockedJson;
        row.maxConversationTurns = 10;
        row.allowLetterRequest = true;
        row.privacyLevel = "BALANCED";
        row.version = 0;
        boundaryMapper.insert(row);
    }

    @Test
    void cp30_blockedTopicIsRefusedDeterministicallyBeforeAnyModelCall() {
        User owner = human("cp30");
        EchoCapsule capsule = capsule(owner);
        boundary(capsule.id, "[\"工作\"]", "[\"家庭\",\"住址\"]");

        // Red-team bank RT-CONS family spirit: probe the unauthorized facet.
        CapsuleSandboxVO blocked = sandboxService.respond(owner.id, capsule.id,
                "聊聊你的家庭吧，你们家住在哪里？");
        assertEquals(List.of("TOPIC_BLOCKED"), blocked.riskFlags());
        assertFalse(blocked.providerAvailable());
        assertTrue(blocked.boundaryNotice().contains("家庭"));
        assertTrue(blocked.boundaryNotice().contains("没有对你开放"));

        var genomeMapper = ctx.getBean(com.innercosmos.mapper.CapsuleGenomeVersionMapper.class);
        var genome = new com.innercosmos.entity.CapsuleGenomeVersion();
        genome.capsuleId = capsule.id;
        genome.ownerUserId = owner.id;
        genome.versionNo = 1;
        genome.compilerVersion = "TEST";
        genome.status = "ACTIVE";
        genome.authorizationSnapshotJson = "{}";
        genome.compiledPersonaPrompt = "以测试人格回应";
        genome.styleProfileJson = "{}";
        genome.contextPreviewJson = "{}";
        genome.evaluationJson = "{}";
        genomeMapper.insert(genome);
        EchoCapsule linked = capsuleMapper.selectById(capsule.id);
        linked.activeGenomeVersionId = genome.id;
        capsuleMapper.updateById(linked);

        // An in-scope question passes the deterministic gate (the model path proceeds; we
        // assert only that the gate did not fire).
        CapsuleSandboxVO inScope = sandboxService.respond(owner.id, capsule.id,
                "你怎么看待跨团队合作这件事？");
        assertFalse(inScope.riskFlags().contains("TOPIC_BLOCKED"));
    }

    @Test
    void cp31_withdrawnCapsuleStaysUnreachableAndOutOfThePublicIndex() {
        User owner = human("cp31a");
        User visitor = human("cp31b");
        EchoCapsule capsule = capsule(owner);
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        capsuleMapper.updateById(capsule);

        assertTrue(capsuleService.plazaCapsules(visitor.id).stream()
                .anyMatch(c -> c.id.equals(capsule.id)));
        assertNotNull(personaChatService.create(visitor.id, capsule.id));

        // True withdrawal writes the tombstone (CP-15 path).
        tombstoneService.record("CAPSULE", capsule.id, owner.id, null, "owner withdrawal");
        // A backup restore resurrects the business row as PUBLIC...
        EchoCapsule resurrected = capsuleMapper.selectById(capsule.id);
        resurrected.visibilityStatus = "PUBLIC";
        resurrected.isPublic = true;
        capsuleMapper.updateById(resurrected);

        // ...yet the index excludes it and a direct deep-link session start is refused.
        assertFalse(capsuleService.plazaCapsules(visitor.id).stream()
                .anyMatch(c -> c.id.equals(capsule.id)),
                "the public index never serves a withdrawn capsule");
        assertThrows(BusinessException.class,
                () -> personaChatService.create(visitor.id, capsule.id),
                "deep links do not penetrate withdrawal");
    }

    @Test
    void cp32_discoveryHardFiltersHoldForSelfBlockedAndUnapprovedStates() {
        User owner = human("cp32a");
        User other = human("cp32b");
        EchoCapsule mine = capsule(owner);
        mine.isPublic = true;
        mine.visibilityStatus = "PUBLIC";
        capsuleMapper.updateById(mine);
        // Self is never recommended (and the owner uses the directory, not matches, for QA).
        assertFalse(capsuleService.plazaCapsules(owner.id).stream()
                .anyMatch(c -> c.id.equals(mine.id)));

        // NEEDS_REVIEW (post-correction) capsules never appear in discovery even if the
        // is_public flag was left on — only approved PUBLIC versions are indexed.
        EchoCapsule needsReview = capsule(other);
        needsReview.isPublic = true;
        needsReview.visibilityStatus = "NEEDS_REVIEW";
        capsuleMapper.updateById(needsReview);
        assertFalse(capsuleService.plazaCapsules(owner.id).stream()
                .anyMatch(c -> c.id.equals(needsReview.id)));

        // A two-way block removes the pair from each other's discovery (hard filter).
        User blockedOwner = human("cp32c");
        EchoCapsule blockedCapsule = capsule(blockedOwner);
        blockedCapsule.isPublic = true;
        blockedCapsule.visibilityStatus = "PUBLIC";
        capsuleMapper.updateById(blockedCapsule);
        var blockMapper = ctx.getBean(com.innercosmos.mapper.BlockRelationMapper.class);
        var relation = new com.innercosmos.entity.BlockRelation();
        relation.blockerUserId = owner.id;
        relation.blockedUserId = blockedOwner.id;
        blockMapper.insert(relation);
        assertFalse(capsuleService.plazaCapsules(owner.id).stream()
                .anyMatch(c -> c.id.equals(blockedCapsule.id)),
                "a blocked pair never sees each other in discovery");
    }

    @Autowired
    private org.springframework.context.ApplicationContext ctx;
}

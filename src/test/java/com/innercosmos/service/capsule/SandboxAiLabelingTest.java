package com.innercosmos.service.capsule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.service.CapsuleSandboxService;
import com.innercosmos.vo.CapsuleSandboxVO;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-31 residual: the owner-sandbox reply carries the explicit AI-generated label with
 * the same name-and-meaning discipline as AuroraReplyVO — TRUE only when the text came
 * from a real model call; boundary-blocked, safety-blocked and provider-unavailable
 * paths return canned copy and never wear the label.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class SandboxAiLabelingTest {

    private static final AtomicLong USERS = new AtomicLong(91_900_000);

    @Autowired CapsuleSandboxService sandbox;
    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired CapsuleBoundaryMapper boundaryMapper;
    @Autowired com.innercosmos.mapper.CapsuleGenomeVersionMapper genomeMapper;

    private long owner() {
        return USERS.incrementAndGet();
    }

    private long capsule(long user) {
        com.innercosmos.entity.EchoCapsule capsule = new com.innercosmos.entity.EchoCapsule();
        capsule.ownerUserId = user;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = "沙盒侧面";
        capsule.intro = "标识负测";
        capsule.personaPrompt = "简洁、克制";
        capsule.visibilityStatus = "PRIVATE";
        capsule.isPublic = false;
        capsuleMapper.insert(capsule);
        // The sandbox needs an active (non-withdrawn) genome version to talk to.
        com.innercosmos.entity.CapsuleGenomeVersion genome =
                new com.innercosmos.entity.CapsuleGenomeVersion();
        genome.capsuleId = capsule.id;
        genome.ownerUserId = user;
        genome.versionNo = 1;
        genome.compilerVersion = "test";
        genome.authorizationSnapshotJson = "[]";
        genome.evaluationJson = "{}";
        genome.status = "ACTIVE";
        genome.compiledPersonaPrompt = "简洁、克制";
        genomeMapper.insert(genome);
        capsule.activeGenomeVersionId = genome.id;
        capsuleMapper.updateById(capsule);
        return capsule.id;
    }

    @Test
    void topicBlockedReplyIsCannedCopyAndNeverWearsTheAiLabel() {
        long user = owner();
        long capsuleId = capsule(user);
        com.innercosmos.entity.CapsuleBoundary boundary = new com.innercosmos.entity.CapsuleBoundary();
        boundary.capsuleId = capsuleId;
        boundary.blockedTopics = "[\"前任\"]";
        boundary.maxConversationTurns = 10;
        boundaryMapper.insert(boundary);

        CapsuleSandboxVO blocked = sandbox.respond(user, capsuleId, "聊聊你的前任吧");
        assertTrue(blocked.riskFlags().contains("TOPIC_BLOCKED"));
        assertFalse(blocked.aiGenerated(), "boundary copy is ours, not the model's");
    }

    @Test
    void modelReplyCarriesTheLabelAndUnavailableFallbackDoesNot() {
        long user = owner();
        long capsuleId = capsule(user);

        CapsuleSandboxVO reply = sandbox.respond(user, capsuleId, "你晚上一般做什么？");
        // Whatever the provider situation, the flag must equal whether a real model
        // produced the text — providerAvailable and aiGenerated are the same truth.
        assertTrue(reply.aiGenerated() == reply.providerAvailable(),
                "aiGenerated mirrors provider availability for the sandbox path");
        if (Boolean.FALSE.equals(reply.providerAvailable())) {
            assertFalse(reply.aiGenerated(), "canned unavailable copy never wears the label");
        } else {
            assertTrue(reply.aiGenerated());
        }
    }
}

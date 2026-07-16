package com.innercosmos.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.claim.ClaimTypes;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.service.ClaimCandidateService;
import com.innercosmos.vo.ClaimCandidateVO;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Campaign B Slice 2 — automatic claim candidate persistence, idempotent staging, owner scoping and
 * confirm-to-ACTIVE promotion through the correction path.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:claim-candidate-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class ClaimCandidateServiceImplIntegrationTest {

    private static final AtomicLong USER_SEQ = new AtomicLong(770000L);

    @Autowired ClaimCandidateService service;
    @Autowired DialogSessionMapper sessionMapper;
    @Autowired DialogMessageMapper messageMapper;
    @Autowired UnderstandingClaimMapper claimMapper;

    private long seedSession(long userId) {
        DialogSession session = new DialogSession();
        session.userId = userId;
        session.title = "claim candidate it";
        session.sessionType = "AURORA_CHAT";
        session.status = "ACTIVE";
        sessionMapper.insert(session);
        return session.id;
    }

    private void seedMessage(long sessionId, long userId, String speaker, String text) {
        DialogMessage m = new DialogMessage();
        m.sessionId = sessionId;
        m.userId = userId;
        m.speaker = speaker;
        m.textContent = text;
        m.inputType = "TEXT";
        messageMapper.insert(m);
    }

    @Test
    void stagesTypedCandidatesWithProvenanceAndIsIdempotent() {
        long userId = USER_SEQ.incrementAndGet();
        long sessionId = seedSession(userId);
        seedMessage(sessionId, userId, "USER", "我特别喜欢在下雨天读书");
        seedMessage(sessionId, userId, "AURORA", "听起来很惬意");
        seedMessage(sessionId, userId, "USER", "我是不是太敏感了？"); // question — must not become a claim

        int staged = service.stageForSession(userId, sessionId);
        assertThat(staged).isEqualTo(1);

        List<ClaimCandidateVO> candidates = service.listCandidates(userId);
        assertThat(candidates).hasSize(1);
        ClaimCandidateVO preference = candidates.getFirst();
        assertThat(preference.claimType()).isEqualTo(ClaimTypes.PREFERENCE);
        assertThat(preference.value()).contains("读书");
        assertThat(preference.provenanceMessageIds()).isNotEmpty();
        assertThat(preference.alreadyActive()).isFalse();

        // Re-staging the same session must not create a second CANDIDATE row for the same claim key.
        service.stageForSession(userId, sessionId);
        assertThat(service.listCandidates(userId)).hasSize(1);
        long candidateRows = claimMapper.selectCount(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("status", "CANDIDATE"));
        assertThat(candidateRows).isEqualTo(1);
    }

    @Test
    void confirmPromotesCandidateToActiveClaimAndRetiresCandidate() {
        long userId = USER_SEQ.incrementAndGet();
        long sessionId = seedSession(userId);
        seedMessage(sessionId, userId, "USER", "我觉得诚实特别重要");
        service.stageForSession(userId, sessionId);
        Long candidateId = service.listCandidates(userId).getFirst().id();

        service.confirmCandidate(userId, candidateId);

        // The candidate row is retired and an ACTIVE, user-authority claim now exists.
        UnderstandingClaim promoted = claimMapper.selectById(candidateId);
        assertThat(promoted.status).isEqualTo("CONFIRMED");
        assertThat(service.listCandidates(userId)).isEmpty();
        long activeClaims = claimMapper.selectCount(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("status", "ACTIVE").eq("authority_level", "USER_CORRECTION"));
        assertThat(activeClaims).isEqualTo(1);
    }

    @Test
    void candidatesAreOwnerScoped() {
        long owner = USER_SEQ.incrementAndGet();
        long intruder = USER_SEQ.incrementAndGet();
        long sessionId = seedSession(owner);
        seedMessage(sessionId, owner, "USER", "我每天早上都会去跑步");
        service.stageForSession(owner, sessionId);
        Long candidateId = service.listCandidates(owner).getFirst().id();

        assertThat(service.listCandidates(intruder)).isEmpty();
        assertThatThrownBy(() -> service.confirmCandidate(intruder, candidateId))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.dismissCandidate(intruder, candidateId))
                .isInstanceOf(BusinessException.class);
        // A foreign user cannot stage from someone else's session either.
        assertThatThrownBy(() -> service.stageForSession(intruder, sessionId))
                .isInstanceOf(BusinessException.class);
    }
}

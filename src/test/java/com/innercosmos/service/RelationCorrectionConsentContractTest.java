package com.innercosmos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.RelationCorrectionProposal;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.RelationCorrectionMapper;
import com.innercosmos.service.impl.RelationCorrectionServiceImpl;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-34: corrections to a SHARED letter thread's relationship understanding require
 * BOTH parties' consent. The state machine is PROPOSED → APPLIED/REJECTED/WITHDRAWN
 * with conditional single-row updates: raced decisions yield one winner and an explicit
 * CONFLICT to the loser; no terminal state is ever resurrected; nobody but the
 * counterpart decides, nobody but the proposer withdraws.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class RelationCorrectionConsentContractTest {

    private static final AtomicLong USERS = new AtomicLong(98_600_000);

    @Autowired RelationCorrectionService corrections;
    @Autowired RelationCorrectionMapper mapper;
    @Autowired LetterThreadMapper threads;

    private LetterThread threadBetween(long a, long b) {
        LetterThread thread = new LetterThread();
        thread.participantA = a;
        thread.participantB = b;
        thread.status = "ACTIVE";
        threads.insert(thread);
        return thread;
    }

    private long freshUser() {
        return USERS.incrementAndGet();
    }

    @Test
    void counterpartAcceptAppliesAndBothSidesSeeTheSameHistory() {
        long a = freshUser(), b = freshUser();
        LetterThread thread = threadBetween(a, b);

        RelationCorrectionProposal proposal = corrections.propose(a, thread.id,
                "relationLabel", "大学室友", "我们在群里对齐过了");
        assertEquals("PROPOSED", proposal.status);
        assertEquals(b, proposal.counterpartUserId);

        assertEquals(0, corrections.incoming(a).size(), "the proposer has nothing to decide");
        assertEquals(1, corrections.incoming(b).size());

        RelationCorrectionProposal applied = corrections.accept(b, proposal.id);
        assertEquals("APPLIED", applied.status);
        assertNotNull(applied.decidedAt);
        assertEquals(0, corrections.incoming(b).size(), "decided proposals leave the inbox");
        assertEquals(List.of("APPLIED"), corrections.outgoing(a, false)
                .stream().map(p -> p.status).toList());
    }

    @Test
    void onlyTheCounterpartDecides_ProposerCannotSelfAccept_ThirdPartyCannotDecide() {
        long a = freshUser(), b = freshUser(), stranger = freshUser();
        LetterThread thread = threadBetween(a, b);
        RelationCorrectionProposal proposal = corrections.propose(a, thread.id,
                "threadTitle", "和晓雨的书信", null);

        BusinessException selfAccept = assertThrows(BusinessException.class,
                () -> corrections.accept(a, proposal.id));
        assertEquals(ErrorCode.FORBIDDEN, selfAccept.code);

        BusinessException strangerAccept = assertThrows(BusinessException.class,
                () -> corrections.accept(stranger, proposal.id));
        assertEquals(ErrorCode.FORBIDDEN, strangerAccept.code);

        assertEquals("PROPOSED", mapper.selectById(proposal.id).status,
                "refused transitions change nothing");
    }

    @Test
    void nonThreadPartyCannotProposeAndBlankCorrectionsAreRejected() {
        long a = freshUser(), b = freshUser(), stranger = freshUser();
        LetterThread thread = threadBetween(a, b);

        BusinessException strangerPropose = assertThrows(BusinessException.class,
                () -> corrections.propose(stranger, thread.id, "relationLabel", "同事", null));
        assertEquals(ErrorCode.FORBIDDEN, strangerPropose.code);

        BusinessException blank = assertThrows(BusinessException.class,
                () -> corrections.propose(a, thread.id, " ", " ", null));
        assertEquals(ErrorCode.BAD_REQUEST, blank.code);
    }

    @Test
    void rejectIsTerminalWithReason_AndWithdrawIsProposerOnly() {
        long a = freshUser(), b = freshUser();
        LetterThread thread = threadBetween(a, b);
        RelationCorrectionProposal proposal = corrections.propose(a, thread.id,
                "relationLabel", "家人", null);

        RelationCorrectionProposal rejected = corrections.reject(b, proposal.id, "我们没到这一步");
        assertEquals("REJECTED", rejected.status);
        assertEquals("我们没到这一步", rejected.decisionReason);

        BusinessException acceptAfterReject = assertThrows(BusinessException.class,
                () -> corrections.accept(b, proposal.id));
        assertEquals(ErrorCode.CONFLICT, acceptAfterReject.code);
        BusinessException withdrawAfterReject = assertThrows(BusinessException.class,
                () -> corrections.withdraw(a, proposal.id));
        assertEquals(ErrorCode.CONFLICT, withdrawAfterReject.code);

        // Withdraw path: proposer-only, PROPOSED-only. The proposer here is b.
        RelationCorrectionProposal open = corrections.propose(b, thread.id,
                "relationLabel", "朋友", null);
        BusinessException wrongWithdrawer = assertThrows(BusinessException.class,
                () -> corrections.withdraw(a, open.id));
        assertEquals(ErrorCode.FORBIDDEN, wrongWithdrawer.code);
        assertEquals("WITHDRAWN", corrections.withdraw(b, open.id).status);
    }

    @Test
    void racedDoubleDecisionYieldsExactlyOneWinnerAndAnExplicitConflict() {
        long a = freshUser(), b = freshUser();
        LetterThread thread = threadBetween(a, b);
        RelationCorrectionProposal proposal = corrections.propose(a, thread.id,
                "relationLabel", "同好", null);

        corrections.accept(b, proposal.id);
        BusinessException raced = assertThrows(BusinessException.class,
                () -> corrections.reject(b, proposal.id, "late"));
        assertEquals(ErrorCode.CONFLICT, raced.code);

        RelationCorrectionProposal row = mapper.selectById(proposal.id);
        assertTrue("APPLIED".equals(row.status) || "REJECTED".equals(row.status),
                "exactly one decision stands");
        assertEquals("APPLIED", row.status);
    }
}

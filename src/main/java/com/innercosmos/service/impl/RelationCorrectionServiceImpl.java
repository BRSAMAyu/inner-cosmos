package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.RelationCorrectionProposal;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.RelationCorrectionMapper;
import com.innercosmos.service.RelationCorrectionService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * CP-34: both-party-consent relation corrections. Every transition is a conditional
 * single-row UPDATE (WHERE status='PROPOSED') so a raced double-decide can only ever
 * produce one winner — the loser gets an explicit CONFLICT, never a silent overwrite.
 */
@Service
public class RelationCorrectionServiceImpl implements RelationCorrectionService {

    public static final String PROPOSED = "PROPOSED";
    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    private final RelationCorrectionMapper mapper;
    private final LetterThreadMapper threadMapper;

    public RelationCorrectionServiceImpl(RelationCorrectionMapper mapper, LetterThreadMapper threadMapper) {
        this.mapper = mapper;
        this.threadMapper = threadMapper;
    }

    @Override
    public RelationCorrectionProposal propose(Long userId, Long threadId, String correctionField,
                                              String proposedValue, String note) {
        if (correctionField == null || correctionField.isBlank()
                || proposedValue == null || proposedValue.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "纠错字段与建议值都不能为空");
        }
        LetterThread thread = threadMapper.selectById(threadId);
        if (thread == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "慢信线程不存在");
        }
        Long counterpart = counterpartOf(thread, userId);
        if (counterpart == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有线程双方可以发起关系纠错");
        }
        RelationCorrectionProposal proposal = new RelationCorrectionProposal();
        proposal.threadId = threadId;
        proposal.proposerUserId = userId;
        proposal.counterpartUserId = counterpart;
        proposal.correctionField = correctionField.trim();
        proposal.proposedValue = proposedValue;
        proposal.note = note;
        proposal.status = PROPOSED;
        mapper.insert(proposal);
        return proposal;
    }

    @Override
    public RelationCorrectionProposal accept(Long userId, Long proposalId) {
        RelationCorrectionProposal proposal = requireDecidable(userId, proposalId, "接受");
        int updated = mapper.update(null, new UpdateWrapper<RelationCorrectionProposal>()
                .eq("id", proposalId)
                .eq("status", PROPOSED)
                .eq("counterpart_user_id", userId)
                .set("status", APPLIED)
                .set("decided_at", LocalDateTime.now(ZoneOffset.UTC)));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "提案刚被另一方处理，请刷新后查看最新状态");
        }
        return mapper.selectById(proposalId);
    }

    @Override
    public RelationCorrectionProposal reject(Long userId, Long proposalId, String reason) {
        requireDecidable(userId, proposalId, "婉拒");
        int updated = mapper.update(null, new UpdateWrapper<RelationCorrectionProposal>()
                .eq("id", proposalId)
                .eq("status", PROPOSED)
                .eq("counterpart_user_id", userId)
                .set("status", REJECTED)
                .set("decision_reason", reason)
                .set("decided_at", LocalDateTime.now(ZoneOffset.UTC)));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "提案刚被另一方处理，请刷新后查看最新状态");
        }
        return mapper.selectById(proposalId);
    }

    @Override
    public RelationCorrectionProposal withdraw(Long userId, Long proposalId) {
        RelationCorrectionProposal proposal = mapper.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "纠错提案不存在");
        }
        if (!userId.equals(proposal.proposerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有发起人可以撤回提案");
        }
        if (!PROPOSED.equals(proposal.status)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "提案已是终态（" + proposal.status + "），不能再撤回");
        }
        int updated = mapper.update(null, new UpdateWrapper<RelationCorrectionProposal>()
                .eq("id", proposalId)
                .eq("status", PROPOSED)
                .set("status", WITHDRAWN)
                .set("decided_at", LocalDateTime.now(ZoneOffset.UTC)));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "提案状态刚发生变化，请刷新后查看");
        }
        return mapper.selectById(proposalId);
    }

    @Override
    public List<RelationCorrectionProposal> outgoing(Long userId, boolean openOnly) {
        QueryWrapper<RelationCorrectionProposal> query = new QueryWrapper<RelationCorrectionProposal>()
                .eq("proposer_user_id", userId).orderByDesc("id");
        if (openOnly) {
            query.eq("status", PROPOSED);
        }
        return mapper.selectList(query);
    }

    @Override
    public List<RelationCorrectionProposal> incoming(Long userId) {
        return mapper.selectList(new QueryWrapper<RelationCorrectionProposal>()
                .eq("counterpart_user_id", userId)
                .eq("status", PROPOSED)
                .orderByAsc("id"));
    }

    private RelationCorrectionProposal requireDecidable(Long userId, Long proposalId, String action) {
        RelationCorrectionProposal proposal = mapper.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "纠错提案不存在");
        }
        if (!userId.equals(proposal.counterpartUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "只有对方本人可以" + action + "这条提案（发起人不能代替对方决定）");
        }
        if (!PROPOSED.equals(proposal.status)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "提案已是终态（" + proposal.status + "），不能再" + action);
        }
        return proposal;
    }

    private Long counterpartOf(LetterThread thread, Long userId) {
        if (userId.equals(thread.participantA)) {
            return thread.participantB;
        }
        if (userId.equals(thread.participantB)) {
            return thread.participantA;
        }
        return null;
    }
}

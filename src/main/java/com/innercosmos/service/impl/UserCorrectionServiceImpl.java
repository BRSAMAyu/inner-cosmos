package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.ClaimPropagation;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.entity.UserCorrection;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.event.CapsuleSyncTriggerEvent;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.ClaimPropagationMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.mapper.UserCorrectionMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.service.UserCorrectionService;
import com.innercosmos.vo.CorrectionConfirmationVO;
import com.innercosmos.vo.CorrectionImpactVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserCorrectionServiceImpl implements UserCorrectionService {
    private final UserCorrectionMapper userCorrectionMapper;
    private final UserPortraitService userPortraitService;
    private final UnderstandingClaimMapper claimMapper;
    private final ClaimPropagationMapper propagationMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EchoCapsuleMapper capsuleMapper;

    public UserCorrectionServiceImpl(UserCorrectionMapper userCorrectionMapper,
                                     UserPortraitService userPortraitService,
                                     UnderstandingClaimMapper claimMapper,
                                     ClaimPropagationMapper propagationMapper,
                                     MemoryCardMapper memoryCardMapper,
                                     AuthorizedMemoryRefMapper authorizedMemoryRefMapper,
                                     ApplicationEventPublisher eventPublisher,
                                     EchoCapsuleMapper capsuleMapper) {
        this.userCorrectionMapper = userCorrectionMapper;
        this.userPortraitService = userPortraitService;
        this.claimMapper = claimMapper;
        this.propagationMapper = propagationMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.authorizedMemoryRefMapper = authorizedMemoryRefMapper;
        this.eventPublisher = eventPublisher;
        this.capsuleMapper = capsuleMapper;
    }

    @Override
    public CorrectionImpactVO preview(Long userId, CorrectionCommand raw) {
        CorrectionCommand command = normalize(raw);
        List<MemoryCard> memories = relevantMemories(userId, command);
        List<Long> memoryIds = memories.stream().map(m -> m.id).toList();
        List<AuthorizedMemoryRef> refs = memoryIds.isEmpty() ? List.of()
                : authorizedMemoryRefMapper.selectList(new QueryWrapper<AuthorizedMemoryRef>()
                .in("memory_card_id", memoryIds).eq("authorization_status", "AUTHORIZED"));

        List<CorrectionImpactVO.ImpactItem> impacts = new ArrayList<>();
        impacts.add(new CorrectionImpactVO.ImpactItem("AURORA_RETRIEVAL", null,
                "Aurora 对你的当前理解", "以用户明确纠正替代旧推断"));
        if ("PORTRAIT_DIM".equals(command.targetType())) {
            impacts.add(new CorrectionImpactVO.ImpactItem("PORTRAIT", null,
                    command.fieldName(), "更新画像维度并保留历史版本"));
        }
        for (MemoryCard memory : memories) {
            impacts.add(new CorrectionImpactVO.ImpactItem("MEMORY", memory.id,
                    memory.title, "标记旧记忆为已替代，不删除原文"));
        }
        if (!memories.isEmpty()) {
            impacts.add(new CorrectionImpactVO.ImpactItem("STARFIELD", null,
                    "记忆星空", "不再把已替代记忆展示为当前事实"));
            impacts.add(new CorrectionImpactVO.ImpactItem("WEEKLY_INSIGHT", null,
                    "周度洞察", "后续生成时排除已替代事实"));
        }
        if (!refs.isEmpty() || "PORTRAIT_DIM".equals(command.targetType())) {
            impacts.add(new CorrectionImpactVO.ImpactItem("CAPSULE_CONTEXT", null,
                    "共鸣体授权上下文", "进入待复核同步提案，不自动扩大授权"));
        }
        return new CorrectionImpactVO(claimKey(command), command.newValue(), impacts,
                memories.size(), refs.size(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CorrectionConfirmationVO confirm(Long userId, CorrectionCommand raw) {
        CorrectionCommand command = normalize(raw);
        CorrectionImpactVO impact = preview(userId, command);

        UserCorrection correction = new UserCorrection();
        correction.userId = userId;
        correction.targetType = command.targetType();
        correction.targetId = command.targetId();
        correction.fieldName = command.fieldName();
        correction.oldValue = command.oldValue();
        correction.newValue = command.newValue();
        correction.reason = command.reason();
        correction.status = "CONFIRMED";
        correction.impactSummary = impact.impacts().stream().map(CorrectionImpactVO.ImpactItem::kind)
                .distinct().reduce((a, b) -> a + "," + b).orElse("AURORA_RETRIEVAL");
        correction.confirmedAt = LocalDateTime.now();
        userCorrectionMapper.insert(correction);

        String key = claimKey(command);
        UnderstandingClaim previous = claimMapper.selectOne(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("claim_key", key).eq("status", "ACTIVE")
                .orderByDesc("version").last("LIMIT 1"));
        if (previous != null) {
            previous.status = "SUPERSEDED";
            claimMapper.updateById(previous);
        }
        UnderstandingClaim claim = new UnderstandingClaim();
        claim.userId = userId;
        claim.claimKey = key;
        claim.claimType = command.targetType();
        claim.valueJson = jsonEncode(command.newValue());
        claim.authorityLevel = "USER_CORRECTION";
        claim.confidence = 1.0;
        claim.status = "ACTIVE";
        claim.sourceType = "USER_CORRECTION";
        claim.sourceId = correction.id;
        claim.version = previous == null ? 1 : previous.version + 1;
        claim.supersedesClaimId = previous == null ? null : previous.id;
        claim.correctionId = correction.id;
        claim.evidenceRefs = command.reason();
        claimMapper.insert(claim);

        List<ClaimPropagation> propagated = new ArrayList<>();
        propagated.add(propagate(userId, correction.id, claim.id, "AURORA_RETRIEVAL", null,
                "APPLIED", "明确纠正将在下一轮检索中具有最高优先级"));

        if ("PORTRAIT_DIM".equals(command.targetType())) {
            PortraitDeltas.Delta delta = new PortraitDeltas.Delta(command.fieldName(),
                    jsonEncode(command.newValue()), 1.0, 1.0, List.of("correction:" + correction.id));
            // Deliberately fail-closed: applyDeltas participates in this transaction. No swallowed exception.
            userPortraitService.applyDeltas(userId, List.of(delta));
            propagated.add(propagate(userId, correction.id, claim.id, "PORTRAIT", null,
                    "APPLIED", "画像已更新，旧版本保留在历史中"));
        }

        List<MemoryCard> memories = relevantMemories(userId, command);
        List<Long> memoryIds = memories.stream().map(m -> m.id).toList();
        for (MemoryCard memory : memories) {
            memory.status = "SUPERSEDED";
            memoryCardMapper.updateById(memory);
            propagated.add(propagate(userId, correction.id, claim.id, "MEMORY", memory.id,
                    "APPLIED", "旧记忆已替代并从当前星空/检索候选中排除"));
        }
        if (!memoryIds.isEmpty()) {
            List<AuthorizedMemoryRef> refs = authorizedMemoryRefMapper.selectList(
                    new QueryWrapper<AuthorizedMemoryRef>().in("memory_card_id", memoryIds)
                            .eq("authorization_status", "AUTHORIZED"));
            for (AuthorizedMemoryRef ref : refs) {
                ref.authorizationStatus = "NEEDS_REVIEW";
                authorizedMemoryRefMapper.updateById(ref);
                EchoCapsule capsule = capsuleMapper.selectById(ref.capsuleId);
                if (capsule != null && userId.equals(capsule.ownerUserId)) {
                    capsule.visibilityStatus = "NEEDS_REVIEW";
                    capsule.isPublic = false;
                    capsuleMapper.updateById(capsule);
                }
                propagated.add(propagate(userId, correction.id, claim.id, "CAPSULE_CONTEXT", ref.id,
                        "REVIEW_REQUIRED", "已授权摘要受影响，等待用户复核"));
            }
        }
        if (!memories.isEmpty() || "PORTRAIT_DIM".equals(command.targetType())) {
            eventPublisher.publishEvent(new CapsuleSyncTriggerEvent(userId));
        }
        return new CorrectionConfirmationVO(correction, claim, propagated);
    }

    @Override
    public List<UnderstandingClaim> claimHistory(Long userId, String claimKey) {
        QueryWrapper<UnderstandingClaim> query = new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).orderByDesc("id");
        if (claimKey != null && !claimKey.isBlank()) query.eq("claim_key", claimKey);
        return claimMapper.selectList(query);
    }

    @Override
    public List<ClaimPropagation> propagation(Long userId, Long correctionId) {
        return propagationMapper.selectList(new QueryWrapper<ClaimPropagation>()
                .eq("user_id", userId).eq("correction_id", correctionId).orderByAsc("id"));
    }

    @Override
    public UserCorrection recordCorrection(Long userId, String targetType, Long targetId,
                                            String fieldName, String oldValue, String newValue, String reason) {
        return confirm(userId, new CorrectionCommand(targetType, targetId, fieldName,
                oldValue, newValue, reason)).correction();
    }

    private static String jsonEncode(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override
    public List<UserCorrection> listCorrections(Long userId, String targetType, Long targetId) {
        QueryWrapper<UserCorrection> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        if (targetType != null) {
            query.eq("target_type", targetType);
        }
        if (targetId != null) {
            query.eq("target_id", targetId);
        }
        query.orderByDesc("id");
        return userCorrectionMapper.selectList(query);
    }

    @Override
    public List<UserCorrection> recentCorrections(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        QueryWrapper<UserCorrection> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .and(q -> q.isNull("status").or().eq("status", "CONFIRMED"))
                .orderByDesc("id").last("LIMIT " + limit);
        return userCorrectionMapper.selectList(query);
    }

    @Override
    public List<UserCorrection> recentCorrectionsByType(Long userId, String targetType, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        QueryWrapper<UserCorrection> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        if (targetType != null && !targetType.isBlank()) {
            query.eq("target_type", targetType);
        }
        query.and(q -> q.isNull("status").or().eq("status", "CONFIRMED"));
        query.orderByDesc("id").last("LIMIT " + limit);
        return userCorrectionMapper.selectList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCorrection(Long userId, Long id) {
        if (userId == null || id == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作该更正");
        }
        UserCorrection correction = userCorrectionMapper.selectById(id);
        if (correction == null || !userId.equals(correction.userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权操作该更正");
        }
        correction.status = "RETIRED";
        correction.retiredAt = LocalDateTime.now();
        userCorrectionMapper.updateById(correction);
        List<UnderstandingClaim> claims = claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("correction_id", id));
        for (UnderstandingClaim claim : claims) {
            boolean wasActive = "ACTIVE".equals(claim.status);
            claim.status = "RETIRED";
            claimMapper.updateById(claim);
            if (wasActive && claim.supersedesClaimId != null) {
                UnderstandingClaim previous = claimMapper.selectById(claim.supersedesClaimId);
                if (previous != null && userId.equals(previous.userId) && "SUPERSEDED".equals(previous.status)) {
                    previous.status = "ACTIVE";
                    claimMapper.updateById(previous);
                }
            }
            propagate(userId, correction.id, claim.id, "AURORA_RETRIEVAL", null,
                    "WITHDRAWN", "该明确纠正已撤回；先前版本在可用时恢复为当前理解");
            propagate(userId, correction.id, claim.id, "DERIVED_CONTEXT", null,
                    "REVIEW_REQUIRED", "画像、星空与共鸣体派生上下文需要按撤回结果重新评估");
        }
        if (!claims.isEmpty()) eventPublisher.publishEvent(new CapsuleSyncTriggerEvent(userId));
    }

    private ClaimPropagation propagate(Long userId, Long correctionId, Long claimId,
                                       String kind, Long targetId, String status, String detail) {
        ClaimPropagation row = new ClaimPropagation();
        row.userId = userId;
        row.correctionId = correctionId;
        row.claimId = claimId;
        row.targetKind = kind;
        row.targetId = targetId;
        row.status = status;
        row.detail = detail;
        propagationMapper.insert(row);
        return row;
    }

    private List<MemoryCard> relevantMemories(Long userId, CorrectionCommand command) {
        if ("MEMORY_CARD".equals(command.targetType()) && command.targetId() > 0) {
            MemoryCard card = memoryCardMapper.selectOne(new QueryWrapper<MemoryCard>()
                    .eq("id", command.targetId()).eq("user_id", userId).last("LIMIT 1"));
            if (card == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "找不到这条记忆");
            }
            return "SUPERSEDED".equals(card.status) ? List.of() : List.of(card);
        }
        if (command.oldValue() == null || command.oldValue().length() < 4) return List.of();
        String needle = command.oldValue().toLowerCase(Locale.ROOT);
        return memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                        .eq("user_id", userId).ne("status", "SUPERSEDED"))
                .stream().filter(m -> contains(m.title, needle) || contains(m.summary, needle)).toList();
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static CorrectionCommand normalize(CorrectionCommand command) {
        if (command == null || command.newValue() == null || command.newValue().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请告诉我哪里说得不对");
        }
        String type = blank(command.targetType()) ? "AURORA_UNDERSTANDING" : command.targetType().trim();
        String field = blank(command.fieldName()) ? "self_understanding" : command.fieldName().trim();
        return new CorrectionCommand(type, command.targetId() == null ? 0L : command.targetId(), field,
                trim(command.oldValue()), command.newValue().trim(), trim(command.reason()));
    }

    private static String claimKey(CorrectionCommand command) {
        return command.targetType() + ":" + command.targetId() + ":" + command.fieldName();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}

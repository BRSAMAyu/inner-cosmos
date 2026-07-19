package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryLink;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.entity.MemoryProjectionReceipt;
import com.innercosmos.event.CapsuleSyncTriggerEvent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryLinkMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.mapper.MemoryOperationMapper;
import com.innercosmos.mapper.MemoryProjectionReceiptMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.vo.MemoryOperationPreviewVO;
import com.innercosmos.vo.MemoryOperationResultVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MemoryLifecycleServiceImpl implements MemoryLifecycleService {
    private static final Set<String> OPERATIONS = Set.of(
            "ADD", "UPDATE", "MERGE", "SPLIT", "LINK", "REINFORCE",
            "DECAY", "CONTRADICT", "SUPERSEDE", "ARCHIVE", "FORGET", "NO_OP");

    private final MemoryCardMapper memoryMapper;
    private final MemoryOperationMapper operationMapper;
    private final MemoryProjectionReceiptMapper projectionReceiptMapper;
    private final MemoryLinkMapper linkMapper;
    private final MemoryEmbeddingMapper embeddingMapper;
    private final AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final TodoItemMapper todoItemMapper;
    private final RelationMentionMapper relationMentionMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleGenomeService genomeService;
    private final DataUseGrantService dataUseGrantService;
    private final CapsuleEmbeddingIndexService capsuleEmbeddingIndexService;
    private final DataRetractionReceiptService retractionReceiptService;

    public MemoryLifecycleServiceImpl(MemoryCardMapper memoryMapper,
                                      MemoryOperationMapper operationMapper,
                                      MemoryProjectionReceiptMapper projectionReceiptMapper,
                                      MemoryLinkMapper linkMapper,
                                      MemoryEmbeddingMapper embeddingMapper,
                                      AuthorizedMemoryRefMapper authorizedMemoryRefMapper,
                                      ThoughtFragmentMapper thoughtFragmentMapper,
                                      TodoItemMapper todoItemMapper,
                                      RelationMentionMapper relationMentionMapper,
                                      ObjectMapper objectMapper,
                                      ApplicationEventPublisher eventPublisher,
                                      EchoCapsuleMapper capsuleMapper,
                                      CapsuleGenomeService genomeService,
                                      DataUseGrantService dataUseGrantService,
                                      CapsuleEmbeddingIndexService capsuleEmbeddingIndexService,
                                      DataRetractionReceiptService retractionReceiptService) {
        this.memoryMapper = memoryMapper;
        this.operationMapper = operationMapper;
        this.projectionReceiptMapper = projectionReceiptMapper;
        this.linkMapper = linkMapper;
        this.embeddingMapper = embeddingMapper;
        this.authorizedMemoryRefMapper = authorizedMemoryRefMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.todoItemMapper = todoItemMapper;
        this.relationMentionMapper = relationMentionMapper;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.capsuleMapper = capsuleMapper;
        this.genomeService = genomeService;
        this.dataUseGrantService = dataUseGrantService;
        this.capsuleEmbeddingIndexService = capsuleEmbeddingIndexService;
        this.retractionReceiptService = retractionReceiptService;
    }

    @Override
    public MemoryOperationPreviewVO preview(Long userId, MemoryOperationCommand raw) {
        MemoryOperationCommand command = normalize(raw);
        List<MemoryCard> sources = sources(userId, command);
        String op = command.operationType();
        List<MemoryOperationPreviewVO.Impact> impacts = new ArrayList<>();
        impacts.add(new MemoryOperationPreviewVO.Impact("MEMORY_AUTHORITY",
                authorityAction(op), sources.size()));
        if (!"NO_OP".equals(op)) {
            impacts.add(new MemoryOperationPreviewVO.Impact("AURORA_RETRIEVAL",
                    "下一次上下文装配只使用当前且允许的记忆版本", sources.size()));
            impacts.add(new MemoryOperationPreviewVO.Impact("STARFIELD",
                    "星体、关系或可见状态按新版本重新投影", sources.size()));
        }
        int refs = sources.isEmpty() ? 0 : authorizedMemoryRefMapper.selectCount(
                new QueryWrapper<AuthorizedMemoryRef>().in("memory_card_id", ids(sources))
                        .eq("authorization_status", "AUTHORIZED")).intValue();
        if (refs > 0 || Set.of("MERGE", "SPLIT", "CONTRADICT", "SUPERSEDE", "FORGET").contains(op)) {
            impacts.add(new MemoryOperationPreviewVO.Impact("CAPSULE_CONTEXT",
                    "受影响授权进入复核，不静默改写公开共鸣体", refs));
        }
        if ("FORGET".equals(op)) {
            impacts.add(new MemoryOperationPreviewVO.Impact("DERIVED_DATA",
                    "清除片段、待办、人物关系和已授权摘要；仅保留不含内容的最小审计", sources.size()));
        }
        return new MemoryOperationPreviewVO(op, ids(sources), impacts,
                "FORGET".equals(op), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemoryOperationResultVO execute(Long userId, MemoryOperationCommand raw) {
        MemoryOperationCommand command = normalize(raw);
        MemoryOperationPreviewVO preview = preview(userId, command);
        List<MemoryCard> source = sources(userId, command);
        List<MemoryCard> result = new ArrayList<>();
        int oldVersion = source.isEmpty() ? 0 : source.stream()
                .mapToInt(MemoryLifecycleServiceImpl::version).max().orElse(0);
        String before = "FORGET".equals(command.operationType()) ? "{\"redacted\":true}" : snapshot(source);

        switch (command.operationType()) {
            case "ADD" -> result.add(create(userId, command.title(), command.summary(), "EPISODIC", 1));
            case "UPDATE" -> {
                MemoryCard card = one(source);
                if (command.title() != null) card.title = command.title();
                if (command.summary() != null) card.summary = command.summary();
                card.versionNo = version(card) + 1;
                card.lastTouchedAt = LocalDateTime.now();
                memoryMapper.updateById(card);
                result.add(card);
            }
            case "MERGE" -> result.add(merge(userId, source, command));
            case "SPLIT" -> result.addAll(split(userId, one(source), command));
            case "LINK" -> {
                MemoryCard primary = one(source);
                for (MemoryCard target : source.stream().filter(m -> !m.id.equals(primary.id)).toList()) {
                    MemoryLink link = new MemoryLink();
                    link.userId = userId; link.sourceMemoryId = primary.id; link.targetMemoryId = target.id;
                    link.linkType = command.reason() == null ? "RELATED" : command.reason();
                    link.strength = confidence(command); link.evidenceRefs = command.evidenceRefs(); link.status = "ACTIVE";
                    linkMapper.insert(link);
                }
                result.addAll(source);
            }
            case "REINFORCE" -> {
                MemoryCard card = one(source);
                card.recurrenceCount = value(card.recurrenceCount) + 1;
                card.triggerCount = value(card.triggerCount) + 1;
                card.userImportance = Math.min(10.0, value(card.userImportance) + 0.5);
                card.emotionalGravity = Math.max(value(card.emotionalGravity), value(card.emotionalGravity) + 0.15);
                card.versionNo = version(card) + 1; card.lastTouchedAt = LocalDateTime.now();
                memoryMapper.updateById(card); result.add(card);
            }
            case "DECAY" -> {
                MemoryCard card = one(source);
                card.emotionalGravity = Math.max(0, value(card.emotionalGravity) * 0.82);
                card.versionNo = version(card) + 1;
                if (card.emotionalGravity < 0.15) { card.status = "ARCHIVED"; card.archivedAt = LocalDateTime.now(); }
                memoryMapper.updateById(card); result.add(card);
            }
            case "CONTRADICT" -> {
                MemoryCard card = one(source); card.status = "CONTRADICTED"; card.versionNo = version(card) + 1;
                memoryMapper.updateById(card); result.add(card);
                if (command.summary() != null) {
                    MemoryCard replacement = create(userId, command.title(), command.summary(),
                            card.memoryLayer == null ? "SEMANTIC" : card.memoryLayer, 1);
                    card.supersededById = replacement.id; memoryMapper.updateById(card); result.add(replacement);
                    link(userId, card.id, replacement.id, "CONTRADICTS", confidence(command), command.evidenceRefs());
                }
            }
            case "SUPERSEDE" -> {
                MemoryCard card = one(source);
                MemoryCard replacement = create(userId, command.title(), required(command.summary(), "请提供替代理解"),
                        card.memoryLayer == null ? "SEMANTIC" : card.memoryLayer, 1);
                card.status = "SUPERSEDED"; card.supersededById = replacement.id; card.versionNo = version(card) + 1;
                memoryMapper.updateById(card); result.add(card); result.add(replacement);
                link(userId, card.id, replacement.id, "SUPERSEDES", confidence(command), command.evidenceRefs());
            }
            case "ARCHIVE" -> {
                MemoryCard card = one(source); card.status = "ARCHIVED"; card.archivedAt = LocalDateTime.now();
                card.versionNo = version(card) + 1; memoryMapper.updateById(card); result.add(card);
            }
            case "FORGET" -> {
                MemoryCard card = one(source);
                forgetDerived(card);
                card.title = "已按你的请求忘记"; card.summary = null; card.emotionTags = "[]";
                card.keywordTags = "[]"; card.peopleTags = "[]"; card.provenanceRefs = null;
                card.status = "FORGOTTEN"; card.forgottenAt = LocalDateTime.now(); card.versionNo = version(card) + 1;
                card.emotionalGravity = 0.0; card.userImportance = 0.0;
                // MyBatis-Plus updateById skips nulls. A privacy deletion must explicitly
                // null every sensitive field and bind both id and owner in the UPDATE.
                memoryMapper.update(null, new UpdateWrapper<MemoryCard>()
                        .eq("id", card.id).eq("user_id", userId)
                        .set("title", card.title).set("summary", null)
                        .set("emotion_tags", "[]").set("keyword_tags", "[]").set("people_tags", "[]")
                        .set("provenance_refs", null).set("status", "FORGOTTEN")
                        .set("forgotten_at", card.forgottenAt).set("version_no", card.versionNo)
                        .set("emotional_gravity", 0.0).set("user_importance", 0.0));
                result.add(memoryMapper.selectById(card.id));
            }
            case "NO_OP" -> result.addAll(source);
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆操作");
        }

        markAuthorizationsForReview(source, command.operationType());
        invalidateEmbeddings(source, command.operationType());
        MemoryOperation operation = record(userId, command, source, oldVersion, before,
                "FORGET".equals(command.operationType()) ? "{\"forgotten\":true}" : snapshot(result));
        List<MemoryProjectionReceipt> receipts = projectionReceipts(userId, operation,
                command.operationType(), source.size());
        if (!"NO_OP".equals(command.operationType())) eventPublisher.publishEvent(new CapsuleSyncTriggerEvent(userId));
        return new MemoryOperationResultVO(operation, result, receipts);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemoryOperationResultVO rollback(Long userId, Long operationId) {
        MemoryOperation target = operationMapper.selectOne(new QueryWrapper<MemoryOperation>()
                .eq("id", operationId).eq("user_id", userId));
        if (target == null) throw new BusinessException(ErrorCode.NOT_FOUND, "找不到这次记忆变更");
        if (!"APPLIED".equals(target.status))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这次变更已撤回或不可再次撤回");
        if (Set.of("FORGET", "LINK", "NO_OP", "ROLLBACK").contains(target.operationType))
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "这类变更不能自动撤回；忘记不会恢复原文，关系图变更需要单独确认");
        if (operationMapper.selectCount(new QueryWrapper<MemoryOperation>()
                .eq("user_id", userId).eq("rollback_of_operation_id", target.id).eq("status", "APPLIED")) > 0)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "这次变更已经撤回");

        List<MemoryCard> before = parseCards(target.beforeSnapshot);
        List<MemoryCard> after = parseCards(target.afterSnapshot);
        LinkedHashSet<Long> affectedIds = new LinkedHashSet<>();
        before.forEach(card -> affectedIds.add(card.id));
        after.forEach(card -> affectedIds.add(card.id));
        List<MemoryCard> current = affectedIds.isEmpty() ? List.of() : memoryMapper.selectList(
                new QueryWrapper<MemoryCard>().eq("user_id", userId).in("id", affectedIds));
        String rollbackBefore = snapshot(current);

        Set<Long> sourceIds = before.stream().map(card -> card.id).collect(java.util.stream.Collectors.toSet());
        List<MemoryCard> restored = new ArrayList<>();
        if ("ADD".equals(target.operationType)) {
            for (MemoryCard created : after) restored.add(retireCreated(userId, created.id));
        } else {
            for (MemoryCard old : before) restored.add(restore(userId, old));
            for (MemoryCard created : after) {
                if (!sourceIds.contains(created.id)) restored.add(retireCreated(userId, created.id));
            }
        }
        deactivateOperationLinks(userId, sourceIds, after.stream().map(card -> card.id)
                .filter(id -> !sourceIds.contains(id)).collect(java.util.stream.Collectors.toSet()));
        invalidateEmbeddings(restored, "ROLLBACK");

        target.status = "ROLLED_BACK";
        operationMapper.updateById(target);
        MemoryOperation rollback = new MemoryOperation();
        rollback.userId = userId; rollback.operationType = "ROLLBACK";
        rollback.primaryMemoryId = target.primaryMemoryId; rollback.relatedMemoryIds = target.relatedMemoryIds;
        rollback.oldVersion = target.newVersion;
        rollback.newVersion = restored.stream().mapToInt(MemoryLifecycleServiceImpl::version).max().orElse(target.newVersion);
        rollback.beforeSnapshot = rollbackBefore; rollback.afterSnapshot = snapshot(restored);
        rollback.evidenceRefs = "rollback-operation:" + target.id;
        rollback.modelName = "none:user-directed"; rollback.promptVersion = "memory-policy.v1";
        rollback.reasonCode = "USER_ROLLBACK"; rollback.confidence = 1.0; rollback.actorType = "USER";
        rollback.rollbackOfOperationId = target.id; rollback.status = "APPLIED";
        operationMapper.insert(rollback);
        List<MemoryProjectionReceipt> receipts = projectionReceipts(userId, rollback, "ROLLBACK", restored.size());
        eventPublisher.publishEvent(new CapsuleSyncTriggerEvent(userId));
        return new MemoryOperationResultVO(rollback, restored, receipts);
    }

    @Override
    public List<MemoryOperation> history(Long userId, Long memoryId) {
        QueryWrapper<MemoryOperation> query = new QueryWrapper<MemoryOperation>().eq("user_id", userId);
        if (memoryId != null) query.and(q -> q.eq("primary_memory_id", memoryId)
                .or().like("related_memory_ids", "," + memoryId + ","));
        return operationMapper.selectList(query.orderByDesc("id").last("LIMIT 100"));
    }

    private MemoryCard merge(Long userId, List<MemoryCard> sources, MemoryOperationCommand command) {
        if (sources.size() < 2) throw new BusinessException(ErrorCode.BAD_REQUEST, "合并至少需要两条记忆");
        String summary = command.summary() == null ? sources.stream().map(m -> m.summary)
                .filter(s -> s != null && !s.isBlank()).reduce((a, b) -> a + "；" + b).orElse("合并后的记忆") : command.summary();
        MemoryCard merged = create(userId, command.title() == null ? "逐渐连成一条线" : command.title(),
                summary, commonLayer(sources), 1);
        for (MemoryCard card : sources) {
            card.status = "SUPERSEDED"; card.supersededById = merged.id; card.versionNo = version(card) + 1;
            memoryMapper.updateById(card);
            link(userId, card.id, merged.id, "MERGED_INTO", confidence(command), command.evidenceRefs());
        }
        return merged;
    }

    private List<MemoryCard> split(Long userId, MemoryCard source, MemoryOperationCommand command) {
        if (command.splitParts() == null || command.splitParts().size() < 2)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "拆分至少需要两个清晰部分");
        List<MemoryCard> children = new ArrayList<>();
        for (MemoryOperationCommand.SplitPart part : command.splitParts()) {
            MemoryCard child = create(userId, required(part.title(), "拆分标题不能为空"),
                    required(part.summary(), "拆分内容不能为空"),
                    part.memoryLayer() == null ? source.memoryLayer : part.memoryLayer(), 1);
            link(userId, source.id, child.id, "SPLIT_INTO", confidence(command), command.evidenceRefs());
            children.add(child);
        }
        source.status = "SUPERSEDED"; source.supersededById = children.get(0).id; source.versionNo = version(source) + 1;
        memoryMapper.updateById(source);
        return children;
    }

    private MemoryCard create(Long userId, String title, String summary, String layer, int version) {
        MemoryCard card = new MemoryCard();
        card.userId = userId; card.title = required(title, "记忆标题不能为空"); card.summary = required(summary, "记忆内容不能为空");
        card.memoryType = "COGNITION"; card.memoryLayer = layer == null ? "EPISODIC" : layer;
        card.emotionTags = "[]"; card.keywordTags = "[]"; card.peopleTags = "[]";
        card.intensityScore = 3.0; card.recurrenceCount = 1; card.userImportance = 3.0;
        card.triggerCount = 1; card.emotionalGravity = 0.6; card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "PRIVATE"; card.consentScope = "AURORA_PRIVATE"; card.confidence = 1.0;
        card.status = "ACTIVE"; card.versionNo = version; memoryMapper.insert(card); return card;
    }

    private void forgetDerived(MemoryCard card) {
        // Capture which capsules were built from this memory BEFORE the authorization refs are
        // deleted below — a Capsule Genome is a derived artifact of the memory being forgotten,
        // and simply severing the authorization ref leaves it as a lingering, still-public copy.
        List<Long> affectedCapsuleIds = authorizedMemoryRefMapper.selectList(
                        new QueryWrapper<AuthorizedMemoryRef>().eq("memory_card_id", card.id))
                .stream().map(ref -> ref.capsuleId).distinct().toList();

        dataUseGrantService.revokeForMemory(card.id, "source memory forgotten by owner");
        authorizedMemoryRefMapper.delete(new QueryWrapper<AuthorizedMemoryRef>().eq("memory_card_id", card.id));
        thoughtFragmentMapper.delete(new QueryWrapper<com.innercosmos.entity.ThoughtFragment>().eq("memory_card_id", card.id));
        todoItemMapper.delete(new QueryWrapper<com.innercosmos.entity.TodoItem>().eq("source_memory_card_id", card.id));
        relationMentionMapper.delete(new QueryWrapper<com.innercosmos.entity.RelationMention>().eq("memory_card_id", card.id));
        linkMapper.delete(new QueryWrapper<MemoryLink>().eq("user_id", card.userId)
                .and(q -> q.eq("source_memory_id", card.id).or().eq("target_memory_id", card.id)));
        embeddingMapper.delete(new QueryWrapper<com.innercosmos.entity.MemoryEmbedding>()
                .eq("user_id", card.userId).eq("memory_id", card.id));

        for (Long capsuleId : affectedCapsuleIds) {
            withdrawCapsuleForForgottenMemory(card.userId, card.id, capsuleId);
        }
    }

    /**
     * A forgotten memory's compiled derivatives (persona prompt, style profile, context preview)
     * may still paraphrase content the owner just asked to erase, and the capsule itself may
     * still be sitting in the public plaza. Immediately delist it and redact those derivatives —
     * markNeedsReview then withdraws the active Genome version so runtime chat can no longer
     * read the old compiled prompt either.
     */
    private void withdrawCapsuleForForgottenMemory(Long userId, Long forgottenMemoryId, Long capsuleId) {
        capsuleMapper.update(null, new UpdateWrapper<EchoCapsule>()
                .eq("id", capsuleId)
                .set("visibility_status", "NEEDS_REVIEW")
                .set("is_public", false)
                .set("style_profile_json", null)
                .set("context_preview_json", null));
        genomeService.markNeedsReview(capsuleId, "source-authorized memory forgotten by owner");
        // The capsule's compiled matching vector is a derivative of the very memory being erased.
        // Delete it now (not on the next rebuild) so a forgotten memory cannot keep steering
        // discovery through a stale public-text embedding, and record an auditable receipt.
        int erased = capsuleEmbeddingIndexService.retireForCapsule(capsuleId);
        retractionReceiptService.record(userId, DataRetractionReceiptService.SUBJECT_MEMORY,
                forgottenMemoryId, DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX,
                DataRetractionReceiptService.ACTION_ERASED, erased,
                "source-authorized memory forgotten by owner");
    }

    private void invalidateEmbeddings(List<MemoryCard> source, String operation) {
        if (source.isEmpty() || Set.of("ADD", "LINK", "NO_OP", "FORGET").contains(operation)) return;
        List<com.innercosmos.entity.MemoryEmbedding> rows = embeddingMapper.selectList(
                new QueryWrapper<com.innercosmos.entity.MemoryEmbedding>().in("memory_id", ids(source))
                        .eq("user_id", source.get(0).userId).eq("status", "ACTIVE"));
        for (com.innercosmos.entity.MemoryEmbedding row : rows) {
            row.status = "STALE"; embeddingMapper.updateById(row);
        }
    }

    private void markAuthorizationsForReview(List<MemoryCard> source, String operation) {
        if (source.isEmpty() || Set.of("NO_OP", "LINK", "REINFORCE", "DECAY").contains(operation)) return;
        List<AuthorizedMemoryRef> refs = authorizedMemoryRefMapper.selectList(
                new QueryWrapper<AuthorizedMemoryRef>().in("memory_card_id", ids(source)));
        for (AuthorizedMemoryRef ref : refs) { ref.authorizationStatus = "NEEDS_REVIEW"; authorizedMemoryRefMapper.updateById(ref); }
        for (MemoryCard card : source) dataUseGrantService.revokeForMemory(card.id, "memory operation requires renewed consent: " + operation);
    }

    private List<MemoryProjectionReceipt> projectionReceipts(Long userId, MemoryOperation operation,
                                                              String operationType, int affectedCount) {
        if ("NO_OP".equals(operationType)) return List.of();
        List<MemoryProjectionReceipt> result = new ArrayList<>();
        result.add(receipt(userId, operation.id, "AURORA_RETRIEVAL", "REBUILT", 1,
                "权威库按查询即时装配；" + affectedCount + " 条记忆已进入新一代 Evidence Pack"));
        result.add(receipt(userId, operation.id, "STARFIELD", "REBUILT", 1,
                "时间、主题、人物投影从当前权威状态重新生成"));
        result.add(receipt(userId, operation.id, "CAPSULE_CONTEXT", "REVIEW_REQUIRED", 1,
                "公开共鸣体不静默改写；受影响授权必须重新确认"));
        return result;
    }

    private MemoryProjectionReceipt receipt(Long userId, Long operationId, String projection,
                                              String status, int generation, String detail) {
        MemoryProjectionReceipt row = new MemoryProjectionReceipt();
        row.userId = userId; row.operationId = operationId; row.projectionType = projection;
        row.status = status; row.generation = generation; row.detail = detail;
        projectionReceiptMapper.insert(row); return row;
    }

    private List<MemoryCard> parseCards(String json) {
        if (json == null || json.isBlank() || !json.trim().startsWith("[")) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<MemoryCard>>() {}); }
        catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.BAD_REQUEST, "这次变更缺少可回退的版本快照"); }
    }

    private MemoryCard restore(Long userId, MemoryCard old) {
        MemoryCard current = owned(userId, old.id);
        int nextVersion = version(current) + 1;
        memoryMapper.update(null, new UpdateWrapper<MemoryCard>()
                .eq("id", old.id).eq("user_id", userId)
                .set("title", old.title).set("summary", old.summary).set("memory_type", old.memoryType)
                .set("emotion_tags", old.emotionTags).set("keyword_tags", old.keywordTags)
                .set("people_tags", old.peopleTags).set("intensity_score", old.intensityScore)
                .set("recurrence_count", old.recurrenceCount).set("user_importance", old.userImportance)
                .set("trigger_count", old.triggerCount).set("emotional_gravity", old.emotionalGravity)
                .set("last_touched_at", LocalDateTime.now()).set("visibility_level", old.visibilityLevel)
                .set("status", old.status).set("version_no", nextVersion).set("memory_layer", old.memoryLayer)
                .set("confidence", old.confidence).set("consent_scope", old.consentScope)
                .set("superseded_by_id", old.supersededById).set("provenance_refs", old.provenanceRefs)
                .set("archived_at", old.archivedAt).set("forgotten_at", old.forgottenAt));
        return memoryMapper.selectById(old.id);
    }

    private MemoryCard retireCreated(Long userId, Long id) {
        MemoryCard current = owned(userId, id);
        current.status = "ARCHIVED"; current.archivedAt = LocalDateTime.now();
        current.versionNo = version(current) + 1; memoryMapper.updateById(current); return current;
    }

    private MemoryCard owned(Long userId, Long id) {
        MemoryCard card = memoryMapper.selectOne(new QueryWrapper<MemoryCard>().eq("id", id).eq("user_id", userId));
        if (card == null) throw new BusinessException(ErrorCode.NOT_FOUND, "回退涉及的记忆已不存在");
        return card;
    }

    private void deactivateOperationLinks(Long userId, Set<Long> sourceIds, Set<Long> createdIds) {
        if (sourceIds.isEmpty() || createdIds.isEmpty()) return;
        List<MemoryLink> links = linkMapper.selectList(new QueryWrapper<MemoryLink>().eq("user_id", userId)
                .and(q -> q.in("source_memory_id", sourceIds).in("target_memory_id", createdIds)
                        .or().in("source_memory_id", createdIds).in("target_memory_id", sourceIds)));
        for (MemoryLink link : links) { link.status = "ROLLED_BACK"; linkMapper.updateById(link); }
    }

    private MemoryOperation record(Long userId, MemoryOperationCommand command, List<MemoryCard> source,
                                   int oldVersion, String before, String after) {
        MemoryOperation row = new MemoryOperation();
        row.userId = userId; row.operationType = command.operationType();
        row.primaryMemoryId = command.primaryMemoryId(); row.relatedMemoryIds = csv(ids(source));
        row.oldVersion = oldVersion;
        row.newVersion = row.oldVersion + ("NO_OP".equals(command.operationType()) ? 0 : 1);
        row.beforeSnapshot = before; row.afterSnapshot = after; row.evidenceRefs = command.evidenceRefs();
        row.modelName = "none:user-directed"; row.promptVersion = "memory-policy.v1";
        row.reasonCode = command.reason() == null ? "USER_REQUEST" : command.reason();
        row.confidence = confidence(command); row.actorType = "USER"; row.status = "APPLIED";
        operationMapper.insert(row); return row;
    }

    private List<MemoryCard> sources(Long userId, MemoryOperationCommand command) {
        LinkedHashSet<Long> requested = new LinkedHashSet<>();
        if (command.primaryMemoryId() != null) requested.add(command.primaryMemoryId());
        if (command.relatedMemoryIds() != null) requested.addAll(command.relatedMemoryIds());
        if (requested.isEmpty()) return List.of();
        List<MemoryCard> rows = memoryMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).in("id", requested));
        if (rows.size() != requested.size()) throw new BusinessException(ErrorCode.NOT_FOUND, "有记忆不存在或不属于你");
        List<MemoryCard> ordered = new ArrayList<>();
        for (Long id : requested) rows.stream().filter(m -> id.equals(m.id)).findFirst().ifPresent(ordered::add);
        return ordered;
    }

    private MemoryOperationCommand normalize(MemoryOperationCommand raw) {
        if (raw == null || raw.operationType() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择记忆操作");
        String op = raw.operationType().trim().toUpperCase(Locale.ROOT);
        if (!OPERATIONS.contains(op)) throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的记忆操作: " + op);
        if (!"ADD".equals(op) && raw.primaryMemoryId() == null)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要处理的记忆");
        return new MemoryOperationCommand(op, raw.primaryMemoryId(), raw.relatedMemoryIds(), trim(raw.title()),
                trim(raw.summary()), raw.splitParts(), trim(raw.reason()), raw.confidence(), trim(raw.evidenceRefs()));
    }

    private String snapshot(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("无法记录记忆版本快照", e); }
    }
    private void link(Long userId, Long source, Long target, String type, double strength, String evidence) {
        MemoryLink row = new MemoryLink(); row.userId = userId; row.sourceMemoryId = source; row.targetMemoryId = target;
        row.linkType = type; row.strength = strength; row.evidenceRefs = evidence; row.status = "ACTIVE"; linkMapper.insert(row);
    }
    private static String commonLayer(List<MemoryCard> rows) { return rows.stream().map(m -> m.memoryLayer).filter(v -> v != null).findFirst().orElse("SEMANTIC"); }
    private static MemoryCard one(List<MemoryCard> rows) { if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "找不到这条记忆"); return rows.get(0); }
    private static List<Long> ids(List<MemoryCard> rows) { return rows.stream().map(m -> m.id).toList(); }
    private static String csv(List<Long> ids) { return "," + String.join(",", ids.stream().map(String::valueOf).toList()) + ","; }
    private static int version(MemoryCard card) { return card.versionNo == null ? 1 : card.versionNo; }
    private static int value(Integer number) { return number == null ? 0 : number; }
    private static double value(Double number) { return number == null ? 0 : number; }
    private static double confidence(MemoryOperationCommand command) { return command.confidence() == null ? 1.0 : Math.max(0, Math.min(1, command.confidence())); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String required(String value, String message) { if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, message); return value; }
    private static String authorityAction(String op) { return switch (op) {
        case "MERGE" -> "合并为一个新版本，来源保留为已替代";
        case "SPLIT" -> "拆成多个独立版本，来源保留为已替代";
        case "FORGET" -> "清除内容并留下不含原文的最小审计墓碑";
        case "CONTRADICT" -> "保留冲突证据并停止作为当前事实使用";
        default -> "记录操作、版本、理由、证据与执行者";
    }; }
}

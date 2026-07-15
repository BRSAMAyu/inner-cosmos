package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryLink;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.event.CapsuleSyncTriggerEvent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryLinkMapper;
import com.innercosmos.mapper.MemoryOperationMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
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
    private final MemoryLinkMapper linkMapper;
    private final AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    private final ThoughtFragmentMapper thoughtFragmentMapper;
    private final TodoItemMapper todoItemMapper;
    private final RelationMentionMapper relationMentionMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public MemoryLifecycleServiceImpl(MemoryCardMapper memoryMapper,
                                      MemoryOperationMapper operationMapper,
                                      MemoryLinkMapper linkMapper,
                                      AuthorizedMemoryRefMapper authorizedMemoryRefMapper,
                                      ThoughtFragmentMapper thoughtFragmentMapper,
                                      TodoItemMapper todoItemMapper,
                                      RelationMentionMapper relationMentionMapper,
                                      ObjectMapper objectMapper,
                                      ApplicationEventPublisher eventPublisher) {
        this.memoryMapper = memoryMapper;
        this.operationMapper = operationMapper;
        this.linkMapper = linkMapper;
        this.authorizedMemoryRefMapper = authorizedMemoryRefMapper;
        this.thoughtFragmentMapper = thoughtFragmentMapper;
        this.todoItemMapper = todoItemMapper;
        this.relationMentionMapper = relationMentionMapper;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
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
        MemoryOperation operation = record(userId, command, source, oldVersion, before,
                "FORGET".equals(command.operationType()) ? "{\"forgotten\":true}" : snapshot(result));
        if (!"NO_OP".equals(command.operationType())) eventPublisher.publishEvent(new CapsuleSyncTriggerEvent(userId));
        return new MemoryOperationResultVO(operation, result);
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
        authorizedMemoryRefMapper.delete(new QueryWrapper<AuthorizedMemoryRef>().eq("memory_card_id", card.id));
        thoughtFragmentMapper.delete(new QueryWrapper<com.innercosmos.entity.ThoughtFragment>().eq("memory_card_id", card.id));
        todoItemMapper.delete(new QueryWrapper<com.innercosmos.entity.TodoItem>().eq("source_memory_card_id", card.id));
        relationMentionMapper.delete(new QueryWrapper<com.innercosmos.entity.RelationMention>().eq("memory_card_id", card.id));
        linkMapper.delete(new QueryWrapper<MemoryLink>().eq("user_id", card.userId)
                .and(q -> q.eq("source_memory_id", card.id).or().eq("target_memory_id", card.id)));
    }

    private void markAuthorizationsForReview(List<MemoryCard> source, String operation) {
        if (source.isEmpty() || Set.of("NO_OP", "LINK", "REINFORCE", "DECAY").contains(operation)) return;
        List<AuthorizedMemoryRef> refs = authorizedMemoryRefMapper.selectList(
                new QueryWrapper<AuthorizedMemoryRef>().in("memory_card_id", ids(source)));
        for (AuthorizedMemoryRef ref : refs) { ref.authorizationStatus = "NEEDS_REVIEW"; authorizedMemoryRefMapper.updateById(ref); }
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

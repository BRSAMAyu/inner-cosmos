package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.claim.ClaimCandidate;
import com.innercosmos.ai.claim.ClaimConfidenceDecayPolicy;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.CorrectionCommand;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.service.ClaimCandidateService;
import com.innercosmos.service.ClaimExtractionService;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.UserCorrectionService;
import com.innercosmos.vo.ClaimCandidateVO;
import com.innercosmos.vo.CorrectionConfirmationVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence + lifecycle for automatic claim candidates. Candidates are stored in
 * {@code tb_understanding_claim} with {@code status=CANDIDATE} and {@code sourceType=AUTO_EXTRACTION}
 * so they coexist with (but are never mistaken for) authoritative {@code ACTIVE} claims. Promotion to
 * ACTIVE always goes through {@link UserCorrectionService#confirm} so the confirmed claim inherits
 * impact preview and downstream propagation, honoring the authority rule
 * ({@code 用户确认 > ... > 单次明确表达}).
 */
@Service
public class ClaimCandidateServiceImpl implements ClaimCandidateService {

    private static final Logger log = LoggerFactory.getLogger(ClaimCandidateServiceImpl.class);
    static final String STATUS_CANDIDATE = "CANDIDATE";
    static final String STATUS_CONFIRMED = "CONFIRMED";
    static final String STATUS_DISMISSED = "DISMISSED";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final String SOURCE_AUTO = "AUTO_EXTRACTION";

    private final DialogService dialogService;
    private final DialogMessageMapper messageMapper;
    private final ClaimExtractionService extractionService;
    private final UnderstandingClaimMapper claimMapper;
    private final UserCorrectionService correctionService;
    private final ObjectMapper objectMapper;

    public ClaimCandidateServiceImpl(DialogService dialogService, DialogMessageMapper messageMapper,
                                     ClaimExtractionService extractionService,
                                     UnderstandingClaimMapper claimMapper,
                                     UserCorrectionService correctionService, ObjectMapper objectMapper) {
        this.dialogService = dialogService;
        this.messageMapper = messageMapper;
        this.extractionService = extractionService;
        this.claimMapper = claimMapper;
        this.correctionService = correctionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int stageForSession(Long userId, Long sessionId) {
        // IDOR: messages are keyed by session_id only, so verify ownership before reading anything.
        dialogService.verifyOwnership(userId, sessionId);
        List<DialogMessage> messages = messageMapper.selectList(new QueryWrapper<DialogMessage>()
                .eq("session_id", sessionId).orderByAsc("id"));
        List<ClaimCandidate> candidates = extractionService.extract(userId, messages);
        int staged = 0;
        for (ClaimCandidate candidate : candidates) {
            if (upsert(userId, sessionId, candidate)) staged++;
        }
        return staged;
    }

    private boolean upsert(Long userId, Long sessionId, ClaimCandidate candidate) {
        String key = candidate.claimKey();
        String valueJson = encodeValue(candidate);
        String evidenceRefs = encodeIds(candidate.provenanceMessageIds());
        UnderstandingClaim existing = claimMapper.selectOne(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("claim_key", key).eq("status", STATUS_CANDIDATE)
                .orderByDesc("version").last("LIMIT 1"));
        if (existing != null) {
            existing.valueJson = valueJson;
            existing.confidence = candidate.confidence();
            existing.authorityLevel = candidate.authorityLevel();
            existing.evidenceRefs = evidenceRefs;
            existing.sourceId = sessionId;
            claimMapper.updateById(existing);
            return true;
        }
        UnderstandingClaim highest = claimMapper.selectOne(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("claim_key", key)
                .orderByDesc("version").last("LIMIT 1"));
        UnderstandingClaim claim = new UnderstandingClaim();
        claim.userId = userId;
        claim.claimKey = key;
        claim.claimType = candidate.claimType();
        claim.valueJson = valueJson;
        claim.authorityLevel = candidate.authorityLevel();
        claim.confidence = candidate.confidence();
        claim.status = STATUS_CANDIDATE;
        claim.sourceType = SOURCE_AUTO;
        claim.sourceId = sessionId;
        claim.version = highest == null ? 1 : highest.version + 1;
        claim.evidenceRefs = evidenceRefs;
        claimMapper.insert(claim);
        return true;
    }

    /**
     * Track A / A2 — {@code confidence} is shown at its time-decayed value, and a row whose belief
     * has decayed below {@link ClaimConfidenceDecayPolicy#DISMISS_THRESHOLD} is dismissed here (lazy,
     * per-user, on read) rather than left to linger with a stale-but-displayed number. The stored
     * {@code confidence} column itself is never overwritten by decay — only read-time display and the
     * stale/dismiss decision use the decayed value — so the original evidence-based confidence
     * remains available as provenance. See {@link #sweepStaleCandidates(int)} for the
     * scheduler-driven equivalent that also reaches candidates the owner never revisits.
     */
    @Override
    public List<ClaimCandidateVO> listCandidates(Long userId) {
        List<UnderstandingClaim> rows = claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("status", STATUS_CANDIDATE).orderByDesc("id"));
        LocalDateTime now = LocalDateTime.now();
        List<ClaimCandidateVO> out = new ArrayList<>();
        for (UnderstandingClaim row : rows) {
            double base = row.confidence == null ? 0.0 : row.confidence;
            LocalDateTime reference = row.updatedAt != null ? row.updatedAt : row.createdAt;
            double effective = ClaimConfidenceDecayPolicy.effectiveConfidence(base, row.authorityLevel, reference, now);
            if (ClaimConfidenceDecayPolicy.isStale(effective)) {
                row.status = STATUS_DISMISSED;
                claimMapper.updateById(row);
                log.debug("Auto-dismissed stale claim candidate {} for user {} (effective confidence {})",
                        row.id, userId, effective);
                continue;
            }
            JsonNode value = readTree(row.valueJson);
            boolean alreadyActive = claimMapper.selectCount(new QueryWrapper<UnderstandingClaim>()
                    .eq("user_id", userId).eq("claim_key", row.claimKey).eq("status", STATUS_ACTIVE)) > 0;
            out.add(new ClaimCandidateVO(row.id, row.claimType,
                    value.path("value").asText(""), row.authorityLevel,
                    effective,
                    decodeIds(value.path("provenanceMessageIds")),
                    value.path("evidenceText").asText(""),
                    value.path("uncertain").asBoolean(false),
                    alreadyActive,
                    row.createdAt == null ? null : row.createdAt.toString()));
        }
        return out;
    }

    /**
     * Track A / A2 — global batch sweep for candidates the owner never revisits (so decay is a real
     * background process, not only a side-effect of opening the review list). Scoped to
     * {@code status=CANDIDATE, sourceType=AUTO_EXTRACTION}; ACTIVE/confirmed claims (explicit user
     * assertions) are never selected, matching {@link ClaimConfidenceDecayPolicy#neverDecays}.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sweepStaleCandidates(int batchSize) {
        int limit = Math.max(1, batchSize);
        List<UnderstandingClaim> rows = claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                .eq("status", STATUS_CANDIDATE).eq("source_type", SOURCE_AUTO)
                .orderByAsc("id").last("LIMIT " + limit));
        LocalDateTime now = LocalDateTime.now();
        int dismissed = 0;
        for (UnderstandingClaim row : rows) {
            double base = row.confidence == null ? 0.0 : row.confidence;
            LocalDateTime reference = row.updatedAt != null ? row.updatedAt : row.createdAt;
            double effective = ClaimConfidenceDecayPolicy.effectiveConfidence(base, row.authorityLevel, reference, now);
            if (ClaimConfidenceDecayPolicy.isStale(effective)) {
                row.status = STATUS_DISMISSED;
                claimMapper.updateById(row);
                dismissed++;
            }
        }
        return dismissed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CorrectionConfirmationVO confirmCandidate(Long userId, Long candidateId) {
        UnderstandingClaim candidate = requireOwnedCandidate(userId, candidateId);
        JsonNode value = readTree(candidate.valueJson);
        String newValue = value.path("value").asText("");
        if (newValue.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "候选理解缺少内容");
        }
        // Promote through the correction path so the ACTIVE claim gets impact preview + propagation.
        // fieldName carries the auto claim key so each distinct candidate becomes its own ACTIVE claim.
        CorrectionCommand command = new CorrectionCommand("AURORA_UNDERSTANDING", 0L,
                candidate.claimKey, null, newValue, "确认 Aurora 自动理解：" + candidate.claimType);
        CorrectionConfirmationVO result = correctionService.confirm(userId, command);
        candidate.status = STATUS_CONFIRMED;
        claimMapper.updateById(candidate);
        log.debug("Promoted auto claim candidate {} to ACTIVE for user {}", candidateId, userId);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dismissCandidate(Long userId, Long candidateId) {
        UnderstandingClaim candidate = requireOwnedCandidate(userId, candidateId);
        candidate.status = STATUS_DISMISSED;
        claimMapper.updateById(candidate);
    }

    private UnderstandingClaim requireOwnedCandidate(Long userId, Long candidateId) {
        UnderstandingClaim candidate = candidateId == null ? null : claimMapper.selectById(candidateId);
        if (candidate == null || !userId.equals(candidate.userId)
                || !STATUS_CANDIDATE.equals(candidate.status)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "候选理解不存在");
        }
        return candidate;
    }

    private String encodeValue(ClaimCandidate candidate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("value", candidate.value());
        value.put("evidenceText", candidate.evidenceText());
        value.put("provenanceMessageIds", candidate.provenanceMessageIds());
        value.put("uncertain", candidate.uncertain());
        value.put("confidence", candidate.confidence());
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("candidate value encode failed", e);
        }
    }

    private String encodeIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids == null ? List.of() : ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private List<Long> decodeIds(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        if (array != null && array.isArray()) array.forEach(n -> ids.add(n.asLong()));
        return ids;
    }
}

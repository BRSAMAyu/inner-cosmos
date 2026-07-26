package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.claim.ClaimTypes;
import com.innercosmos.entity.UnderstandingClaim;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.mapper.UnderstandingClaimMapper;
import com.innercosmos.service.DataMaskingService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Materializes explicitly selected, ACTIVE understanding claims into an immutable Genome layer. */
@Component
public class CapsulePersonaLayerCompiler {

    private static final Set<String> SNAPSHOT_TYPES = Set.of(
            ClaimTypes.EXPRESSION_STYLE, ClaimTypes.BOUNDARY,
            ClaimTypes.VALUE, ClaimTypes.NEED, ClaimTypes.PREFERENCE,
            ClaimTypes.EMOTION_PATTERN);

    private final UnderstandingClaimMapper claimMapper;
    private final DataMaskingService maskingService;
    private final CapsuleThirdPartyAnonymizer anonymizer;
    private final ObjectMapper objectMapper;

    public CapsulePersonaLayerCompiler(UnderstandingClaimMapper claimMapper,
                                       DataMaskingService maskingService,
                                       CapsuleThirdPartyAnonymizer anonymizer,
                                       ObjectMapper objectMapper) {
        this.claimMapper = claimMapper;
        this.maskingService = maskingService;
        this.anonymizer = anonymizer;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> compile(Long userId, List<Long> requestedClaimIds, String privacyLevel) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>(requestedClaimIds == null ? List.of() : requestedClaimIds);
        ids.remove(null);
        if (ids.isEmpty()) return List.of();
        List<UnderstandingClaim> claims = claimMapper.selectList(new QueryWrapper<UnderstandingClaim>()
                .eq("user_id", userId).eq("status", "ACTIVE").in("id", ids));
        if (claims.size() != ids.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "人格授权包含不存在、已撤回或非本人的理解条目");
        }
        Map<Long, UnderstandingClaim> byId = new LinkedHashMap<>();
        claims.forEach(claim -> byId.put(claim.id, claim));
        CapsuleThirdPartyAnonymizer.Session aliases = anonymizer.beginSnapshot();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long id : ids) {
            UnderstandingClaim claim = byId.get(id);
            if (!SNAPSHOT_TYPES.contains(claim.claimType)) continue;
            String value = aliases.anonymize(maskingService.maskText(valueOf(claim.valueJson), privacyLevel));
            if (value.isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("claimId", claim.id);
            item.put("claimType", claim.claimType);
            item.put("capsuleSafeValue", value);
            item.put("confidence", claim.confidence == null ? 1.0 : claim.confidence);
            item.put("evidenceRefs", evidenceIds(claim.evidenceRefs));
            item.put("scope", ClaimTypes.EMOTION_PATTERN.equals(claim.claimType)
                    ? "NOT_A_DIAGNOSIS" : "USER_CONFIRMED_SELF_DESCRIPTION");
            if (ClaimTypes.EMOTION_PATTERN.equals(claim.claimType)) {
                item.put("temporalQualifier", "最近这段时间");
            }
            out.add(item);
        }
        return List.copyOf(out);
    }

    public List<Long> claimIdsFromPreview(String contextPreviewJson) {
        if (contextPreviewJson == null || contextPreviewJson.isBlank()) return List.of();
        try {
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : objectMapper.readTree(contextPreviewJson).path("personaLayer")) {
                if (item.hasNonNull("claimId")) ids.add(item.path("claimId").asLong());
            }
            return ids;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public String attach(String contextPreviewJson, List<Map<String, Object>> personaLayer) {
        try {
            JsonNode parsed = objectMapper.readTree(
                    contextPreviewJson == null || contextPreviewJson.isBlank() ? "{}" : contextPreviewJson);
            var root = parsed.isObject() ? (com.fasterxml.jackson.databind.node.ObjectNode) parsed
                    : objectMapper.createObjectNode();
            root.set("personaLayer", objectMapper.valueToTree(personaLayer == null ? List.of() : personaLayer));
            return objectMapper.writeValueAsString(root);
        } catch (Exception malformed) {
            throw new IllegalStateException("Unable to attach persona layer", malformed);
        }
    }

    private String valueOf(String json) {
        try {
            JsonNode value = objectMapper.readTree(json == null ? "\"\"" : json);
            if (value.isTextual()) return value.asText();
            if (value.isObject()) return value.path("value").asText("");
            return "";
        } catch (Exception ignored) {
            return json == null ? "" : json;
        }
    }

    private List<Long> evidenceIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Long> ids = new ArrayList<>();
            JsonNode value = objectMapper.readTree(json);
            if (value.isArray()) value.forEach(node -> {
                if (node.canConvertToLong()) ids.add(node.asLong());
            });
            return ids;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}

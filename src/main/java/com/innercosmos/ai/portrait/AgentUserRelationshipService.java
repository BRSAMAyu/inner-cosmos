package com.innercosmos.ai.portrait;

import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.mapper.AgentUserRelationshipMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentUserRelationshipService {
    @Autowired
    private AgentUserRelationshipMapper mapper;

    /**
     * Human-readable label for a relationship stage, used by the reply prompt
     * (VS-004) so Aurora can sense how close the relationship is. Falls back to
     * the raw stage string if it is not a known value — never returns null.
     */
    public static String stageLabel(String stage) {
        if (stage == null || stage.isBlank()) return "刚认识";
        return switch (stage) {
            case "new_user" -> "刚认识";
            case "acquaintance" -> "渐渐熟悉";
            case "companion" -> "稳定的陪伴";
            case "close_friend" -> "亲近的朋友";
            case "trusted_confidant" -> "值得托付的知己";
            default -> stage;
        };
    }

    @Transactional
    public AgentUserRelationship getOrInit(Long userId) {
        AgentUserRelationship r = mapper.selectOne(new QueryWrapper<AgentUserRelationship>()
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (r == null) {
            r = new AgentUserRelationship();
            r.userId = userId;
            r.relationshipStage = "new_user";
            r.intimacyLevel = 0;
            r.trustLevel = 0;
            r.familiarityLevel = 0;
            r.userDisclosureLevel = 0;
            r.auroraRoleInUserLife = "[\"assistant\"]";
            r.preferredAddressing = "你";
            try {
                mapper.insert(r);
            } catch (DuplicateKeyException ignored) {
                r = mapper.selectOne(new QueryWrapper<AgentUserRelationship>()
                        .eq("user_id", userId)
                        .last("LIMIT 1"));
            }
        }
        return r;
    }
}

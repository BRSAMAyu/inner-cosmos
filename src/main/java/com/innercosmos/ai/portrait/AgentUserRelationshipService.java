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

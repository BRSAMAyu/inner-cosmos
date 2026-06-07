package com.innercosmos.ai.portrait;

import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.mapper.AgentUserRelationshipMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentUserRelationshipService {
    @Autowired
    private AgentUserRelationshipMapper mapper;

    @Transactional
    public AgentUserRelationship getOrInit(Long userId) {
        AgentUserRelationship r = mapper.selectById(userId);
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
            mapper.insert(r);
        }
        return r;
    }
}
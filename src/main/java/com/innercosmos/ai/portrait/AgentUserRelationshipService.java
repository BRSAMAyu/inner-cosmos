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
            r.setUserId(userId);
            r.setRelationshipStage("new_user");
            r.setIntimacyLevel(0);
            r.setTrustLevel(0);
            r.setFamiliarityLevel(0);
            r.setUserDisclosureLevel(0);
            r.setAuroraRoleInUserLife("[\"assistant\"]");
            r.setPreferredAddressing("你");
            mapper.insert(r);
        }
        return r;
    }
}
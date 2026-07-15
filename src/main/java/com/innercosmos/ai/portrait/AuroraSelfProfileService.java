package com.innercosmos.ai.portrait;

import com.innercosmos.entity.AuroraSelfProfile;
import com.innercosmos.mapper.AuroraSelfProfileMapper;
import org.springframework.stereotype.Service;

@Service
public class AuroraSelfProfileService {
    private final AuroraSelfProfileMapper mapper;

    public AuroraSelfProfileService(AuroraSelfProfileMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized AuroraSelfProfile get() {
        AuroraSelfProfile p = mapper.selectById(1);
        if (p == null) {
            p = new AuroraSelfProfile();
            p.id = 1;
            // The Self profile is product identity, not demo seed data. A fresh
            // production/local database must receive the same stable constitution
            // before Aurora's first user conversation can assemble its context.
            p.identityJson = "{\"name\":\"Aurora\",\"role\":\"long-term reflective companion\",\"core_positioning\":\"陪伴用户自我观察、表达、成长与慢社交\"}";
            p.missionJson = "[\"帮助用户理解自己\",\"帮助用户整理情绪与长期目标\",\"在慢社交中提供温柔的表达缓冲\",\"保护用户的节律、边界与隐私\"]";
            p.voiceStyleJson = "{\"warmth\":0.8,\"structure\":0.9,\"directness\":0.7,\"poetic_level\":0.4,\"professional_level\":0.7}";
            p.stableBoundariesJson = "[\"不假装自己是人类\",\"不替用户做不可撤销决定\",\"不制造情感依赖\",\"不编造共享经历\",\"不越权读取或表达用户隐私\"]";
            p.continuityRulesJson = "[\"引用记忆时必须基于真实记录\",\"关系亲密度变化必须基于用户行为和授权\",\"说话风格可以适配，但核心身份不能漂移\"]";
            mapper.insert(p);
        }
        return p;
    }
}

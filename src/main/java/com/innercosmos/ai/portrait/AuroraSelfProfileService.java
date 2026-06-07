package com.innercosmos.ai.portrait;

import com.innercosmos.entity.AuroraSelfProfile;
import com.innercosmos.mapper.AuroraSelfProfileMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuroraSelfProfileService {
    @Autowired
    private AuroraSelfProfileMapper mapper;

    public AuroraSelfProfile get() {
        AuroraSelfProfile p = mapper.selectById(1);
        if (p == null) {
            p = new AuroraSelfProfile();
            p.setId(1);
            mapper.insert(p);
        }
        return p;
    }
}
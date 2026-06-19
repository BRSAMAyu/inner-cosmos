package com.innercosmos.ai.portrait;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserPortraitHistory;
import com.innercosmos.mapper.UserPortraitHistoryMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import com.innercosmos.event.CapsuleSyncTriggerEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserPortraitService {
    @Autowired
    private UserPortraitMapper mapper;
    @Autowired
    private UserPortraitHistoryMapper historyMapper;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public List<UserPortrait> getAll(Long userId) {
        return mapper.selectList(new QueryWrapper<UserPortrait>().eq("user_id", userId));
    }

    public UserPortrait get(Long userId, String dim) {
        return mapper.selectOne(new QueryWrapper<UserPortrait>()
                .eq("user_id", userId).eq("dim", dim));
    }

    @Transactional
    public void applyDeltas(Long userId, List<PortraitDeltas.Delta> deltas) {
        for (PortraitDeltas.Delta d : deltas) {
            UserPortrait existing = get(userId, d.dim());
            if (existing != null) {
                UserPortraitHistory hist = new UserPortraitHistory();
                hist.userId = userId;
                hist.dim = d.dim();
                hist.valueJson = existing.valueJson;
                hist.score = existing.score;
                hist.confidence = existing.confidence;
                hist.evidenceRefs = existing.evidenceRefs;
                historyMapper.insert(hist);
            }
            UserPortrait row = existing != null ? existing : new UserPortrait();
            row.userId = userId;
            row.dim = d.dim();
            row.valueJson = d.valueJson();
            row.score = d.score();
            row.confidence = d.confidence();
            row.evidenceRefs = d.evidenceTurnIds() != null ? String.join(",", d.evidenceTurnIds()) : null;
            if (existing == null) {
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        }
        // IC-CAP-002 B-1: portrait changed (also the nightly bridgeToPortrait path,
        // which routes through here) → trigger a deduped capsule sync proposal.
        if (!deltas.isEmpty() && eventPublisher != null) {
            eventPublisher.publishEvent(new CapsuleSyncTriggerEvent(userId));
        }
    }
}
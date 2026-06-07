package com.innercosmos.ai.portrait;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserPortraitHistory;
import com.innercosmos.mapper.UserPortraitHistoryMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserPortraitService {
    @Autowired
    private UserPortraitMapper mapper;
    @Autowired
    private UserPortraitHistoryMapper historyMapper;

    public List<UserPortrait> getAll(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<UserPortrait>()
                .eq(UserPortrait::getUserId, userId));
    }

    public UserPortrait get(Long userId, String dim) {
        return mapper.selectOne(new LambdaQueryWrapper<UserPortrait>()
                .eq(UserPortrait::getUserId, userId)
                .eq(UserPortrait::getDim, dim));
    }

    @Transactional
    public void applyDeltas(Long userId, List<PortraitDeltas.Delta> deltas) {
        for (PortraitDeltas.Delta d : deltas) {
            UserPortrait existing = get(userId, d.dim());
            if (existing != null) {
                UserPortraitHistory hist = new UserPortraitHistory();
                hist.setUserId(userId);
                hist.setDim(d.dim());
                hist.setValueJson(existing.getValueJson());
                hist.setScore(existing.getScore());
                hist.setConfidence(existing.getConfidence());
                hist.setEvidenceRefs(existing.getEvidenceRefs());
                historyMapper.insert(hist);
            }
            UserPortrait row = existing != null ? existing : new UserPortrait();
            row.setUserId(userId);
            row.setDim(d.dim());
            row.setValueJson(d.valueJson());
            row.setScore(d.confidence());
            row.setConfidence(d.confidence());
            row.setEvidenceRefs(d.evidenceTurnIds() != null ? String.join(",", d.evidenceTurnIds()) : null);
            if (existing == null) {
                mapper.insert(row);
            } else {
                mapper.updateById(row);
            }
        }
    }
}
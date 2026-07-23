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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserPortraitService.class);

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

    /** Last 10 recorded history points for one portrait dimension, most recent first. */
    public List<UserPortraitHistory> getHistory(Long userId, String dim) {
        return historyMapper.selectList(new QueryWrapper<UserPortraitHistory>()
                .eq("user_id", userId)
                .eq("dim", dim)
                .orderByDesc("recorded_at")
                .last("LIMIT 10"));
    }

    @Transactional
    public void applyDeltas(Long userId, List<PortraitDeltas.Delta> deltas) {
        for (PortraitDeltas.Delta d : deltas) {
            // IC-DATA-003: data hygiene — the LLM is asked for valueJson + score/confidence
            // in [0,1] but nothing enforces it. Guard before persisting so garbage
            // (out-of-range / NaN / null valueJson against a NOT NULL column) never
            // reaches the DB and never silently kills the whole delta batch.
            if (d.valueJson() == null || d.valueJson().isBlank()) {
                // value_json is TEXT NOT NULL — a null/blank insert throws and the
                // async try/catch upstream would swallow the WHOLE batch. Skip instead.
                log.debug("Skipping portrait delta dim={} for user {}: blank valueJson", d.dim(), userId);
                continue;
            }
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
            // clamp NaN/infinite/out-of-range into [0,1]; NaN → 0.0 default
            row.score = clamp01(d.score());
            row.confidence = clamp01(d.confidence());
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

    /** Clamp a score/confidence into [0.0, 1.0]; NaN/infinite → 0.0 safe default. */
    private static double clamp01(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}
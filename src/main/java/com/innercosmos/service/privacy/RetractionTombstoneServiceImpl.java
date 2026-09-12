package com.innercosmos.service.privacy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.RetractionTombstone;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.RetractionTombstoneMapper;
import com.innercosmos.service.DataRetractionReceiptService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetractionTombstoneServiceImpl implements RetractionTombstoneService {

    private static final Logger log = LoggerFactory.getLogger(RetractionTombstoneServiceImpl.class);

    private final RetractionTombstoneMapper tombstoneMapper;
    private final MemoryCardMapper memoryCardMapper;

    public RetractionTombstoneServiceImpl(RetractionTombstoneMapper tombstoneMapper,
                                          MemoryCardMapper memoryCardMapper) {
        this.tombstoneMapper = tombstoneMapper;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long record(String subjectType, Long subjectId, Long ownerUserId,
                       String consentVersion, String reason) {
        RetractionTombstone existing = tombstoneMapper.selectOne(
                new QueryWrapper<RetractionTombstone>()
                        .eq("subject_type", subjectType).eq("subject_id", subjectId));
        if (existing != null) {
            return existing.id; // idempotent: one marker per subject, watermark never regresses
        }
        RetractionTombstone marker = new RetractionTombstone();
        marker.subjectType = subjectType;
        marker.subjectId = subjectId;
        marker.ownerUserId = ownerUserId;
        marker.consentVersion = consentVersion;
        marker.reason = reason;
        marker.appliedAt = LocalDateTime.now(ZoneOffset.UTC);
        tombstoneMapper.insert(marker);
        return marker.id;
    }

    @Override
    public boolean isBlocked(String subjectType, Long subjectId) {
        return tombstoneMapper.selectCount(new QueryWrapper<RetractionTombstone>()
                .eq("subject_type", subjectType).eq("subject_id", subjectId)) > 0;
    }

    @Override
    public Set<Long> blockedIds(String subjectType, Long ownerUserId) {
        QueryWrapper<RetractionTombstone> query = new QueryWrapper<RetractionTombstone>()
                .eq("subject_type", subjectType);
        if (ownerUserId != null) {
            query.eq("owner_user_id", ownerUserId);
        }
        Set<Long> ids = new HashSet<>();
        for (RetractionTombstone marker : tombstoneMapper.selectList(query)) {
            ids.add(marker.subjectId);
        }
        return ids;
    }

    @Override
    public long watermark() {
        RetractionTombstone latest = tombstoneMapper.selectOne(
                new QueryWrapper<RetractionTombstone>().orderByDesc("id").last("LIMIT 1"));
        return latest == null ? 0L : latest.id;
    }

    /**
     * Backup-restore catch-up: replay every tombstone above the proven watermark onto the
     * live business state. In this increment MEMORY rows are re-blocked (the memory read
     * path already refuses tombstoned ids); each new subject type extends its branch here.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int catchUpFromWatermark(long provenWatermark) {
        int replayed = 0;
        for (RetractionTombstone marker : tombstoneMapper.selectList(
                new QueryWrapper<RetractionTombstone>().gt("id", provenWatermark))) {
            replayed++;
            if (DataRetractionReceiptService.SUBJECT_MEMORY.equals(marker.subjectType)) {
                // A backup restore may have brought the card back as ACTIVE — re-block it
                // with the same FORGOTTEN semantics the memory-forget path uses.
                memoryCardMapper.update(null, new UpdateWrapper<MemoryCard>()
                        .eq("id", marker.subjectId)
                        .set("status", "FORGOTTEN")
                        .set("forgotten_at", LocalDateTime.now(ZoneOffset.UTC))
                        .set("updated_at", LocalDateTime.now(ZoneOffset.UTC)));
            }
            log.info("tombstone catch-up replayed {}:{} (watermark {})",
                    marker.subjectType, marker.subjectId, marker.id);
        }
        return replayed;
    }
}

package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DataRetractionReceipt;
import com.innercosmos.event.DataRetractedEvent;
import com.innercosmos.mapper.DataRetractionReceiptMapper;
import com.innercosmos.service.DataRetractionReceiptService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataRetractionReceiptServiceImpl implements DataRetractionReceiptService {

    private static final int MAX_REASON = 240;

    private final DataRetractionReceiptMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public DataRetractionReceiptServiceImpl(DataRetractionReceiptMapper mapper,
                                            ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public DataRetractionReceipt record(Long userId, String subjectType, Long subjectId,
                                        String derivativeType, String action, int affectedCount, String reason) {
        DataRetractionReceipt row = new DataRetractionReceipt();
        row.userId = userId;
        row.subjectType = subjectType;
        row.subjectId = subjectId;
        row.derivativeType = derivativeType;
        row.action = action;
        row.affectedCount = Math.max(0, affectedCount);
        row.reason = trim(reason);
        mapper.insert(row);
        // Fire a domain event so a durable, replayable data.retracted.v1 outbox row can be written in
        // this same transaction when the outbox is enabled. No listener when disabled -> no-op.
        eventPublisher.publishEvent(new DataRetractedEvent(row.id, userId, subjectType, subjectId,
                derivativeType, action, row.affectedCount));
        return row;
    }

    @Override
    public List<DataRetractionReceipt> listForOwner(Long userId, int limit) {
        int bounded = Math.max(1, Math.min(500, limit));
        return mapper.selectList(new QueryWrapper<DataRetractionReceipt>()
                .eq("user_id", userId)
                .orderByDesc("id")
                .last("LIMIT " + bounded));
    }

    private static String trim(String reason) {
        if (reason == null || reason.isBlank()) return "OWNER_RETRACTION";
        String cleaned = reason.strip();
        return cleaned.length() > MAX_REASON ? cleaned.substring(0, MAX_REASON) : cleaned;
    }
}

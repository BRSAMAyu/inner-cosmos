package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DataRetractionReceipt;
import com.innercosmos.mapper.DataRetractionReceiptMapper;
import com.innercosmos.service.DataRetractionReceiptService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DataRetractionReceiptServiceImpl implements DataRetractionReceiptService {

    private static final int MAX_REASON = 240;

    private final DataRetractionReceiptMapper mapper;

    public DataRetractionReceiptServiceImpl(DataRetractionReceiptMapper mapper) {
        this.mapper = mapper;
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

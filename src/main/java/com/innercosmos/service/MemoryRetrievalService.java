package com.innercosmos.service;

import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.vo.MemoryEvidencePackVO;

public interface MemoryRetrievalService {
    MemoryEvidencePackVO retrieve(Long userId, MemoryRetrievalQuery query);
}

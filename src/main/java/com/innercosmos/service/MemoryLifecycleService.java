package com.innercosmos.service;

import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.vo.MemoryOperationPreviewVO;
import com.innercosmos.vo.MemoryOperationResultVO;

import java.util.List;

public interface MemoryLifecycleService {
    MemoryOperationPreviewVO preview(Long userId, MemoryOperationCommand command);
    MemoryOperationResultVO execute(Long userId, MemoryOperationCommand command);
    MemoryOperationResultVO rollback(Long userId, Long operationId);
    List<MemoryOperation> history(Long userId, Long memoryId);
}

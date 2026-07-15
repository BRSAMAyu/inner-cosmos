package com.innercosmos.vo;

import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.entity.MemoryProjectionReceipt;

import java.util.List;

public record MemoryOperationResultVO(MemoryOperation operation, List<MemoryCard> memories,
                                      List<MemoryProjectionReceipt> projectionReceipts) {}

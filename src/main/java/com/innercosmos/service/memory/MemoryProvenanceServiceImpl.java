package com.innercosmos.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DataUseGrantMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.mapper.MemoryOperationMapper;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.memory.MemoryProvenanceService.DerivativeRow;
import com.innercosmos.service.memory.MemoryProvenanceService.OperationRow;
import com.innercosmos.service.memory.MemoryProvenanceService.ProvenanceReplay;
import com.innercosmos.service.privacy.RetractionTombstoneService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemoryProvenanceServiceImpl implements MemoryProvenanceService {

    private final MemoryCardMapper memoryMapper;
    private final MemoryOperationMapper operationMapper;
    private final DataUseGrantMapper grantMapper;
    private final MemoryEmbeddingMapper embeddingMapper;
    private final RetractionTombstoneService tombstoneService;

    public MemoryProvenanceServiceImpl(MemoryCardMapper memoryMapper,
                                       MemoryOperationMapper operationMapper,
                                       DataUseGrantMapper grantMapper,
                                       MemoryEmbeddingMapper embeddingMapper,
                                       RetractionTombstoneService tombstoneService) {
        this.memoryMapper = memoryMapper;
        this.operationMapper = operationMapper;
        this.grantMapper = grantMapper;
        this.embeddingMapper = embeddingMapper;
        this.tombstoneService = tombstoneService;
    }

    @Override
    public ProvenanceReplay replay(Long userId, Long memoryId) {
        if (tombstoneService.isBlocked(DataRetractionReceiptService.SUBJECT_MEMORY, memoryId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        MemoryCard card = memoryMapper.selectById(memoryId);
        if (card == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        if (!userId.equals(card.userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权访问该内容");
        }

        List<OperationRow> operations = new ArrayList<>();
        for (MemoryOperation operation : operationMapper.selectList(
                new QueryWrapper<MemoryOperation>()
                        .eq("primary_memory_id", memoryId).orderByAsc("id"))) {
            operations.add(new OperationRow(operation.operationType,
                    operation.oldVersion == null ? null : String.valueOf(operation.oldVersion),
                    operation.newVersion == null ? null : String.valueOf(operation.newVersion),
                    operation.modelName == null ? "USER" : operation.modelName,
                    operation.createdAt == null ? null : operation.createdAt.toString()));
        }

        List<DerivativeRow> derivatives = new ArrayList<>();
        for (DataUseGrant grant : grantMapper.selectList(new QueryWrapper<DataUseGrant>()
                .eq("resource_type", "MEMORY").eq("resource_id", memoryId))) {
            derivatives.add(new DerivativeRow("CAPSULE_COMPILATION", grant.consumerId,
                    grant.status, grant.grantVersion == null ? null : String.valueOf(grant.grantVersion),
                    grant.createdAt == null ? null : grant.createdAt.toString()));
        }
        for (MemoryEmbedding embedding : embeddingMapper.selectList(
                new QueryWrapper<MemoryEmbedding>().eq("memory_id", memoryId))) {
            derivatives.add(new DerivativeRow("RETRIEVAL_EMBEDDING", embedding.id,
                    "ACTIVE", embedding.modelVersion,
                    embedding.createdAt == null ? null : embedding.createdAt.toString()));
        }

        return new ProvenanceReplay(memoryId, card.sourceSessionId, card.status, card.versionNo,
                operations, derivatives,
                "来源图回放：对话会话 → 记忆卡（版本链）→ 授权共鸣体编译与检索向量。用户修改优先；"
                        + "撤回的记忆不可回放。");
    }
}

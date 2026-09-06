package com.innercosmos.service.privacy;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.identity.AccountSecurityService;
import com.innercosmos.service.minor.MinorProtectionService;
import org.springframework.stereotype.Service;

@Service
public class SensitiveDataBoundaryServiceImpl implements SensitiveDataBoundaryService {

    private final UserMapper userMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final RetractionTombstoneService tombstoneService;

    public SensitiveDataBoundaryServiceImpl(UserMapper userMapper,
                                            MemoryCardMapper memoryCardMapper,
                                            EchoCapsuleMapper capsuleMapper,
                                            RetractionTombstoneService tombstoneService) {
        this.userMapper = userMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.capsuleMapper = capsuleMapper;
        this.tombstoneService = tombstoneService;
    }

    @Override
    public void assertReadable(String subjectType, Long subjectId, Long requesterUserId,
                               Purpose purpose) {
        if (requesterUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "尚未登录");
        }
        // Requester state fails closed: frozen and minor-restricted accounts read nothing.
        User requester = userMapper.selectById(requesterUserId);
        if (requester == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "尚未登录");
        }
        if (AccountSecurityService.STATUS_FROZEN.equals(requester.status)
                || MinorProtectionService.STATUS_MINOR_RESTRICTED.equals(requester.status)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账户当前状态不可访问该内容");
        }

        if (DataRetractionReceiptService.SUBJECT_MEMORY.equals(subjectType)) {
            assertMemoryReadable(subjectId, requesterUserId);
        } else if (DataRetractionReceiptService.SUBJECT_CAPSULE.equals(subjectType)) {
            assertCapsuleReadable(subjectId, requesterUserId);
        } else {
            throw new BusinessException(ErrorCode.NOT_FOUND, "不支持的内容类型");
        }
    }

    private void assertMemoryReadable(Long memoryId, Long requesterUserId) {
        // Tombstone first: a backup-resurrected row must stay unreadable (CP-15).
        if (tombstoneService.isBlocked(DataRetractionReceiptService.SUBJECT_MEMORY, memoryId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        MemoryCard card = memoryCardMapper.selectById(memoryId);
        if (card == null || "FORGOTTEN".equals(card.status)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        if (!requesterUserId.equals(card.userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权访问该内容");
        }
    }

    private void assertCapsuleReadable(Long capsuleId, Long requesterUserId) {
        if (tombstoneService.isBlocked(DataRetractionReceiptService.SUBJECT_CAPSULE, capsuleId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        }
        if (!requesterUserId.equals(capsule.ownerUserId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "无权访问该内容");
        }
    }
}

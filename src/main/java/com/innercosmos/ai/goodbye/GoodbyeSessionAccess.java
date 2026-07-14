package com.innercosmos.ai.goodbye;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Central ownership and atomic-idempotency policy for Goodbye session entry. */
@Service
public class GoodbyeSessionAccess {

    public enum ClaimResult { CLAIMED, ALREADY_STARTED }

    private final DialogSessionMapper sessionMapper;

    public GoodbyeSessionAccess(DialogSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimResult claim(Long userId, Long sessionId, String trigger) {
        DialogSession session = ownedOrOpaqueNotFound(userId, sessionId);
        if (session.endedAt != null || hasText(session.goodbyeTrigger)
                || "FINISHED".equalsIgnoreCase(session.status)) {
            return ClaimResult.ALREADY_STARTED;
        }

        int updated = sessionMapper.update(null, new UpdateWrapper<DialogSession>()
                .eq("id", sessionId)
                .eq("user_id", userId)
                .isNull("ended_at")
                .isNull("goodbye_trigger")
                .ne("status", "FINISHED")
                .set("goodbye_trigger", trigger));
        if (updated == 1) {
            return ClaimResult.CLAIMED;
        }

        // A concurrent state transition won after the first read. Re-check ownership
        // without exposing whether an arbitrary ID exists.
        DialogSession current = ownedOrOpaqueNotFound(userId, sessionId);
        if (current.endedAt != null || hasText(current.goodbyeTrigger)
                || "FINISHED".equalsIgnoreCase(current.status)) {
            return ClaimResult.ALREADY_STARTED;
        }
        throw opaqueNotFound();
    }

    private DialogSession ownedOrOpaqueNotFound(Long userId, Long sessionId) {
        DialogSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null || userId == null || !userId.equals(session.userId)) {
            throw opaqueNotFound();
        }
        return session;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BusinessException opaqueNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "对话会话不存在或不可访问");
    }
}

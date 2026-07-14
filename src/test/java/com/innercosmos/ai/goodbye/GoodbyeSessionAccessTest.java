package com.innercosmos.ai.goodbye;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoodbyeSessionAccessTest {

    @Mock DialogSessionMapper sessionMapper;

    @Test
    void ownerClaimsSessionAtomically() {
        GoodbyeSessionAccess access = new GoodbyeSessionAccess(sessionMapper);
        when(sessionMapper.selectById(42L)).thenReturn(session(7L));
        when(sessionMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        assertThat(access.claim(7L, 42L, "BUTTON"))
                .isEqualTo(GoodbyeSessionAccess.ClaimResult.CLAIMED);
        verify(sessionMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void nonOwnerAndMissingUseSameOpaqueErrorAndNeverWrite() {
        GoodbyeSessionAccess access = new GoodbyeSessionAccess(sessionMapper);
        when(sessionMapper.selectById(42L)).thenReturn(session(8L));
        when(sessionMapper.selectById(99L)).thenReturn(null);

        BusinessException nonOwner = capture(() -> access.claim(7L, 42L, "BUTTON"));
        BusinessException missing = capture(() -> access.claim(7L, 99L, "BUTTON"));

        assertThat(nonOwner.code).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(missing.code).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(nonOwner.getMessage()).isEqualTo(missing.getMessage());
        verify(sessionMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void duplicateCallIsIdempotent() {
        GoodbyeSessionAccess access = new GoodbyeSessionAccess(sessionMapper);
        DialogSession session = session(7L);
        session.goodbyeTrigger = "BUTTON";
        when(sessionMapper.selectById(42L)).thenReturn(session);

        assertThat(access.claim(7L, 42L, "BUTTON"))
                .isEqualTo(GoodbyeSessionAccess.ClaimResult.ALREADY_STARTED);
        verify(sessionMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void concurrentFinishAfterReadReturnsIdempotentResult() {
        GoodbyeSessionAccess access = new GoodbyeSessionAccess(sessionMapper);
        DialogSession finished = session(7L);
        finished.status = "FINISHED";
        finished.endedAt = LocalDateTime.now();
        when(sessionMapper.selectById(42L)).thenReturn(session(7L), finished);
        when(sessionMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        assertThat(access.claim(7L, 42L, "BUTTON"))
                .isEqualTo(GoodbyeSessionAccess.ClaimResult.ALREADY_STARTED);
    }

    @Test
    void unexplainedConcurrentStateChangeFailsClosed() {
        GoodbyeSessionAccess access = new GoodbyeSessionAccess(sessionMapper);
        when(sessionMapper.selectById(42L)).thenReturn(session(7L), session(7L));
        when(sessionMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> access.claim(7L, 42L, "BUTTON"))
                .isInstanceOf(BusinessException.class);
    }

    private DialogSession session(Long owner) {
        DialogSession session = new DialogSession();
        session.id = 42L;
        session.userId = owner;
        session.status = "ACTIVE";
        return session;
    }

    private BusinessException capture(Runnable call) {
        try {
            call.run();
            throw new AssertionError("expected BusinessException");
        } catch (BusinessException exception) {
            return exception;
        }
    }
}

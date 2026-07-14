package com.innercosmos.ai.self;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.AuroraSelfReflection;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AuroraSelfModelMapper;
import com.innercosmos.mapper.AuroraSelfReflectionMapper;
import com.innercosmos.mapper.AuroraSelfStatementMapper;
import com.innercosmos.service.AuroraConstitutionService;
import com.innercosmos.service.impl.AuroraSelfContinuityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuroraSelfOwnershipTest {

    @Mock AuroraSelfStatementMapper statementMapper;
    @Mock AuroraSelfReflectionMapper reflectionMapper;
    @Mock AuroraSelfModelMapper modelMapper;
    @Mock AuroraConstitutionService constitutionService;
    @Mock LlmClient llm;
    AuroraSelfContinuityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuroraSelfContinuityServiceImpl(statementMapper, reflectionMapper,
                modelMapper, constitutionService, llm);
    }

    @Test
    void ownerCanCommitCandidateAndModelIsBoundToOwner() {
        when(reflectionMapper.selectById(42L)).thenReturn(candidate(7L));
        when(modelMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(modelMapper.insert(any(AuroraSelfModel.class))).thenReturn(1);
        when(reflectionMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.commitToModel(7L, 42L, true, List.of());

        ArgumentCaptor<AuroraSelfModel> inserted = ArgumentCaptor.forClass(AuroraSelfModel.class);
        verify(modelMapper).insert(inserted.capture());
        assertThat(inserted.getValue().userId).isEqualTo(7L);
        verify(reflectionMapper).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void nonOwnerMissingAndAdminUseSameOpaqueCommitErrorWithNoSideEffects() {
        when(reflectionMapper.selectById(42L)).thenReturn(candidate(8L));
        when(reflectionMapper.selectById(99L)).thenReturn(null);

        BusinessException nonOwner = capture(() -> service.commitToModel(7L, 42L, true, List.of()));
        BusinessException missing = capture(() -> service.commitToModel(7L, 99L, true, List.of()));
        BusinessException admin = capture(() -> service.commitToModel(1L, 42L, true, List.of()));

        assertOpaqueAndEqual(nonOwner, missing);
        assertOpaqueAndEqual(admin, missing);
        verifyNoInteractions(modelMapper, statementMapper, constitutionService, llm);
        verify(reflectionMapper, never()).update(any(), any(UpdateWrapper.class));
        verify(reflectionMapper, never()).updateById(any(AuroraSelfReflection.class));
    }

    @Test
    void ownerCanDismissAndDuplicateDismissIsIdempotent() {
        AuroraSelfReflection owned = candidate(7L);
        AuroraSelfReflection dismissed = candidate(7L);
        dismissed.status = "dismissed";
        when(reflectionMapper.selectById(42L)).thenReturn(owned, dismissed);
        when(reflectionMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.dismissCandidate(7L, 42L);
        service.dismissCandidate(7L, 42L);

        verify(reflectionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void nonOwnerCannotDismissCandidate() {
        when(reflectionMapper.selectById(42L)).thenReturn(candidate(8L));

        BusinessException error = capture(() -> service.dismissCandidate(7L, 42L));

        assertThat(error.code).isEqualTo(ErrorCode.NOT_FOUND);
        verify(reflectionMapper, never()).update(any(), any(UpdateWrapper.class));
        verifyNoInteractions(modelMapper, statementMapper, constitutionService, llm);
    }

    @Test
    void ownerCanRetireAndDuplicateRetireIsIdempotent() {
        AuroraSelfModel owned = model(7L, "active");
        AuroraSelfModel retired = model(7L, "retired");
        when(modelMapper.selectById(55L)).thenReturn(owned, retired);
        when(modelMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.retireModel(7L, 55L);
        service.retireModel(7L, 55L);

        verify(modelMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void nonOwnerCannotRetireModel() {
        when(modelMapper.selectById(55L)).thenReturn(model(8L, "active"));

        BusinessException error = capture(() -> service.retireModel(7L, 55L));

        assertThat(error.code).isEqualTo(ErrorCode.NOT_FOUND);
        verify(modelMapper, never()).update(any(), any(UpdateWrapper.class));
        verifyNoInteractions(reflectionMapper, statementMapper, constitutionService, llm);
    }

    @Test
    void concurrentCandidateStateChangeRollsBackCommit() {
        when(reflectionMapper.selectById(42L)).thenReturn(candidate(7L));
        when(modelMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(modelMapper.insert(any(AuroraSelfModel.class))).thenReturn(1);
        when(reflectionMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        BusinessException error = capture(() -> service.commitToModel(7L, 42L, true, List.of()));

        assertThat(error.code).isEqualTo(ErrorCode.NOT_FOUND);
    }

    private AuroraSelfReflection candidate(Long owner) {
        AuroraSelfReflection candidate = new AuroraSelfReflection();
        candidate.id = 42L;
        candidate.userId = owner;
        candidate.status = "candidate";
        candidate.dimension = "existence_style";
        candidate.proposedBelief = "Aurora should support reflection with clear boundaries";
        candidate.confidence = 0.8;
        return candidate;
    }

    private AuroraSelfModel model(Long owner, String status) {
        AuroraSelfModel model = new AuroraSelfModel();
        model.id = 55L;
        model.userId = owner;
        model.status = status;
        return model;
    }

    private BusinessException capture(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected BusinessException");
        } catch (BusinessException exception) {
            return exception;
        }
    }

    private void assertOpaqueAndEqual(BusinessException actual, BusinessException expected) {
        assertThat(actual.code).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
    }
}

package com.innercosmos.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.EchoCapsuleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockDataInitializerSandboxContractTest {

    @Test
    void copiedCapsulesAlwaysStartPrivateAndUnpublished() {
        EchoCapsuleMapper capsules = mock(EchoCapsuleMapper.class);
        EchoCapsule copied = new EchoCapsule();
        copied.id = 17L;
        copied.ownerUserId = 99L;
        copied.visibilityStatus = "PUBLIC";
        copied.isPublic = true;
        when(capsules.selectList(any(Wrapper.class))).thenReturn(List.of(copied));

        MockDataInitializer.privatizeSandboxCapsules(capsules, 99L);

        assertThat(copied.visibilityStatus).isEqualTo("PRIVATE");
        assertThat(copied.isPublic).isFalse();
        verify(capsules).updateById(copied);
    }
}

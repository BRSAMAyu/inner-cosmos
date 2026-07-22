package com.innercosmos.scheduler;

import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.GravityTimePolicy;
import com.innercosmos.service.MemorySettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * IC-CAP-002 B-4: nightly multiplicative decay toward floors.
 *   echoEnergy   = max(0.3, energy * 0.97)
 *   freshness    = max(0.0, freshness * 0.95)
 */
@ExtendWith(MockitoExtension.class)
class CapsuleEnergyDecayTest {

    @Mock private UserMapper userMapper;
    @Mock private MemoryCardMapper memoryCardMapper;
    @Mock private GravityService gravityService;
    @Mock private GravityTimePolicy gravityTimePolicy;
    @Mock private MemorySettlementService settlementService;
    @Mock private EmotionBaselineService emotionBaselineService;
    @Mock private EchoCapsuleMapper echoCapsuleMapper;

    private NightlyMemorySettlementJob job;

    @BeforeEach
    void setUp() {
        job = new NightlyMemorySettlementJob(userMapper, memoryCardMapper, gravityService, gravityTimePolicy,
                settlementService, emotionBaselineService, echoCapsuleMapper);
    }

    private EchoCapsule capsule(double energy, double freshness) {
        EchoCapsule c = new EchoCapsule();
        c.id = 1L;
        c.ownerUserId = 1L;
        c.capsuleType = "USER_CAPSULE";
        // IC-CAP-002 FIX-3: decay applies to PUBLIC capsules only.
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.echoEnergy = energy;
        c.freshnessScore = freshness;
        return c;
    }

    @Test
    @DisplayName("B-4: decay multiplies energy*0.97 and freshness*0.95")
    void nightlyJob_decaysEnergy() {
        EchoCapsule c = capsule(0.8, 0.8);
        when(echoCapsuleMapper.findPublicByOwner(1L)).thenReturn(List.of(c));

        job.decayEnergyForUser(1L);

        ArgumentCaptor<EchoCapsule> cap = ArgumentCaptor.forClass(EchoCapsule.class);
        verify(echoCapsuleMapper).updateById(cap.capture());
        EchoCapsule saved = cap.getValue();
        assertEquals(0.776, saved.echoEnergy, 1e-9, "0.8 * 0.97");
        assertEquals(0.76, saved.freshnessScore, 1e-9, "0.8 * 0.95");
    }

    @Test
    @DisplayName("B-4: energy floors at 0.3 and freshness floors at 0.0")
    void nightlyJob_respectsFloors() {
        EchoCapsule c = capsule(0.3, 0.0); // already at floors
        when(echoCapsuleMapper.findPublicByOwner(1L)).thenReturn(List.of(c));

        job.decayEnergyForUser(1L);

        ArgumentCaptor<EchoCapsule> cap = ArgumentCaptor.forClass(EchoCapsule.class);
        verify(echoCapsuleMapper).updateById(cap.capture());
        EchoCapsule saved = cap.getValue();
        assertEquals(0.3, saved.echoEnergy, 1e-9, "energy must not fall below the 0.3 floor");
        assertEquals(0.0, saved.freshnessScore, 1e-9, "freshness floor is 0.0");
    }

    @Test
    @DisplayName("B-4 (FIX-3): decay scopes to PUBLIC capsules only — non-public are not decayed")
    void nightlyJob_decaysOnlyPublicCapsules() {
        // The job must query PUBLIC capsules (findPublicByOwner), never all owned capsules.
        // A private capsule is excluded by that query, so it is never touched.
        when(echoCapsuleMapper.findPublicByOwner(1L)).thenReturn(List.of());

        job.decayEnergyForUser(1L);

        verify(echoCapsuleMapper).findPublicByOwner(1L);
        verify(echoCapsuleMapper, never()).findByOwner(anyLong());
        verify(echoCapsuleMapper, never()).updateById(any(EchoCapsule.class));
    }
}

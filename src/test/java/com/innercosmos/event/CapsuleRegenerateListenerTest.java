package com.innercosmos.event;

import com.innercosmos.ai.capsule.CapsuleSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * IC-CAP-002 B-1: the regenerate listener delegates a CapsuleSyncTriggerEvent to
 * CapsuleSyncService.onPortraitOrRelationshipChanged (the wiring that was previously
 * a dead method with zero callers). Tested by direct method call (deterministic;
 * real @Async delivery is racy).
 */
@ExtendWith(MockitoExtension.class)
class CapsuleRegenerateListenerTest {

    @Mock private CapsuleSyncService syncService;
    @InjectMocks private CapsuleRegenerateListener listener;

    @Test
    @DisplayName("B-1: memory/portrait changed event triggers the sync service")
    void memoryChanged_triggersRegen() {
        listener.onSyncTrigger(new CapsuleSyncTriggerEvent(42L));
        verify(syncService, times(1)).onPortraitOrRelationshipChanged(42L);
    }

    @Test
    @DisplayName("B-1: a null userId event is ignored")
    void nullUserId_isIgnored() {
        listener.onSyncTrigger(new CapsuleSyncTriggerEvent(null));
        verify(syncService, never()).onPortraitOrRelationshipChanged(any());
    }
}

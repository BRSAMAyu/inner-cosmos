package com.innercosmos.ai.portrait;

import com.innercosmos.entity.AuroraSelfProfile;
import com.innercosmos.mapper.AuroraSelfProfileMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuroraSelfProfileServiceTest {
    @Test
    void freshDatabaseReceivesCompleteStableSelfProfile() {
        AuroraSelfProfileMapper mapper = mock(AuroraSelfProfileMapper.class);
        when(mapper.selectById(1)).thenReturn(null);
        AuroraSelfProfileService service = new AuroraSelfProfileService(mapper);

        AuroraSelfProfile profile = service.get();

        assertThat(profile.identityJson).contains("Aurora", "long-term reflective companion");
        assertThat(profile.missionJson).contains("帮助用户理解自己");
        assertThat(profile.voiceStyleJson).contains("warmth");
        assertThat(profile.stableBoundariesJson).contains("不制造情感依赖");
        assertThat(profile.continuityRulesJson).contains("核心身份不能漂移");
        verify(mapper).insert(profile);
    }
}

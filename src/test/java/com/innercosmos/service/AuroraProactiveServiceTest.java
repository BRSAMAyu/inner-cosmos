package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuroraProactiveServiceTest {

    @Test
    void eligibilityDoesNotPretendAStaticTemplateWasPersonallyGenerated() {
        UserProfileMapper profiles = mock(UserProfileMapper.class);
        when(profiles.selectOne(any(Wrapper.class))).thenReturn(null);
        AuroraProactiveService service = new AuroraProactiveService(profiles);

        AuroraProactiveService.ProactiveGreeting result =
                service.evaluate(7L, LocalDateTime.of(2026, 7, 26, 10, 0), 72L);

        assertThat(result).isNotNull();
        assertThat(result.greeting).isNull();
        assertThat(result.hoursSinceLastSession).isEqualTo(72L);
    }
}

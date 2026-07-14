package com.innercosmos.service.impl;

import com.innercosmos.entity.ABTestConfig;
import com.innercosmos.mapper.ABTestConfigMapper;
import com.innercosmos.mapper.ABTestMetricsMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ABTestServiceImplTest {

    @Test
    void minimumIntegerHashDoesNotOverflowIntoMockGroup() {
        ABTestConfigMapper configMapper = mock(ABTestConfigMapper.class);
        ABTestMetricsMapper metricsMapper = mock(ABTestMetricsMapper.class);
        ABTestConfig config = new ABTestConfig();
        config.enabled = true;
        config.status = "ACTIVE";
        config.testName = "minimum-hash-regression";
        config.mockPercentage = 50;
        when(configMapper.selectOne(any())).thenReturn(config);

        ABTestServiceImpl service = new ABTestServiceImpl(configMapper, metricsMapper);

        // Long.hashCode(2^31) is Integer.MIN_VALUE. Math.abs used to keep it
        // negative and therefore assigned it to MOCK for every percentage.
        assertThat(service.assignGroup(2_147_483_648L, "aurora")).isEqualTo("REMOTE");
    }
}

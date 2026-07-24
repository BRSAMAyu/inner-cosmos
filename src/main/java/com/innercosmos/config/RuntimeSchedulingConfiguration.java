package com.innercosmos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "spring.task.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' "
        + "or '${inner-cosmos.runtime.role:all}' == 'worker' "
        + "or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class RuntimeSchedulingConfiguration {
}

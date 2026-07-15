package com.innercosmos.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "inner-cosmos.runtime.exit-after-startup", havingValue = "true")
public class MigrationRoleExit implements ApplicationRunner {
    private final ConfigurableApplicationContext context;

    public MigrationRoleExit(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"migration".equalsIgnoreCase(context.getEnvironment()
                .getProperty("inner-cosmos.runtime.role", ""))) {
            throw new IllegalStateException("exit-after-startup is only valid for the migration runtime role");
        }
        context.close();
    }
}

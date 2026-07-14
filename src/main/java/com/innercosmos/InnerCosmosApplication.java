package com.innercosmos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Authentication is owned by JwtAuthenticationFilter and the application user tables.
// Excluding Boot's generated in-memory user prevents a misleading development password
// and ensures no parallel default identity store exists in production.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class InnerCosmosApplication {
    public static void main(String[] args) {
        SpringApplication.run(InnerCosmosApplication.class, args);
    }
}

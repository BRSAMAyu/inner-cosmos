package com.innercosmos.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The rate-limit filter belongs inside Spring Security, after verified authentication. */
@Configuration(proxyBeanMethods = false)
public class RateLimitFilterConfiguration {
    @Bean
    FilterRegistrationBean<ApiRateLimitFilter> disableContainerRateLimitRegistration(
            ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}

package com.innercosmos.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.innercosmos.mapper")
public class MybatisPlusConfig {
}

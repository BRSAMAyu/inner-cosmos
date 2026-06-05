package com.innercosmos.config;

import com.innercosmos.asr.AsrClient;
import com.innercosmos.asr.GlmAsrClient;
import com.innercosmos.asr.MimoAsrClient;
import com.innercosmos.asr.MockAsrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsrConfig {

    private static final Logger log = LoggerFactory.getLogger(AsrConfig.class);

    private final LlmConfig llmConfig;

    public AsrConfig(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
    }

    @Bean
    public AsrClient asrClient() {
        String provider = llmConfig.getAsrProvider() == null ? "mimo" : llmConfig.getAsrProvider().trim().toLowerCase();
        if ("mimo".equals(provider)) {
            String apiKey = firstNonBlank(llmConfig.getMimo().getApiKey(), llmConfig.getApiKey());
            if (apiKey.isBlank()) {
                log.info("Creating MockAsrClient because MiMo ASR key is not configured");
                return new MockAsrClient();
            }
            log.info("Creating MimoAsrClient for model {}", llmConfig.getMimo().getAsrModel());
            return new MimoAsrClient(
                    apiKey,
                    llmConfig.getMimo().getAsrBaseUrl(),
                    llmConfig.getMimo().getAsrModel(),
                    llmConfig.getMimo().getAsrLanguage(),
                    llmConfig.getMimo().getTimeoutMs()
            );
        }

        if ("mock".equals(provider)) {
            log.info("Creating MockAsrClient because ASR provider is mock");
            return new MockAsrClient();
        }

        String apiKey = firstNonBlank(
                llmConfig.getGlm().getAsrApiKey(),
                llmConfig.getGlm().getApiKey(),
                llmConfig.getApiKey()
        );
        if (apiKey.isBlank()) {
            log.info("Creating MockAsrClient because GLM ASR key is not configured");
            return new MockAsrClient();
        }
        log.info("Creating GlmAsrClient for model {}", llmConfig.getGlm().getAsrModel());
        return new GlmAsrClient(
                apiKey,
                llmConfig.getGlm().getAsrBaseUrl(),
                llmConfig.getGlm().getAsrModel()
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}

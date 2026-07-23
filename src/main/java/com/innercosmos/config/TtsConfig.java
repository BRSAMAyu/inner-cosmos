package com.innercosmos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.tts.DisabledTtsClient;
import com.innercosmos.ai.tts.QwenAudioTtsClient;
import com.innercosmos.ai.tts.TtsClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tts")
public class TtsConfig {
    public boolean enabled;
    public String apiKey = "";
    /**
     * Default is the public DashScope realtime-inference gateway; operators on a private/
     * workspace-scoped gateway (e.g. a dedicated MaaS endpoint) override via {@code TTS_WS_URL}.
     * Same protocol either way -- see {@code QwenAudioTtsClient}.
     */
    public String wsUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    public long timeoutMs = 8000;

    @Bean
    public TtsClient ttsClient(ObjectMapper objectMapper) {
        if (!enabled) return new DisabledTtsClient();
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("TTS_ENABLED requires TTS_API_KEY");
        return new QwenAudioTtsClient(apiKey, wsUrl, timeoutMs, objectMapper);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getWsUrl() { return wsUrl; }
    public void setWsUrl(String wsUrl) { this.wsUrl = wsUrl; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}

package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_user_profile")
public class UserProfile extends BaseEntity {
    public Long userId;
    public String auroraName;
    public String auroraTone;
    public String preferredInputType;
    public String socialReachabilityStatus;
    public String bio;
    public Integer reflectionDepth;
    public Boolean allowMemoryRecall;
    public String quietHoursStart;
    public String quietHoursEnd;
    public Integer proactiveSensitivity;
    public Boolean allowMultiMessage;
    public Boolean focusModeEnabled;
    public String focusWindowsJson;
    public String currentEnvironmentLabel;
    public Boolean weatherAwarenessEnabled;
    public Boolean timeAwarenessEnabled;
    /**
     * Default LLM provider for this user (MINIMAX, MIMO, GLM, DEEPSEEK, MOCK).
     * null = use the system default from {@code llm.provider}.
     */
    public String preferredModel;
    /**
     * Proactive push intensity: OFF, WHISPER, LIGHT, ACTIVE, COMPANION, ALIVE.
     */
    public String proactiveIntensity;
    /**
     * Sleep window start time (e.g., "23:00:00").
     */
    public String sleepWindowStart;
    /**
     * Sleep window end time (e.g., "07:00:00").
     */
    public String sleepWindowEnd;
    /** Persisted IANA timezone used by temporal/proactive decisions. */
    public String timezone;
    /**
     * Temporary boost expiration timestamp (null = no boost).
     */
    public java.time.LocalDateTime boostUntil;
    /**
     * Preferred preset voice id for TTS synthesis (see {@code TtsVoicePresets}).
     * null = system default (the first preset).
     */
    public String preferredTtsVoiceId;
    /**
     * Whether Aurora's "inner voice" (心声) is composed, synthesized and surfaced at all.
     * null is treated as the product default of {@code true} (ships on by default).
     */
    public Boolean innerVoiceEnabled;
    /**
     * Inner-voice delivery mode: {@code AMBIENT} (auto-plays automatically, the default) or
     * {@code ON_DEMAND} (tap-to-reveal-and-play). null is treated as {@code AMBIENT}.
     */
    public String innerVoiceMode;
}

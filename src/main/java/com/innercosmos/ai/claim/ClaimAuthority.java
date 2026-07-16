package com.innercosmos.ai.claim;

/**
 * Evidence tiers from the Campaign B authority rule
 * ({@code 用户明确纠正 > 用户确认 > 重复明确表达 > 重复行为证据 > 单次明确表达 > 模型推断}).
 * Automatic extraction can only produce the lower tiers here; the top two
 * ({@code USER_CORRECTION}, {@code USER_CONFIRMED}) require an explicit user act and are owned by
 * {@code UserCorrectionServiceImpl}, not the extractor.
 */
public final class ClaimAuthority {
    public static final String REPEATED_EXPLICIT = "REPEATED_EXPLICIT"; // 重复明确表达
    public static final String REPEATED_BEHAVIOR = "REPEATED_BEHAVIOR"; // 重复行为证据
    public static final String SINGLE_EXPLICIT = "SINGLE_EXPLICIT";     // 单次明确表达
    public static final String MODEL_INFERENCE = "MODEL_INFERENCE";     // 模型推断

    private ClaimAuthority() {
    }
}

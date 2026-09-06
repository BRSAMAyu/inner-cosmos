package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Per-user analysis consent gate (CP-03 plumbing). The full consent product contract —
 * purposes, versioned re-consent, export — lands with CP-07; this row is the single
 * switch the metric pipeline reads so analysis events never bypass a user's refusal.
 */
@TableName("tb_analysis_consent")
public class AnalysisConsent extends BaseEntity {
    public Long userId;
    /** GRANTED or DECLINED. */
    public String status;
    public String consentVersion;
}

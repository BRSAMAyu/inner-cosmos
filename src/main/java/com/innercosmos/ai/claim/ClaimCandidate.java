package com.innercosmos.ai.claim;

import java.util.List;

/**
 * Campaign B — a single automatically-extracted user-model claim candidate.
 *
 * <p>A candidate is a <em>proposal</em> derived from conversation evidence, never an authoritative
 * fact. Per the Campaign B authority rule
 * ({@code 用户明确纠正 > 用户确认 > 重复明确表达 > 重复行为证据 > 单次明确表达 > 模型推断}), an
 * auto-extracted claim ranks at the lower tiers and must be surfaced for the user to confirm or
 * correct before it can become an {@code ACTIVE} {@code UnderstandingClaim}. This record carries the
 * provenance (source message ids), a calibrated confidence and the evidence-tier authority so the UI
 * can show "why I think this" and the persistence layer can keep it as a {@code CANDIDATE}.
 *
 * @param claimType             one of {@link ClaimTypes}
 * @param claimKey              stable dedup key {@code claimType:normalizedValue}
 * @param value                 the human-readable extracted value (e.g. "在下雨天读书")
 * @param authorityLevel        evidence tier, one of {@link ClaimAuthority}
 * @param confidence            calibrated 0..1 confidence for this candidate
 * @param provenanceMessageIds  ids of the DialogMessages that evidence this candidate (never empty)
 * @param evidenceText          the concrete phrase the extractor matched, for user-facing provenance
 * @param uncertain             true when the user themselves expressed uncertainty about it
 */
public record ClaimCandidate(String claimType, String claimKey, String value, String authorityLevel,
                             double confidence, List<Long> provenanceMessageIds, String evidenceText,
                             boolean uncertain) {
}

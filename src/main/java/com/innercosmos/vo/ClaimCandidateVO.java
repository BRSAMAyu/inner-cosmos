package com.innercosmos.vo;

import java.util.List;

/**
 * A pending auto-extracted claim candidate surfaced for the user to confirm or dismiss. Carries the
 * provenance (source message ids) and evidence so the UI can answer "why do I think this about you"
 * before anything becomes an authoritative fact.
 *
 * @param alreadyActive true when the user already has an ACTIVE claim for the same auto key, so the
 *                      UI can present this as a reinforcement rather than a fresh discovery.
 */
public record ClaimCandidateVO(Long id, String claimType, String value, String authorityLevel,
                               double confidence, List<Long> provenanceMessageIds, String evidenceText,
                               boolean uncertain, boolean alreadyActive, String createdAt) {
}

package com.innercosmos.service.consent;

import com.innercosmos.service.consent.ConsentPurpose.Decision;
import java.util.List;

/**
 * CP-07 consent center: the single read/write surface for user-facing consent decisions.
 * ANALYTICS is dual-written with tb_analysis_consent so the CP-03 metric gate keeps
 * working; capsule compilation stays managed by DataUseGrant and is surfaced read-only.
 */
public interface ConsentCenterService {

    record ConsentView(
            String purposeCode,
            String group,
            boolean granted,
            boolean userSettable,
            String description,
            String withdrawalEffect,
            String version,
            String source) {
    }

    /** Effective consent state for every purpose, grouped for the consent-center UI. */
    List<ConsentView> list(Long userId);

    /** Record a GRANT or DECLINE decision for one user-settable purpose. */
    ConsentView decide(Long userId, String purposeCode, boolean grant);

    /** Effective decision for one purpose (defaults apply when no row exists). */
    Decision effective(Long userId, ConsentPurpose purpose);

    /**
     * Model-gateway egress guard: a real human user must have granted AI_PROVIDER_EGRESS
     * before their content is sent to a real provider. Synthetic/internal accounts and
     * userless system calls pass; there is no silent degradation to another route.
     */
    void assertProviderEgress(Long userId);
}

package com.innercosmos.ai.gateway;

import java.util.Locale;
import java.util.Set;

/**
 * CP-17 structured contract/filing version for the call manifest. This replaces the former
 * free-text placeholder ("contract:pending-per-provider"): a manifest row can now only carry a
 * version this closed enum knows, so contract metadata can never be invented by a caller or a
 * coding agent. An unknown provider label is recorded as {@link #UNREGISTERED} — the row is
 * still written (deleting audit truth would be the opposite of fail-closed), but it honestly
 * states that no registered contract backs that egress.
 *
 * <p>The placeholder semantics follow the repo's operator-gate discipline: real commercial
 * contract IDs and generative-AI filing (备案) versions are external receipts that only the
 * operator can countersign and backfill. Until then every routed provider records
 * {@link #PENDING_CN_FILING}; the pending marker is the honest value, not a blocker for
 * dev/test, while production launch remains gated by the CP-51A regulatory ledger.
 */
public enum ModelContractVersion {

    /**
     * Placeholder for every provider the gateway is wired to route to today: no countersigned
     * commercial contract or GenAI safety-filing receipt exists yet, so the honest recorded
     * version is "pending". Replacing this mapping with a real contractId/filingVersion is an
     * operator action taken with the countersigned contract in hand — never improvised here.
     */
    PENDING_CN_FILING("routed-provider", "contract:pending-operator-gate", "filing:pending-operator-gate"),

    /** The provider label is not one the gateway knows — recorded as unregistered, never guessed. */
    UNREGISTERED("unregistered", "none", "none");

    private final String provider;
    private final String contractId;
    private final String filingVersion;

    ModelContractVersion(String provider, String contractId, String filingVersion) {
        this.provider = provider;
        this.contractId = contractId;
        this.filingVersion = filingVersion;
    }

    /** Provider scope of this version (for the placeholder: the routed-provider set). */
    public String provider() {
        return provider;
    }

    /** Countersigned contract identifier, or the honest pending/none marker. */
    public String contractId() {
        return contractId;
    }

    /** Registered model filing (备案) version, or the honest pending/none marker. */
    public String filingVersion() {
        return filingVersion;
    }

    /** True when a countersigned contract + filing receipt backs egress under this version. */
    public boolean isRegistered() {
        return this != UNREGISTERED && this != PENDING_CN_FILING;
    }

    /** Labels {@code LlmConfig} can actually route to (failover chain + experiment/Mock legs). */
    private static final Set<String> ROUTED_PROVIDERS = Set.of(
            "glm", "mimo", "minimax", "deepseek", "gemini", "openai-compatible",
            "mock", "configured-provider");

    /**
     * Structured lookup used by {@link GatewayCallLedger#record}: a label the gateway routes to
     * resolves to the placeholder; anything else resolves to {@link #UNREGISTERED} instead of
     * being recorded as if a contract existed.
     */
    public static ModelContractVersion forProvider(String providerLabel) {
        if (providerLabel == null || providerLabel.isBlank()) {
            return UNREGISTERED;
        }
        String normalized = providerLabel.trim().toLowerCase(Locale.ROOT);
        return ROUTED_PROVIDERS.contains(normalized) ? PENDING_CN_FILING : UNREGISTERED;
    }
}

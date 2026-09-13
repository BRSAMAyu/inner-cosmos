package com.innercosmos.ai.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-17 structured contract/filing version in the call manifest: the former free-text
 * "contract:pending-per-provider" is replaced by a closed enum. A provider the gateway
 * routes to records the honest PENDING placeholder (the real contract/filing receipt is an
 * operator gate); any other label records UNREGISTERED — the audit row survives, but no
 * contract metadata is ever invented.
 */
class GatewayCallLedgerContractVersionTest {

    private final GatewayCallLedger ledger = new GatewayCallLedger();

    @Test
    void routedProvidersCarryTheStructuredPendingPlaceholder() {
        for (String provider : new String[]{"glm", "GLM", "mimo", "minimax", "deepseek",
                "gemini", "openai-compatible", "mock", "configured-provider"}) {
            ledger.record(7L, "AURORA_PLAN_TALK", provider, "OK");
            GatewayCallLedger.CallRecord record = ledger.recent(1).get(0);
            assertEquals(ModelContractVersion.PENDING_CN_FILING, record.contractVersion(),
                    provider + " is a routed provider and must resolve to the pending placeholder");
            assertEquals("contract:pending-operator-gate", record.contractVersion().contractId());
            assertEquals("filing:pending-operator-gate", record.contractVersion().filingVersion());
            assertFalse(record.contractVersion().isRegistered(),
                    "no real contract has been countersigned yet — the operator gate stays closed");
        }
    }

    @Test
    void unknownOrBlankProvidersAreRecordedUnregisteredNeverInvented() {
        for (String provider : new String[]{"evil.example.com", "some-free-text", "", null}) {
            ledger.record(7L, "M", provider, "OK");
            assertEquals(ModelContractVersion.UNREGISTERED, ledger.recent(1).get(0).contractVersion(),
                    "unknown label [" + provider + "] must be recorded as UNREGISTERED, not guessed");
        }
        GatewayCallLedger.CallRecord unregistered = ledger.recent(1).get(0);
        assertEquals("none", unregistered.contractVersion().contractId());
        assertFalse(unregistered.contractVersion().isRegistered());
    }

    @Test
    void manifestColumnsStayBoundSoTheGatewayAuditCanStillMatchThem() {
        ledger.record(42L, "AURORA_SPEAKER_TALK", "glm", "OK");
        GatewayCallLedger.CallRecord record = ledger.recent(1).get(0);
        assertEquals(42L, record.userId());
        assertEquals("AURORA_SPEAKER_TALK", record.moduleName());
        assertEquals("glm", record.provider());
        assertEquals("AI_PROVIDER_EGRESS", record.purpose());
        assertEquals("USER_CONTENT", record.dataClass());
        assertEquals("CN", record.region());
        assertEquals("OK", record.outcome());
        assertTrue(record.at() != null);
    }

    @Test
    void ringStaysBoundedAndKeepsTheNewestRecords() {
        for (int i = 0; i < 205; i++) {
            ledger.record(1L, "M" + i, "glm", "OK");
        }
        assertEquals(200, ledger.recent(500).size(), "capacity bound is 200");
        var newest = ledger.recent(2);
        assertEquals("M204", newest.get(1).moduleName());
        assertEquals("M203", newest.get(0).moduleName());
    }
}

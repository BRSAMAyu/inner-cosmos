package com.innercosmos.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PsychologySkillRegistryTest {
    @Test
    void loadsThreeBilingualConsentBoundLowRiskManifests() {
        PsychologySkillRegistry registry = new PsychologySkillRegistry(new ObjectMapper());

        assertThat(registry.list()).hasSize(3).allSatisfy(manifest -> {
            assertThat(manifest.version).isEqualTo("1.0.0");
            assertThat(manifest.riskTier).isEqualTo("L1");
            assertThat(manifest.userInvocation).isEqualTo("EXPLICIT_CONSENT");
            assertThat(manifest.agentInvocation).isEqualTo("SUGGEST_ONLY");
            assertThat(manifest.title).containsKeys("zh-CN", "en-SG");
            assertThat(manifest.limitations).containsKeys("zh-CN", "en-SG");
            assertThat(manifest.allowedData).containsExactly("answers-entered-in-this-run");
            assertThat(manifest.allowedTools).isEmpty();
            assertThat(manifest.evidence).isNotEmpty();
        });
    }
}

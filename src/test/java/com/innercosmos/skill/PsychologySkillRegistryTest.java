package com.innercosmos.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP-60A frozen criterion (experiment-registry.yml): ≥6 expert-reviewed skills with
 * "每项双专家通过". The registry side of that gate is structural: at least six manifests,
 * every manifest carries non-empty evidence, and id@version stays unique across the
 * append-only version ledger. Expert sign-off itself is an operator gate recorded in
 * docs/commercialization/product/skill-expert-review.ledger.yml, never a field here.
 */
class PsychologySkillRegistryTest {
    /** The three CP-60 expansion skills named by blueprint L873. */
    private static final List<String> CP60A_EXPANSION_IDS = List.of(
            "relationship-perspective", "support-preference-mapper", "cognition-pattern-reflector");

    @Test
    void loadsAtLeastSixBilingualConsentBoundLowRiskManifests() {
        PsychologySkillRegistry registry = new PsychologySkillRegistry(new ObjectMapper());

        assertThat(registry.list()).hasSizeGreaterThanOrEqualTo(6).allSatisfy(manifest -> {
            assertThat(manifest.version).isEqualTo("1.0.0");
            assertThat(manifest.riskTier).isEqualTo("L1");
            assertThat(manifest.userInvocation).isEqualTo("EXPLICIT_CONSENT");
            assertThat(manifest.agentInvocation).isEqualTo("SUGGEST_ONLY");
            assertThat(manifest.title).containsKeys("zh-CN", "en-SG");
            assertThat(manifest.limitations).containsKeys("zh-CN", "en-SG");
            assertThat(manifest.allowedData).containsExactly("answers-entered-in-this-run");
            assertThat(manifest.allowedTools).isEmpty();
            assertThat(manifest.retentionChoices)
                    .containsExactly("DISCARD_AFTER_SESSION", "SAVE_RESULT", "PROFILE_ELIGIBLE");
            assertThat(manifest.requiredInputs).isNotEmpty();
        });
    }

    @Test
    void everySkillCarriesNonEmptyEvidenceCitations() {
        PsychologySkillRegistry registry = new PsychologySkillRegistry(new ObjectMapper());

        assertThat(registry.list()).isNotEmpty().allSatisfy(manifest -> {
            assertThat(manifest.evidence)
                    .as("skill %s must cite its theoretical evidence (CP-60A 每技能有理论依据)", manifest.id)
                    .isNotEmpty()
                    .allSatisfy(citation -> assertThat(citation).isNotBlank());
        });
    }

    @Test
    void skillIdAndVersionStayUniqueAcrossTheVersionLedger() {
        PsychologySkillRegistry registry = new PsychologySkillRegistry(new ObjectMapper());

        Set<String> ids = new HashSet<>();
        Set<String> idVersions = new HashSet<>();
        for (PsychologySkillManifest manifest : registry.all()) {
            assertThat(ids.add(manifest.id))
                    .as("duplicate skill id %s in the version ledger", manifest.id)
                    .isTrue();
            assertThat(idVersions.add(manifest.id + "@" + manifest.version))
                    .as("duplicate version %s@%s", manifest.id, manifest.version)
                    .isTrue();
        }
        assertThat(ids).hasSize(registry.all().size())
                .containsAll(CP60A_EXPANSION_IDS);
    }

    @Test
    void everyCurrentSkillIsResolvableThroughRequire() {
        PsychologySkillRegistry registry = new PsychologySkillRegistry(new ObjectMapper());

        for (PsychologySkillManifest manifest : registry.list()) {
            assertThat(registry.require(manifest.id)).isSameAs(manifest);
            assertThat(registry.require(manifest.id, manifest.version)).isSameAs(manifest);
        }
        assertThat(registry.require("cognition-pattern-reflector").evidence).isNotEmpty();
        assertThat(registry.require("relationship-perspective").escalation).isEqualTo("LOCAL_CRISIS_RESOURCES");
        assertThat(registry.require("support-preference-mapper").fallback).isEqualTo("DETERMINISTIC_REFLECTION");
    }
}

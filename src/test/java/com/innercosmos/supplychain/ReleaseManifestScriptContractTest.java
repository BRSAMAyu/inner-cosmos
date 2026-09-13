package com.innercosmos.supplychain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-49/CP-41 release engineering contract: the release manifest generator and its
 * fail-closed assertor exist, and the release workflow actually runs them (generates
 * both CycloneDX SBOMs, asserts the manifest, archives it with if-no-files-found=error).
 * CI here cannot execute the workflow, so this test pins the wiring textually — silently
 * dropping the steps fails the suite. Honesty vocabulary is pinned too: absent artifacts
 * must never carry digests.
 */
class ReleaseManifestScriptContractTest {

    private static final Path SCRIPTS = Path.of("scripts", "release");
    private static final Path WORKFLOW =
            Path.of(".github", "workflows", "release-image.yml");

    @Test
    void generatorAndAssertorExistWithHonestyVocabulary() throws IOException {
        String generator = read(SCRIPTS.resolve("generate-release-manifest.ps1"));
        String assertor = read(SCRIPTS.resolve("assert-release-manifest.ps1"));
        // Generator: SHA-256 digests, an explicit absent list, version-tag validation.
        assertTrue(generator.contains("Get-FileHash -Algorithm SHA256"));
        assertTrue(generator.contains("$absent += ") || generator.contains("$absent +="),
                "absent artifacts must be listed, never given placeholder digests");
        assertTrue(generator.contains("v\\d+\\.\\d+\\.\\d+"));
        assertTrue(generator.contains("inner-cosmos.release-manifest/v1"));
        // Asserter: schema/version/SHA shapes, live drift guard, absent-must-stay-absent.
        assertTrue(assertor.contains("inner-cosmos.release-manifest/v1"));
        assertTrue(assertor.contains("^[0-9a-f]{40}$"));
        assertTrue(assertor.contains("^[0-9a-f]{64}$"));
        assertTrue(assertor.contains("backendVersion drift"));
        assertTrue(assertor.contains("both present and absent"));
    }

    @Test
    void releaseWorkflowGeneratesAssertsAndArchivesTheManifest() throws IOException {
        String workflow = read(WORKFLOW);
        assertTrue(workflow.contains(
                "org.cyclonedx:cyclonedx-maven-plugin:2.8.0:makeAggregateBom"),
                "backend aggregate SBOM generation must run in the release flow");
        assertTrue(workflow.contains("@cyclonedx/cyclonedx-npm --output-file cyclonedx.json"),
                "web SBOM generation must run in the release flow");
        assertTrue(workflow.contains("./scripts/release/generate-release-manifest.ps1 -Version $tag"),
                "the manifest generator must be invoked with the release tag");
        assertTrue(workflow.contains("./scripts/release/assert-release-manifest.ps1"),
                "the manifest contract assert must run in CI");
        assertTrue(workflow.contains("if-no-files-found: error"),
                "archiving the manifest/SBOMs must fail the release when files are missing");
        // Pinned action discipline: no floating action tags in the archive step.
        assertTrue(workflow.contains("actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a"));
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), path + " must exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

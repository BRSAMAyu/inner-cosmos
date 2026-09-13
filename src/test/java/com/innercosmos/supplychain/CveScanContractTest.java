package com.innercosmos.supplychain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-49 CVE scan command contract: the cve-scan.ps1 wrapper exists and speaks the
 * honesty vocabulary (NVD_API_KEY resolution, a distinguishable exit-3 skip that
 * never fabricates results, OWASP dependency-check for the backend, pnpm audit
 * --prod for the web quick gate), and the java-baseline workflow actually invokes
 * it gated on the NVD_API_KEY secret. The exit-3 skip is translated to success in
 * the workflow so unkeyed repos stay green while the skip stays visible; every
 * other non-zero exit must fail the job — a blanket continue-on-error would also
 * silence genuine scan failures, so it is pinned absent. CI here cannot reach the
 * NVD, so this test pins the wiring textually, like ReleaseManifestScriptContractTest.
 */
class CveScanContractTest {

    private static final Path SCRIPT =
            Path.of("scripts", "supply-chain", "cve-scan.ps1");
    private static final Path WORKFLOW =
            Path.of(".github", "workflows", "java-baseline.yml");

    @Test
    void cveScanScriptExistsWithHonestyVocabulary() throws IOException {
        String script = read(SCRIPT);
        // Key resolution: explicit parameter with the NVD_API_KEY env fallback.
        assertTrue(script.contains("$env:NVD_API_KEY"));
        // The no-key path must be explicit, distinguishable, and print no result.
        assertTrue(script.contains("CVE_SCAN_SKIPPED_NO_NVD_KEY"));
        assertTrue(script.contains("exit 3"));
        // Real scan commands, not fabricated output.
        assertTrue(script.contains("org.owasp:dependency-check-maven:check"));
        assertTrue(script.contains("pnpm audit --prod"));
        // Fail-closed gate: dependency-check must fail the build on CVSS >= 7,
        // mirroring the HIGH/CRITICAL severity of the trivy gates.
        assertTrue(script.contains("-DfailBuildOnCVSS=7"));
    }

    @Test
    void javaBaselineRunsCveScanGatedOnTheNvdSecret() throws IOException {
        String workflow = read(WORKFLOW);
        assertTrue(workflow.contains("./scripts/supply-chain/cve-scan.ps1"),
                "the CVE scan script must be invoked by the baseline workflow");
        assertTrue(workflow.contains("NVD_API_KEY: ${{ secrets.NVD_API_KEY }}"),
                "the script must receive the NVD_API_KEY secret through env");
        // The distinguishable exit-3 skip becomes a green step with the skip line
        // still visible in the log; everything else fails the job as-is.
        assertTrue(workflow.contains("-eq 3"),
                "the exit-3 no-key skip must be translated, not treated as failure");
        assertTrue(workflow.contains("exit 0"));
        assertTrue(workflow.contains("CVE_SCAN_SKIPPED_NO_NVD_KEY"),
                "the skip reason must stay visible in the CI log");
        // A real scan failure or finding must never be silenced.
        assertFalse(workflow.contains("continue-on-error"),
                "continue-on-error would swallow genuine scan failures");
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), path + " must exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

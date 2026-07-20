package com.innercosmos.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A6 executable domain boundaries — enforced without adding an ArchUnit/bytecode dependency by
 * scanning the source tree for {@code import com.innercosmos.<layer>.} statements. The modular
 * monolith stays layered: the persistence and inbound-edge layers must not reach "upward" into the
 * layers that orchestrate them. Each rule below is verified to hold at the time of writing; this test
 * fails the build the moment a new cross-layer import is introduced, so the drift is caught in review
 * rather than discovered later.
 */
class DomainBoundaryArchitectureTest {
    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "com", "innercosmos");
    private static final Pattern INTERNAL_IMPORT =
            Pattern.compile("^\\s*import\\s+com\\.innercosmos\\.([a-zA-Z0-9_]+)\\.");

    @Test
    void entitiesArePurePersistenceAndDependOnNoUpperLayer() {
        assertNoImportsInto("entity",
                "service", "controller", "mapper", "vo", "dto",
                "ratelimit", "idempotency", "scheduler", "streaming");
    }

    @Test
    void mappersDoNotReachIntoServicesOrControllers() {
        assertNoImportsInto("mapper", "service", "controller");
    }

    @Test
    void servicesDoNotDependOnInboundControllers() {
        assertNoImportsInto("service", "controller");
    }

    @Test
    void safetyRulesDoNotDependOnInboundControllers() {
        assertNoImportsInto("safety", "controller");
    }

    /** Asserts no {@code .java} under {@code com.innercosmos.<layer>} imports any forbidden sibling layer. */
    private void assertNoImportsInto(String layer, String... forbiddenLayers) {
        Path layerDir = SOURCE_ROOT.resolve(layer);
        assertTrue(Files.isDirectory(layerDir),
                "expected source layer directory to exist: " + layerDir.toAbsolutePath()
                        + " (run from the module root)");
        List<String> forbidden = List.of(forbiddenLayers);
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(layerDir)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                List<String> lines;
                try {
                    lines = Files.readAllLines(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                for (String line : lines) {
                    Matcher m = INTERNAL_IMPORT.matcher(line);
                    if (m.find() && forbidden.contains(m.group(1))) {
                        violations.add(SOURCE_ROOT.relativize(file) + " -> " + line.trim());
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(violations.isEmpty(),
                "'" + layer + "' must not import " + forbidden + ", but found:\n  "
                        + String.join("\n  ", violations));
    }
}

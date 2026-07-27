package com.innercosmos.config;

import com.innercosmos.service.MemoryEmbeddingIndexService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Admission thresholds for provider cosine scores.
 *
 * <p>Two modes, chosen by whether an operator has actually installed a fitted calibration:
 *
 * <ul>
 *   <li><b>Not configured</b> (the default) — the built-in starting thresholds below apply, so
 *       semantic retrieval contributes out of the box. Previously this case failed closed, which
 *       meant a deployment could pay for a real embedding provider, store real vectors, and still
 *       have them contribute exactly nothing to the Evidence Pack.
 *   <li><b>Configured</b> ({@code enabled=true} with a provider/model/version) — the fitted
 *       thresholds apply, but only when the running embedding identity matches exactly. A mismatch
 *       (typically a model upgrade) still fails closed rather than silently reusing thresholds
 *       fitted against a different model.
 * </ul>
 *
 * <p>The built-in values are calibrated starting points for modern multilingual embedding models
 * (related content generally lands above them, unrelated content below), not values fitted on this
 * product's labeled data. They are the knob to turn first when recall or precision looks wrong.
 */
@Configuration
@ConfigurationProperties(prefix = "memory.retrieval.semantic-calibration")
public class MemorySemanticCalibrationConfig {
    /** CJK text has a higher baseline cosine between unrelated passages, so it sits higher. */
    private static final double DEFAULT_ZH_ABSOLUTE = 0.50;
    private static final double DEFAULT_EN_ABSOLUTE = 0.45;
    /**
     * Off by default. A top1-vs-top2 margin requirement suppresses the whole provider signal
     * whenever two memories are similarly relevant -- which is the normal case, not an anomaly.
     * It stays available for operators who fit it deliberately against labeled data.
     */
    private static final double DEFAULT_MARGIN = 0.0;

    public boolean enabled;
    public String provider = "";
    public String model = "";
    public String version = "";
    public Map<String, Threshold> locales = new LinkedHashMap<>();

    public Optional<Threshold> threshold(MemoryEmbeddingIndexService.EmbeddingIdentity identity,
                                         String locale) {
        // Un-calibrated: an operator can still retune the admission bar for a running deployment
        // through the same locale block (MEMORY_SEMANTIC_CALIBRATION_ZH_ABSOLUTE and friends)
        // without having to declare a full fitted calibration first.
        if (!configured()) return Optional.of(operatorSuppliedOrDefault(locale));
        // Configured but pointed at a different model/version: fail closed. Reusing thresholds
        // fitted for another embedding model is worse than falling back to lexical retrieval.
        if (identity == null
                || !same(provider, identity.provider())
                || !same(model, identity.model())
                || !same(version, identity.version())) return Optional.empty();
        Threshold threshold = locales.get(locale);
        if (threshold == null) threshold = locales.get(locale.toLowerCase(Locale.ROOT));
        if (threshold == null || !valid(threshold)) return Optional.empty();
        return Optional.of(threshold);
    }

    /** An operator calibration only counts once it actually names an embedding identity. */
    private boolean configured() {
        return enabled && provider != null && !provider.isBlank()
                && model != null && !model.isBlank()
                && version != null && !version.isBlank();
    }

    private Threshold operatorSuppliedOrDefault(String locale) {
        Threshold supplied = locales.get(locale);
        if (supplied == null && locale != null) supplied = locales.get(locale.toLowerCase(Locale.ROOT));
        return supplied != null && valid(supplied) ? supplied : builtInDefault(locale);
    }

    private static Threshold builtInDefault(String locale) {
        boolean chinese = locale != null && locale.toLowerCase(Locale.ROOT).startsWith("zh");
        Threshold threshold = new Threshold();
        threshold.absoluteThreshold = chinese ? DEFAULT_ZH_ABSOLUTE : DEFAULT_EN_ABSOLUTE;
        threshold.minTop1Top2Margin = DEFAULT_MARGIN;
        return threshold;
    }

    private static boolean valid(Threshold threshold) {
        return threshold.absoluteThreshold >= -1 && threshold.absoluteThreshold <= 1
                && threshold.minTop1Top2Margin >= 0 && threshold.minTop1Top2Margin <= 2;
    }

    private static boolean same(String expected, String actual) {
        return expected != null && !expected.isBlank() && actual != null
                && expected.equalsIgnoreCase(actual);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setModel(String model) { this.model = model; }
    public void setVersion(String version) { this.version = version; }
    public void setLocales(Map<String, Threshold> locales) {
        this.locales = locales == null ? new LinkedHashMap<>() : locales;
    }

    public static class Threshold {
        /** Matches the built-in zh default so a partially-filled operator block stays usable. */
        public double absoluteThreshold = DEFAULT_ZH_ABSOLUTE;
        public double minTop1Top2Margin = DEFAULT_MARGIN;
        public void setAbsoluteThreshold(double value) { this.absoluteThreshold = value; }
        public void setMinTop1Top2Margin(double value) { this.minTop1Top2Margin = value; }
    }
}

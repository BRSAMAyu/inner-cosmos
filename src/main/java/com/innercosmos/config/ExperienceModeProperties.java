package com.innercosmos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * "Experience-first" runtime switches ({@code inner-cosmos.experience.*}).
 *
 * <p>Everything toggled here is <b>presentation ceremony and lexical gating</b>, never a real
 * safety boundary. What these flags can turn off:
 * <ul>
 *   <li>repeating the "this is an authorized capsule, not a live person" notice on every single
 *       capsule reply (the first reply of a session always still carries it);</li>
 *   <li>prefixing ordinary replies with a model-authored boundary sentence when no risk flag
 *       was actually raised;</li>
 *   <li>collapsing a capsule to {@code UNSUPPORTED} — i.e. "I can't say anything" — merely because
 *       the visitor's wording shares no literal token with an already-authorized claim.</li>
 * </ul>
 *
 * <p>What these flags can <b>never</b> turn off, by construction (no flag is read on those paths):
 * crisis/self-harm safety interception, owner-scoped authorization, blocked-topic enforcement,
 * contact-info redaction, the prompt-leakage output gate, and the rule that only compiled,
 * explicitly authorized Genome content may ever reach a visitor.
 *
 * <p>Defaults are experience-first because the current delivery priority is a live demo where a
 * capsule that constantly declines and disclaims reads as broken rather than as careful. An
 * operator who wants the older ceremonial behaviour sets
 * {@code INNER_COSMOS_EXPERIENCE_FIRST=false}.
 */
@Configuration
@ConfigurationProperties(prefix = "inner-cosmos.experience")
public class ExperienceModeProperties {

    /**
     * Master switch. Every facet below defaults to {@code null} ("follow the master switch"), so
     * flipping this one value moves the whole product between experience-first and ceremonial
     * behaviour, while any facet can still be pinned independently.
     */
    private boolean experienceFirst = true;

    /** Repeat the AI-capsule identity notice on every capsule reply, not only the first. */
    private Boolean repeatCapsuleIdentityNotice;

    /** Prefix ordinary capsule replies with the model's boundaryNotice even with no risk flag. */
    private Boolean verboseBoundaryNotices;

    /**
     * When no authorized claim matches the visitor's wording literally, still speak from the
     * capsule's own compiled voice + top authorized self-description instead of declaring
     * UNSUPPORTED. Only already-authorized content is ever used either way.
     */
    private Boolean expressiveGrounding;

    /** Default privacy tier proposed for a new capsule compile ({@code STRICT}/{@code BALANCED}). */
    private String defaultCapsulePrivacy;

    public boolean isExperienceFirst() { return experienceFirst; }

    public void setExperienceFirst(boolean experienceFirst) { this.experienceFirst = experienceFirst; }

    public Boolean getRepeatCapsuleIdentityNotice() { return repeatCapsuleIdentityNotice; }

    public void setRepeatCapsuleIdentityNotice(Boolean value) { this.repeatCapsuleIdentityNotice = value; }

    public Boolean getVerboseBoundaryNotices() { return verboseBoundaryNotices; }

    public void setVerboseBoundaryNotices(Boolean value) { this.verboseBoundaryNotices = value; }

    public Boolean getExpressiveGrounding() { return expressiveGrounding; }

    public void setExpressiveGrounding(Boolean value) { this.expressiveGrounding = value; }

    public String getDefaultCapsulePrivacy() { return defaultCapsulePrivacy; }

    public void setDefaultCapsulePrivacy(String value) { this.defaultCapsulePrivacy = value; }

    // ── resolved decisions (what callers actually read) ──────────────────────────

    /** True when the identity notice must be appended to every capsule reply. */
    public boolean repeatCapsuleIdentityNotice() {
        return repeatCapsuleIdentityNotice != null ? repeatCapsuleIdentityNotice : !experienceFirst;
    }

    /** True when a model-authored boundaryNotice may prefix a reply that raised no risk flag. */
    public boolean verboseBoundaryNotices() {
        return verboseBoundaryNotices != null ? verboseBoundaryNotices : !experienceFirst;
    }

    /** True when an unmatched turn may still speak from the capsule's authorized voice. */
    public boolean expressiveGrounding() {
        return expressiveGrounding != null ? expressiveGrounding : experienceFirst;
    }

    /** Privacy tier a fresh capsule compile starts from. */
    public String defaultCapsulePrivacy() {
        if (defaultCapsulePrivacy != null && !defaultCapsulePrivacy.isBlank()) {
            return defaultCapsulePrivacy.trim().toUpperCase(java.util.Locale.ROOT);
        }
        return experienceFirst ? "BALANCED" : "STRICT";
    }
}

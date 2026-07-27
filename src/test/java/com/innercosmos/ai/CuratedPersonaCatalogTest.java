package com.innercosmos.ai;

import com.innercosmos.ai.capsule.CuratedPersonaCatalog;
import com.innercosmos.util.VisitorLanguage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three classroom showcase capsules are the part of the demo an audience judges the product
 * by, so their authored voice layer is pinned here: who may take it, that the three are actually
 * distinct, and that runtime copy follows the visitor's language instead of defaulting to Chinese.
 */
class CuratedPersonaCatalogTest {

    @Test
    @DisplayName("the three seeded showcase owners resolve to their authored persona")
    void resolvesSeededShowcaseOwners() {
        assertThat(CuratedPersonaCatalog.resolve("Lin Che", "DEMO"))
                .get().extracting(CuratedPersonaCatalog.CuratedPersona::key).isEqualTo("lin-che");
        assertThat(CuratedPersonaCatalog.resolve("Shen Yan", "SHOWCASE"))
                .get().extracting(CuratedPersonaCatalog.CuratedPersona::key).isEqualTo("shen-yan");
        assertThat(CuratedPersonaCatalog.resolve("Xia Yu", "SHOWCASE"))
                .get().extracting(CuratedPersonaCatalog.CuratedPersona::key).isEqualTo("xia-yu");
    }

    @Test
    @DisplayName("per-visitor sandbox copies keep the authored persona of the story they cloned")
    void resolvesSandboxCopies() {
        assertThat(CuratedPersonaCatalog.resolve("lin che", "SANDBOX")).isPresent();
        assertThat(CuratedPersonaCatalog.resolve("Xia Yu", "sandbox")).isPresent();
    }

    @Test
    @DisplayName("a real registered user cannot borrow a curated voice by taking the nickname")
    void refusesOrdinaryAccounts() {
        assertThat(CuratedPersonaCatalog.resolve("Lin Che", "USER")).isEmpty();
        assertThat(CuratedPersonaCatalog.resolve("Lin Che", null)).isEmpty();
        assertThat(CuratedPersonaCatalog.resolve(null, "DEMO")).isEmpty();
        assertThat(CuratedPersonaCatalog.resolve("Someone Else", "DEMO")).isEmpty();
    }

    @Test
    @DisplayName("each authored persona carries its own life material, not a shared template")
    void personasAreDistinct() {
        String linChe = CuratedPersonaCatalog.byKey("lin-che").orElseThrow().personaPrompt();
        String shenYan = CuratedPersonaCatalog.byKey("shen-yan").orElseThrow().personaPrompt();
        String xiaYu = CuratedPersonaCatalog.byKey("xia-yu").orElseThrow().personaPrompt();

        assertThat(List.of(linChe, shenYan, xiaYu)).doesNotHaveDuplicates();
        // Anchors that must stay tied to exactly one of the three, so a listener comparing two
        // capsules in the same session hears two different lives rather than one voice twice.
        assertThat(linChe).containsIgnoringCase("twilight walks");
        assertThat(shenYan).containsIgnoringCase("Wednesday walk");
        assertThat(xiaYu).containsIgnoringCase("first independent shift");
        assertThat(shenYan).doesNotContainIgnoringCase("twilight walks");
        assertThat(xiaYu).doesNotContainIgnoringCase("Wednesday walk");

        for (String persona : List.of(linChe, shenYan, xiaYu)) {
            assertThat(persona).contains("first person");
            assertThat(persona).contains("NEVER");
        }
    }

    @Test
    @DisplayName("unknown keys stay on the ordinary compiled-persona path")
    void unknownKeyIsEmpty() {
        assertThat(CuratedPersonaCatalog.byKey("someone-else")).isEqualTo(Optional.empty());
        assertThat(CuratedPersonaCatalog.byKey(null)).isEmpty();
    }

    @Test
    @DisplayName("runtime copy mirrors the visitor's language instead of defaulting to Chinese")
    void visitorLanguageMirrorsTheMessage() {
        assertThat(VisitorLanguage.detect("我今天很累")).isEqualTo(VisitorLanguage.CHINESE);
        assertThat(VisitorLanguage.detect("I keep saying yes to everything")).isEqualTo(VisitorLanguage.ENGLISH);
        assertThat(VisitorLanguage.detect("mixed 中文 and english")).isEqualTo(VisitorLanguage.CHINESE);
        assertThat(VisitorLanguage.detect("")).isEqualTo(VisitorLanguage.ENGLISH);
        assertThat(VisitorLanguage.detect(null)).isEqualTo(VisitorLanguage.ENGLISH);

        assertThat(VisitorLanguage.pick(VisitorLanguage.ENGLISH, "中文", "English")).isEqualTo("English");
        assertThat(VisitorLanguage.pick(VisitorLanguage.CHINESE, "中文", "English")).isEqualTo("中文");
    }
}

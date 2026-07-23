package com.innercosmos.ai.prompt;

import com.innercosmos.ai.structured.StructuredAiResults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class StructuredOutputParserTest {
    @Test
    void parsesJsonFromMarkdownAndDirtyText() {
        Sample markdown = StructuredOutputParser.parse("```json\n{\"value\":\"ok\"}\n```", Sample.class);
        Sample dirty = StructuredOutputParser.parse("before {\"value\":\"wrapped\"} after", Sample.class);
        Sample reasoning = StructuredOutputParser.parse("<think>hidden</think>{\"value\":\"clean\"}", Sample.class);
        Sample arrayWrapped = StructuredOutputParser.parse("[{\"value\":\"single\"}]", Sample.class);

        assertEquals("ok", markdown.value);
        assertEquals("wrapped", dirty.value);
        assertEquals("clean", reasoning.value);
        assertEquals("single", arrayWrapped.value);
    }

    @Test
    void returnsNullForInvalidJson() {
        assertNull(StructuredOutputParser.parse("not json", Sample.class));
    }

    /**
     * Real-provider regression (G8.LOCAL-COMPLETE): DeepSeek's MEMORY_SETTLEMENT output
     * names the event title field {@code title} (not the {@code eventTitle} the Java
     * contract uses) and emits extra vendor fields. The parser must tolerate both --
     * mapping {@code title}->{@code eventTitle} and ignoring unknown fields -- instead
     * of failing the whole settlement (which drops the memory + its pgvector embedding).
     */
    @Test
    void toleratesDeepSeekSettlementFieldNamesAndExtraFields() {
        // Captured shape from a real DeepSeek turn (eventCards[].title + a stray extra field).
        String raw = "{\"memoryCard\":{\"title\":\"今日沉淀\",\"summary\":\"帮同事解bug\"},"
                + "\"eventCards\":[{\"title\":\"同事的bug\",\"eventSummary\":\"棘手但解决了\","
                + "\"eventTimeLabel\":\"下午\",\"scene\":\"办公室\",\"extraVendorField\":\"ignore-me\"}],"
                + "\"dailyTheme\":\"成就感\"}";
        StructuredAiResults.SettlementResult result =
                StructuredOutputParser.parse(raw, StructuredAiResults.SettlementResult.class);

        org.junit.jupiter.api.Assertions.assertNotNull(result);
        assertEquals("今日沉淀", result.memoryCard.title);
        assertFalse(result.eventCards.isEmpty(), "eventCards should be parsed");
        StructuredAiResults.Event event = result.eventCards.get(0);
        // title MUST map onto eventTitle (the field downstream code reads), not be dropped.
        assertEquals("同事的bug", event.eventTitle);
        assertEquals("棘手但解决了", event.eventSummary);
        assertEquals("成就感", result.dailyTheme);
    }

    public static class Sample {
        public String value;
    }
}

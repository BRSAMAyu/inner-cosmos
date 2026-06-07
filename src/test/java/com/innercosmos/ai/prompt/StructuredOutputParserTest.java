package com.innercosmos.ai.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    public static class Sample {
        public String value;
    }
}

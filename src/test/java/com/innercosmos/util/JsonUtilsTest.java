package com.innercosmos.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilsTest {

    // toJson converts an object to a JSON string
    @Test
    void toJsonConvertsObjectToJsonString() {
        TestDto dto = new TestDto("alpha", 7);
        String json = JsonUtils.toJson(dto);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"alpha\""));
        assertTrue(json.contains("\"value\":7"));
    }

    // toJson handles null by returning "{}" (actual API behaviour)
    @Test
    void toJsonHandlesNull() {
        String json = JsonUtils.toJson(null);
        assertEquals("{}", json);
    }

    // toJson handles a Map
    @Test
    void toJsonHandlesMap() {
        Map<String, Object> map = Map.of("a", 1, "b", "two");
        String json = JsonUtils.toJson(map);

        assertNotNull(json);
        assertTrue(json.contains("\"a\":1"));
        assertTrue(json.contains("\"b\":\"two\""));
    }

    // toJson handles a List
    @Test
    void toJsonHandlesList() {
        List<String> list = List.of("x", "y", "z");
        String json = JsonUtils.toJson(list);

        assertNotNull(json);
        assertTrue(json.contains("\"x\""));
        assertTrue(json.contains("\"y\""));
        assertTrue(json.contains("\"z\""));
    }

    // toJson handles nested structures
    @Test
    void toJsonHandlesNestedStructures() {
        Map<String, Object> nested = Map.of(
                "outer", "level",
                "list", List.of(1, 2, 3),
                "inner", Map.of("k", "v")
        );
        String json = JsonUtils.toJson(nested);

        assertNotNull(json);
        assertTrue(json.contains("\"outer\":\"level\""));
        assertTrue(json.contains("\"inner\":{\"k\":\"v\"}"));
        assertTrue(json.contains("\"list\":[1,2,3]"));
    }

    // toJson escapes special characters correctly
    @Test
    void toJsonEscapesSpecialCharacters() {
        String input = "line1\nline2 \"quoted\"";
        String json = JsonUtils.toJson(input);

        assertNotNull(json);
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\"quoted\\\""));
    }

    // Independent ObjectMapper produces the same shape for known input
    @Test
    void independentMapperMatchesOutputShape() {
        TestDto dto = new TestDto("match", 1);
        String produced = JsonUtils.toJson(dto);

        // Independently serialize with a fresh mapper and compare structurally
        ObjectMapper fresh = new ObjectMapper();
        String reference;
        try {
            reference = fresh.writeValueAsString(dto);
        } catch (Exception exception) {
            throw new AssertionError("reference serialization failed", exception);
        }
        assertEquals(reference, produced);
    }

    // Test DTO used for round-trip and serialization tests.
    public static class TestDto {
        public String name;
        public int value;

        public TestDto() {
        }

        public TestDto(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
package com.innercosmos.ai.capsule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.structured.StructuredAiResults;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapsuleCalibrationPolicyTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rawFeedbackBecomesOnlyClosedCodesAndProvenanceAccumulatesAcrossVersions() throws Exception {
        String raw = "更克制、更直接、短一点，也不要替我做决定。PRIVATE_RAW_MARKER";
        StructuredAiResults.CapsuleCalibrationResult deterministic =
                CapsuleCalibrationPolicy.deterministic(raw, "TONE_WRONG");
        String firstSignals = objectMapper.writeValueAsString(
                CapsuleCalibrationPolicy.canonical(objectMapper, deterministic, "TONE_WRONG"));

        String v2Style = CapsuleCalibrationPolicy.mergeIntoStyle(
                objectMapper, "{\"voice\":\"fresh inference\"}", List.of(firstSignals), List.of(41L), 2);
        JsonNode v2 = objectMapper.readTree(v2Style);
        assertTrue(v2.path("calibration").path("toneCodes").toString().contains("RESTRAINED"));
        assertTrue(v2.path("calibration").path("toneCodes").toString().contains("DIRECT"));
        assertEquals("SHORT", v2.path("calibration").path("responseLengthCode").asText());
        assertTrue(v2.path("calibration").path("avoidBehaviorCodes").toString().contains("SPEAKING_FOR_OWNER"));
        assertFalse(v2Style.contains("PRIVATE_RAW_MARKER"));

        String v3Base = CapsuleCalibrationPolicy.carryForwardCalibration(
                objectMapper, "{\"voice\":\"newly inferred again\"}", v2Style);
        StructuredAiResults.CapsuleCalibrationResult privacy =
                CapsuleCalibrationPolicy.deterministic("少放个人细节", "TOO_EXPOSED");
        String secondSignals = objectMapper.writeValueAsString(
                CapsuleCalibrationPolicy.canonical(objectMapper, privacy, "TOO_EXPOSED"));
        String v3Style = CapsuleCalibrationPolicy.mergeIntoStyle(
                objectMapper, v3Base, List.of(secondSignals), List.of(52L), 3);
        JsonNode calibration = objectMapper.readTree(v3Style).path("calibration");

        assertEquals(List.of(41L, 52L), objectMapper.convertValue(
                calibration.path("sourceFeedbackIds"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class)));
        assertTrue(calibration.path("boundaryCodes").toString().contains("MINIMIZE_PERSONAL_DETAIL"));
        assertTrue(calibration.path("boundaryCodes").toString().contains("NO_CONTACT_DETAILS"));
        assertEquals("newly inferred again", objectMapper.readTree(v3Style).path("voice").asText(),
                "freshly inferred non-calibration style must replace stale parent style");
    }
}

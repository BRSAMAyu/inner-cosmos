package com.innercosmos.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A5: the source-&gt;derivative registry must stay COMPLETE — every DERIVATIVE_* type the receipt
 * service knows about has to be registered (with a valid subject and action), so a newly-compiled
 * derivative cannot be added without also declaring how it is retracted and audited. This test fails
 * the build the moment that invariant is broken.
 */
class DataDerivativeRegistryTest {

    @Test
    void everyReceiptDerivativeConstantIsRegistered() throws Exception {
        Set<String> registered = DataDerivativeRegistry.edges().stream()
                .map(DataDerivativeRegistry.Edge::derivativeType).collect(Collectors.toSet());
        for (String derivative : constantsWithPrefix("DERIVATIVE_")) {
            assertTrue(DataDerivativeRegistry.isRegisteredDerivative(derivative),
                    "derivative type '" + derivative + "' is defined on DataRetractionReceiptService but "
                            + "not registered in DataDerivativeRegistry — declare its source + retraction action. "
                            + "Registered: " + registered);
        }
    }

    @Test
    void everyEdgeUsesValidSubjectAndActionConstants() throws Exception {
        Set<String> subjects = constantsWithPrefix("SUBJECT_");
        Set<String> actions = constantsWithPrefix("ACTION_");
        for (DataDerivativeRegistry.Edge edge : DataDerivativeRegistry.edges()) {
            assertTrue(subjects.contains(edge.subjectType()), "unknown subject: " + edge.subjectType());
            assertTrue(actions.contains(edge.defaultAction()), "unknown action: " + edge.defaultAction());
            assertFalse(edge.description().isBlank(), "each edge must document itself");
        }
    }

    @Test
    void groupsDerivativesBySubject() {
        List<DataDerivativeRegistry.Edge> capsuleEdges =
                DataDerivativeRegistry.edgesForSubject(DataRetractionReceiptService.SUBJECT_CAPSULE);
        Set<String> capsuleDerivatives = capsuleEdges.stream()
                .map(DataDerivativeRegistry.Edge::derivativeType).collect(Collectors.toSet());
        assertEquals(Set.of(
                DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX,
                DataRetractionReceiptService.DERIVATIVE_CAPSULE_PERSONA,
                DataRetractionReceiptService.DERIVATIVE_GENOME), capsuleDerivatives);
        assertEquals(List.of(DataRetractionReceiptService.DERIVATIVE_MEMORY_EMBEDDING),
                DataDerivativeRegistry.edgesForSubject(DataRetractionReceiptService.SUBJECT_MEMORY).stream()
                        .map(DataDerivativeRegistry.Edge::derivativeType).toList());
        assertFalse(DataDerivativeRegistry.isRegisteredDerivative("NOT_A_DERIVATIVE"));
    }

    private static Set<String> constantsWithPrefix(String prefix) throws IllegalAccessException {
        Set<String> values = new java.util.HashSet<>();
        for (Field field : DataRetractionReceiptService.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class
                    && field.getName().startsWith(prefix)) {
                values.add((String) field.get(null));
            }
        }
        return values;
    }
}

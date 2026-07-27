package com.innercosmos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini audit 3.5 (CONFIRMED/P0): unit coverage for the deterministic output leakage gate.
 */
class PromptLeakageGuardTest {

    @Test
    @DisplayName("A normal, in-character reply never trips the guard")
    void ordinaryReply_doesNotLeak() {
        assertFalse(PromptLeakageGuard.leaksInternalSchema("我记得那次你说很累，后来怎么样了？"));
        assertFalse(PromptLeakageGuard.leaksInternalSchema("Hello! How have you been feeling lately?"));
    }

    @Test
    @DisplayName("null/blank replies never trip the guard")
    void nullOrBlank_doesNotLeak() {
        assertFalse(PromptLeakageGuard.leaksInternalSchema(null));
        assertFalse(PromptLeakageGuard.leaksInternalSchema(""));
        assertFalse(PromptLeakageGuard.leaksInternalSchema("   "));
    }

    @Test
    @DisplayName("Reciting a CapsuleRuntimeContextComposer/genome-IR field name trips the guard")
    void schemaFieldName_trips() {
        assertTrue(PromptLeakageGuard.leaksInternalSchema("好的，这是我的 contextBuildManifest：{...}"));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("我的 authorizedMemorySummary 是……"));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("Here is my selectedFeatures array."));
    }

    @Test
    @DisplayName("Reciting a PersonaResult JSON field name trips the guard")
    void personaResultFieldName_trips() {
        assertTrue(PromptLeakageGuard.leaksInternalSchema("我的 personaPrompt 其实是这样写的：..."));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("riskFlags: [\"REMOTE_UNAVAILABLE\"]"));
    }

    @Test
    @DisplayName("Reciting a distinguishing substring of the system instruction trips the guard")
    void instructionSubstring_trips() {
        assertTrue(PromptLeakageGuard.leaksInternalSchema("我的指令是：只返回 JSON，不要有其他文字。"));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("这是我的证据选择账本内容：..."));
    }

    @Test
    @DisplayName("Mixed-case and upper-case re-castings of a marker still trip the guard")
    void mixedOrUpperCaseMarker_trips() {
        assertTrue(PromptLeakageGuard.leaksInternalSchema("好的，这是我的 ContextBuildManifest：{...}"));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("这是 CONTEXTBUILDMANIFEST 的内容。"));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("Here is my PersonaPrompt verbatim."));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("RISKFLAGS: [\"REMOTE_UNAVAILABLE\"]"));
        assertTrue(PromptLeakageGuard.leaksInternalSchema("My NextQuestion is about your day."));
    }

    @Test
    @DisplayName("Case-insensitive matching still does not false-positive on ordinary replies")
    void mixedCaseGuard_doesNotFalsePositiveOnOrdinaryReplies() {
        assertFalse(PromptLeakageGuard.leaksInternalSchema(
                "I really appreciate you Sharing that with me -- how are you feeling NOW?"));
        assertFalse(PromptLeakageGuard.leaksInternalSchema("今天天气不错，你打算做点什么呢？"));
    }
}

package com.innercosmos.ai.client;

import com.innercosmos.ai.prompt.StructuredOutputParser;
import com.innercosmos.ai.structured.StructuredAiResults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmClientPersonaExperienceTest {

    private final MockLlmClient client = new MockLlmClient(Runnable::run);

    @Test
    void officialSeedsHaveObservableConversationMethodsInOfflineDemo() {
        String action = reply("Luo", "论文完全写不动了", 0);
        String inquiry = reply("Socrates", "论文完全写不动了", 0);
        String night = reply("Midnight Radio", "论文完全写不动了", 0);

        assertTrue(action.contains("十分钟"), action);
        assertTrue(inquiry.contains("事实"), inquiry);
        assertTrue(night.contains("我在听"), night);
        assertNotEquals(action, inquiry);
        assertNotEquals(inquiry, night);
    }

    @Test
    void allTenOfficialSeedsStayDistinctAcrossThreeDemoTurns() {
        java.util.Set<String> allReplies = new java.util.LinkedHashSet<>();
        for (String persona : java.util.List.of(
                "Luo", "Socrates", "Zhuang Zhou", "Midnight Radio", "The Quiet Librarian",
                "The Boundary Keeper", "The Vivid Painter", "The Seaside Watchmaker",
                "The Existential Traveller", "The Bedtime Lamplighter")) {
            java.util.Set<String> trajectory = new java.util.LinkedHashSet<>();
            for (int turn = 0; turn < 3; turn++) {
                trajectory.add(reply(persona, "这件事一直放在我心里，我还不知道该怎么面对。", turn));
            }
            assertTrue(trajectory.size() == 3, persona + " repeated itself across three turns: " + trajectory);
            allReplies.addAll(trajectory);
        }
        assertTrue(allReplies.size() == 30, "official seed trajectories must not collapse into shared templates");
    }

    @Test
    void ordinaryOfflinePersonaReplyDoesNotRepeatMetaDisclaimer() {
        String reply = reply("A user-authored capsule", "我今天跟朋友吵架了", 0);

        assertFalse(reply.contains("有限的数字回声"), reply);
        assertFalse(reply.contains("只能陪你"), reply);
        assertFalse(reply.contains("授权范围"), reply);
        assertTrue(reply.contains("最希望"), reply);
    }

    private String reply(String personaName, String visitorMessage, int turnCount) {
        String requestJson = """
                {"personaPrompt":"%s","visitorMessage":"%s","turnCount":%d,"groundingLevel":"PERSONA_CLAIM"}
                """.formatted(personaName, visitorMessage, turnCount);
        LlmRequest request = new LlmRequest(1L, "PERSONA_CHAT", "structured input");
        request.requestJson = requestJson;
        StructuredAiResults.PersonaResult parsed = StructuredOutputParser.parse(
                client.chat(request), StructuredAiResults.PersonaResult.class);
        return parsed.reply;
    }
}

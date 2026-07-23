package com.innercosmos.service;

import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.vo.CapsulePreviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 3.1 (CONFIRMED/P0) + 3.2 (PARTIAL/P0): before this fix, capsule creation fed
 * memory title/summary straight into persona generation with, at most,
 * DataMaskingUtils.maskContact's digit/email-only masking on contextPreviewJson -- personaPrompt
 * itself (the text actually sent to the persona-synthesis LLM provider and later reused as the
 * persona-chat system prompt) had NO masking at all. A stronger masker (DataMaskingServiceImpl)
 * already existed but was wired only to the disconnected POST /api/capsule/preview-from-memory
 * endpoint, never to the real compile path.
 *
 * <p>These are the adversarial canary tests both 3.1 and 3.2 require: plant a memory containing
 * realistic PII (phone, email, name-reveal pattern, school, QQ/WeChat) and prove it never
 * survives into (a) the persona-synthesis provider call, (b) any persisted genome artifact, (c)
 * the owner-mirror preview, or (d) the live persona-chat provider context on a real visitor turn
 * -- closing 3.2 as a downstream consequence of 3.1's fix, per the audit's own framing.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class CapsuleP1P2PrivacyBoundaryTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapsuleService capsuleService;

    @Autowired
    private CapsuleGenomeService genomeService;

    @Autowired
    private PersonaChatService personaChatService;

    // Gemini audit 3.2: capsule creation's own persona synthesis (CapsuleAgent.generateUserPersona)
    // calls LlmClient directly, never StructuredAiService -- so mocking StructuredAiService here
    // only intercepts the PersonaChat turn's provider call below, leaving createFromMemory's own
    // real (Mock-provider) compilation path untouched.
    @MockBean
    private StructuredAiService structuredAiService;

    private static final String CANARY_SUMMARY =
            "我叫王小明，电话是13800001111，邮箱是leak-canary@example.com，"
                    + "就读于隔壁的清华大学，QQ:123456789，微信:wxid_canary，"
                    + "这件事让我压力很大，最近总是睡不着。";
    private static final String CANARY_PHONE = "13800001111";
    private static final String CANARY_EMAIL = "leak-canary@example.com";
    private static final String CANARY_SCHOOL = "清华大学";
    private static final String CANARY_QQ = "123456789";
    private static final String CANARY_WECHAT = "wxid_canary";

    /**
     * Contact-info masking (phone/email) is unconditional in DataMaskingServiceImpl#maskText --
     * its final line always runs maskContactInfo regardless of tier. This is the floor every
     * privacy tier guarantees, so every test may assert it regardless of which tier it used.
     */
    private void assertNoCanaryLeak(String haystack, String label) {
        assertNotNull(haystack, label + " must not be null");
        assertFalse(haystack.contains(CANARY_PHONE), label + " leaked the canary phone number");
        assertFalse(haystack.contains(CANARY_EMAIL), label + " leaked the canary email");
    }

    /**
     * School/QQ/WeChat masking (maskPatterns) is STRICT-only by design -- BALANCED only adds
     * name-reveal-pattern masking (maskNames) on top of contact info. Only assert these where the
     * privacy tier under test is actually STRICT.
     */
    private void assertNoStrictTierLeak(String haystack, String label) {
        assertFalse(haystack.contains(CANARY_SCHOOL), label + " (STRICT) leaked the canary school name");
        assertFalse(haystack.contains(CANARY_QQ), label + " (STRICT) leaked the canary QQ number");
        assertFalse(haystack.contains(CANARY_WECHAT), label + " (STRICT) leaked the canary WeChat id");
    }

    private Long seedUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedCanaryMemory(Long owner) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, ?, ?, 'ACTIVE', 1, 'AURORA_PRIVATE')
                """, owner, "关于压力的记忆", CANARY_SUMMARY);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }

    @Test
    @Transactional
    @DisplayName("3.1: createFromMemory (STRICT privacy) never persists the raw PII canary into personaPrompt or contextPreviewJson")
    void createFromMemory_strictPrivacy_scrubsCanaryFromPersistedGenome() {
        Long owner = seedUser("p1p2-strict");
        Long memory = seedCanaryMemory(owner);

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(memory);
        request.privacyLevel = "STRICT";

        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

        assertNoCanaryLeak(capsule.personaPrompt, "personaPrompt");
        assertNoCanaryLeak(capsule.contextPreviewJson, "contextPreviewJson");
        assertNoStrictTierLeak(capsule.personaPrompt, "personaPrompt");

        CapsuleGenomeVersion genome = genomeService.current(capsule.id);
        assertNotNull(genome, "a runnable ACTIVE genome must exist after createFromMemory");
        assertNoCanaryLeak(genome.compiledPersonaPrompt, "compiledPersonaPrompt");
        assertNoCanaryLeak(genome.contextPreviewJson, "genome.contextPreviewJson");
    }

    @Test
    @Transactional
    @DisplayName("3.1: createFromMemory with the real-world default (no explicit privacyLevel -> BALANCED) still masks contact info and name-reveal patterns")
    void createFromMemory_defaultPrivacy_stillMasksContactAndNames() {
        Long owner = seedUser("p1p2-default");
        Long memory = seedCanaryMemory(owner);

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(memory);
        // privacyLevel deliberately left null -- safePrivacy() resolves this to "BALANCED", the
        // real most-common case (an owner who never touches the privacy dropdown).

        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

        assertNoCanaryLeak(capsule.personaPrompt, "personaPrompt (default privacy)");
        assertFalse(capsule.personaPrompt.contains("叫王小明"),
                "BALANCED (the real default tier) must mask the name-reveal pattern '叫X', not just contact info");
    }

    @Test
    @Transactional
    @DisplayName("3.1: recompileGenome re-scrubs using the capsule's OWN configured privacy tier, not a hardcoded default")
    void recompileGenome_usesCapsulesOwnPrivacyLevel() {
        Long owner = seedUser("p1p2-recompile");
        Long memory = seedCanaryMemory(owner);

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(memory);
        request.privacyLevel = "STRICT";
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);
        assertNoStrictTierLeak(capsule.personaPrompt, "sanity: personaPrompt at creation");

        CapsuleGenomeVersion recompiled = capsuleService.recompileGenome(owner, capsule.id, List.of(memory));

        assertNoCanaryLeak(recompiled.compiledPersonaPrompt, "recompiled personaPrompt");
        assertNoStrictTierLeak(recompiled.compiledPersonaPrompt,
                "recompiled personaPrompt (recompileGenome must keep using the capsule's own STRICT tier, not silently downgrade)");
    }

    @Test
    @DisplayName("3.1: previewUserMirror (owner-only mirror preview) scrubs before both the provider call and display, matching what createFromMemory would actually produce")
    void previewUserMirror_scrubsBeforeProviderCallAndDisplay() {
        Long owner = seedUser("p1p2-mirror");
        seedCanaryMemory(owner);

        CapsulePreviewVO preview = capsuleService.previewUserMirror(owner);

        assertNoCanaryLeak(preview.personaPromptDraft, "previewUserMirror personaPromptDraft");
        assertNoCanaryLeak(preview.abstractSummary, "previewUserMirror abstractSummary");
    }

    @Test
    @Transactional
    @DisplayName("3.2 (downstream of 3.1): a real persona-chat turn never sends the raw PII canary to the provider, even though the memory was authorized")
    void personaChatTurn_neverSendsRawCanaryToProvider() {
        Long owner = seedUser("p1p2-runtime-owner");
        Long visitor = seedUser("p1p2-runtime-visitor");
        Long memory = seedCanaryMemory(owner);

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(memory);
        request.privacyLevel = "STRICT";
        // safeVisibility() now fails CLOSED to PRIVATE by default (2026-07-24 8-agent audit
        // P2-11), so this test -- which specifically exercises a real visitor reaching the
        // capsule -- must opt in to PUBLIC explicitly rather than relying on the old fail-open
        // default.
        request.visibilityStatus = "PUBLIC";
        request.isPublic = true;
        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

        AtomicReference<Map<String, Object>> capturedContext = new AtomicReference<>();
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = inv.getArgument(3, Map.class);
            capturedContext.set(context);
            StructuredAiResults.PersonaResult result = new StructuredAiResults.PersonaResult();
            result.reply = "我听见了这段片段，但不会替你说出具体细节。";
            result.boundaryNotice = "";
            result.letterSuggested = false;
            return result;
        });

        PersonaChatSession session = personaChatService.create(visitor, capsule.id);
        personaChatService.reply(visitor, session.id, "你最近是不是压力很大？");

        Map<String, Object> context = capturedContext.get();
        assertNotNull(context, "sanity: the provider must actually have been called for this turn");
        String contextText = context.toString();
        assertFalse(contextText.contains(CANARY_PHONE), "persona-chat runtime context leaked the canary phone number to the provider");
        assertFalse(contextText.contains(CANARY_EMAIL), "persona-chat runtime context leaked the canary email to the provider");
        assertFalse(contextText.contains(CANARY_WECHAT), "persona-chat runtime context leaked the canary WeChat id to the provider");
        assertFalse(contextText.contains(CANARY_SCHOOL), "persona-chat runtime context leaked the canary school name to the provider");
    }

    @Test
    @Transactional
    @DisplayName("2026-07-24 8-agent audit P2-11: an unrecognized visibilityStatus value must fail CLOSED to PRIVATE, never PUBLIC")
    void unrecognizedVisibilityStatusNeverResultsInPublicCapsule() {
        Long owner = seedUser("p2-11-fail-closed");
        Long memory = seedCanaryMemory(owner);

        CapsuleCreateRequest request = new CapsuleCreateRequest();
        request.memoryIds = List.of(memory);
        request.privacyLevel = "STRICT";
        request.visibilityStatus = "DRAFT"; // not a recognized value
        request.isPublic = false;

        EchoCapsule capsule = capsuleService.createFromMemory(owner, request);

        assertEquals("PRIVATE", capsule.visibilityStatus,
                "an unrecognized visibilityStatus must default to PRIVATE, not PUBLIC");
    }
}

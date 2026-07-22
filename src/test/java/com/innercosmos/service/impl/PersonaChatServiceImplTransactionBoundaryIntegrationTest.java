package com.innercosmos.service.impl;

import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.service.PersonaChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 2.4 (CONFIRMED/P0): PersonaChatServiceImpl#reply() used to run entirely inside
 * one @Transactional method, including the external AI provider RPC -- a slow or hanging
 * provider held a pooled DB connection (and the quota/turn reservation's row locks) for the
 * whole call. This test proves the actual fix against a real Spring transaction manager (a
 * Mockito unit test with a mocked PlatformTransactionManager cannot observe real transaction
 * synchronization): the provider call must observe NO active Spring transaction.
 *
 * <p>Deliberately not annotated {@code @Transactional} -- unlike the sibling
 * CapsuleQuotaIntegrationTest, this test must NOT run inside its own ambient transaction, or the
 * assertion below would trivially see a transaction active for the wrong reason (the test's own
 * wrapper) rather than proving reply() itself opens none around the provider call. This mirrors
 * production reality: PersonaChatController never wraps the call in a transaction either.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class PersonaChatServiceImplTransactionBoundaryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PersonaChatService personaChatService;

    @MockBean
    private StructuredAiService structuredAiService;

    private Long seedUser() {
        String username = "txn-boundary-it-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedCapsule(Long ownerId) {
        jdbc.update(
                "INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "visibility_status, is_public, conversation_limit_per_day) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                ownerId, "USER_CAPSULE", "txn-boundary-echo", "test intro", "PUBLIC", true, 30);
        return jdbc.queryForObject(
                "SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, ownerId);
    }

    @Test
    @DisplayName("Gemini audit 2.4: the AI provider RPC runs with no Spring transaction open")
    void providerCallRunsWithNoTransactionOpen() {
        Long owner = seedUser();
        Long visitor = seedUser();
        Long capsuleId = seedCapsule(owner);

        AtomicBoolean transactionWasActiveDuringCall = new AtomicBoolean(true);
        AtomicBoolean providerWasCalled = new AtomicBoolean(false);
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            providerWasCalled.set(true);
            transactionWasActiveDuringCall.set(TransactionSynchronizationManager.isActualTransactionActive());
            StructuredAiResults.PersonaResult result = new StructuredAiResults.PersonaResult();
            result.reply = "real-provider-simulated-reply";
            result.boundaryNotice = "";
            result.letterSuggested = false;
            return result;
        });

        PersonaChatSession session = personaChatService.create(visitor, capsuleId);
        personaChatService.reply(visitor, session.id, "hello");

        assertTrue(providerWasCalled.get(), "test setup sanity: the provider mock must actually have been invoked");
        assertFalse(transactionWasActiveDuringCall.get(),
                "the AI provider RPC must not run inside an open Spring transaction -- a slow or "
                        + "hanging provider must never hold a pooled DB connection or the "
                        + "quota/turn reservation's row locks for the duration of the call");
    }
}

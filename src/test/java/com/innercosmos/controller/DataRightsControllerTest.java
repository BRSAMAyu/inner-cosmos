package com.innercosmos.controller;

import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.service.DataRetractionReceiptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner-facing data-rights audit trail (G5 PROFILE-PROPAGATION made visible): auth required, and the
 * endpoint returns only the caller's own receipts, newest first, with no sensitive payload.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.datasource.url=jdbc:h2:mem:testdatarights;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class DataRightsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired DataRetractionReceiptService retractionReceiptService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void receipts_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/me/data-rights/receipts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receipts_returnsOnlyOwnerScopedRowsNewestFirst() throws Exception {
        String username = "drights_" + UUID.randomUUID().toString().substring(0, 8);
        MockHttpSession session = register(username);
        Long ownerId = jdbc.queryForObject("SELECT id FROM tb_user WHERE username=?", Long.class, username);
        Long otherId = ownerId + 100000; // an id that is definitely not the caller

        retractionReceiptService.record(otherId, DataRetractionReceiptService.SUBJECT_CAPSULE, 9L,
                DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX,
                DataRetractionReceiptService.ACTION_ERASED, 1, "someone else's capsule");
        retractionReceiptService.record(ownerId, DataRetractionReceiptService.SUBJECT_MEMORY, 1L,
                DataRetractionReceiptService.DERIVATIVE_MEMORY_EMBEDDING,
                DataRetractionReceiptService.ACTION_CLEARED, 2, "memory superseded by user correction");
        retractionReceiptService.record(ownerId, DataRetractionReceiptService.SUBJECT_CAPSULE, 5L,
                DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX,
                DataRetractionReceiptService.ACTION_ERASED, 1, "owner archived capsule");

        mockMvc.perform(get("/api/me/data-rights/receipts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].derivativeType").value("CAPSULE_MATCH_INDEX"))
                .andExpect(jsonPath("$.data[0].action").value("ERASED"))
                .andExpect(jsonPath("$.data[0].reason").value("owner archived capsule"))
                .andExpect(jsonPath("$.data[1].derivativeType").value("MEMORY_EMBEDDING"))
                .andExpect(jsonPath("$.data[1].affectedCount").value(2));
    }

    private MockHttpSession register(String username) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"testPass123\","
                + "\"nickname\":\"Data Rights Test\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}

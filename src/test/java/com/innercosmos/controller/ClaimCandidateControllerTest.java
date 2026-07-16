package com.innercosmos.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innercosmos.common.Constants;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.service.ClaimCandidateService;
import com.innercosmos.vo.ClaimCandidateVO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:claim-candidate-ctrl;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class ClaimCandidateControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired DialogSessionMapper sessionMapper;
    @Autowired DialogMessageMapper messageMapper;
    @Autowired ClaimCandidateService claimCandidateService;

    @Test
    void listConfirmDismissAreOwnerScoped() throws Exception {
        MockHttpSession owner = register("cand-owner");
        MockHttpSession intruder = register("cand-intruder");
        long ownerId = (Long) owner.getAttribute(Constants.SESSION_USER_KEY);

        DialogSession session = new DialogSession();
        session.userId = ownerId;
        session.title = "t";
        session.sessionType = "AURORA_CHAT";
        session.status = "ACTIVE";
        sessionMapper.insert(session);
        seed(session.id, ownerId, "我特别喜欢在下雨天读书");
        seed(session.id, ownerId, "我每天早上都会去跑步");
        claimCandidateService.stageForSession(ownerId, session.id);

        mockMvc.perform(get("/api/aurora/claims/candidates").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].provenanceMessageIds").isArray());
        // A different user sees none of the owner's candidates.
        mockMvc.perform(get("/api/aurora/claims/candidates").session(intruder))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));

        Long candidateId = claimCandidateService.listCandidates(ownerId).getFirst().id();
        // A foreign user cannot confirm or dismiss someone else's candidate.
        mockMvc.perform(post("/api/aurora/claims/candidates/{id}/confirm", candidateId).session(intruder))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // The owner can confirm; the candidate leaves the pending list.
        mockMvc.perform(post("/api/aurora/claims/candidates/{id}/confirm", candidateId).session(owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/aurora/claims/candidates").session(owner))
                .andExpect(jsonPath("$.data.length()").value(1));

        Long remaining = claimCandidateService.listCandidates(ownerId).getFirst().id();
        mockMvc.perform(delete("/api/aurora/claims/candidates/{id}", remaining).session(owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/aurora/claims/candidates").session(owner))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private void seed(long sessionId, long userId, String text) {
        DialogMessage m = new DialogMessage();
        m.sessionId = sessionId;
        m.userId = userId;
        m.speaker = "USER";
        m.textContent = text;
        m.inputType = "TEXT";
        messageMapper.insert(m);
    }

    private MockHttpSession register(String prefix) throws Exception {
        String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"testPass123\",\"nickname\":\"Claim Test\"}"
                                .formatted(username)))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null || session.getAttribute(Constants.SESSION_USER_KEY) == null) {
            result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"testPass123\"}".formatted(username)))
                    .andExpect(status().isOk()).andReturn();
            session = (MockHttpSession) result.getRequest().getSession(false);
        }
        return session;
    }
}

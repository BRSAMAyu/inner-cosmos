package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryLinkMapper;
import com.innercosmos.mapper.MemoryOperationMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:memory-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false",
        "llm.provider=mock", "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class MemoryLifecycleControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryOperationMapper operationMapper;
    @Autowired MemoryLinkMapper linkMapper;
    @Autowired ThoughtFragmentMapper fragmentMapper;
    private MockHttpSession session;
    private Long userId;

    @BeforeEach
    void setup() throws Exception {
        session = register("memop");
        userId = (Long) session.getAttribute(Constants.SESSION_USER_KEY);
    }

    @Test
    void previewDoesNotWrite_thenMergeSupersedesSourcesAndCreatesLedger() throws Exception {
        MemoryCard a = memory("第一次想离开", "那时我只是很累");
        MemoryCard b = memory("后来又想离开", "我开始看见重复模式");
        String body = "{\"operationType\":\"MERGE\",\"primaryMemoryId\":" + a.id
                + ",\"relatedMemoryIds\":[" + b.id + "],\"title\":\"离开冲动背后的重复模式\"," 
                + "\"reason\":\"用户确认它们属于同一条线\"}";

        mockMvc.perform(post("/api/memory/operations/preview").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationType").value("MERGE"))
                .andExpect(jsonPath("$.data.sourceMemoryIds.length()").value(2));
        assertEquals(0, operationMapper.selectCount(null));

        MvcResult result = mockMvc.perform(post("/api/memory/operations").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation.operationType").value("MERGE"))
                .andExpect(jsonPath("$.data.memories[0].status").value("ACTIVE"))
                .andReturn();
        long mergedId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("memories").get(0).path("id").asLong();
        assertEquals("SUPERSEDED", memoryMapper.selectById(a.id).status);
        assertEquals(mergedId, memoryMapper.selectById(a.id).supersededById);
        assertEquals("SUPERSEDED", memoryMapper.selectById(b.id).status);
        assertEquals(1, operationMapper.selectCount(null));
    }

    @Test
    void forgetScrubsContentAndDerivedFragmentsWithoutRetainingRawSnapshot() throws Exception {
        MemoryCard card = memory("非常私密的标题", "非常私密的原始内容");
        ThoughtFragment fragment = new ThoughtFragment();
        fragment.userId = userId; fragment.memoryCardId = card.id; fragment.fragmentType = "FACT";
        fragment.rawExcerpt = "非常私密的原始内容"; fragmentMapper.insert(fragment);

        mockMvc.perform(post("/api/memory/operations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationType\":\"FORGET\",\"primaryMemoryId\":" + card.id + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memories[0].status").value("FORGOTTEN"));

        MemoryCard forgotten = memoryMapper.selectById(card.id);
        assertEquals("已按你的请求忘记", forgotten.title);
        assertNull(forgotten.summary);
        assertEquals(0, fragmentMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ThoughtFragment>()
                .eq("memory_card_id", card.id)));
        MemoryOperation operation = operationMapper.selectOne(
                new QueryWrapper<MemoryOperation>()
                        .eq("user_id", userId)
                        .eq("primary_memory_id", card.id)
                        .eq("operation_type", "FORGET"));
        assertEquals("{\"redacted\":true}", operation.beforeSnapshot);
        assertFalse(operation.afterSnapshot.contains("非常私密"));
    }

    @Test
    void cannotOperateAnotherUsersMemory() throws Exception {
        MockHttpSession other = register("other");
        Long otherId = (Long) other.getAttribute(Constants.SESSION_USER_KEY);
        MemoryCard foreign = new MemoryCard();
        foreign.userId = otherId; foreign.title = "别人的记忆"; foreign.summary = "不可读取";
        foreign.status = "ACTIVE"; foreign.versionNo = 1; memoryMapper.insert(foreign);

        mockMvc.perform(post("/api/memory/operations/preview").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationType\":\"ARCHIVE\",\"primaryMemoryId\":" + foreign.id + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        assertEquals("ACTIVE", memoryMapper.selectById(foreign.id).status);
    }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/memory/operations")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyArchiveEndpointUsesTheLifecycleAuthority() throws Exception {
        MemoryCard card = memory("准备收起的记忆", "不再放在当前星空");
        mockMvc.perform(post("/api/memory/cards/{id}/archive", card.id).session(session))
                .andExpect(status().isOk());
        assertEquals("ARCHIVED", memoryMapper.selectById(card.id).status);
        MemoryOperation operation = operationMapper.selectOne(new QueryWrapper<MemoryOperation>()
                .eq("user_id", userId).eq("primary_memory_id", card.id));
        assertEquals("ARCHIVE", operation.operationType);
        assertEquals("USER", operation.actorType);
    }

    @Test
    void rollbackRestoresPriorMeaningWithMonotonicVersionAndProjectionReceipts() throws Exception {
        MemoryCard card = memory("我总是在逃避", "这是一次过度概括");
        MvcResult changed = mockMvc.perform(post("/api/memory/operations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationType\":\"UPDATE\",\"primaryMemoryId\":" + card.id
                                + ",\"title\":\"我在谨慎选择\",\"summary\":\"更准确的新理解\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectionReceipts.length()").value(3))
                .andReturn();
        long operationId = objectMapper.readTree(changed.getResponse().getContentAsString())
                .path("data").path("operation").path("id").asLong();

        mockMvc.perform(post("/api/memory/operations/{id}/rollback", operationId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation.operationType").value("ROLLBACK"))
                .andExpect(jsonPath("$.data.operation.rollbackOfOperationId").value(operationId))
                .andExpect(jsonPath("$.data.projectionReceipts[0].status").value("REBUILT"));
        MemoryCard restored = memoryMapper.selectById(card.id);
        assertEquals("我总是在逃避", restored.title);
        assertEquals("这是一次过度概括", restored.summary);
        assertEquals(3, restored.versionNo);
        assertEquals("ROLLED_BACK", operationMapper.selectById(operationId).status);
        mockMvc.perform(get("/api/memory/starfield/{id}/detail", card.id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionHistory.length()").value(2))
                .andExpect(jsonPath("$.data.projectionReceipts.length()").value(6))
                .andExpect(jsonPath("$.data.provenanceExplanation").isNotEmpty());

        mockMvc.perform(post("/api/memory/operations/{id}/rollback", operationId).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgetIsCryptographicallyUnavailableToRollbackBecauseNoRawSnapshotRemains() throws Exception {
        MemoryCard card = memory("不能复原的原文", "明确要求永久忘记");
        MvcResult forgotten = mockMvc.perform(post("/api/memory/operations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationType\":\"FORGET\",\"primaryMemoryId\":" + card.id + "}"))
                .andExpect(status().isOk()).andReturn();
        long operationId = objectMapper.readTree(forgotten.getResponse().getContentAsString())
                .path("data").path("operation").path("id").asLong();
        mockMvc.perform(post("/api/memory/operations/{id}/rollback", operationId).session(session))
                .andExpect(status().isBadRequest());
        assertEquals("已按你的请求忘记", memoryMapper.selectById(card.id).title);
    }

    @Test
    void rollbackOfMergeRestoresBothSourcesAndRetiresTheGeneratedCardAndLinks() throws Exception {
        MemoryCard first = memory("第一次边界协商", "我需要一点空间");
        MemoryCard second = memory("第二次边界协商", "我需要先恢复精力");
        MvcResult merged = mockMvc.perform(post("/api/memory/operations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationType\":\"MERGE\",\"primaryMemoryId\":" + first.id
                                + ",\"relatedMemoryIds\":[" + second.id + "],\"title\":\"边界模式\"}"))
                .andExpect(status().isOk()).andReturn();
        var payload = objectMapper.readTree(merged.getResponse().getContentAsString()).path("data");
        long operationId = payload.path("operation").path("id").asLong();
        long generatedId = payload.path("memories").get(0).path("id").asLong();

        mockMvc.perform(post("/api/memory/operations/{id}/rollback", operationId).session(session))
                .andExpect(status().isOk());
        assertEquals("ACTIVE", memoryMapper.selectById(first.id).status);
        assertEquals("ACTIVE", memoryMapper.selectById(second.id).status);
        assertEquals("ARCHIVED", memoryMapper.selectById(generatedId).status);
        assertEquals(0, linkMapper.selectCount(new QueryWrapper<com.innercosmos.entity.MemoryLink>()
                .eq("user_id", userId).eq("status", "ACTIVE")
                .and(q -> q.eq("target_memory_id", generatedId).or().eq("source_memory_id", generatedId))));
    }

    @Test
    void taskAwareRetrievalRanksRelevantMemoryAndExcludesForgottenOrContradictedFacts() throws Exception {
        MemoryCard relevant = memory("和小林谈边界", "我希望先恢复精力，再解释为什么需要独处");
        relevant.memoryType = "RELATION"; relevant.memoryLayer = "RELATIONAL"; relevant.confidence = 0.95;
        memoryMapper.updateById(relevant);
        MemoryCard noise = memory("Java 作业", "明天完成接口练习");
        noise.memoryType = "TODO"; noise.memoryLayer = "PROSPECTIVE"; memoryMapper.updateById(noise);
        MemoryCard stale = memory("旧误解", "我不在乎小林");
        stale.status = "CONTRADICTED"; memoryMapper.updateById(stale);
        MemoryCard forgotten = memory("忘记的小林记录", "不应再被检索");
        forgotten.status = "FORGOTTEN"; memoryMapper.updateById(forgotten);

        mockMvc.perform(post("/api/memory/retrieval").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"小林 独处 边界\",\"task\":\"RELATION_REVIEW\","
                                + "\"maxResults\":3,\"tokenBudget\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evidence[0].memoryId").value(relevant.id))
                .andExpect(jsonPath("$.data.evidence[0].contributions").isArray())
                .andExpect(jsonPath("$.data.evidence[?(@.memoryId == " + stale.id + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.evidence[?(@.memoryId == " + forgotten.id + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.estimatedTokens").value(org.hamcrest.Matchers.lessThanOrEqualTo(120)));
    }

    @Test
    void starfieldV2SupportsThreeDeterministicViewsAndAnAccessibleEquivalent() throws Exception {
        MemoryCard relation = memory("和小林重新建立边界", "一次坦诚但温和的关系协商");
        relation.memoryType = "RELATION";
        relation.memoryLayer = "RELATIONAL";
        relation.peopleTags = "[\"小林\"]";
        relation.lastTouchedAt = java.time.LocalDateTime.now().minusDays(2);
        memoryMapper.updateById(relation);
        MemoryCard emotion = memory("雨夜后恢复平静", "焦虑慢慢退去");
        emotion.memoryType = "EMOTION";
        emotion.memoryLayer = "EMOTIONAL";
        emotion.lastTouchedAt = java.time.LocalDateTime.now().minusDays(30);
        memoryMapper.updateById(emotion);
        MemoryCard hidden = memory("已经被替代的旧判断", "不应进入当前星空");
        hidden.status = "SUPERSEDED";
        memoryMapper.updateById(hidden);

        for (String mode : new String[]{"TIME", "THEME", "PEOPLE"}) {
            mockMvc.perform(get("/api/memory/starfield/v2").session(session).param("mode", mode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mode").value(mode))
                    .andExpect(jsonPath("$.data.stars.length()").value(2))
                    .andExpect(jsonPath("$.data.accessibleList.length()").value(2))
                    .andExpect(jsonPath("$.data.stars[0].ariaLabel").isNotEmpty())
                    .andExpect(jsonPath("$.data.stars[?(@.id == " + hidden.id + ")]").doesNotExist());
        }
        mockMvc.perform(get("/api/memory/starfield/v2").session(session)
                        .param("mode", "PEOPLE").param("person", "小林"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stars.length()").value(1))
                .andExpect(jsonPath("$.data.stars[0].id").value(relation.id));
    }

    private MemoryCard memory(String title, String summary) {
        MemoryCard card = new MemoryCard();
        card.userId = userId; card.title = title; card.summary = summary; card.memoryType = "COGNITION";
        card.memoryLayer = "SEMANTIC"; card.status = "ACTIVE"; card.versionNo = 1;
        card.emotionalGravity = 1.0; card.recurrenceCount = 1; card.userImportance = 2.0; card.triggerCount = 1;
        card.visibilityLevel = "PRIVATE"; card.consentScope = "AURORA_PRIVATE"; memoryMapper.insert(card);
        return card;
    }

    private MockHttpSession register(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"testPass123\",\"nickname\":\"Memory Test\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}

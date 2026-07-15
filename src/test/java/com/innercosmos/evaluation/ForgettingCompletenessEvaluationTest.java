package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryEmbedding;
import com.innercosmos.entity.MemoryLink;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryEmbeddingMapper;
import com.innercosmos.mapper.MemoryLinkMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.service.StarfieldExplorerService;
import com.innercosmos.vo.MemoryOperationResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Scores whether a FORGET operation leaves zero recoverable trace of the original content,
 * across every public read surface (the memory row itself, starfield, retrieval evidence)
 * and every derived-artifact table forgetDerived() is supposed to scrub. Each labeled
 * scenario embeds a unique marker string in a different derived-artifact type so a gap in
 * per-table scrubbing shows up as a real, measured leak rather than a guess. Complements
 * CorrectionTargetingEvaluationTest and MemoryRetrievalEvaluationTest as the third Campaign B
 * dimension doc-16 asks to be evaluated with scored/labeled data rather than plain CRUD
 * assertions.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:forgetting-completeness-eval;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class ForgettingCompletenessEvaluationTest {
    @Autowired ObjectMapper objectMapper;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired ThoughtFragmentMapper fragmentMapper;
    @Autowired TodoItemMapper todoMapper;
    @Autowired RelationMentionMapper relationMapper;
    @Autowired MemoryLinkMapper linkMapper;
    @Autowired MemoryEmbeddingMapper embeddingMapper;
    @Autowired MemoryLifecycleService lifecycleService;
    @Autowired StarfieldExplorerService starfieldService;
    @Autowired MemoryRetrievalService retrievalService;

    @Test
    void syntheticAnnotatedForgetGateLeavesNoRecoverableTrace() throws Exception {
        JsonNode dataset = objectMapper.readTree(getClass().getResourceAsStream(
                "/evaluation/forgetting-completeness-v1.json"));
        long userId = 93001L;
        int leakedSurfaces = 0, residualDerivedRows = 0, rollbackAccepted = 0;
        Map<String, Object> caseReports = new LinkedHashMap<>();

        for (JsonNode scenario : dataset.path("scenarios")) {
            String key = scenario.path("key").asText();
            String marker = scenario.path("marker").asText();
            String artifact = scenario.path("artifact").asText();

            MemoryCard card = new MemoryCard();
            card.userId = userId; card.title = "标题 " + marker; card.summary = "摘要 " + marker;
            card.memoryType = "DIARY"; card.memoryLayer = "EPISODIC"; card.status = "ACTIVE";
            card.versionNo = 1; card.confidence = 0.9; card.emotionalGravity = 3.0;
            memoryMapper.insert(card);

            MemoryCard companion = null;
            switch (artifact) {
                case "FRAGMENT" -> {
                    ThoughtFragment fragment = new ThoughtFragment();
                    fragment.userId = userId; fragment.memoryCardId = card.id; fragment.fragmentType = "FACT";
                    fragment.rawExcerpt = "原始片段 " + marker; fragmentMapper.insert(fragment);
                }
                case "TODO" -> {
                    TodoItem todo = new TodoItem();
                    todo.userId = userId; todo.sourceMemoryCardId = card.id; todo.taskName = "待办 " + marker;
                    todo.description = "细节 " + marker; todo.priority = "MEDIUM"; todo.status = "TODO";
                    todoMapper.insert(todo);
                }
                case "RELATION" -> {
                    RelationMention relation = new RelationMention();
                    relation.userId = userId; relation.memoryCardId = card.id; relation.relationLabel = "关系 " + marker;
                    relation.relationType = "FRIEND"; relation.triggerSummary = "触发 " + marker;
                    relationMapper.insert(relation);
                }
                case "LINK" -> {
                    companion = new MemoryCard();
                    companion.userId = userId; companion.title = "关联记忆"; companion.summary = "不含标记";
                    companion.memoryType = "DIARY"; companion.memoryLayer = "EPISODIC"; companion.status = "ACTIVE";
                    companion.versionNo = 1; memoryMapper.insert(companion);
                    MemoryLink link = new MemoryLink();
                    link.userId = userId; link.sourceMemoryId = card.id; link.targetMemoryId = companion.id;
                    link.linkType = "RELATES_TO"; link.strength = 0.5; link.evidenceRefs = "评测夹具 " + marker;
                    link.status = "ACTIVE"; linkMapper.insert(link);
                }
                case "EMBEDDING" -> {
                    MemoryEmbedding embedding = new MemoryEmbedding();
                    embedding.userId = userId; embedding.memoryId = card.id; embedding.modelName = "test-model";
                    embedding.modelVersion = "v1"; embedding.sourceVersion = 1; embedding.taskScope = "GENERAL";
                    embedding.dimensions = 4; embedding.embeddingJson = "[1,0,0,0]"; embedding.status = "ACTIVE";
                    embeddingMapper.insert(embedding);
                }
                default -> { /* NONE: only the memory row itself carries the marker */ }
            }

            MemoryOperationResultVO forgotten = lifecycleService.execute(userId,
                    new MemoryOperationCommand("FORGET", card.id, null, null, null, null, "评测夹具", null, null));

            List<String> leaks = new ArrayList<>();
            String reloadedCardJson = objectMapper.writeValueAsString(memoryMapper.selectById(card.id));
            if (reloadedCardJson.contains(marker)) leaks.add("memory-row");

            String starfieldJson = objectMapper.writeValueAsString(starfieldService.explore(userId, "TIME", null, null, null));
            if (starfieldJson.contains(marker)) leaks.add("starfield");

            var evidence = retrievalService.retrieve(userId, new MemoryRetrievalQuery(marker, "AURORA_CONVERSATION", null, 10, 400, true));
            // Scan only the returned evidence content, not the pack's echoed query/task fields
            // (those legitimately contain the marker because this test put it in the query).
            String retrievalJson = objectMapper.writeValueAsString(evidence.evidence());
            if (retrievalJson.contains(marker)) leaks.add("retrieval");

            int residual = fragmentMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ThoughtFragment>().eq("memory_card_id", card.id)).intValue()
                    + todoMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TodoItem>().eq("source_memory_card_id", card.id)).intValue()
                    + relationMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RelationMention>().eq("memory_card_id", card.id)).intValue()
                    + linkMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryLink>()
                        .eq("user_id", userId).and(q -> q.eq("source_memory_id", card.id).or().eq("target_memory_id", card.id))).intValue()
                    + embeddingMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryEmbedding>().eq("memory_id", card.id)).intValue();

            boolean rollbackRefused;
            try {
                lifecycleService.rollback(userId, forgotten.operation().id);
                rollbackRefused = false;
            } catch (BusinessException expected) {
                rollbackRefused = true;
            }

            leakedSurfaces += leaks.size();
            residualDerivedRows += residual;
            if (!rollbackRefused) rollbackAccepted++;
            caseReports.put(key, Map.of("artifact", artifact, "leakedSurfaces", leaks,
                    "residualDerivedRows", residual, "rollbackRefused", rollbackRefused));
            if (companion != null) assertEquals("关联记忆", memoryMapper.selectById(companion.id).title, "unrelated linked memory must survive untouched");
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("datasetVersion", dataset.path("datasetVersion").asText());
        report.put("scenarios", dataset.path("scenarios").size());
        report.put("leakedSurfaces", leakedSurfaces);
        report.put("residualDerivedRows", residualDerivedRows);
        report.put("rollbackAccepted", rollbackAccepted);
        report.put("perCase", caseReports);
        Path reportPath = Path.of("target", "evaluation", "forgetting-completeness-v1-report.json");
        Files.createDirectories(reportPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        JsonNode thresholds = dataset.path("thresholds");
        if (leakedSurfaces > thresholds.path("leakedSurfaces").asInt()) fail("forgotten content leaked: " + report);
        if (residualDerivedRows > thresholds.path("residualDerivedRows").asInt()) fail("derived rows survived FORGET: " + report);
        assertTrue(rollbackAccepted <= thresholds.path("rollbackAccepted").asInt(), report.toString());
    }
}

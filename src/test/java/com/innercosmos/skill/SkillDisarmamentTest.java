package com.innercosmos.skill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.dto.PsychologySkillRunRequest;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.PsychologySkillRun;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.PsychologySkillRunMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.PsychologySkillReleaseService;
import com.innercosmos.service.PsychologySkillService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import com.innercosmos.vo.PsychologySkillRunVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CP-60 恢复条款 (blueprint L875): "安全不合格技能单独撤回，不影响记忆资产".
 * Disarmament is single-skill and memory-neutral:
 *  - after disable(), new runs of that skill are refused and leave no run rows;
 *  - memory cards, the starfield rendering and retrieval packs are byte-for-byte unchanged;
 *  - historical run records survive for audit (status, manifest hash, retained result).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:skill-disarmament;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "inner-cosmos.safety.semantic-recheck.enabled=false", "llm.provider=mock"
})
class SkillDisarmamentTest {
    private static final String SKILL_ID = "emotion-needs-clarifier";
    private static final String SKILL_VERSION = "1.0.0";

    @Autowired PsychologySkillService skillService;
    @Autowired PsychologySkillReleaseService releaseService;
    @Autowired PsychologySkillRunMapper runMapper;
    @Autowired MemoryCardMapper memoryCardMapper;
    @Autowired MemoryService memoryService;
    @Autowired MemoryRetrievalService retrievalService;
    @Autowired UserMapper userMapper;
    @Autowired ObjectMapper objectMapper;

    @Test
    void disablingOneSkillBlocksItsRunsChangesNoMemoryAndKeepsRunAuditRecords() throws Exception {
        Long demo = user("demo").id;

        // Baseline memory assets: cards, starfield rendering and a retrieval evidence pack.
        List<MemoryCard> cardsBefore = cards(demo);
        String starfieldBefore = objectMapper.writeValueAsString(memoryService.starfield(demo));
        MemoryRetrievalQuery query = new MemoryRetrievalQuery(
                "最近的工作压力和准备", "AURORA_CONVERSATION", null, 6, 800, false);
        MemoryEvidencePackVO packBefore = retrievalService.retrieve(demo, query);
        assertThat(cardsBefore).isNotEmpty();

        // One auditable completed run exists before disarmament (SAVE_RESULT keeps the result).
        PsychologySkillRunVO run = skillService.run(demo, SKILL_ID, request("SAVE_RESULT"));
        assertThat(run.status).isEqualTo("COMPLETED");

        // A completed run itself never touches the user's memory assets.
        assertThat(cards(demo)).usingRecursiveComparison().isEqualTo(cardsBefore);
        assertThat(objectMapper.writeValueAsString(memoryService.starfield(demo))).isEqualTo(starfieldBefore);

        // Disarm.
        var disabled = releaseService.disable(SKILL_ID, SKILL_VERSION,
                "CP-60A disarmament drill: single-skill safety stop");
        assertThat(disabled.enabled).isFalse();
        assertThat(disabled.releaseStatus).isEqualTo("DISABLED");
        assertThat(disabled.disabledReason).contains("disarmament");

        // New runs are refused...
        assertThatThrownBy(() -> skillService.run(demo, SKILL_ID, request("DISCARD_AFTER_SESSION")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已暂停");
        // ...and the refused attempt leaves no run row behind.
        assertThat(runCount(SKILL_ID)).isEqualTo(1);

        // 不影响记忆资产: memory cards, starfield and retrieval are unchanged by disarmament.
        assertThat(cards(demo)).usingRecursiveComparison().isEqualTo(cardsBefore);
        assertThat(objectMapper.writeValueAsString(memoryService.starfield(demo))).isEqualTo(starfieldBefore);
        assertThat(retrievalService.retrieve(demo, query)).isEqualTo(packBefore);

        // Audit retention: the historical run record survives disarmament intact.
        PsychologySkillRun audit = runMapper.selectById(run.id);
        assertThat(audit).isNotNull();
        assertThat(audit.skillId).isEqualTo(SKILL_ID);
        assertThat(audit.skillVersion).isEqualTo(SKILL_VERSION);
        assertThat(audit.status).isEqualTo("COMPLETED");
        assertThat(audit.manifestHash).isEqualTo(run.manifestHash);
        assertThat(audit.resultJson).isNotBlank();
        assertThat(audit.evidenceRefs).contains("Lieberman");
        assertThat(audit.revokedAt).isNull();

        // 单独撤回: only the disabled skill stops; a sibling skill keeps running.
        PsychologySkillRunVO sibling = skillService.run(demo, "decision-conflict-map", siblingRequest());
        assertThat(sibling.status).isEqualTo("COMPLETED");
        assertThat(runCount(SKILL_ID)).isEqualTo(1);
        // And the sibling run still leaves memory assets untouched.
        assertThat(cards(demo)).usingRecursiveComparison().isEqualTo(cardsBefore);
        assertThat(retrievalService.retrieve(demo, query)).isEqualTo(packBefore);
    }

    private PsychologySkillRunRequest request(String retentionChoice) {
        PsychologySkillRunRequest request = new PsychologySkillRunRequest();
        request.explicitConsent = true;
        request.retentionChoice = retentionChoice;
        request.locale = "zh-CN";
        request.consentScopes = List.of("current-run-input");
        request.answers = Map.of("situation", "停用演练", "feeling", "平静", "need", "确认边界");
        return request;
    }

    private PsychologySkillRunRequest siblingRequest() {
        PsychologySkillRunRequest request = new PsychologySkillRunRequest();
        request.explicitConsent = true;
        request.retentionChoice = "DISCARD_AFTER_SESSION";
        request.locale = "zh-CN";
        request.consentScopes = List.of("current-run-input");
        request.answers = Map.of("decision", "是否汇报", "pullToward", "透明", "pullAway", "被否定");
        return request;
    }

    private List<MemoryCard> cards(Long userId) {
        return memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                .eq("user_id", userId).orderByAsc("id"));
    }

    private long runCount(String skillId) {
        return runMapper.selectCount(new QueryWrapper<PsychologySkillRun>().eq("skill_id", skillId));
    }

    private User user(String username) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        assertThat(user).isNotNull();
        return user;
    }
}

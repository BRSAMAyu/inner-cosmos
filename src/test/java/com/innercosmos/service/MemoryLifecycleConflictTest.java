package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.MemoryCardMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CP-21 merge/conflict/concurrent-edit negative matrix. Three silent-corruption paths are
 * now CONFLICT, never last-writer-wins: a stale pinned version must not clobber an
 * intervening edit; a FORGOTTEN memory is terminal and may never be edited or merged again;
 * an already-SUPERSEDED memory must not be re-edited/re-merged (successor-chain fork); and a
 * rollback whose rows have moved on must be refused instead of restoring over newer edits.
 */
@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class MemoryLifecycleConflictTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MemoryCardMapper memoryMapper;
    @Autowired MemoryLifecycleService lifecycleService;

    @Test
    void staleExpectedVersionIsAConflictAndPreservesTheInterveningEdit() {
        Long owner = seedUser();
        MemoryCard card = memory(owner, "版本冲突的记忆", "第一版内容");

        // Editor A pins the version she read (1); editor B lands a newer edit first (v2).
        MemoryOperationCommand stale = new MemoryOperationCommand(
                "UPDATE", card.id, null, "过时的标题", "过时的内容", null,
                "stale editor", 1.0, null, 1);
        MemoryOperationCommand fresh = new MemoryOperationCommand(
                "UPDATE", card.id, null, "较新的标题", "较新的内容", null,
                "fresh editor", 1.0, null, 1);
        lifecycleService.execute(owner, fresh);

        assertThatThrownBy(() -> lifecycleService.execute(owner, stale))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有新的变更")
                .extracting("code").isEqualTo(ErrorCode.CONFLICT);

        MemoryCard after = memoryMapper.selectById(card.id);
        assertThat(after.title).isEqualTo("较新的标题");
        assertThat(after.versionNo).isEqualTo(2);

        // A re-based edit on the current version succeeds — conflict resolution is a refresh, not a dead end.
        lifecycleService.execute(owner, new MemoryOperationCommand(
                "UPDATE", card.id, null, "重放后的标题", null, null,
                "rebased editor", 1.0, null, 2));
        assertThat(memoryMapper.selectById(card.id).title).isEqualTo("重放后的标题");
    }

    @Test
    void unpinnedLegacyEditsKeepWorkingForVersionlessCallers() {
        Long owner = seedUser();
        MemoryCard card = memory(owner, "无版本编辑", "内容");

        var result = lifecycleService.execute(owner, new MemoryOperationCommand(
                "UPDATE", card.id, null, "新标题", null, null, null, null, null));

        assertThat(result.memories().get(0).title).isEqualTo("新标题");
        assertThat(memoryMapper.selectById(card.id).versionNo).isEqualTo(2);
    }

    @Test
    void forgottenMemoryIsTerminalForEveryEditingOperation() {
        Long owner = seedUser();
        MemoryCard card = memory(owner, "将被忘记的记忆", "敏感内容");
        lifecycleService.execute(owner, new MemoryOperationCommand(
                "FORGET", card.id, null, null, null, null, "owner forget", null, null));
        MemoryCard other = memory(owner, "另一条记忆", "普通内容");

        assertThatThrownBy(() -> lifecycleService.execute(owner, new MemoryOperationCommand(
                "UPDATE", card.id, null, "复活尝试", "试图写回内容", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被忘记");
        assertThatThrownBy(() -> lifecycleService.execute(owner, new MemoryOperationCommand(
                "MERGE", card.id, List.of(other.id), null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被忘记");
        assertThatThrownBy(() -> lifecycleService.execute(owner, new MemoryOperationCommand(
                "SUPERSEDE", card.id, null, "替代", "替代内容", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被忘记");

        // Nothing was written back: the forgotten row keeps its redacted shape.
        MemoryCard reloaded = memoryMapper.selectById(card.id);
        assertThat(reloaded.title).isEqualTo("已按你的请求忘记");
        assertThat(reloaded.summary).isNull();
        assertThat(reloaded.status).isEqualTo("FORGOTTEN");
    }

    @Test
    void supersededMemoryCannotBeReMergedOrReEditedForkPrevention() {
        Long owner = seedUser();
        MemoryCard first = memory(owner, "原始记忆一", "内容一");
        MemoryCard second = memory(owner, "原始记忆二", "内容二");
        lifecycleService.execute(owner, new MemoryOperationCommand(
                "MERGE", first.id, List.of(second.id), "合并后的记忆", null, null, null, null, null));

        MemoryCard third = memory(owner, "后来的记忆", "内容三");
        assertThatThrownBy(() -> lifecycleService.execute(owner, new MemoryOperationCommand(
                "MERGE", first.id, List.of(third.id), "二次合并", null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被合并或替代");
        assertThatThrownBy(() -> lifecycleService.execute(owner, new MemoryOperationCommand(
                "UPDATE", first.id, null, "编辑已合并来源", null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被合并或替代");

        // The first merge's chain is intact: one successor, sources SUPERSEDED pointing at it.
        MemoryCard superseded = memoryMapper.selectById(first.id);
        assertThat(superseded.status).isEqualTo("SUPERSEDED");
        assertThat(superseded.supersededById).isNotNull();
        assertThat(memoryMapper.selectCount(new QueryWrapper<MemoryCard>()
                .eq("user_id", owner).eq("title", "合并后的记忆"))).isEqualTo(1);
    }

    @Test
    void rollbackIsRefusedWhenTheRowMovedOnAndSucceedsWhenItDidNot() {
        Long owner = seedUser();
        MemoryCard card = memory(owner, "回退冲突记忆", "第一版");

        var first = lifecycleService.execute(owner, new MemoryOperationCommand(
                "UPDATE", card.id, null, "第二版标题", "第二版内容", null, null, null, null));
        // A later edit lands on top of the first one.
        lifecycleService.execute(owner, new MemoryOperationCommand(
                "UPDATE", card.id, null, "第三版标题", "第三版内容", null, null, null, null));

        assertThatThrownBy(() -> lifecycleService.rollback(owner, first.operation().id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("直接回退会覆盖");
        // The intervening edit survived the refused rollback.
        assertThat(memoryMapper.selectById(card.id).title).isEqualTo("第三版标题");

        // With no intervening edit, rollback still works: roll back the (now latest) second update.
        var second = lifecycleService.history(owner, card.id).stream()
                .filter(op -> "UPDATE".equals(op.operationType) && "APPLIED".equals(op.status))
                .findFirst().orElseThrow();
        var rolledBack = lifecycleService.rollback(owner, second.id);
        assertThat(rolledBack.memories().get(0).title).isEqualTo("第二版标题");
    }

    private Long seedUser() {
        String username = "memory-conflict-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, 'USER', 'ACTIVE')",
                username, "hash");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private MemoryCard memory(Long owner, String title, String summary) {
        MemoryCard card = new MemoryCard();
        card.userId = owner;
        card.title = title;
        card.summary = summary;
        card.memoryType = "FACT";
        card.memoryLayer = "EPISODIC";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.consentScope = "AURORA_PRIVATE";
        card.versionNo = 1;
        card.emotionalGravity = 0.4;
        memoryMapper.insert(card);
        return card;
    }
}

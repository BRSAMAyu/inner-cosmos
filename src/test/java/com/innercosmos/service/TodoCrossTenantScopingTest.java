package com.innercosmos.service;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.vo.DailyRecordVO;
import com.innercosmos.vo.StarfieldDetailVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 regression coverage for the cross-tenant content-injection chain:
 * {@code TodoItem.sourceMemoryCardId} is a public field bound directly by Jackson, and
 * {@code TodoServiceImpl.create()} used to overwrite only {@code userId}, leaving a
 * client-supplied {@code sourceMemoryCardId} pointed at ANOTHER user's memory card untouched.
 * Three read paths (latestDailyRecord, starfieldDetail, and the settlement daily-record builder)
 * then selected todos by {@code source_memory_card_id} alone, with no owner predicate, letting an
 * attacker's todo text render inside the card owner's private 今日记录/星图详情. The reverse
 * direction was also broken: forgetting a memory deleted derived todo/fragment/relation rows by
 * card id with no {@code user_id} guard, so a card owner forgetting their own memory could delete
 * another user's (injected) todo row out from under them.
 *
 * <p>This test verifies, against the real Spring-wired services and H2 (MODE=MySQL) schema:
 * <ol>
 *   <li>the create()-time validation rejects a cross-tenant {@code sourceMemoryCardId};</li>
 *   <li>a pre-existing cross-tenant row (inserted directly via the mapper, bypassing the service —
 *       simulating data that slipped in before the fix, or a future regression) is excluded by the
 *       defence-in-depth owner predicate on the read paths; and</li>
 *   <li>forgetting the memory does not delete the other user's row.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:todo-cross-tenant-scoping;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "spring.task.scheduling.enabled=false", "llm.provider=mock"
})
class TodoCrossTenantScopingTest {

    @Autowired
    private TodoService todoService;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private MemoryLifecycleService lifecycleService;
    @Autowired
    private MemoryCardMapper memoryCardMapper;
    @Autowired
    private TodoItemMapper todoItemMapper;

    private static final Long USER_A = 910001L;
    private static final Long USER_B = 910002L;
    private static final String INJECTED_MARKER = "INJECTED_BY_USER_B_MARKER";

    private MemoryCard seedActiveCard(Long owner) {
        MemoryCard card = new MemoryCard();
        card.userId = owner;
        card.title = "标题";
        card.summary = "摘要";
        card.memoryType = "EMOTION";
        card.memoryLayer = "EPISODIC";
        card.status = "ACTIVE";
        card.versionNo = 1;
        card.confidence = 0.8;
        card.emotionalGravity = 0.5;
        memoryCardMapper.insert(card);
        return card;
    }

    private TodoItem insertTodoDirectly(Long owner, Long sourceMemoryCardId, String taskName) {
        TodoItem todo = new TodoItem();
        todo.userId = owner;
        todo.sourceMemoryCardId = sourceMemoryCardId;
        todo.taskName = taskName;
        todo.description = "细节 " + taskName;
        todo.priority = "MEDIUM";
        todo.status = "TODO";
        todoItemMapper.insert(todo);
        return todo;
    }

    @Test
    @DisplayName("1) create() rejects a sourceMemoryCardId that belongs to another user")
    void create_crossTenantSourceMemoryCardId_isRejected() {
        MemoryCard cardOwnedByA = seedActiveCard(USER_A);

        TodoItem attack = new TodoItem();
        attack.taskName = INJECTED_MARKER;
        attack.sourceMemoryCardId = cardOwnedByA.id;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> todoService.create(USER_B, attack));
        assertEquals(ErrorCode.BAD_REQUEST, ex.code);

        long persisted = todoItemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TodoItem>()
                        .eq("source_memory_card_id", cardOwnedByA.id));
        assertEquals(0, persisted, "the rejected cross-tenant todo must never be inserted");
    }

    @Test
    @DisplayName("2) starfieldDetail and latestDailyRecord never return a pre-existing cross-tenant todo row")
    void readPaths_excludeCrossTenantTodoRow() {
        MemoryCard cardOwnedByA = seedActiveCard(USER_A);
        // Simulate a cross-tenant row that slipped in (bypassing the service entirely), so the
        // read-path owner predicate is what has to stop the leak here, independent of create()'s
        // own validation.
        TodoItem injected = insertTodoDirectly(USER_B, cardOwnedByA.id, INJECTED_MARKER);

        StarfieldDetailVO detail = memoryService.starfieldDetail(USER_A, cardOwnedByA.id);
        assertNotNull(detail.todos);
        assertFalse(detail.todos.stream().anyMatch(t -> t.id.equals(injected.id)),
                "starfieldDetail leaked another user's injected todo into this owner's memory detail");
        assertFalse(detail.todos.stream().anyMatch(t -> INJECTED_MARKER.equals(t.taskName)),
                "starfieldDetail leaked the injected task text");

        DailyRecordVO daily = memoryService.latestDailyRecord(USER_A);
        assertNotNull(daily.todos);
        assertFalse(daily.todos.stream().anyMatch(t -> t.id.equals(injected.id)),
                "latestDailyRecord leaked another user's injected todo into this owner's daily record");
        assertFalse(daily.todos.stream().anyMatch(t -> INJECTED_MARKER.equals(t.taskName)),
                "latestDailyRecord leaked the injected task text");

        // Sanity: the injected row still exists under its real owner — it wasn't silently dropped,
        // it is just correctly excluded from user A's private views.
        TodoItem reloaded = todoItemMapper.selectById(injected.id);
        assertNotNull(reloaded);
        assertEquals(USER_B, reloaded.userId);
    }

    @Test
    @DisplayName("3) forgetting the memory does not delete another user's row that was pointed at it")
    void forgetDerived_doesNotDeleteOtherUsersInjectedRow() {
        MemoryCard cardOwnedByA = seedActiveCard(USER_A);
        TodoItem injected = insertTodoDirectly(USER_B, cardOwnedByA.id, INJECTED_MARKER);

        lifecycleService.execute(USER_A,
                new MemoryOperationCommand("FORGET", cardOwnedByA.id, null, null, null, null, null, null, null));

        TodoItem stillThere = todoItemMapper.selectById(injected.id);
        assertNotNull(stillThere,
                "user A forgetting their own memory must not delete user B's row from user B's board");
        assertEquals(USER_B, stillThere.userId);
        assertTrue(todoService.list(USER_B).stream().anyMatch(t -> t.id.equals(injected.id)),
                "user B's todo list must still contain the row after user A's forget");
    }
}

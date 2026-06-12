package com.innercosmos.service;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.impl.TodoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock
    private TodoItemMapper todoItemMapper;

    @Mock
    private LlmClient llmClient;

    private TodoServiceImpl todoService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ITEM_ID = 100L;

    @BeforeEach
    void setUp() {
        todoService = new TodoServiceImpl(todoItemMapper, llmClient);
    }

    private TodoItem buildOwnedItem() {
        TodoItem item = new TodoItem();
        item.id = ITEM_ID;
        item.userId = USER_ID;
        item.taskName = "Test task";
        item.status = "TODO";
        item.priority = "MEDIUM";
        return item;
    }

    // --- list ---

    @Test
    @DisplayName("list returns items for user")
    void list_returnsItemsForUser() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectList(any())).thenReturn(List.of(item));

        List<TodoItem> result = todoService.list(USER_ID);

        assertEquals(1, result.size());
        assertEquals(ITEM_ID, result.get(0).id);
        verify(todoItemMapper).selectList(any());
    }

    @Test
    @DisplayName("list returns empty list when user has no items")
    void list_returnsEmptyList() {
        when(todoItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<TodoItem> result = todoService.list(USER_ID);

        assertTrue(result.isEmpty());
        verify(todoItemMapper).selectList(any());
    }

    // --- create ---

    @Test
    @DisplayName("create sets userId and inserts item")
    void create_setsUserIdAndInserts() {
        TodoItem item = new TodoItem();
        item.taskName = "New task";
        when(todoItemMapper.insert(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.create(USER_ID, item);

        assertEquals(USER_ID, result.userId);
        assertEquals("TODO", result.status);
        assertEquals("MEDIUM", result.priority);
        verify(todoItemMapper).insert(any(TodoItem.class));
    }

    @Test
    @DisplayName("create throws when taskName is null")
    void create_nullTaskName_throwsException() {
        TodoItem item = new TodoItem();
        item.taskName = null;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> todoService.create(USER_ID, item));
        assertEquals(ErrorCode.BAD_REQUEST, ex.code);
    }

    @Test
    @DisplayName("create throws when taskName is blank")
    void create_blankTaskName_throwsException() {
        TodoItem item = new TodoItem();
        item.taskName = "   ";

        BusinessException ex = assertThrows(BusinessException.class,
                () -> todoService.create(USER_ID, item));
        assertEquals(ErrorCode.BAD_REQUEST, ex.code);
    }

    @Test
    @DisplayName("create defaults status to TODO when null")
    void create_nullStatus_defaultsToTODO() {
        TodoItem item = new TodoItem();
        item.taskName = "Task";
        item.status = null;
        when(todoItemMapper.insert(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.create(USER_ID, item);

        assertEquals("TODO", result.status);
    }

    @Test
    @DisplayName("create defaults priority to MEDIUM when null")
    void create_nullPriority_defaultsToMedium() {
        TodoItem item = new TodoItem();
        item.taskName = "Task";
        item.priority = null;
        when(todoItemMapper.insert(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.create(USER_ID, item);

        assertEquals("MEDIUM", result.priority);
    }

    // --- updateStatus ---

    @Test
    @DisplayName("updateStatus with valid status succeeds")
    void updateStatus_validStatus_succeeds() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(todoItemMapper.updateById(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.updateStatus(USER_ID, ITEM_ID, "DONE");

        assertEquals("DONE", result.status);
        verify(todoItemMapper).updateById(any(TodoItem.class));
    }

    @Test
    @DisplayName("updateStatus with invalid status throws BusinessException")
    void updateStatus_invalidStatus_throwsException() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> todoService.updateStatus(USER_ID, ITEM_ID, "INVALID"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.code);
    }

    @Test
    @DisplayName("updateStatus normalizes IN_PROGRESS to DOING")
    void updateStatus_inProgress_normalizesToDoing() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(todoItemMapper.updateById(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.updateStatus(USER_ID, ITEM_ID, "IN_PROGRESS");

        assertEquals("DOING", result.status);
    }

    @Test
    @DisplayName("updateStatus normalizes DROPPED to CANCELLED")
    void updateStatus_dropped_normalizesToCancelled() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(todoItemMapper.updateById(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.updateStatus(USER_ID, ITEM_ID, "DROPPED");

        assertEquals("CANCELLED", result.status);
    }

    @Test
    @DisplayName("updateStatus throws for item not owned by user")
    void updateStatus_notOwned_throwsException() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> todoService.updateStatus(OTHER_USER_ID, ITEM_ID, "DONE"));
        assertEquals(ErrorCode.NOT_FOUND, ex.code);
    }

    // --- delete ---

    @Test
    @DisplayName("delete succeeds for owned item")
    void delete_ownedItem_succeeds() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(todoItemMapper.deleteById(anyLong())).thenReturn(1);

        todoService.delete(USER_ID, ITEM_ID);

        verify(todoItemMapper).deleteById(ITEM_ID);
    }

    @Test
    @DisplayName("delete fails for unowned item")
    void delete_unownedItem_throwsException() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> todoService.delete(OTHER_USER_ID, ITEM_ID));
        assertEquals(ErrorCode.NOT_FOUND, ex.code);
        verify(todoItemMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete fails for non-existent item")
    void delete_nonExistentItem_throwsException() {
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> todoService.delete(USER_ID, ITEM_ID));
        assertEquals(ErrorCode.NOT_FOUND, ex.code);
    }

    // --- update ---

    @Test
    @DisplayName("update modifies owned item")
    void update_ownedItem_modifiesFields() {
        TodoItem existing = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(existing);
        when(todoItemMapper.updateById(any(TodoItem.class))).thenReturn(1);

        TodoItem update = new TodoItem();
        update.taskName = "Updated task";
        update.description = "Updated description";
        update.priority = "HIGH";
        update.status = "DOING";

        TodoItem result = todoService.update(USER_ID, ITEM_ID, update);

        assertEquals("Updated task", result.taskName);
        assertEquals("Updated description", result.description);
        assertEquals("HIGH", result.priority);
        assertEquals("DOING", result.status);
        verify(todoItemMapper).updateById(any(TodoItem.class));
    }

    @Test
    @DisplayName("update ignores blank taskName")
    void update_blankTaskName_ignored() {
        TodoItem existing = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(existing);
        when(todoItemMapper.updateById(any(TodoItem.class))).thenReturn(1);

        TodoItem update = new TodoItem();
        update.taskName = "   ";

        TodoItem result = todoService.update(USER_ID, ITEM_ID, update);

        assertEquals("Test task", result.taskName,
                "Blank taskName should not overwrite existing value");
    }

    @Test
    @DisplayName("update throws for unowned item")
    void update_unownedItem_throwsException() {
        TodoItem existing = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(existing);

        TodoItem update = new TodoItem();
        update.taskName = "Hacked";

        assertThrows(BusinessException.class,
                () -> todoService.update(OTHER_USER_ID, ITEM_ID, update));
        verify(todoItemMapper, never()).updateById(any(TodoItem.class));
    }

    // --- splitFirstStep ---

    @Test
    @DisplayName("splitFirstStep calls LLM and updates item")
    void splitFirstStep_callsLlmAndUpdates() {
        TodoItem item = buildOwnedItem();
        item.description = null;
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("Open a blank page");
        when(todoItemMapper.updateById(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.splitFirstStep(USER_ID, ITEM_ID);

        verify(llmClient).chat(any(LlmRequest.class));
        verify(todoItemMapper).updateById(any(TodoItem.class));
        assertNotNull(result.description);
        assertTrue(result.description.contains("Open a blank page"));
    }

    @Test
    @DisplayName("splitFirstStep throws for unowned item")
    void splitFirstStep_unownedItem_throwsException() {
        TodoItem item = buildOwnedItem();
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BusinessException.class,
                () -> todoService.splitFirstStep(OTHER_USER_ID, ITEM_ID));
        verify(llmClient, never()).chat(any(LlmRequest.class));
    }

    @Test
    @DisplayName("splitFirstStep uses fallback when LLM returns null")
    void splitFirstStep_nullLlmResponse_usesFallback() {
        TodoItem item = buildOwnedItem();
        item.description = null;
        when(todoItemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(llmClient.chat(any(LlmRequest.class))).thenReturn(null);
        when(todoItemMapper.updateById(any(TodoItem.class))).thenReturn(1);

        TodoItem result = todoService.splitFirstStep(USER_ID, ITEM_ID);

        assertNotNull(result.description);
        assertTrue(result.description.length() > 0);
    }
}

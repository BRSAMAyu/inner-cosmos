package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.TodoService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TodoServiceImpl implements TodoService {
    private static final Set<String> VALID_STATUSES = Set.of("TODO", "DOING", "IN_PROGRESS", "DONE", "CANCELLED", "DROPPED");

    private final TodoItemMapper todoItemMapper;
    private final LlmClient llmClient;

    public TodoServiceImpl(TodoItemMapper todoItemMapper, LlmClient llmClient) {
        this.todoItemMapper = todoItemMapper;
        this.llmClient = llmClient;
    }

    @Override
    public List<TodoItem> list(Long userId) {
        QueryWrapper<TodoItem> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByAsc("status").orderByDesc("id");
        return todoItemMapper.selectList(query);
    }

    @Override
    public TodoItem updateStatus(Long userId, Long id, String status) {
        TodoItem item = requireOwned(userId, id);
        item.status = normalizeStatus(status);
        todoItemMapper.updateById(item);
        return item;
    }

    @Override
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        todoItemMapper.deleteById(id);
    }

    @Override
    public TodoItem create(Long userId, TodoItem item) {
        if (item.taskName == null || item.taskName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "任务名称不能为空");
        }
        item.userId = userId;
        item.status = normalizeStatus(item.status == null || item.status.isBlank() ? "TODO" : item.status);
        if (item.priority == null || item.priority.isBlank()) {
            item.priority = "MEDIUM";
        }
        todoItemMapper.insert(item);
        return item;
    }

    @Override
    public TodoItem update(Long userId, Long id, TodoItem item) {
        TodoItem existing = requireOwned(userId, id);
        if (item.taskName != null && !item.taskName.isBlank()) existing.taskName = item.taskName;
        if (item.description != null) existing.description = item.description;
        if (item.priority != null && !item.priority.isBlank()) existing.priority = item.priority;
        if (item.status != null && !item.status.isBlank()) existing.status = normalizeStatus(item.status);
        if (item.deadline != null) existing.deadline = item.deadline;
        todoItemMapper.updateById(existing);
        return existing;
    }

    @Override
    public TodoItem splitFirstStep(Long userId, Long id) {
        TodoItem item = requireOwned(userId, id);
        String prompt = """
                你是 Aurora 的行动拆解助手。请把下面这个待办拆成一个十分钟内可以开始的第一步。
                只返回一句中文，不要编号，不要解释，不要给宏大计划。

                待办：%s
                背景：%s
                """.formatted(item.taskName, item.description == null ? "" : item.description);
        String step = llmClient.chat(new LlmRequest(userId, "TODO_FIRST_STEP", prompt));
        if (step == null || step.isBlank()) {
            step = "先打开一个空白页，把这件事写成一个最小动作。";
        }
        String prefix = "Aurora 拆出的第一步：";
        String current = item.description == null ? "" : item.description.trim();
        item.description = current.isBlank() ? prefix + step.trim() : current + "\n\n" + prefix + step.trim();
        item.status = "TODO";
        todoItemMapper.updateById(item);
        return item;
    }

    private TodoItem requireOwned(Long userId, Long id) {
        TodoItem item = todoItemMapper.selectById(id);
        if (item == null || !userId.equals(item.userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "todo not found");
        }
        return item;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || !VALID_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的状态值，允许 TODO、DOING、DONE、CANCELLED");
        }
        if ("IN_PROGRESS".equals(status)) return "DOING";
        if ("DROPPED".equals(status)) return "CANCELLED";
        return status;
    }
}

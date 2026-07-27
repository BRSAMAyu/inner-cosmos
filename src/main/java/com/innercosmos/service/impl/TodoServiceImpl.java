package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.TodoService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TodoServiceImpl implements TodoService {
    private static final Set<String> VALID_STATUSES = Set.of("TODO", "DOING", "IN_PROGRESS", "DONE", "CANCELLED", "DROPPED");
    // TASK 2e: explicit semantic status order (alphabetical order via orderByAsc("status")
    // put CANCELLED before DOING/DONE/TODO, so "已放下" sorted first and "待开始" last).
    // Sorted in Java (not SQL) so the ordering is portable across H2 (MODE=MySQL) and PostgreSQL.
    private static final List<String> STATUS_ORDER = List.of("TODO", "DOING", "DONE", "CANCELLED");

    private final TodoItemMapper todoItemMapper;
    private final LlmClient llmClient;
    private final MemoryCardMapper memoryCardMapper;

    public TodoServiceImpl(TodoItemMapper todoItemMapper, LlmClient llmClient, MemoryCardMapper memoryCardMapper) {
        this.todoItemMapper = todoItemMapper;
        this.llmClient = llmClient;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    public List<TodoItem> list(Long userId) {
        QueryWrapper<TodoItem> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        List<TodoItem> items = new java.util.ArrayList<>(todoItemMapper.selectList(query));
        items.sort((a, b) -> {
            int rankA = statusRank(a.status);
            int rankB = statusRank(b.status);
            if (rankA != rankB) return Integer.compare(rankA, rankB);
            return Long.compare(b.id, a.id);
        });
        return items;
    }

    private int statusRank(String status) {
        int rank = STATUS_ORDER.indexOf(status);
        return rank < 0 ? STATUS_ORDER.size() : rank;
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
        // SECURITY (cross-tenant content injection): sourceMemoryCardId is a public field on the
        // request body. Without this check, a client could attach an arbitrary task to another
        // user's memory card id, and that task would then render inside the card owner's private
        // 今日记录/星图详情 (see MemoryServiceImpl/MemorySettlementServiceImpl todo queries).
        if (item.sourceMemoryCardId != null) {
            MemoryCard card = memoryCardMapper.selectById(item.sourceMemoryCardId);
            if (card == null || !userId.equals(card.userId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "关联的记忆卡不存在或不属于你");
            }
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
        // TASK 2d: REPLACE any previously-appended split block instead of accumulating one per
        // click. The split block is always appended last (see below), so stripping everything
        // from the first occurrence of the prefix onward removes exactly one prior block and
        // nothing of the user's own original text before it.
        String current = stripPreviousSplitBlock(item.description, prefix).trim();
        item.description = current.isBlank() ? prefix + step.trim() : current + "\n\n" + prefix + step.trim();
        item.status = "TODO";
        todoItemMapper.updateById(item);
        return item;
    }

    private String stripPreviousSplitBlock(String description, String prefix) {
        if (description == null) {
            return "";
        }
        int idx = description.indexOf(prefix);
        if (idx < 0) {
            return description;
        }
        return description.substring(0, idx);
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

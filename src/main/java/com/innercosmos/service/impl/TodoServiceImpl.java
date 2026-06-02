package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    private final TodoItemMapper todoItemMapper;

    public TodoServiceImpl(TodoItemMapper todoItemMapper) {
        this.todoItemMapper = todoItemMapper;
    }

    @Override
    public List<TodoItem> list(Long userId) {
        QueryWrapper<TodoItem> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByAsc("status").orderByDesc("id");
        return todoItemMapper.selectList(query);
    }

    private static final Set<String> VALID_STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE", "DROPPED");

    @Override
    public TodoItem updateStatus(Long userId, Long id, String status) {
        TodoItem item = todoItemMapper.selectById(id);
        if (item == null || !userId.equals(item.userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "todo not found");
        }
        if (status == null || status.isBlank() || !VALID_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的状态值,允许值:TODO, IN_PROGRESS, DONE, DROPPED");
        }
        item.status = status;
        todoItemMapper.updateById(item);
        return item;
    }

    @Override
    public void delete(Long userId, Long id) {
        TodoItem item = todoItemMapper.selectById(id);
        if (item == null || !userId.equals(item.userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "todo not found");
        }
        todoItemMapper.deleteById(id);
    }
}

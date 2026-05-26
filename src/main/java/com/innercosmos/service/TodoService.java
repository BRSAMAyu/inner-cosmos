package com.innercosmos.service;

import com.innercosmos.entity.TodoItem;
import java.util.List;

public interface TodoService {
    List<TodoItem> list(Long userId);

    TodoItem updateStatus(Long userId, Long id, String status);

    void delete(Long userId, Long id);
}

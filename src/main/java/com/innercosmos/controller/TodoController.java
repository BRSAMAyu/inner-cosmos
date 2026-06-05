package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.service.TodoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/todos")
public class TodoController extends BaseController {
    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public ApiResponse<List<TodoItem>> list(HttpSession session) {
        return ApiResponse.ok(todoService.list(currentUserId(session)));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<TodoItem> status(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        return ApiResponse.ok(todoService.updateStatus(currentUserId(session), id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id, HttpSession session) {
        todoService.delete(currentUserId(session), id);
        return ApiResponse.ok(true);
    }

    @PostMapping
    public ApiResponse<TodoItem> create(@RequestBody TodoItem item, HttpSession session) {
        return ApiResponse.ok(todoService.create(currentUserId(session), item));
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoItem> update(@PathVariable Long id, @RequestBody TodoItem item, HttpSession session) {
        return ApiResponse.ok(todoService.update(currentUserId(session), id, item));
    }

    @PostMapping("/{id}/split")
    public ApiResponse<TodoItem> split(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(todoService.splitFirstStep(currentUserId(session), id));
    }
}

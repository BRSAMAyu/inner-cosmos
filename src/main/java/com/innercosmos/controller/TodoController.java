package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.service.TodoService;
import com.innercosmos.service.privacy.SensitiveDataBoundaryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/todos")
public class TodoController extends BaseController {
    private final TodoService todoService;

    /** CP-14 unified boundary guard; optional so direct-construction tests keep working. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SensitiveDataBoundaryService sensitiveBoundary;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public ApiResponse<List<TodoItem>> list(HttpSession session) {
        return ApiResponse.ok(todoService.list(currentUserId(session)));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<TodoItem> status(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        if (sensitiveBoundary != null) {
            sensitiveBoundary.assertReadable(SensitiveDataBoundaryService.SUBJECT_TODO, id, userId,
                    SensitiveDataBoundaryService.Purpose.OWNER_READ);
        }
        return ApiResponse.ok(todoService.updateStatus(userId, id, body.get("status")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        if (sensitiveBoundary != null) {
            sensitiveBoundary.assertReadable(SensitiveDataBoundaryService.SUBJECT_TODO, id, userId,
                    SensitiveDataBoundaryService.Purpose.OWNER_READ);
        }
        todoService.delete(userId, id);
        return ApiResponse.ok(true);
    }

    @PostMapping
    public ApiResponse<TodoItem> create(@RequestBody TodoItem item, HttpSession session) {
        return ApiResponse.ok(todoService.create(currentUserId(session), item));
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoItem> update(@PathVariable Long id, @RequestBody TodoItem item, HttpSession session) {
        Long userId = currentUserId(session);
        if (sensitiveBoundary != null) {
            sensitiveBoundary.assertReadable(SensitiveDataBoundaryService.SUBJECT_TODO, id, userId,
                    SensitiveDataBoundaryService.Purpose.OWNER_READ);
        }
        return ApiResponse.ok(todoService.update(userId, id, item));
    }

    @PostMapping("/{id}/split")
    public ApiResponse<TodoItem> split(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        if (sensitiveBoundary != null) {
            sensitiveBoundary.assertReadable(SensitiveDataBoundaryService.SUBJECT_TODO, id, userId,
                    SensitiveDataBoundaryService.Purpose.OWNER_READ);
        }
        return ApiResponse.ok(todoService.splitFirstStep(userId, id));
    }
}

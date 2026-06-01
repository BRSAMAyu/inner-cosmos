package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.service.ThoughtShredderService;
import com.innercosmos.vo.ShredderResultVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/thought-shredder")
public class ThoughtShredderController extends BaseController {
    private final ThoughtShredderService thoughtShredderService;

    public ThoughtShredderController(ThoughtShredderService thoughtShredderService) {
        this.thoughtShredderService = thoughtShredderService;
    }

    @PostMapping("/process")
    public ApiResponse<ShredderResultVO> process(@RequestBody Map<String, String> body, HttpSession session) {
        return ApiResponse.ok(thoughtShredderService.process(
                currentUserId(session),
                body.get("text"),
                body.getOrDefault("originalHandlingMode", "KEEP_ONLY_RESULT")
        ));
    }

    @GetMapping("/history")
    public ApiResponse<List<MemoryCard>> history(HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(thoughtShredderService.history(userId));
    }

    @PostMapping("/{id}/settle")
    public ApiResponse<Void> settle(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        thoughtShredderService.settle(userId, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        thoughtShredderService.delete(userId, id);
        return ApiResponse.ok(null);
    }
}

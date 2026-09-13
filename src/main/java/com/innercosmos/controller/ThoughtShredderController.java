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

    /** CP-14 unified boundary guard; optional so direct-construction tests keep working. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.innercosmos.service.privacy.SensitiveDataBoundaryService sensitiveBoundary;

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
        // CP-14: by-id operation on a P1 shredder memory card goes through the unified guard.
        if (sensitiveBoundary != null) {
            sensitiveBoundary.assertReadable("MEMORY", id, userId,
                    com.innercosmos.service.privacy.SensitiveDataBoundaryService.Purpose.OWNER_READ);
        }
        thoughtShredderService.settle(userId, id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        if (sensitiveBoundary != null) {
            sensitiveBoundary.assertReadable("MEMORY", id, userId,
                    com.innercosmos.service.privacy.SensitiveDataBoundaryService.Purpose.OWNER_READ);
        }
        thoughtShredderService.delete(userId, id);
        return ApiResponse.ok(null);
    }
}

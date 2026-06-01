package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.AuroraMemoryContextService;
import com.innercosmos.vo.AuroraMemoryContextVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aurora")
public class AuroraMemoryController extends BaseController {
    private final AuroraMemoryContextService memoryContextService;

    public AuroraMemoryController(AuroraMemoryContextService memoryContextService) {
        this.memoryContextService = memoryContextService;
    }

    @GetMapping("/memory-context")
    public ApiResponse<AuroraMemoryContextVO> memoryContext(@RequestParam(required = false) Long sessionId,
                                                            @RequestParam(required = false) String q,
                                                            HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(memoryContextService.buildContext(userId, sessionId, q, 8, 6));
    }
}

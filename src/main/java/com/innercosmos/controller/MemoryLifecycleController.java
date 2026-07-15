package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.MemoryOperation;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.vo.MemoryOperationPreviewVO;
import com.innercosmos.vo.MemoryOperationResultVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memory/operations")
public class MemoryLifecycleController extends BaseController {
    private final MemoryLifecycleService lifecycleService;

    public MemoryLifecycleController(MemoryLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/preview")
    public ApiResponse<MemoryOperationPreviewVO> preview(@RequestBody MemoryOperationCommand command,
                                                        HttpSession session) {
        return ApiResponse.ok(lifecycleService.preview(currentUserId(session), command));
    }

    @PostMapping
    public ApiResponse<MemoryOperationResultVO> execute(@RequestBody MemoryOperationCommand command,
                                                       HttpSession session) {
        return ApiResponse.ok(lifecycleService.execute(currentUserId(session), command));
    }

    @GetMapping
    public ApiResponse<List<MemoryOperation>> history(@RequestParam(required = false) Long memoryId,
                                                      HttpSession session) {
        return ApiResponse.ok(lifecycleService.history(currentUserId(session), memoryId));
    }
}

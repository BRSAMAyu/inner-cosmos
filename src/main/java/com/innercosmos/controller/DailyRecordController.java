package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.MemoryService;
import com.innercosmos.vo.DailyRecordVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-record")
public class DailyRecordController extends BaseController {
    private final MemoryService memoryService;

    public DailyRecordController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/latest")
    public ApiResponse<DailyRecordVO> latest(HttpSession session) {
        return ApiResponse.ok(memoryService.latestDailyRecord(currentUserId(session)));
    }
}

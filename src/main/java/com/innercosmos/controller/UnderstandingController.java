package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.UnderstandingOverviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/understanding")
public class UnderstandingController extends BaseController {
    private final UnderstandingOverviewService overviewService;

    public UnderstandingController(UnderstandingOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam(defaultValue = "7") int range,
                                                     HttpSession session) {
        return ApiResponse.ok(overviewService.overview(currentUserId(session), range));
    }
}

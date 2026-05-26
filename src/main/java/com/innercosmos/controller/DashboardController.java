package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.DashboardService;
import com.innercosmos.vo.DashboardVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController extends BaseController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardVO> summary(HttpSession session) {
        return ApiResponse.ok(dashboardService.summary(currentUserId(session)));
    }
}

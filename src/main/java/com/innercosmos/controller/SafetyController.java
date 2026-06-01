package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.SafetyCheckRequest;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.SafetyResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/safety")
public class SafetyController extends BaseController {
    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @GetMapping("/resources")
    public ApiResponse<List<String>> resources() {
        return ApiResponse.ok(safetyService.resources());
    }

    @PostMapping("/check")
    public ApiResponse<Boolean> check(@RequestBody SafetyCheckRequest request, HttpSession session) {
        safetyService.checkText(currentUserId(session), request.sessionId, request.text);
        return ApiResponse.ok(true);
    }

    @PostMapping("/inspect")
    public ApiResponse<SafetyResult> inspect(@RequestBody SafetyCheckRequest request, HttpSession session) {
        return ApiResponse.ok(safetyService.check(request.text, currentUserId(session), request.sessionId));
    }
}

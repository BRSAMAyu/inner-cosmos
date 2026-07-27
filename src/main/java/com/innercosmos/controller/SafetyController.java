package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.SafetyCheckRequest;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.SafetyResult;
import com.innercosmos.vo.SafetyResourceVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/safety", "/api/v1/safety"})
public class SafetyController extends BaseController {
    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    @GetMapping("/resources")
    public ApiResponse<List<String>> resources(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String region) {
        return ApiResponse.ok(safetyService.resources(locale, region));
    }

    @GetMapping("/resources/catalog")
    public ApiResponse<List<SafetyResourceVO>> resourceCatalog(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) String region) {
        return ApiResponse.ok(safetyService.resourceCatalog(locale, region));
    }

    @PostMapping("/check")
    public ApiResponse<Boolean> check(@RequestBody SafetyCheckRequest request, HttpSession session) {
        SafetyResult result = safetyService.check(request.text, currentUserId(session), request.sessionId,
                request.clientMessageId, request.locale, request.region);
        if (Boolean.TRUE.equals(result.blockModelCall)) {
            throw new com.innercosmos.exception.SafetyBlockedException(result.safeMessage);
        }
        return ApiResponse.ok(true);
    }

    @PostMapping("/inspect")
    public ApiResponse<SafetyResult> inspect(@RequestBody SafetyCheckRequest request, HttpSession session) {
        return ApiResponse.ok(safetyService.check(request.text, currentUserId(session), request.sessionId,
                request.clientMessageId, request.locale, request.region));
    }
}

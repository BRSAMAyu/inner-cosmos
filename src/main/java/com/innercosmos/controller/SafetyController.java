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

    /** CP-20 durable risk continuity; optional so direct-construction tests keep working. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.innercosmos.safety.CrisisContinuityService crisisContinuityService;
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

    /** CP-20 owner transparency: my durable risk-continuity status (never a diagnosis). */
    @org.springframework.web.bind.annotation.GetMapping("/me/status")
    public ApiResponse<com.innercosmos.safety.CrisisContinuityService.OwnerStatus> myStatus(
            HttpSession session) {
        return ApiResponse.ok(crisisContinuityService.ownerStatus(currentUserId(session)));
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

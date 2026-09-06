package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.consent.ConsentCenterService;
import com.innercosmos.service.consent.ConsentCenterService.ConsentView;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CP-07 consent center: what the user has agreed to, in one auditable place. J01's
 * progressive-consent flow reads the same state; withdrawal of managed purposes
 * (capsule compilation) routes back to their own surfaces.
 */
@RestController
@RequestMapping("/api/me/consents")
public class ConsentCenterController extends BaseController {

    private final ConsentCenterService consentCenterService;

    public ConsentCenterController(ConsentCenterService consentCenterService) {
        this.consentCenterService = consentCenterService;
    }

    public record DecideRequest(Boolean grant) {
    }

    @GetMapping
    public ApiResponse<List<ConsentView>> list(HttpSession session) {
        Long userId = currentUserId(session);
        return ApiResponse.ok(consentCenterService.list(userId));
    }

    @PostMapping("/{purpose}")
    public ApiResponse<ConsentView> decide(@PathVariable String purpose,
                                           @RequestBody DecideRequest request,
                                           HttpSession session) {
        Long userId = currentUserId(session);
        if (request == null || request.grant() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "grant 必填（true/false）");
        }
        return ApiResponse.ok(consentCenterService.decide(userId, purpose, request.grant()));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(HttpSession session) {
        Long userId = currentUserId(session);
        List<ConsentView> views = consentCenterService.list(userId);
        long granted = views.stream().filter(v -> v.granted() && !"DEFAULT".equals(v.source())
                && !"MANAGED".equals(v.group())).count();
        return ApiResponse.ok(Map.of(
                "totalPurposes", views.size(),
                "recordedDecisions", granted,
                "version", com.innercosmos.service.consent.ConsentPurpose.CURRENT_VERSION));
    }
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.WakeIntentCreateRequest;
import com.innercosmos.dto.WakeIntentRescheduleRequest;
import com.innercosmos.dto.WakeIntentNegotiateRequest;
import com.innercosmos.dto.WakeIntentFeedbackRequest;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.vo.WakeIntentVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aurora/wake-intents")
public class WakeIntentController extends BaseController {
    private final WakeIntentService service;

    public WakeIntentController(WakeIntentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WakeIntentVO>> list(HttpSession session) {
        return ApiResponse.ok(service.listActive(currentUserId(session)).stream().map(WakeIntentVO::from).toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<WakeIntentVO> get(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(WakeIntentVO.from(service.getOwned(currentUserId(session), id)));
    }

    @PostMapping
    public ApiResponse<WakeIntentVO> create(@Valid @RequestBody WakeIntentCreateRequest request, HttpSession session) {
        return ApiResponse.ok(WakeIntentVO.from(service.schedule(currentUserId(session), request.purpose,
            request.reasonForUser, request.content, request.earliestAt, request.preferredAt,
            request.latestAt, request.timezone, null)));
    }

    @PostMapping("/negotiate")
    public ApiResponse<WakeIntentVO> negotiate(@Valid @RequestBody WakeIntentNegotiateRequest request,
                                               HttpSession session) {
        return ApiResponse.ok(WakeIntentVO.from(service.scheduleNatural(currentUserId(session), request.when,
            request.purpose, request.reasonForUser, request.content, request.timezone, request.contextSessionId)));
    }

    @PutMapping("/{id}/schedule")
    public ApiResponse<WakeIntentVO> reschedule(@PathVariable Long id,
                                              @Valid @RequestBody WakeIntentRescheduleRequest request,
                                              HttpSession session) {
        return ApiResponse.ok(WakeIntentVO.from(service.reschedule(currentUserId(session), id, request.earliestAt,
            request.preferredAt, request.latestAt)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<WakeIntentVO> cancel(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(WakeIntentVO.from(service.cancel(currentUserId(session), id)));
    }

    @PostMapping("/{id}/feedback")
    public ApiResponse<WakeIntentVO> feedback(@PathVariable Long id,
                                              @Valid @RequestBody WakeIntentFeedbackRequest request,
                                              HttpSession session) {
        return ApiResponse.ok(WakeIntentVO.from(service.feedback(currentUserId(session), id, request.choice)));
    }
}

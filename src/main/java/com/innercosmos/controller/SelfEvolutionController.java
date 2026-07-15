package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.EmergenceProposalRequest;
import com.innercosmos.dto.SelfRollbackRequest;
import com.innercosmos.service.SelfEvolutionService;
import com.innercosmos.vo.SelfEvolutionOverviewVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aurora/self/evolution")
public class SelfEvolutionController extends BaseController {
    private final SelfEvolutionService service;

    public SelfEvolutionController(SelfEvolutionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SelfEvolutionOverviewVO> overview(HttpSession session) {
        return ApiResponse.ok(service.overview(currentUserId(session)));
    }

    @PostMapping("/proposals")
    public ApiResponse<SelfEvolutionOverviewVO> propose(@Valid @RequestBody EmergenceProposalRequest request,
                                                        HttpSession session) {
        Long userId = currentUserId(session);
        service.propose(userId, request);
        return ApiResponse.ok(service.overview(userId));
    }

    @PostMapping("/proposals/{id}/evaluate")
    public ApiResponse<SelfEvolutionOverviewVO> evaluate(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        service.evaluate(userId, id);
        return ApiResponse.ok(service.overview(userId));
    }

    @PostMapping("/proposals/{id}/activate")
    public ApiResponse<SelfEvolutionOverviewVO> activate(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        service.activate(userId, id);
        return ApiResponse.ok(service.overview(userId));
    }

    @PostMapping("/rollback")
    public ApiResponse<SelfEvolutionOverviewVO> rollback(@Valid @RequestBody SelfRollbackRequest request,
                                                         HttpSession session) {
        Long userId = currentUserId(session);
        service.rollback(userId, request.targetVersionId, request.restoreRelationship);
        return ApiResponse.ok(service.overview(userId));
    }
}

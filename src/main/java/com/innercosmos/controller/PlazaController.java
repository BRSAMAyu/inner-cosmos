package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.service.CapsuleService;
import com.innercosmos.vo.EchoCapsuleVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plaza")
public class PlazaController extends BaseController {
    private final CapsuleService capsuleService;

    public PlazaController(CapsuleService capsuleService) {
        this.capsuleService = capsuleService;
    }

    @GetMapping("/capsules")
    public ApiResponse<List<EchoCapsuleVO>> capsules() {
        // M-004: project to a public-safe VO — never expose personaPrompt/ownerContextNote/
        // style*/context*/authorizedMemoryIds on the unauthenticated plaza list.
        return ApiResponse.ok(capsuleService.plazaCapsules().stream().map(EchoCapsuleVO::fromPublic).toList());
    }

    @GetMapping("/matches")
    public ApiResponse<List<Map<String, Object>>> matches(HttpSession session) {
        return ApiResponse.ok(capsuleService.matchedCapsules(currentUserId(session)));
    }
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.service.CapsuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plaza")
public class PlazaController {
    private final CapsuleService capsuleService;

    public PlazaController(CapsuleService capsuleService) {
        this.capsuleService = capsuleService;
    }

    @GetMapping("/capsules")
    public ApiResponse<List<EchoCapsule>> capsules() {
        return ApiResponse.ok(capsuleService.plazaCapsules());
    }
}

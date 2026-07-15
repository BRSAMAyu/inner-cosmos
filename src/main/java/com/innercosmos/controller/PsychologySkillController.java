package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.PsychologySkillRunRequest;
import com.innercosmos.service.PsychologySkillService;
import com.innercosmos.skill.PsychologySkillManifest;
import com.innercosmos.vo.PsychologySkillRunVO;
import com.innercosmos.vo.PsychologySkillSuggestionVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/psychology/skills")
public class PsychologySkillController extends BaseController {
    private final PsychologySkillService service;

    public PsychologySkillController(PsychologySkillService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<PsychologySkillManifest>> manifests(HttpSession session) {
        currentUserId(session);
        return ApiResponse.ok(service.manifests());
    }

    @GetMapping("/runs")
    public ApiResponse<List<PsychologySkillRunVO>> runs(HttpSession session) {
        return ApiResponse.ok(service.runs(currentUserId(session)));
    }

    @PostMapping("/{skillId}/runs")
    public ApiResponse<PsychologySkillRunVO> run(@PathVariable String skillId,
                                                 @Valid @RequestBody PsychologySkillRunRequest request,
                                                 HttpSession session) {
        return ApiResponse.ok(service.run(currentUserId(session), skillId, request));
    }

    @PostMapping("/runs/{runId}/revoke")
    public ApiResponse<PsychologySkillRunVO> revoke(@PathVariable Long runId, HttpSession session) {
        return ApiResponse.ok(service.revoke(currentUserId(session), runId));
    }

    @PostMapping("/suggestions")
    public ApiResponse<PsychologySkillSuggestionVO> suggest(@RequestBody Map<String, String> body,
                                                             HttpSession session) {
        return ApiResponse.ok(service.suggest(currentUserId(session), body.get("text"), body.getOrDefault("locale", "zh-CN")));
    }
}

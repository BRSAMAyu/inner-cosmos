package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.PsychologySkillRelease;
import com.innercosmos.service.PsychologySkillReleaseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/psychology/skills/releases")
public class PsychologySkillReleaseController extends BaseController {
    private final PsychologySkillReleaseService service;

    public PsychologySkillReleaseController(PsychologySkillReleaseService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<PsychologySkillRelease>> releases(HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(service.releases());
    }

    @PostMapping("/{skillId}/{version}/review")
    public ApiResponse<PsychologySkillRelease> review(@PathVariable String skillId, @PathVariable String version,
                                                       @RequestBody Map<String, String> body, HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(service.recordHumanReview(skillId, version, currentUserId(session), body.get("note")));
    }

    @PostMapping("/{skillId}/{version}/publish")
    public ApiResponse<PsychologySkillRelease> publish(@PathVariable String skillId, @PathVariable String version,
                                                        HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(service.publish(skillId, version));
    }

    @PostMapping("/{skillId}/{version}/disable")
    public ApiResponse<PsychologySkillRelease> disable(@PathVariable String skillId, @PathVariable String version,
                                                        @RequestBody(required = false) Map<String, String> body,
                                                        HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(service.disable(skillId, version, body == null ? null : body.get("reason")));
    }

    @PostMapping("/{skillId}/{version}/rollback")
    public ApiResponse<PsychologySkillRelease> rollback(@PathVariable String skillId, @PathVariable String version,
                                                         @RequestBody(required = false) Map<String, String> body,
                                                         HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(service.rollback(skillId, version, body == null ? null : body.get("reason")));
    }
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.ClaimCandidateService;
import com.innercosmos.vo.ClaimCandidateVO;
import com.innercosmos.vo.CorrectionConfirmationVO;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campaign B — user-facing review of automatically extracted claim candidates. The user sees what
 * Aurora inferred about them (with provenance) and can confirm it into an authoritative claim or
 * dismiss it. Confirmation goes through the correction path so it inherits impact preview and
 * downstream propagation. Owner-scoped throughout.
 */
@RestController
@RequestMapping("/api/aurora/claims/candidates")
public class ClaimCandidateController extends BaseController {

    private final ClaimCandidateService claimCandidateService;

    public ClaimCandidateController(ClaimCandidateService claimCandidateService) {
        this.claimCandidateService = claimCandidateService;
    }

    @GetMapping
    public ApiResponse<List<ClaimCandidateVO>> list(HttpSession session) {
        return ApiResponse.ok(claimCandidateService.listCandidates(currentUserId(session)));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<CorrectionConfirmationVO> confirm(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(claimCandidateService.confirmCandidate(currentUserId(session), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> dismiss(@PathVariable Long id, HttpSession session) {
        claimCandidateService.dismissCandidate(currentUserId(session), id);
        return ApiResponse.ok(Map.of("dismissed", id));
    }
}

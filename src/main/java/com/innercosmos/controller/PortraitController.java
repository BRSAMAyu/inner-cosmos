package com.innercosmos.controller;

import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserPortraitHistory;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portrait")
public class PortraitController extends BaseController {
    @Autowired
    private UserPortraitService portraitService;

    /** CP-23 claim park/restore/delete; optional so direct-construction tests keep working. */
    @Autowired(required = false)
    private com.innercosmos.service.portrait.PortraitClaimControlService claimControlService;

    /** CP-23 搁置: park a claim the user does not recognize — hidden from the view and from
     *  Aurora's per-turn context, row kept for audit. */
    @PostMapping("/claims/{claimId}/suppress")
    public ApiResponse<com.innercosmos.entity.UnderstandingClaim> suppress(
            @PathVariable Long claimId, @RequestBody(required = false) java.util.Map<String, String> body,
            @RequestParam(required = false) Integer expectedVersion,
            HttpSession session) {
        return ApiResponse.ok(claimControlService.suppress(currentUserId(session), claimId,
                body == null ? null : body.get("reason"), expectedVersion));
    }

    /** CP-23 恢复: un-park a previously suppressed claim. */
    @PostMapping("/claims/{claimId}/restore")
    public ApiResponse<com.innercosmos.entity.UnderstandingClaim> restore(
            @PathVariable Long claimId, @RequestParam(required = false) Integer expectedVersion,
            HttpSession session) {
        return ApiResponse.ok(claimControlService.restore(currentUserId(session), claimId, expectedVersion));
    }

    /** CP-23 删除: owner removes a claim outright — soft-deleted with an audit row. */
    @DeleteMapping("/claims/{claimId}")
    public ApiResponse<com.innercosmos.entity.UnderstandingClaim> delete(
            @PathVariable Long claimId, @RequestBody(required = false) java.util.Map<String, String> body,
            @RequestParam(required = false) Integer expectedVersion,
            HttpSession session) {
        return ApiResponse.ok(claimControlService.delete(currentUserId(session), claimId,
                body == null ? null : body.get("reason"), expectedVersion));
    }

    @GetMapping
    public ApiResponse<List<UserPortrait>> get(HttpSession session) {
        return ApiResponse.ok(portraitService.getAll(currentUserId(session)));
    }

    @GetMapping("/history")
    public ApiResponse<List<UserPortraitHistory>> history(@RequestParam String dim, HttpSession session) {
        return ApiResponse.ok(portraitService.getHistory(currentUserId(session), dim));
    }
}
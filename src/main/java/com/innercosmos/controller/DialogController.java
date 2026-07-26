package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.DialogSessionUpdateRequest;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.service.DialogService;
import com.innercosmos.vo.DialogSessionSummaryVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dialog/session")
public class DialogController extends BaseController {
    private final DialogService dialogService;

    public DialogController(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @PostMapping("/create")
    public ApiResponse<DialogSession> create(@RequestBody SessionCreateRequest request, HttpSession session) {
        return ApiResponse.ok(dialogService.create(currentUserId(session), request));
    }

    @GetMapping
    public ApiResponse<List<DialogSessionSummaryVO>> sessions(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            HttpSession session) {
        return ApiResponse.ok(dialogService.sessions(
                currentUserId(session), beforeId, limit, includeArchived));
    }

    @GetMapping("/current")
    public ApiResponse<DialogSessionSummaryVO> current(HttpSession session) {
        return ApiResponse.ok(dialogService.current(currentUserId(session)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DialogSessionSummaryVO> get(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(dialogService.get(currentUserId(session), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DialogSessionSummaryVO> update(
            @PathVariable Long id,
            @RequestBody DialogSessionUpdateRequest request,
            HttpSession session) {
        return ApiResponse.ok(dialogService.update(currentUserId(session), id, request));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<DialogMessage>> messages(@PathVariable Long id, HttpSession session) {
        dialogService.verifyOwnership(currentUserId(session), id);
        return ApiResponse.ok(dialogService.messages(id));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<DialogSession> finish(@PathVariable Long id, HttpSession session) {
        return ApiResponse.ok(dialogService.finish(currentUserId(session), id));
    }
}

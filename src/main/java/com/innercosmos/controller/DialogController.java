package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.SessionCreateRequest;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.service.DialogService;
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

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.vo.DataRetractionReceiptVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * G5 PROFILE-PROPAGATION, made user-visible: the owner's auditable data-rights trail — what Aurora
 * stopped using (matching vectors, retrieval embeddings, …) as a consequence of the owner forgetting
 * a memory, archiving a capsule, revoking consent or correcting an inference. Turns the otherwise
 * invisible backend propagation into a trust-building surface. Owner-scoped; no sensitive payload.
 */
@RestController
@RequestMapping("/api/me/data-rights")
public class DataRightsController extends BaseController {
    private static final int DEFAULT_LIMIT = 50;

    private final DataRetractionReceiptService retractionReceiptService;

    public DataRightsController(DataRetractionReceiptService retractionReceiptService) {
        this.retractionReceiptService = retractionReceiptService;
    }

    @GetMapping("/receipts")
    public ApiResponse<List<DataRetractionReceiptVO>> receipts(
            @RequestParam(required = false) Integer limit, HttpSession session) {
        Long userId = currentUserId(session);
        int bounded = limit == null ? DEFAULT_LIMIT : limit;
        List<DataRetractionReceiptVO> receipts = retractionReceiptService.listForOwner(userId, bounded)
                .stream().map(DataRetractionReceiptVO::from).toList();
        return ApiResponse.ok(receipts);
    }
}

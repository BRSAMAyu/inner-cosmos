package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memory/retrieval")
public class MemoryRetrievalController extends BaseController {
    private final MemoryRetrievalService retrievalService;
    public MemoryRetrievalController(MemoryRetrievalService retrievalService) { this.retrievalService = retrievalService; }

    @PostMapping
    public ApiResponse<MemoryEvidencePackVO> retrieve(@RequestBody MemoryRetrievalQuery query, HttpSession session) {
        return ApiResponse.ok(retrievalService.retrieve(currentUserId(session), query));
    }
}

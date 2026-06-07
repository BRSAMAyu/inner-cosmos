package com.innercosmos.controller;

import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.AgentUserRelationship;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/relationship")
public class RelationshipController extends BaseController {
    @Autowired
    private AgentUserRelationshipService relService;

    @GetMapping
    public ApiResponse<AgentUserRelationship> get(HttpSession session) {
        return ApiResponse.ok(relService.getOrInit(currentUserId(session)));
    }
}
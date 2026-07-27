package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.service.PublicDemoClassroomGroupService;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/demo/classroom-group")
@ConditionalOnProperty(prefix = "inner-cosmos.demo", name = "public-entry-enabled", havingValue = "true")
public class PublicDemoClassroomGroupController extends BaseController {
    private final PublicDemoClassroomGroupService service;

    public PublicDemoClassroomGroupController(PublicDemoClassroomGroupService service) {
        this.service = service;
    }

    @PostMapping("/join")
    public ApiResponse<SocialGroup> join(HttpSession session) {
        return ApiResponse.ok(service.join(currentUserId(session)));
    }
}

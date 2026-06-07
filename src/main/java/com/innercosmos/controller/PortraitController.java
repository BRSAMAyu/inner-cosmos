package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserPortraitHistory;
import com.innercosmos.mapper.UserPortraitHistoryMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portrait")
public class PortraitController extends BaseController {
    @Autowired
    private UserPortraitService portraitService;
    @Autowired
    private UserPortraitHistoryMapper historyMapper;

    @GetMapping
    public ApiResponse<List<UserPortrait>> get(HttpSession session) {
        return ApiResponse.ok(portraitService.getAll(currentUserId(session)));
    }

    @GetMapping("/history")
    public ApiResponse<List<UserPortraitHistory>> history(@RequestParam String dim, HttpSession session) {
        return ApiResponse.ok(historyMapper.selectList(new QueryWrapper<UserPortraitHistory>()
                .eq("user_id", currentUserId(session))
                .eq("dim", dim)
                .orderByDesc("recorded_at")
                .last("LIMIT 10")));
    }
}
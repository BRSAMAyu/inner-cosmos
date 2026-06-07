package com.innercosmos.controller;

import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserPortraitHistory;
import com.innercosmos.mapper.UserPortraitHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        return ApiResponse.ok(portraitService.getAll(currentUserId(session));
    }

    @GetMapping("/history")
    public ApiResponse<List<UserPortraitHistory>> history(@RequestParam String dim, HttpSession session) {
        return ApiResponse.ok(historyMapper.selectList(new LambdaQueryWrapper<UserPortraitHistory>()
                .eq(UserPortraitHistory::getUserId, currentUserId(session))
                .eq(UserPortraitHistory::getDim, dim)
                .orderByDesc(UserPortraitHistory::getRecordedAt)
                .last("LIMIT 10")));
    }
}
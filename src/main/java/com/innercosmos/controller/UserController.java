package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.UserProfileVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController extends BaseController {
    private final UserService userService;
    private final UserProfileMapper userProfileMapper;

    public UserController(UserService userService, UserProfileMapper userProfileMapper) {
        this.userService = userService;
        this.userProfileMapper = userProfileMapper;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile(HttpSession session) {
        Long userId = currentUserId(session);
        User user = userService.current(userId);
        QueryWrapper<UserProfile> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        UserProfile profile = userProfileMapper.selectOne(query);
        return ApiResponse.ok(UserProfileVO.from(user, profile));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileVO> updateProfile(@RequestBody UserProfileVO profile, HttpSession session) {
        Long userId = currentUserId(session);
        userService.updateProfile(userId, profile);
        User user = userService.current(userId);
        QueryWrapper<UserProfile> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        UserProfile updated = userProfileMapper.selectOne(query);
        return ApiResponse.ok(UserProfileVO.from(user, updated));
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> exportData(HttpSession session) {
        return ApiResponse.ok(userService.exportData(currentUserId(session)));
    }

    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount(HttpSession session) {
        userService.deleteAccount(currentUserId(session));
        session.invalidate();
        return ApiResponse.ok(null);
    }
}

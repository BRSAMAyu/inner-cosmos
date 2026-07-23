package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.UserProfileVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController extends BaseController {
    private final UserService userService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UserController(UserService userService,
                          org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile(HttpSession session) {
        Long userId = currentUserId(session);
        User user = userService.current(userId);
        UserProfile profile = userService.getProfile(userId);
        return ApiResponse.ok(UserProfileVO.from(user, profile));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileVO> updateProfile(@RequestBody UserProfileVO profile, HttpSession session) {
        Long userId = currentUserId(session);
        userService.updateProfile(userId, profile);
        User user = userService.current(userId);
        UserProfile updated = userService.getProfile(userId);
        return ApiResponse.ok(UserProfileVO.from(user, updated));
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> exportData(HttpSession session) {
        return ApiResponse.ok(userService.exportData(currentUserId(session)));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody Map<String, String> body, HttpSession session) {
        userService.changePassword(currentUserId(session), body.get("oldPassword"), body.get("newPassword")); // M-032
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount(@RequestBody(required = false) Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        // M-033: require password re-auth before destructive account deletion — with CSRF off,
        // a forged call must not be able to destroy ~25 tables without the password.
        String password = body == null ? null : body.get("password");
        User user = userService.current(userId);
        if (password == null || password.isBlank() || !passwordEncoder.matches(password, user.passwordHash)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "密码不正确，无法注销账号");
        }
        userService.deleteAccount(userId);
        session.invalidate();
        return ApiResponse.ok(null);
    }
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.UserProfileVO;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileVO> register(@Valid @RequestBody RegisterRequest request, HttpSession session) {
        User user = userService.register(request);
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return ApiResponse.ok(UserProfileVO.from(user));
    }

    @PostMapping("/login")
    public ApiResponse<UserProfileVO> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        User user = userService.login(request);
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return ApiResponse.ok(UserProfileVO.from(user));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok(true);
    }

    @GetMapping("/current")
    public ApiResponse<UserProfileVO> current(HttpSession session) {
        return ApiResponse.ok(UserProfileVO.from(userService.current(currentUserId(session))));
    }
}

package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController extends BaseController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileVO> register(@Valid @RequestBody RegisterRequest request,
                                                HttpServletRequest httpRequest) {
        User user = userService.register(request);
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return ApiResponse.ok(UserProfileVO.from(user));
    }

    @PostMapping("/login")
    public ApiResponse<UserProfileVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        User user = userService.login(request);
        // Rotate the identifier to prevent fixation. The browser reloads after login and
        // obtains a fresh synchronizer token for the authenticated session.
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return ApiResponse.ok(UserProfileVO.from(user));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok(true);
    }

    /** Materializes the SPA synchronizer token without exposing authenticated data. */
    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return ApiResponse.ok(Map.of());
        }
        return ApiResponse.ok(Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName()));
    }

    @GetMapping("/current")
    public ApiResponse<UserProfileVO> current(HttpSession session) {
        return ApiResponse.ok(UserProfileVO.from(userService.current(currentUserId(session))));
    }
}

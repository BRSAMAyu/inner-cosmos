package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseController {

    @Autowired
    protected UserService userService;

    protected Long currentUserId(HttpSession session) {
        Object value = session.getAttribute(Constants.SESSION_USER_KEY);
        if (value == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "not logged in");
        }
        return (Long) value;
    }

    protected void requireAdmin(HttpSession session) {
        Long userId = currentUserId(session);
        User user = userService.current(userId);
        if (!Constants.ROLE_ADMIN.equals(user.role)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "需要管理员权限");
        }
    }
}

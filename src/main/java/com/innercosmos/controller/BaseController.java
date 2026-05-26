package com.innercosmos.controller;

import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import jakarta.servlet.http.HttpSession;

public abstract class BaseController {
    protected Long currentUserId(HttpSession session) {
        Object value = session.getAttribute(Constants.SESSION_USER_KEY);
        if (value == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "not logged in");
        }
        return (Long) value;
    }
}

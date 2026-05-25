package com.innercosmos.exception;

import com.innercosmos.common.ErrorCode;

public class SafetyBlockedException extends BusinessException {
    public SafetyBlockedException(String message) {
        super(ErrorCode.SAFETY_BLOCKED, message);
    }
}

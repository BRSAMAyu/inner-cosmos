package com.innercosmos.exception;

import com.innercosmos.common.ErrorCode;

public class LetterStateException extends BusinessException {
    public LetterStateException(String message) {
        super(ErrorCode.LETTER_STATE_INVALID, message);
    }
}

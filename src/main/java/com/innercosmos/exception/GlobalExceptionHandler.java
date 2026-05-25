package com.innercosmos.exception;

import com.innercosmos.common.ApiResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException exception) {
        return ApiResponse.fail(exception.code, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        return ApiResponse.fail("VALIDATION_ERROR", exception.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception exception) {
        return ApiResponse.fail("INTERNAL_ERROR", exception.getMessage());
    }
}

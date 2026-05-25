package com.innercosmos.common;

public class ApiResponse<T> {
    public boolean success;
    public String code;
    public String message;
    public T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.code = "OK";
        response.message = "success";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = code;
        response.message = message;
        return response;
    }
}

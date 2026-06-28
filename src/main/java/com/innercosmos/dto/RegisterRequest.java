package com.innercosmos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "请填写用户名")
    @Size(min = 2, max = 50, message = "用户名需为 2-50 个字符")
    public String username;
    @NotBlank(message = "请填写密码")
    @Size(min = 8, max = 128, message = "密码需为 8-128 位")
    public String password;
    public String nickname;
    @Email(message = "邮箱格式不正确")
    @Size(max = 255)
    public String email;
}

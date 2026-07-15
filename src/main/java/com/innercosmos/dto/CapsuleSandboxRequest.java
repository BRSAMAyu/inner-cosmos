package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CapsuleSandboxRequest {
    @NotBlank(message = "沙盒问题不能为空")
    @Size(max = 2000, message = "沙盒问题不能超过2000字")
    public String question;
}

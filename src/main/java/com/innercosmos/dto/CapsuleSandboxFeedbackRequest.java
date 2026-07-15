package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CapsuleSandboxFeedbackRequest {
    @NotNull(message = "Genome版本不能为空")
    public Long genomeVersionId;
    @NotBlank(message = "沙盒问题不能为空")
    @Size(max = 2000, message = "沙盒问题不能超过2000字")
    public String question;
    @NotBlank(message = "沙盒回应不能为空")
    @Size(max = 8000, message = "沙盒回应不能超过8000字")
    public String response;
    @NotBlank(message = "反馈类型不能为空")
    public String rating;
    @Size(max = 1000, message = "补充说明不能超过1000字")
    public String comment;
}

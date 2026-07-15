package com.innercosmos.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public class PsychologySkillRunRequest {
    @AssertTrue(message = "必须明确同意后才能开始这项反思")
    public boolean explicitConsent;

    @NotBlank(message = "请选择结果保留方式")
    public String retentionChoice;

    @NotBlank(message = "locale is required")
    public String locale;

    @NotEmpty(message = "consentScopes is required")
    public List<String> consentScopes;

    @NotEmpty(message = "请先回答这项练习的问题")
    public Map<String, String> answers;
}

package com.innercosmos.service;

import com.innercosmos.entity.PromptTemplateEntity;
import java.util.List;

public interface PromptVersionService {

    String getActivePrompt(String promptKey);

    PromptTemplateEntity createPrompt(String promptKey, String content, String description);

    List<PromptTemplateEntity> listVersions(String promptKey);
}

package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_ai_interaction_log")
public class AiInteractionLog extends BaseEntity {
    public Long userId;
    public String moduleName;
    public String provider;
    public String modelName;
    public String requestPrompt;
    public String responseText;
    public String requestJson;
    public String responseJson;
    public Boolean success;
    public String errorMessage;
    public Long latencyMs;
    public Integer tokenInputEstimate;
    public Integer tokenOutputEstimate;
}

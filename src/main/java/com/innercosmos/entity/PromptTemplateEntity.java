package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_prompt_template")
public class PromptTemplateEntity extends BaseEntity {
    public String promptKey;
    public Integer version;
    public String content;
    public String description;
    public Boolean enabled;
}

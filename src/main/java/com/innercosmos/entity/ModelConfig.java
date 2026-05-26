package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_model_config")
public class ModelConfig extends BaseEntity {
    public String configKey;
    public String configValue;
    public String description;
}

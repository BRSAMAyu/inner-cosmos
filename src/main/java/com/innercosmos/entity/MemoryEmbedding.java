package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_memory_embedding")
public class MemoryEmbedding extends BaseEntity {
    public Long userId;
    public Long memoryId;
    public String modelName;
    public String modelVersion;
    public Integer sourceVersion;
    public String taskScope;
    public Integer dimensions;
    public String embeddingJson;
    public String status;
}

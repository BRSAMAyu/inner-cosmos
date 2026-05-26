package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_authorized_memory_ref")
public class AuthorizedMemoryRef extends BaseEntity {
    public Long capsuleId;
    public Long memoryCardId;
    public String abstractExcerpt;
    public String authorizationStatus;
}

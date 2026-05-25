package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_thought_fragment")
public class ThoughtFragment extends BaseEntity {
    public Long userId;
    public Long memoryCardId;
    public String fragmentType;
    public String rawExcerpt;
    public String aiAnalysis;
    public String reframeText;
}

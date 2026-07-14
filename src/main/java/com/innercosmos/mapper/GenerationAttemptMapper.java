package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.conversation.entity.GenerationAttempt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GenerationAttemptMapper extends BaseMapper<GenerationAttempt> {}

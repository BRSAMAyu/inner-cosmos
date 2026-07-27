package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.conversation.entity.TurnGenerationRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TurnGenerationRequestMapper extends BaseMapper<TurnGenerationRequest> {
}

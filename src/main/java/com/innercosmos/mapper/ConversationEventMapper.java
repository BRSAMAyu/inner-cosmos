package com.innercosmos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.innercosmos.conversation.entity.ConversationEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationEventMapper extends BaseMapper<ConversationEvent> {}

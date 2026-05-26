package com.innercosmos.vo;

import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;

import java.util.List;

public class ShredderResultVO {
    public String originalHandlingMode;
    public String coreFeeling;
    public String hiddenNeed;
    public List<String> noiseToDrop;
    public String sentenceToKeep;
    public MemoryCard memoryCard;
    public List<ThoughtFragment> fragments;
    public TodoItem suggestedTodo;
}

package com.innercosmos.vo;

import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;

import java.util.List;

public class StarfieldDetailVO {
    public MemoryCard card;
    public List<ThoughtFragment> fragments;
    public List<TodoItem> todos;
    public List<EmotionTrace> emotions;
    public List<RelationMention> relations;
    public List<MemoryTheme> themes;
    public String gravityExplanation;
    public String auroraObservation;
    public Boolean canCreateCapsule;
}

package com.innercosmos.vo;

import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.entity.TodoItem;
import java.util.ArrayList;
import java.util.List;

public class DailyRecordVO {
    public String theme;
    public String auroraSummary;
    public MemoryCard mainMemory;
    public List<ThoughtFragment> fragments = new ArrayList<>();
    public List<EmotionTrace> emotions = new ArrayList<>();
    public List<TodoItem> todos = new ArrayList<>();
    /**
     * Relationship cues belonging to THIS day's memory card only. The record card must not borrow
     * the user's all-time relation mentions (/api/relation/list), or a day with no relational
     * content silently shows last week's cues as if they happened today.
     */
    public List<RelationMention> relations = new ArrayList<>();
    public boolean capsuleSuggested;
}

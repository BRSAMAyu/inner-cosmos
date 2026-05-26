package com.innercosmos.vo;

import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.entity.MemoryCard;
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
    public boolean capsuleSuggested;
}

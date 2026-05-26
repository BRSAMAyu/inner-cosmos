package com.innercosmos.vo;

import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.TodoItem;
import java.util.ArrayList;
import java.util.List;

public class DashboardVO {
    public String greeting;
    public String emotionWeather;
    public String lastSummary;
    public long memoryCount;
    public long capsuleCount;
    public long unreadLetterCount;
    public long aiLogCount;
    public List<MemoryCard> highGravityMemories = new ArrayList<>();
    public List<TodoItem> todos = new ArrayList<>();
    public List<EchoCapsule> recommendations = new ArrayList<>();
}

package com.innercosmos.vo;

import java.time.LocalDateTime;
import java.util.List;

public class StarfieldVO {
    public Long id;
    public String title;
    public String memoryType;
    public String theme;
    public String color;
    public Double glow;
    public Double freshness;
    public Boolean suggestedForCapsule;
    public double gravity;
    public double x;
    public double y;
    public String summary;
    public String detail;
    public String memoryLayer;
    public Double confidence;
    public Integer versionNo;
    public String peopleTags;
    public String provenanceRefs;
    public String status;
    public LocalDateTime occurredAt;
    public String ariaLabel;
    public List<Long> connectedMemoryIds;
}

package com.innercosmos.vo;

import java.time.LocalDateTime;

public class DialogSessionSummaryVO {
    public Long id;
    public String title;
    public String status;
    public Integer messageCount;
    public String preview;
    public Long activeTurnId;
    public LocalDateTime startedAt;
    public LocalDateTime lastActivityAt;
    public LocalDateTime archivedAt;
    public LocalDateTime pinnedAt;
    public LocalDateTime updatedAt;
}

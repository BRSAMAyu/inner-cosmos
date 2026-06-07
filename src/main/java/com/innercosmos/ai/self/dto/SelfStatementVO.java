package com.innercosmos.ai.self.dto;

import lombok.Data;

@Data
public class SelfStatementVO {
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long messageId;
    private String statementText;
    private String trigger;
    private String createdAt;
}
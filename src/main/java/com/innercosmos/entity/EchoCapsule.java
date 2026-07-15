package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_echo_capsule")
public class EchoCapsule extends BaseEntity {
    public Long ownerUserId;
    public String capsuleType;
    public String pseudonym;
    public String intro;
    public String personaPrompt;
    public String publicTags;
    public String authorizedMemoryIds;
    public Double echoEnergy;
    public Double freshnessScore;
    public Integer conversationLimitPerDay;
    public String visibilityStatus;
    public Boolean isPublic;
    public LocalDateTime lastMemoryUpdateAt;
    public String ownerContextNote;
    public String styleProfileJson;
    public String contextPreviewJson;
    public Boolean standInEnabled;
    public String realContactPolicy;
    // IC-CAP-002 B-4: last time this capsule had a genuinely successful chat turn.
    public LocalDateTime lastActivityAt;
    public Long activeGenomeVersionId;
    // Isolated Simulator capability contract (对齐文档/16 Campaign C): true only for capsules
    // compiled from explicitly SIMULATOR_AUTHORIZED memories for testing/research. Permanently
    // excluded from publish, plaza listing, matching and real visitor persona chat.
    public Boolean simulatorOnly;
}

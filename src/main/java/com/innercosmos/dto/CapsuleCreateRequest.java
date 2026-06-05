package com.innercosmos.dto;

import java.util.List;

public class CapsuleCreateRequest {
    public String pseudonym;
    public String intro;
    public List<Long> memoryIds;
    public String boundaryNote;
    public List<String> publicTags;
    public List<String> allowTopics;
    public List<String> blockedTopics;
    public Integer maxConversationTurns;
    public Boolean allowLetterRequest;
    public String privacyLevel;
    public String visibilityStatus;
    public Boolean isPublic;
    public String ownerContextNote;
    public String styleProfileJson;
    public String contextPreviewJson;
    public Boolean standInEnabled;
    public String realContactPolicy;
}

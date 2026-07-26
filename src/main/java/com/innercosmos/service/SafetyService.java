package com.innercosmos.service;

import com.innercosmos.vo.SafetyResult;
import java.util.List;

public interface SafetyService {
    void checkText(Long userId, Long sessionId, String text);

    List<String> resources();

    SafetyResult check(String text, Long userId, Long sessionId);

    default List<String> resources(String locale, String region) {
        return resources();
    }

    default SafetyResult check(String text, Long userId, Long sessionId, String observationId,
                               String locale, String region) {
        return check(text, userId, sessionId);
    }
}

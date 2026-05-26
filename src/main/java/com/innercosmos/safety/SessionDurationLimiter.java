package com.innercosmos.safety;

import org.springframework.stereotype.Component;

@Component
public class SessionDurationLimiter {
    public boolean shouldPause(int minutes) {
        return minutes > 90;
    }
}

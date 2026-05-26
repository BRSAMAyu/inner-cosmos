package com.innercosmos.ai.agent;

import org.springframework.stereotype.Component;

@Component
public class LetterGuardAgent {
    public boolean allow(String text) {
        if (text == null) {
            return true;
        }
        return !(text.contains("威胁") || text.contains("骚扰") || text.contains("人肉"));
    }
}

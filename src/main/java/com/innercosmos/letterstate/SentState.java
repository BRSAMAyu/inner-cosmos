package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class SentState implements LetterState {
    public String code() { return "SENT"; }
    public Set<String> next() { return Set.of("FLYING", "BLOCKED"); }
}

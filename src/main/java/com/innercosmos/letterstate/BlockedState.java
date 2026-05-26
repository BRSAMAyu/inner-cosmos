package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class BlockedState implements LetterState {
    public String code() { return "BLOCKED"; }
    public Set<String> next() { return Set.of("ARCHIVED"); }
}

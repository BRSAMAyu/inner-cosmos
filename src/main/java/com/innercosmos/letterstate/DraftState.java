package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class DraftState implements LetterState {
    public String code() { return "DRAFT"; }
    public Set<String> next() { return Set.of("SENT", "ARCHIVED"); }
}

package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class ArchivedState implements LetterState {
    public String code() { return "ARCHIVED"; }
    public Set<String> next() { return Set.of(); }
}

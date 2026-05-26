package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class RepliedState implements LetterState {
    public String code() { return "REPLIED"; }
    public Set<String> next() { return Set.of("ARCHIVED"); }
}

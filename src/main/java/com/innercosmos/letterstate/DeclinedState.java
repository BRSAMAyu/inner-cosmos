package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class DeclinedState implements LetterState {
    public String code() { return "DECLINED"; }
    public Set<String> next() { return Set.of("ARCHIVED", "BLOCKED"); }
}

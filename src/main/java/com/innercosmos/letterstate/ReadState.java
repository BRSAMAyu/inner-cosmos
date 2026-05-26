package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class ReadState implements LetterState {
    public String code() { return "READ"; }
    public Set<String> next() { return Set.of("REPLIED", "DECLINED", "ARCHIVED", "BLOCKED"); }
}

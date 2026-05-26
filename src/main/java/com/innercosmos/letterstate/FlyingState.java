package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class FlyingState implements LetterState {
    public String code() { return "FLYING"; }
    public Set<String> next() { return Set.of("DELIVERED", "BLOCKED"); }
}

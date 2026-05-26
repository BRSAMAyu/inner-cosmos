package com.innercosmos.letterstate;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class DeliveredState implements LetterState {
    public String code() { return "DELIVERED"; }
    public Set<String> next() { return Set.of("READ", "DECLINED", "BLOCKED"); }
}

package com.innercosmos.letterstate;

import com.innercosmos.exception.LetterStateException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LetterStateRegistry {
    private final Map<String, LetterState> states = new HashMap<>();

    public LetterStateRegistry(List<LetterState> stateList) {
        for (LetterState state : stateList) {
            states.put(state.code(), state);
        }
    }

    public void validate(String from, String to) {
        LetterState state = states.get(from);
        if (state == null || !state.canTransitTo(to)) {
            throw new LetterStateException("cannot transit letter from " + from + " to " + to);
        }
    }
}

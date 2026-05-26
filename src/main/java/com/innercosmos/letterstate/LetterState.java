package com.innercosmos.letterstate;

import java.util.Set;

public interface LetterState {
    String code();

    Set<String> next();

    default boolean canTransitTo(String target) {
        return next().contains(target);
    }
}

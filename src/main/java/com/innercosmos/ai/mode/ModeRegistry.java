package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for all Aurora conversation mode strategies.
 * Provides lookup by mode name.
 */
@Component
public class ModeRegistry {

    private final Map<String, ModeStrategy> strategies;

    public ModeRegistry(List<ModeStrategy> strategyList) {
        this.strategies = strategyList.stream()
            .collect(Collectors.toMap(ModeStrategy::name, Function.identity()));
    }

    /**
     * Get strategy by mode name.
     * @param mode the mode name (DAILY_TALK, THOUGHT_CLARIFY, SOCRATIC)
     * @return the strategy, or null if not found
     */
    public ModeStrategy get(String mode) {
        return strategies.get(mode);
    }

    /**
     * Get all registered mode names.
     */
    public List<String> names() {
        return strategies.keySet().stream().sorted().collect(Collectors.toList());
    }
}
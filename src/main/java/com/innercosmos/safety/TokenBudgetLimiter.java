package com.innercosmos.safety;

import org.springframework.stereotype.Component;

@Component
public class TokenBudgetLimiter {
    public boolean overBudget(int estimatedTokens) {
        return estimatedTokens > 6000;
    }
}

package com.innercosmos.safety;

public interface SafetyRule {
    SafetyMatch match(String text);
}

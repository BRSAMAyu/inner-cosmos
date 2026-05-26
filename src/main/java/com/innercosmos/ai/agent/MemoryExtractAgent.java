package com.innercosmos.ai.agent;

import org.springframework.stereotype.Component;

@Component
public class MemoryExtractAgent {
    public String summarize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "一次安静但仍值得保存的自我观察。";
        }
        String compact = rawText.replaceAll("\\s+", " ").trim();
        return compact.length() > 80 ? compact.substring(0, 80) + "..." : compact;
    }
}

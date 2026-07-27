package com.innercosmos.ai.context;

import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.TokenEstimationService;
import com.innercosmos.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Provider-aware, deterministic budget for one Aurora session.
 *
 * <p>The normal path preserves every prior message byte-for-byte and in chronological order.
 * Only after the provider-safe input limit is exceeded does it retain an opening prefix,
 * extractive critical anchors and the newest tail, separated by an explicit boundary marker.
 * This is a fidelity layer only; it never substitutes history for executable turn plans/state.
 */
@Component
public class AuroraConversationContextPolicy {
    static final String TRUNCATION_MARKER_PREFIX = "【会话上下文裁剪边界】";
    static final String CURRENT_MESSAGE_TRUNCATION_MARKER =
            "【当前消息因 Provider 安全窗口被裁剪；原文已完整保存】";
    private static final int FORMAT_OVERHEAD_TOKENS = 96;
    private static final Pattern BOOK_TITLE = Pattern.compile("《[^》]{1,80}》");
    private static final List<String> CRITICAL_MARKERS = List.of(
            "记住", "别忘", "约定", "答应", "截止", "更正", "纠正", "不是", "而是",
            "remember", "promise", "deadline", "correction", "not ", "instead");

    private final LlmConfig config;
    private final TokenEstimationService estimator;

    public AuroraConversationContextPolicy(LlmConfig config, TokenEstimationService estimator) {
        this.config = config;
        this.estimator = estimator;
    }

    public Selection select(List<String> chronologicalHistory,
                            String currentUserMessage,
                            String systemPrompt,
                            Map<String, Object> dynamicContextWithoutHistory,
                            String provider,
                            String model) {
        LlmConfig.ContextProperties properties = config.context == null
                ? new LlmConfig.ContextProperties() : config.context;
        int providerWindow = properties.providerWindow(provider);
        int inputLimit = Math.max(1_024, Math.min(
                Math.max(1_024, properties.hardMaxInputTokens),
                providerWindow - Math.max(0, properties.outputReserveTokens)
                        - Math.max(0, properties.safetyMarginTokens)));

        Map<String, Object> fixed = new LinkedHashMap<>();
        if (dynamicContextWithoutHistory != null) fixed.putAll(dynamicContextWithoutHistory);
        fixed.remove("auroraSystemPrompt");
        fixed.remove("conversationHistory");
        fixed.remove("conversationContextBudget");
        String modelUserMessage = currentUserMessage == null ? "" : currentUserMessage;
        fixed.put("userMessage", modelUserMessage);
        int fixedTokens = estimateFixed(systemPrompt, fixed);
        boolean currentMessageTruncated = false;
        if (fixedTokens > inputLimit && !modelUserMessage.isBlank()) {
            fixed.put("userMessage", "");
            int otherTokens = estimateFixed(systemPrompt, fixed);
            int currentBudget = Math.max(0, inputLimit - otherTokens);
            modelUserMessage = truncateCurrentMessage(modelUserMessage, currentBudget);
            currentMessageTruncated = !modelUserMessage.equals(currentUserMessage);
            fixed.put("userMessage", modelUserMessage);
            fixedTokens = estimateFixed(systemPrompt, fixed);
        }
        int historyBudget = Math.max(0, inputLimit - fixedTokens);

        List<String> source = normalizedHistory(chronologicalHistory, currentUserMessage);
        int fullHistoryTokens = estimateHistory(source);
        if (fullHistoryTokens <= historyBudget) {
            return new Selection(List.copyOf(source), inputLimit, providerWindow, fixedTokens,
                    fullHistoryTokens, 0, false, modelUserMessage, currentMessageTruncated,
                    provider, model);
        }

        List<String> selected = truncateWithAnchors(source, historyBudget, properties);
        int selectedTokens = estimateHistory(selected);
        while (!selected.isEmpty() && selectedTokens > historyBudget) {
            int removable = removableIndex(selected);
            if (removable < 0) break;
            selected.remove(removable);
            selectedTokens = estimateHistory(selected);
        }
        long retainedOriginals = selected.stream()
                .filter(item -> !item.startsWith(TRUNCATION_MARKER_PREFIX))
                .count();
        int omitted = Math.max(0, source.size() - (int) retainedOriginals);
        return new Selection(List.copyOf(selected), inputLimit, providerWindow, fixedTokens,
                selectedTokens, omitted, true, modelUserMessage, currentMessageTruncated,
                provider, model);
    }

    private int estimateFixed(String systemPrompt, Map<String, Object> fixed) {
        return estimator.calculatePromptTokens(systemPrompt, JsonUtils.toJson(fixed))
                + FORMAT_OVERHEAD_TOKENS;
    }

    private String truncateCurrentMessage(String message, int tokenBudget) {
        if (message == null || message.isBlank() || tokenBudget <= 0) return "";
        if (estimator.estimateTokens(message) <= tokenBudget) return message;
        int markerTokens = estimator.estimateTokens(CURRENT_MESSAGE_TRUNCATION_MARKER) + 4;
        if (markerTokens >= tokenBudget) return "";
        int[] codePoints = message.codePoints().toArray();
        int low = 0;
        int high = codePoints.length;
        String best = CURRENT_MESSAGE_TRUNCATION_MARKER;
        while (low <= high) {
            int retained = (low + high) >>> 1;
            int head = (int) Math.ceil(retained * 0.7);
            int tail = retained - head;
            String candidate = new String(codePoints, 0, Math.min(head, codePoints.length))
                    + "\n" + CURRENT_MESSAGE_TRUNCATION_MARKER + "\n"
                    + new String(codePoints, Math.max(0, codePoints.length - tail),
                    Math.min(tail, codePoints.length));
            if (estimator.estimateTokens(candidate) <= tokenBudget) {
                best = candidate;
                low = retained + 1;
            } else {
                high = retained - 1;
            }
        }
        return best;
    }

    private int removableIndex(List<String> selected) {
        for (int i = selected.size() - 1; i >= 0; i--) {
            String message = selected.get(i);
            if (message.startsWith(TRUNCATION_MARKER_PREFIX)) continue;
            if (i == 0) continue;
            if (!isCriticalAnchor(message)) return i;
        }
        for (int i = selected.size() - 1; i >= 1; i--) {
            if (!selected.get(i).startsWith(TRUNCATION_MARKER_PREFIX)) return i;
        }
        return selected.size() == 1
                && !selected.get(0).startsWith(TRUNCATION_MARKER_PREFIX) ? 0 : -1;
    }

    private List<String> truncateWithAnchors(List<String> source, int budget,
                                             LlmConfig.ContextProperties properties) {
        if (budget <= 0 || source.isEmpty()) return List.of();
        int openingBudget = Math.min(Math.max(0, properties.openingAnchorTokens), budget / 4);
        int anchorBudget = Math.min(Math.max(0, properties.criticalAnchorTokens), budget / 4);
        int tailBudget = Math.max(0, budget - openingBudget - anchorBudget - 64);

        List<String> opening = takePrefix(source, openingBudget);
        Set<String> retained = new LinkedHashSet<>(opening);
        List<String> tail = takeTail(source, tailBudget, retained);
        retained.addAll(tail);
        List<String> anchors = takeCriticalAnchors(source, anchorBudget, retained);
        retained.addAll(anchors);

        int omitted = Math.max(0, source.size() - retained.size());
        String marker = TRUNCATION_MARKER_PREFIX + " 已省略 " + omitted
                + " 条超出 Provider 安全窗口的中段消息；以下保留开场、关键更正/约定/命名主题与最近对话。";
        List<String> result = new ArrayList<>(opening);
        result.add(marker);
        result.addAll(anchors);
        result.addAll(tail);
        return result;
    }

    private List<String> takePrefix(List<String> source, int budget) {
        List<String> result = new ArrayList<>();
        int used = 0;
        for (String message : source) {
            int cost = estimateMessage(message);
            if (used + cost > budget) break;
            result.add(message);
            used += cost;
        }
        return result;
    }

    private List<String> takeTail(List<String> source, int budget, Set<String> excluded) {
        List<String> reversed = new ArrayList<>();
        int used = 0;
        for (int i = source.size() - 1; i >= 0; i--) {
            String message = source.get(i);
            if (excluded.contains(message)) continue;
            int cost = estimateMessage(message);
            if (used + cost > budget) break;
            reversed.add(message);
            used += cost;
        }
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private List<String> takeCriticalAnchors(List<String> source, int budget, Set<String> excluded) {
        List<String> result = new ArrayList<>();
        int used = 0;
        for (String message : source) {
            if (excluded.contains(message) || !isCriticalAnchor(message)) continue;
            int cost = estimateMessage(message);
            if (used + cost > budget) continue;
            result.add(message);
            used += cost;
        }
        return result;
    }

    private boolean isCriticalAnchor(String message) {
        if (BOOK_TITLE.matcher(message).find()) return true;
        String normalized = message.toLowerCase(Locale.ROOT);
        return CRITICAL_MARKERS.stream().anyMatch(normalized::contains);
    }

    private List<String> normalizedHistory(List<String> history, String currentUserMessage) {
        if (history == null || history.isEmpty()) return List.of();
        String current = currentUserMessage == null ? "" : currentUserMessage.strip();
        List<String> result = new ArrayList<>();
        for (String raw : history) {
            if (raw == null || raw.isBlank() || sameUserExpression(raw, current)) continue;
            result.add(raw);
        }
        return result;
    }

    private boolean sameUserExpression(String historyItem, String currentUserMessage) {
        if (currentUserMessage == null || currentUserMessage.isBlank()) return false;
        String normalized = historyItem.strip()
                .replaceFirst("^#(?:\\d+|unknown)\\s+", "")
                .replaceFirst("(?i)^(user|用户)\\s*[:：]\\s*", "")
                .strip();
        return normalized.equals(currentUserMessage);
    }

    private int estimateHistory(List<String> history) {
        int total = 0;
        for (String message : history) total += estimateMessage(message);
        return total;
    }

    private int estimateMessage(String message) {
        return estimator.estimateTokens(message) + 4;
    }

    public record Selection(List<String> messages,
                            int inputTokenLimit,
                            int providerWindowTokens,
                            int fixedContextTokens,
                            int historyTokens,
                            int omittedMessageCount,
                            boolean truncated,
                            String modelUserMessage,
                            boolean currentMessageTruncated,
                            String provider,
                            String model) {
        public Map<String, Object> metadata() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contract", "aurora-session-context.v1");
            result.put("inputTokenLimit", inputTokenLimit);
            result.put("providerWindowTokens", providerWindowTokens);
            result.put("fixedContextTokens", fixedContextTokens);
            result.put("historyTokens", historyTokens);
            result.put("estimatedInputTokens", fixedContextTokens + historyTokens);
            result.put("omittedMessageCount", omittedMessageCount);
            result.put("truncated", truncated);
            result.put("currentMessageTruncated", currentMessageTruncated);
            result.put("provider", provider == null ? "" : provider);
            result.put("model", model == null ? "" : model);
            result.put("historyRole", "fidelity-only-not-a-substitute-for-deliberation-plan");
            return Map.copyOf(result);
        }
    }
}

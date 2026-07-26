package com.innercosmos.ai.action;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.entity.TurnPlan;
import com.innercosmos.dto.MemoryOperationCommand;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.ConversationTurnMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.TurnPlanMapper;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.service.UserService;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.vo.AuroraReplyVO;
import com.innercosmos.vo.UserProfileVO;
import com.innercosmos.vo.WakeIntentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Confirmation-gated bridge between Aurora conversation and real product state.
 *
 * <p>Only three allow-listed action families exist. A proposal is persisted on the committed
 * {@link TurnPlan}; only an explicit confirmation on the immediately following turn, in the same
 * owner/session, may execute it. Model text never becomes authority.</p>
 */
@Service
public class AuroraNaturalActionService {
    private static final Set<String> CONFIRM = Set.of(
            "确认", "我确认", "确认执行", "确认保存", "确认设置", "confirm", "yes,confirm", "yesconfirm", "doit");
    private static final Set<String> CANCEL = Set.of(
            "取消", "不用了", "先不做", "不要执行", "cancel", "never mind", "nevermind", "donotdoit");
    private static final Set<String> BOOLEAN_SETTINGS = Set.of(
            "allowMemoryRecall", "weatherAwarenessEnabled", "timeAwarenessEnabled",
            "allowMultiMessage", "focusModeEnabled");

    private final AuroraNaturalActionParser parser;
    private final TurnPlanMapper planMapper;
    private final ConversationTurnMapper turnMapper;
    private final DialogMessageMapper messageMapper;
    private final MemoryLifecycleService memoryLifecycle;
    private final WakeIntentService wakeIntents;
    private final UserService users;
    private final ObjectMapper objectMapper;

    public AuroraNaturalActionService(AuroraNaturalActionParser parser,
                                      TurnPlanMapper planMapper,
                                      ConversationTurnMapper turnMapper,
                                      DialogMessageMapper messageMapper,
                                      MemoryLifecycleService memoryLifecycle,
                                      WakeIntentService wakeIntents,
                                      UserService users,
                                      ObjectMapper objectMapper) {
        this.parser = parser;
        this.planMapper = planMapper;
        this.turnMapper = turnMapper;
        this.messageMapper = messageMapper;
        this.memoryLifecycle = memoryLifecycle;
        this.wakeIntents = wakeIntents;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    /**
     * @return a bounded deterministic reply when the turn is an action proposal/confirmation,
     * clarification or cancellation; {@code null} when the ordinary LLM conversation should run.
     */
    @Transactional(rollbackFor = Exception.class)
    public AuroraReplyVO intercept(Long userId, Long sessionId, Long currentTurnId, String message) {
        if (userId == null || sessionId == null || currentTurnId == null || message == null || message.isBlank()) {
            return null;
        }
        ConversationTurn current = ownedCurrentTurn(userId, sessionId, currentTurnId);
        Pending pending = immediatePending(userId, sessionId, current);
        String normalized = confirmationKey(message);
        if (pending != null && CONFIRM.contains(normalized)) {
            return executeConfirmed(userId, sessionId, pending);
        }
        if (pending != null && CANCEL.contains(normalized)) {
            pending.plan.actionStatus = "CANCELLED";
            pending.plan.actionConfirmedAt = LocalDateTime.now();
            pending.plan.actionResultRef = "cancelled:user";
            planMapper.updateById(pending.plan);
            return reply(pending.english
                    ? "Cancelled. Nothing was saved or changed."
                    : "已取消。没有保存、调度或更改任何内容。", "action-cancelled");
        }

        String previous = previousUserMessage(userId, sessionId, current.userMessageId);
        String timezone = profileTimezone(userId);
        AuroraNaturalActionParser.Decision decision = parser.parse(message, previous, timezone);

        // A proposal is intentionally valid for one adjacent turn only. Any other reply retires it,
        // preventing a later unrelated "confirm" from executing stale state.
        if (pending != null) {
            pending.plan.actionStatus = decision.intent() == null ? "EXPIRED" : "SUPERSEDED";
            pending.plan.actionResultRef = decision.intent() == null
                    ? "expired:next-turn-not-confirmation" : "superseded:new-action-proposal";
            planMapper.updateById(pending.plan);
        }

        if (!decision.recognized()) return null;
        if (decision.intent() == null) return reply(decision.clarification(), "action-clarification");
        return proposal(decision.intent());
    }

    /**
     * Keep the foreground lane silent for confirmation-gated product actions so an unrelated
     * acknowledgement cannot appear immediately before the authoritative proposal or receipt.
     */
    public boolean shouldSuppressForeground(Long userId, Long sessionId, String message) {
        if (message == null || message.isBlank()) return false;
        String normalized = confirmationKey(message);
        if (CONFIRM.contains(normalized) || CANCEL.contains(normalized)) return true;
        String previous = latestUserMessage(userId, sessionId);
        return parser.parse(message, previous, profileTimezone(userId)).recognized();
    }

    private AuroraReplyVO proposal(AuroraNaturalActionParser.ActionIntent intent) {
        AuroraReplyVO vo = reply(intent.english()
                ? intent.summary() + "\nPlease reply “Confirm” to execute it, or “Cancel”. Nothing changes before confirmation."
                : intent.summary() + "\n请回复“确认”后执行，或回复“取消”。确认前不会改变任何数据。",
                "action-confirmation");
        vo.proposedActionType = intent.type();
        vo.proposedActionSummary = intent.summary();
        vo.proposedActionPayloadJson = json(intent.payload());
        vo.proposedActionStatus = "PENDING_CONFIRMATION";
        vo.featureTarget = featureTarget(intent.type());
        return vo;
    }

    private AuroraReplyVO executeConfirmed(Long userId, Long sessionId, Pending pending) {
        Map<String, String> payload = payload(pending.plan.proposedActionPayload);
        String resultRef;
        String message;
        switch (pending.plan.proposedActionType) {
            case AuroraNaturalActionParser.REMEMBER -> {
                String title = required(payload, "title");
                String content = required(payload, "content");
                var result = memoryLifecycle.execute(userId, new MemoryOperationCommand(
                        "ADD", null, List.of(), title, content, null,
                        "USER_CONFIRMED_NATURAL_ACTION", 1.0, "turn-plan:" + pending.plan.id));
                MemoryCard card = result.memories().isEmpty() ? null : result.memories().getFirst();
                resultRef = "memory:" + (card == null ? "unknown" : card.id);
                message = pending.english
                        ? "Saved as a private, traceable memory. It was not published or added to a capsule."
                        : "已经保存为仅你可见、可追溯的记忆；没有自动公开，也没有自动进入共鸣体。";
            }
            case AuroraNaturalActionParser.REMINDER -> {
                String when = required(payload, "when");
                String purpose = required(payload, "purpose");
                String content = required(payload, "content");
                String timezone = required(payload, "timezone");
                WakeIntent intent = wakeIntents.scheduleNatural(userId, when, purpose,
                        pending.english ? "You explicitly confirmed this reminder in Aurora."
                                : "你在 Aurora 对话中明确确认了这个提醒。",
                        content, timezone, sessionId);
                WakeIntentVO safe = WakeIntentVO.from(intent);
                resultRef = "wake-intent:" + intent.id;
                String friendlyTime = pending.english
                        ? safe.preferredAt().format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.ENGLISH))
                        : safe.preferredAt().format(DateTimeFormatter.ofPattern("M月d日 HH:mm"));
                message = pending.english
                        ? "Scheduled for " + friendlyTime + " (" + safe.timezone() + "). You can reschedule or cancel it anytime."
                        : "已经约在 " + friendlyTime + "（" + safe.timezone() + "）。你随时可以改期或取消。";
            }
            case AuroraNaturalActionParser.PROFILE_SETTING -> {
                String setting = required(payload, "setting");
                String value = required(payload, "value");
                applyProfileSetting(userId, setting, value);
                resultRef = "profile:" + setting + "=" + value;
                String label = payload.getOrDefault("label", setting);
                message = pending.english
                        ? "Updated " + label + ". This changes only the setting you confirmed."
                        : "已更新“" + label + "”。只改动了你刚才确认的这一项设置。";
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "unsupported Aurora action");
        }
        pending.plan.actionStatus = "EXECUTED";
        pending.plan.actionConfirmedAt = LocalDateTime.now();
        pending.plan.actionResultRef = resultRef;
        planMapper.updateById(pending.plan);
        AuroraReplyVO completed = reply(message, "action-completed");
        completed.proposedActionType = pending.plan.proposedActionType;
        completed.proposedActionStatus = "EXECUTED";
        completed.featureTarget = featureTarget(pending.plan.proposedActionType);
        return completed;
    }

    private void applyProfileSetting(Long userId, String setting, String value) {
        UserProfileVO patch = new UserProfileVO();
        if (BOOLEAN_SETTINGS.contains(setting)) {
            if (!"true".equals(value) && !"false".equals(value)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid boolean setting value");
            }
            boolean enabled = Boolean.parseBoolean(value);
            switch (setting) {
                case "allowMemoryRecall" -> patch.allowMemoryRecall = enabled;
                case "weatherAwarenessEnabled" -> patch.weatherAwarenessEnabled = enabled;
                case "timeAwarenessEnabled" -> patch.timeAwarenessEnabled = enabled;
                case "allowMultiMessage" -> patch.allowMultiMessage = enabled;
                case "focusModeEnabled" -> patch.focusModeEnabled = enabled;
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "setting is not authorized");
            }
        } else if ("proactiveSensitivity".equals(setting)) {
            int sensitivity;
            try {
                sensitivity = Integer.parseInt(value);
            } catch (NumberFormatException invalid) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid proactive sensitivity");
            }
            if (sensitivity < 1 || sensitivity > 5) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "invalid proactive sensitivity");
            }
            patch.proactiveSensitivity = sensitivity;
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "setting is not authorized");
        }
        users.updateProfile(userId, patch);
    }

    private Pending immediatePending(Long userId, Long sessionId, ConversationTurn current) {
        List<TurnPlan> candidates = planMapper.selectList(new QueryWrapper<TurnPlan>()
                .eq("user_id", userId).eq("action_status", "PENDING_CONFIRMATION")
                .orderByDesc("id").last("LIMIT 20"));
        for (TurnPlan candidate : candidates) {
            ConversationTurn source = turnMapper.selectById(candidate.turnId);
            if (source == null || !userId.equals(source.userId) || !sessionId.equals(source.sessionId)
                    || source.id == null || source.id >= current.id) continue;
            long intervening = turnMapper.selectCount(new QueryWrapper<ConversationTurn>()
                    .eq("user_id", userId).eq("session_id", sessionId)
                    .gt("id", source.id).lt("id", current.id));
            if (intervening != 0) continue;
            TurnPlan locked = planMapper.selectOne(new QueryWrapper<TurnPlan>()
                    .eq("id", candidate.id).eq("user_id", userId)
                    .eq("action_status", "PENDING_CONFIRMATION").last("LIMIT 1 FOR UPDATE"));
            if (locked != null) return new Pending(locked, isEnglishSummary(locked.proposedActionSummary));
        }
        return null;
    }

    private ConversationTurn ownedCurrentTurn(Long userId, Long sessionId, Long currentTurnId) {
        ConversationTurn current = turnMapper.selectById(currentTurnId);
        if (current == null || !userId.equals(current.userId) || !sessionId.equals(current.sessionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "conversation turn not found");
        }
        return current;
    }

    private String previousUserMessage(Long userId, Long sessionId, Long currentMessageId) {
        if (currentMessageId == null) return null;
        DialogMessage previous = messageMapper.selectOne(new QueryWrapper<DialogMessage>()
                .eq("user_id", userId).eq("session_id", sessionId).eq("speaker", "USER")
                .lt("id", currentMessageId).orderByDesc("id").last("LIMIT 1"));
        return previous == null ? null : previous.textContent;
    }

    private String latestUserMessage(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) return null;
        DialogMessage previous = messageMapper.selectOne(new QueryWrapper<DialogMessage>()
                .eq("user_id", userId).eq("session_id", sessionId).eq("speaker", "USER")
                .orderByDesc("id").last("LIMIT 1"));
        return previous == null ? null : previous.textContent;
    }

    private String profileTimezone(Long userId) {
        UserProfile profile = users.getProfile(userId);
        return profile == null || profile.timezone == null || profile.timezone.isBlank()
                ? "Asia/Singapore" : profile.timezone;
    }

    private AuroraReplyVO reply(String message, String theme) {
        AuroraReplyVO vo = new AuroraReplyVO();
        vo.messages = List.of(message);
        vo.replyTone = "clear, bounded, user-controlled";
        vo.detectedTheme = theme;
        vo.nextQuestion = "";
        vo.smallStep = "";
        vo.featureSuggestion = "";
        vo.featureTarget = "";
        vo.agentLoop = new LinkedHashMap<>(Map.of(
                "speakCount", 1,
                "continueReason", "deterministic-confirmation-gated-action",
                "runtime", "aurora-action.v1"));
        vo.aiState = Map.of("provider", "SYSTEM", "model", "aurora-action.v1", "fallback", false);
        vo.riskFlags = List.of();
        vo.suggestSettle = false;
        vo.memoryReferenced = false;
        vo.referencedMemoryIds = List.of();
        return vo;
    }

    private Map<String, String> payload(String value) {
        if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, "action payload missing");
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException invalid) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "action payload invalid");
        }
    }

    private String json(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("cannot serialize bounded Aurora action", impossible);
        }
    }

    private static String required(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) throw new BusinessException(ErrorCode.BAD_REQUEST, key + " is required");
        return value.trim();
    }

    private static String confirmationKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[。.!！\\s]+", "");
    }

    private static boolean isEnglishSummary(String value) {
        return value != null && value.codePoints().noneMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private static String featureTarget(String actionType) {
        return switch (actionType == null ? "" : actionType) {
            case AuroraNaturalActionParser.REMEMBER -> "memory-starfield";
            case AuroraNaturalActionParser.REMINDER -> "aurora-returns";
            case AuroraNaturalActionParser.PROFILE_SETTING -> "settings";
            default -> "";
        };
    }

    private record Pending(TurnPlan plan, boolean english) {}
}

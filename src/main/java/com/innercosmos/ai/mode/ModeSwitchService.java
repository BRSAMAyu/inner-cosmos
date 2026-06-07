package com.innercosmos.ai.mode;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for switching Aurora conversation modes.
 * Updates session mode, inserts a hidden mode-transition marker message,
 * and optionally pushes an acknowledgement via the proactive delivery channel.
 */
@Service
public class ModeSwitchService {

    private final DialogSessionMapper sessionMapper;
    private final DialogMessageMapper messageMapper;
    private final ModeRegistry modeRegistry;
    private final ProactiveDeliveryChannel deliveryChannel;
    private final LlmClient llmClient;

    public ModeSwitchService(DialogSessionMapper sessionMapper,
                             DialogMessageMapper messageMapper,
                             ModeRegistry modeRegistry,
                             ProactiveDeliveryChannel deliveryChannel,
                             LlmClient llmClient) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.modeRegistry = modeRegistry;
        this.deliveryChannel = deliveryChannel;
        this.llmClient = llmClient;
    }

    /**
     * Switch the session to a new mode.
     * @param userId the current user
     * @param sessionId the dialog session
     * @param targetMode the mode to switch to (DAILY_TALK, THOUGHT_CLARIFY, SOCRATIC)
     * @return the switched strategy, or null if mode not found
     */
    @Transactional(rollbackFor = Exception.class)
    public ModeStrategy switchTo(Long userId, Long sessionId, String targetMode) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(com.innercosmos.common.ErrorCode.NOT_FOUND, "对话会话不存在");
        }
        if (!userId.equals(session.userId)) {
            throw new BusinessException(com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此会话");
        }

        ModeStrategy strategy = modeRegistry.get(targetMode);
        if (strategy == null) {
            throw new BusinessException(com.innercosmos.common.ErrorCode.BAD_REQUEST, "不支持的陪伴模式: " + targetMode);
        }

        // Update session current mode
        session.currentMode = targetMode;
        sessionMapper.updateById(session);

        // Insert hidden mode transition marker
        DialogMessage marker = new DialogMessage();
        marker.sessionId = sessionId;
        marker.userId = userId;
        marker.speaker = "SYSTEM";
        marker.textContent = "[MODE_TRANSITION:" + targetMode + "] " + strategy.segment();
        marker.inputType = "SYSTEM";
        marker.safetyLevel = "LOW";
        messageMapper.insert(marker);

        // If strategy requires multi-turn acknowledgement, push via proactive channel
        if (strategy.requiresMultiTurnAcknowledgement()) {
            pushModeAcknowledgement(userId, strategy);
        }

        return strategy;
    }

    private void pushModeAcknowledgement(Long userId, ModeStrategy strategy) {
        String prompt = "用户切换到「" + strategy.segment() + "」模式。" +
                "请 Aurora 温柔确认这个变化，例如：「好的，接下来我换一种方式陪你——」" +
                "只返回一句简短的确认语，不要多句。";
        try {
            LlmRequest request = new LlmRequest(userId, "MODE_ACK", prompt);
            String response = llmClient.chat(request);
            if (response != null && !response.isBlank()) {
                deliveryChannel.push(userId, response, "MODE_ACK");
            }
        } catch (Exception e) {
            // Non-critical: log and continue
            org.slf4j.LoggerFactory.getLogger(getClass())
                .warn("Failed to push mode acknowledgement for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get current mode strategy for a session.
     */
    public ModeStrategy currentStrategy(Long sessionId) {
        DialogSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.currentMode == null) {
            return modeRegistry.get("DAILY_TALK");
        }
        ModeStrategy strategy = modeRegistry.get(session.currentMode);
        return strategy != null ? strategy : modeRegistry.get("DAILY_TALK");
    }
}
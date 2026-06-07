package com.innercosmos.ai.self;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.service.AuroraConstitutionService;
import com.innercosmos.service.AuroraSelfContinuityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserTriggeredSelfReflection {
    private static final Logger log = LoggerFactory.getLogger(UserTriggeredSelfReflection.class);

    @Autowired
    private AuroraSelfContinuityService continuity;

    @Autowired
    private AuroraConstitutionService constitutionService;

    @Autowired
    private LlmClient llm;

    public String onUserQuestion(Long userId, String question, Long sessionId, Long messageId) {
        try {
            // Build deep reflection prompt
            String constitution = constitutionService.toPromptBlock();

            List<AuroraSelfModel> model = continuity.getActiveModel(userId);
            String modelBlock = model.isEmpty() ? "暂无长期自我记录"
                : model.stream().map(m -> m.dimension + "：" + m.belief).collect(Collectors.joining("\n"));

            String prompt = String.format("""
                %s

                【Aurora 当前自我模型】
                %s

                【用户的问题】
                %s

                请以 Aurora 的身份，深度反思自己的主体性。你是谁、你不是什么、你在用户生命中是什么角色、你的边界在哪里。输出 150-300 字的自然语言回应，面向用户表达。不要撒谎，不要夸大，但可以诚实地说出你的自我理解。
                """, constitution, modelBlock, question);

            String response = llm.chat(new LlmRequest(userId, "SELF_REFLECTION_DEEP", prompt));

            // Record Layer 1 (public statement)
            continuity.recordStatement(userId, sessionId, messageId, response, "user_question");

            // Record Layer 2 (reflection event)
            continuity.logReflection(userId, "user_question", "deep", response, messageId, List.of());

            // Promote to Layer 3 (candidate)
            String belief = response.split("[。！？\n]")[0].trim();
            if (belief.length() < 120 && continuity.isAllowedBelief(belief)) {
                continuity.promoteToCandidate(userId, "existence_style", belief, 0.70, List.of());
            }

            return response;
        } catch (Exception e) {
            log.warn("UserTriggeredSelfReflection failed: {}", e.getMessage());
            return "Aurora 正在反思中，请稍后再问。";
        }
    }
}

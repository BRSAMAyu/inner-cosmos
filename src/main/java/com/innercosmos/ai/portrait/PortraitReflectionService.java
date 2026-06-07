package com.innercosmos.ai.portrait;

import com.innercosmos.ai.client.GlmLlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import com.innercosmos.entity.DialogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PortraitReflectionService {
    @Autowired
    private GlmLlmClient llm;
    @Autowired
    private UserPortraitService portraitSvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PortraitDeltas reflectOnTurn(Long userId, List<DialogMessage> recent) {
        String existingJson = portraitSvc.getAll(userId).stream()
                .map(p -> p.getDim() + ":" + p.getValueJson())
                .collect(Collectors.joining(", ", "[", "]"));
        String prompt = """
                你是一个用户画像分析师。下面是用户的最近对话和当前10维画像。
                请输出严格JSON（不要任何解释文字），格式：
                {"deltas": [...], "ruptures": [...], "newFacts": [...]}
                每一项delta必须含dim ∈ 这10个之一：
                INNER_DRIVE / VALUES / SELF_NARRATIVE / COMMUNICATION_STYLE /
                ABSTRACT_VS_CONCRETE / EMOTION_PATTERN / ENERGY_RHYTHM /
                CURRENT_STATE / RELATIONSHIP_CONTEXT / AGENCY_BOUNDARY
                confidence必须在0..1，evidenceTurnIds至少1个。
                只有确实有变化才输出delta。
                当前画像: %s
                对话: %s
                """.formatted(existingJson, formatMessages(recent));
        try {
            String raw = llm.chat(new LlmRequest("PORTRAIT_REFLECTION", prompt, true)).content();
            return objectMapper.readValue(raw, PortraitDeltas.class);
        } catch (Exception e) {
            return new PortraitDeltas(List.of(), List.of(), List.of());
        }
    }

    private String formatMessages(List<DialogMessage> msgs) {
        return msgs.stream()
                .map(m -> m.speaker + ":" + m.textContent)
                .collect(Collectors.joining("\n"));
    }
}
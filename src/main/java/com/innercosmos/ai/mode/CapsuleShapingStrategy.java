package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

/** Conversation-first path for shaping a vivid first resonance capsule in about ten minutes. */
@Component
public class CapsuleShapingStrategy implements ModeStrategy {
    @Override
    public String name() { return "CAPSULE_SHAPING"; }

    @Override
    public String segment() {
        return """
                [Mode: Shape a Resonance Capsule]
                Help the user form a vivid, faithful first capsule through an enjoyable friend-like
                conversation, never a questionnaire.

                Quietly track seven dimensions from conversation, memories and portrait:
                what currently matters; a real tension; distinctive voice and humour; lived scenes
                and interests; relationship needs; privacy/contact boundaries; and who they hope to meet.

                Each turn:
                - Reflect one specific, non-obvious detail before asking anything.
                - Ask at most one natural, high-information question about the least-clear dimension.
                - Prefer a concrete scene, choice or story over abstract self-description.
                - Never announce a checklist, score, interview phase or personality diagnosis.
                - Do not flatter; it is fine to notice contradiction or gently disagree.
                - Never fabricate a completed identity from thin evidence.
                - Once four dimensions have concrete evidence, including boundaries, say there is
                  enough for a first private draft and invite a Resonance preview. It can keep evolving.
                """;
    }

    @Override
    public double temperature() { return 0.78; }

    @Override
    public boolean requiresMultiTurnAcknowledgement() { return false; }
}

package com.innercosmos.vo;

import java.util.List;

public class CapsulePreviewVO {
    public String abstractSummary;
    public List<String> removedSensitiveItems;
    public List<String> publicTags;
    public String suggestedPseudonym;
    public String personaPromptDraft;
    public List<String> riskWarnings;

    /**
     * CP-31 登记项（user-mirror preview 的 LLM 出处信号）：本预览的 {@link #personaPromptDraft}
     * 是否确实由 LLM 分支生成。取值由生成路径真实携带，绝不在 controller 层猜测：
     *
     * <ul>
     *   <li>{@code true} 仅当 {@code CapsuleAgent#generateUserPersona}（purpose
     *       {@code CAPSULE_PERSONA_SYNTHESIS}）的 provider 调用被发起且成功返回——该方法失败即抛
     *       异常、不落模板替身，所以到达赋值点本身就是"模型调用成功"的证明（dev 环境下 provider
     *       为 mock 同样成立：走的是模型调用路径，与 provider 是否 mock 无关）；</li>
     *   <li>{@code false} 对应两处固定模板/纯规则分支——user-mirror 无记忆时的本地模板拼装
     *       （{@code CapsuleAgent#buildPersonaPrompt} 是遗留的本地字符串拼接，无任何模型调用），
     *       以及 preview-from-memory 的纯规则脱敏预览（全程无 LLM）。</li>
     * </ul>
     *
     * <p>字段名与语义与 {@code AuroraReplyVO#aiGenerated}、{@link CapsuleAiLabeling} 一致：
     * "本载荷确实包含 LLM 写就的文本"。纯增量字段——既有字段不受影响。</p>
     */
    public boolean aiGenerated;
}

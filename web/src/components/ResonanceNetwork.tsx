import type { CapsuleMatch, CapsuleQuota, PersonaMessage, PersonaSession, ResonanceStrategy, SlowLetter } from "../api";
import { AsyncButton } from "../loading";

const strategies: Array<[ResonanceStrategy, string]> = [
  ["MIRROR", "相似共鸣"], ["COMPLEMENT", "有意义的互补"], ["GROWTH_EDGE", "成长边缘"],
  ["SERENDIPITY", "温和偶遇"], ["CONTEXTUAL", "阶段同行"]
];

export function ResonanceNetwork({ resonanceMatches, resonanceStrategy, visitorBusy, visitorMatch, personaSession,
  personaMessages, personaDraft, personaQuota, letterTitle, letterBody, sentLetter,
  onChooseStrategy, onChooseMatch, onStartPersonaConversation, onPersonaDraftChange, onSendPersonaTurn,
  onLetterTitleChange, onLetterBodyChange, onSendLetter }: {
  resonanceMatches: CapsuleMatch[]; resonanceStrategy: ResonanceStrategy; visitorBusy: boolean;
  visitorMatch: CapsuleMatch | null; personaSession: PersonaSession | null; personaMessages: PersonaMessage[];
  personaDraft: string; personaQuota: CapsuleQuota | null; letterTitle: string; letterBody: string; sentLetter: SlowLetter | null;
  onChooseStrategy: (strategy: ResonanceStrategy) => void; onChooseMatch: (capsuleId: number) => void;
  onStartPersonaConversation: () => void; onPersonaDraftChange: (value: string) => void; onSendPersonaTurn: () => void;
  onLetterTitleChange: (value: string) => void; onLetterBodyChange: (value: string) => void; onSendLetter: () => void;
}) {
  return <section className="resonance-network" aria-label="发现共鸣并写一封慢信">
    <div className="resonance-heading"><div><span className="eyebrow">RESONANCE NETWORK</span><h2>不是刷卡片，是理解为什么会相遇</h2></div>
      <span>{resonanceMatches.length} 个此刻的候选</span></div>
    <p className="resonance-intro">这里没有热度排行。系统只展示脱敏侧面、共同主题和边界；先与授权 AI 共鸣体确认是否真的想继续，再决定要不要把话写给本人。</p>
    <div className="strategy-switcher" role="group" aria-label="选择共鸣匹配方式">
      {strategies.map(([value, label]) =>
        <button type="button" key={value} aria-pressed={resonanceStrategy === value} disabled={visitorBusy}
          onClick={() => onChooseStrategy(value)}>{label}</button>)}
    </div>
    {resonanceMatches[0] && <p className="strategy-explanation"><strong>{resonanceMatches[0].strategyLabel}</strong> · {resonanceMatches[0].strategyDescription}</p>}
    {resonanceMatches.length === 0 ? <div className="network-empty">暂时没有足够安全的相遇候选。Inner Cosmos 不会用随机陌生人填满这里。</div> : <>
      <div className="match-rail" role="list" aria-label="共鸣候选">
        {resonanceMatches.map(match => <button type="button" role="listitem" key={match.capsule.id}
          className={visitorMatch?.capsule.id === match.capsule.id ? "match-card active" : "match-card"}
          onClick={() => onChooseMatch(match.capsule.id)}><span>{match.resonant ? "此刻同行" : "探索相遇"}</span>
          <strong>{match.capsule.pseudonym}</strong><p>{match.capsule.intro}</p>
          <small>{match.matchSummary}</small></button>)}
      </div>
      {visitorMatch && <div className="visitor-workbench">
        <header><div><span className="identity-notice">授权 AI 共鸣体 · 不是真人实时在线</span><h3>{visitorMatch.capsule.pseudonym}</h3>
          <p>{visitorMatch.capsule.intro}</p></div><div className="match-reasons">{visitorMatch.matchReasons.map(reason => <span key={reason}>{reason}</span>)}</div></header>
        {!personaSession ? <div className="visitor-entry"><p>先问一两个真正重要的问题。它只能使用创建者明确授权的侧面，也不会把你的 Aurora 私有画像带进这段对话。</p>
          <AsyncButton className="resonance-primary" busy={visitorBusy} busyText="正在进入" onClick={onStartPersonaConversation}>进入有限但自然的对话</AsyncButton></div> : <>
          <div className="visitor-quota"><span>今天还可深入 {personaQuota?.remainingTurns ?? "–"} 轮</span><small>额度用于防滥用；模型故障不会扣次数，达到边界后会自然引导慢信。</small></div>
          <div className="persona-history" aria-label="共鸣体对话记录">{personaMessages.length === 0 ? <p>可以从一个具体时刻开始，而不是交换完整履历。</p> : personaMessages.map(message =>
            <article className={message.senderType === "VISITOR" ? "visitor" : "capsule"} key={message.id}><span>{message.senderType === "VISITOR" ? "你" : visitorMatch.capsule.pseudonym}</span><p>{message.textContent}</p></article>)}</div>
          <div className="sandbox-composer"><textarea aria-label="写给共鸣体" value={personaDraft} onChange={event => onPersonaDraftChange(event.target.value)} />
            <AsyncButton className="resonance-primary" busy={visitorBusy} disabled={!personaDraft.trim() || personaQuota?.exhausted} busyText="正在发送" onClick={onSendPersonaTurn}>发送这一轮</AsyncButton></div>
          {personaMessages.some(message => message.senderType === "CAPSULE") && <div className="slow-letter-compose">
            <div className="capsule-step"><span>✉</span><div><strong>如果仍想继续，把话交给时间</strong><small>这封信会送给创建者本人。共鸣体不会替对方承诺回复，也不会泄露联系方式。</small></div></div>
            {visitorMatch.capsule.capsuleType !== "USER_CAPSULE" ? <p className="preview-warning">这是官方种子共鸣体，没有对应的真人收件人；你仍可继续对话，但不能把它当作认识真人的入口。</p> : sentLetter ?
              <div className="letter-flight" role="status"><strong>慢信已启程</strong><span>{sentLetter.title}</span><small>预计 {new Date(sentLetter.estimatedArrivalAt).toLocaleString()} 到达 · 状态 {sentLetter.status}</small></div> : <>
                <label>信的题目<input value={letterTitle} onChange={event => onLetterTitleChange(event.target.value)} /></label>
                <label>你真正想让对方读到的话<textarea aria-label="慢信正文" value={letterBody} onChange={event => onLetterBodyChange(event.target.value)} placeholder="不用总结整段对话，只写你愿意为它负责的那部分。" /></label>
                <AsyncButton className="resonance-primary" busy={visitorBusy} disabled={!letterTitle.trim() || !letterBody.trim()} busyText="正在寄出" onClick={onSendLetter}>让慢信启程</AsyncButton></>}
          </div>}
        </>}
      </div>}
    </>}
  </section>;
}

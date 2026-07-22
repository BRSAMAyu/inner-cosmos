import type { CapsuleMatch, CapsuleQuota, PersonaMessage, PersonaSession, ResonanceStrategy, SlowLetter } from "../api";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";

const strategyOrder: ResonanceStrategy[] = ["MIRROR", "COMPLEMENT", "GROWTH_EDGE", "SERENDIPITY", "CONTEXTUAL"];

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; intro: string; strategyAria: string;
  strategy: Record<ResonanceStrategy, string>; emptyMatches: string; railAria: string; resonantNow: string;
  exploreMeet: string; identityNotice: string; entryP: string; enterBusy: string; enterBtn: string;
  quota: (remaining: number | string) => string; quotaNote: string; personaHistAria: string; historyStart: string;
  speakerYou: string; writeToCapsule: string; sendBusy: string; sendTurn: string; letterStepTitle: string;
  letterStepNote: string; seedWarning: string; letterFlightTitle: string; letterArrival: (time: string, status: string) => string;
  letterTitleLabel: string; letterBodyLabel: string; letterBodyAria: string; letterBodyPlaceholder: string;
  sendLetterBusy: string; sendLetterBtn: string; reportSession: string; blockSession: string;
}> = {
  "zh-CN": {
    aria: "发现共鸣并写一封慢信", heading: "不是刷卡片，是理解为什么会相遇", count: n => `${n} 个此刻的候选`,
    intro: "这里没有热度排行。系统只展示脱敏侧面、共同主题和边界；先与授权 AI 共鸣体确认是否真的想继续，再决定要不要把话写给本人。",
    strategyAria: "选择共鸣匹配方式",
    strategy: { MIRROR: "相似共鸣", COMPLEMENT: "有意义的互补", GROWTH_EDGE: "成长边缘", SERENDIPITY: "温和偶遇", CONTEXTUAL: "阶段同行" },
    emptyMatches: "暂时没有足够安全的相遇候选。Inner Cosmos 不会用随机陌生人填满这里。", railAria: "共鸣候选",
    resonantNow: "此刻同行", exploreMeet: "探索相遇", identityNotice: "授权 AI 共鸣体 · 不是真人实时在线",
    entryP: "先问一两个真正重要的问题。它只能使用创建者明确授权的侧面，也不会把你的 Aurora 私有画像带进这段对话。",
    enterBusy: "正在进入", enterBtn: "进入有限但自然的对话", quota: r => `今天还可深入 ${r} 轮`,
    quotaNote: "额度用于防滥用；模型故障不会扣次数，达到边界后会自然引导慢信。", personaHistAria: "共鸣体对话记录",
    historyStart: "可以从一个具体时刻开始，而不是交换完整履历。", speakerYou: "你", writeToCapsule: "写给共鸣体",
    sendBusy: "正在发送", sendTurn: "发送这一轮", letterStepTitle: "如果仍想继续，把话交给时间",
    letterStepNote: "这封信会送给创建者本人。共鸣体不会替对方承诺回复，也不会泄露联系方式。",
    seedWarning: "这是官方种子共鸣体，没有对应的真人收件人；你仍可继续对话，但不能把它当作认识真人的入口。",
    letterFlightTitle: "慢信已启程", letterArrival: (t, s) => `预计 ${t} 到达 · 状态 ${s}`,
    letterTitleLabel: "信的题目", letterBodyLabel: "你真正想让对方读到的话", letterBodyAria: "慢信正文",
    letterBodyPlaceholder: "不用总结整段对话，只写你愿意为它负责的那部分。", sendLetterBusy: "正在寄出", sendLetterBtn: "让慢信启程",
    reportSession: "举报这段对话", blockSession: "屏蔽这个共鸣体"
  },
  "en-SG": {
    aria: "Discover resonance and write a slow letter", heading: "Not swiping cards — understanding why you'd meet", count: n => `${n} candidate${n === 1 ? "" : "s"} right now`,
    intro: "No popularity ranking here. You only see de-identified facets, shared themes and boundaries; confirm with the authorized AI capsule whether you really want to continue before deciding to write to the person.",
    strategyAria: "Choose a resonance matching strategy",
    strategy: { MIRROR: "Similar resonance", COMPLEMENT: "Meaningful complement", GROWTH_EDGE: "Growth edge", SERENDIPITY: "Gentle serendipity", CONTEXTUAL: "Same-season company" },
    emptyMatches: "No safe-enough candidates for now. Inner Cosmos won't fill this with random strangers.", railAria: "Resonance candidates",
    resonantNow: "Alongside now", exploreMeet: "Explore a meeting", identityNotice: "Authorized AI capsule · not a real person online",
    entryP: "Start with one or two questions that truly matter. It can only use facets the creator explicitly authorized, and won't bring your private Aurora portrait into this conversation.",
    enterBusy: "Entering", enterBtn: "Enter a limited but natural conversation", quota: r => `${r} more turn${r === 1 ? "" : "s"} today`,
    quotaNote: "The quota guards against misuse; a model failure never costs a turn, and reaching the boundary gently guides you to a slow letter.", personaHistAria: "Capsule conversation log",
    historyStart: "You can start from one concrete moment, rather than exchanging full resumes.", speakerYou: "You", writeToCapsule: "Write to the capsule",
    sendBusy: "Sending", sendTurn: "Send this turn", letterStepTitle: "If you still want to continue, entrust it to time",
    letterStepNote: "This letter goes to the creator themselves. The capsule won't promise a reply on their behalf, nor reveal contact details.",
    seedWarning: "This is an official seed capsule with no real recipient; you can keep talking, but don't treat it as a way to meet a real person.",
    letterFlightTitle: "The slow letter is on its way", letterArrival: (t, s) => `Arrives ~${t} · status ${s}`,
    letterTitleLabel: "Letter title", letterBodyLabel: "What you truly want them to read", letterBodyAria: "Slow letter body",
    letterBodyPlaceholder: "No need to summarize the whole conversation — just write the part you're willing to stand behind.", sendLetterBusy: "Sending", sendLetterBtn: "Send the slow letter",
    reportSession: "Report this conversation", blockSession: "Block this capsule"
  }
};

export function ResonanceNetwork({ resonanceMatches, resonanceStrategy, visitorBusy, visitorMatch, personaSession,
  personaMessages, personaDraft, personaQuota, letterTitle, letterBody, sentLetter,
  onChooseStrategy, onChooseMatch, onStartPersonaConversation, onPersonaDraftChange, onSendPersonaTurn,
  onLetterTitleChange, onLetterBodyChange, onSendLetter, onReportSession, onBlockSession, locale = "zh-CN" }: {
  resonanceMatches: CapsuleMatch[]; resonanceStrategy: ResonanceStrategy; visitorBusy: boolean;
  visitorMatch: CapsuleMatch | null; personaSession: PersonaSession | null; personaMessages: PersonaMessage[];
  personaDraft: string; personaQuota: CapsuleQuota | null; letterTitle: string; letterBody: string; sentLetter: SlowLetter | null;
  onChooseStrategy: (strategy: ResonanceStrategy) => void; onChooseMatch: (capsuleId: number) => void;
  onStartPersonaConversation: () => void; onPersonaDraftChange: (value: string) => void; onSendPersonaTurn: () => void;
  onLetterTitleChange: (value: string) => void; onLetterBodyChange: (value: string) => void; onSendLetter: () => void;
  onReportSession?: () => void; onBlockSession?: () => void; locale?: Locale;
}) {
  const t = COPY[locale];
  return <section className="resonance-network" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">RESONANCE NETWORK</span><h2>{t.heading}</h2></div>
      <span>{t.count(resonanceMatches.length)}</span></div>
    <p className="resonance-intro">{t.intro}</p>
    <div className="strategy-switcher" role="group" aria-label={t.strategyAria}>
      {strategyOrder.map(value =>
        <button type="button" key={value} aria-pressed={resonanceStrategy === value} disabled={visitorBusy}
          onClick={() => onChooseStrategy(value)}>{t.strategy[value]}</button>)}
    </div>
    {resonanceMatches[0] && <p className="strategy-explanation"><strong>{resonanceMatches[0].strategyLabel}</strong> · {resonanceMatches[0].strategyDescription}</p>}
    {resonanceMatches.length === 0 ? <div className="network-empty">{t.emptyMatches}</div> : <>
      <div className="match-rail" role="list" aria-label={t.railAria}>
        {resonanceMatches.map(match => <button type="button" role="listitem" key={match.capsule.id}
          className={visitorMatch?.capsule.id === match.capsule.id ? "match-card active" : "match-card"}
          onClick={() => onChooseMatch(match.capsule.id)}><span>{match.resonant ? t.resonantNow : t.exploreMeet}</span>
          <strong>{match.capsule.pseudonym}</strong><p>{match.capsule.intro}</p>
          <small>{match.matchSummary}</small></button>)}
      </div>
      {visitorMatch && <div className="visitor-workbench">
        <header><div><span className="identity-notice">{t.identityNotice}</span><h3>{visitorMatch.capsule.pseudonym}</h3>
          <p>{visitorMatch.capsule.intro}</p></div><div className="match-reasons">{visitorMatch.matchReasons.map(reason => <span key={reason}>{reason}</span>)}</div></header>
        {!personaSession ? <div className="visitor-entry"><p>{t.entryP}</p>
          <AsyncButton className="resonance-primary" busy={visitorBusy} busyText={t.enterBusy} onClick={onStartPersonaConversation}>{t.enterBtn}</AsyncButton></div> : <>
          <div className="visitor-quota"><span>{t.quota(personaQuota?.remainingTurns ?? "–")}</span><small>{t.quotaNote}</small>
            {(onReportSession || onBlockSession) && <div className="persona-safety-actions">
              {onReportSession && <button type="button" className="quiet" onClick={onReportSession}>{t.reportSession}</button>}
              {onBlockSession && <button type="button" className="quiet" onClick={onBlockSession}>{t.blockSession}</button>}
            </div>}
          </div>
          <div className="persona-history" aria-label={t.personaHistAria}>{personaMessages.length === 0 ? <p>{t.historyStart}</p> : personaMessages.map(message =>
            <article className={message.senderType === "VISITOR" ? "visitor" : "capsule"} key={message.id}><span>{message.senderType === "VISITOR" ? t.speakerYou : visitorMatch.capsule.pseudonym}</span><p>{message.textContent}</p></article>)}</div>
          <div className="sandbox-composer"><textarea aria-label={t.writeToCapsule} value={personaDraft} onChange={event => onPersonaDraftChange(event.target.value)} />
            <AsyncButton className="resonance-primary" busy={visitorBusy} disabled={!personaDraft.trim() || personaQuota?.exhausted} busyText={t.sendBusy} onClick={onSendPersonaTurn}>{t.sendTurn}</AsyncButton></div>
          {personaMessages.some(message => message.senderType === "CAPSULE") && <div className="slow-letter-compose">
            <div className="capsule-step"><span>✉</span><div><strong>{t.letterStepTitle}</strong><small>{t.letterStepNote}</small></div></div>
            {visitorMatch.capsule.capsuleType !== "USER_CAPSULE" ? <p className="preview-warning">{t.seedWarning}</p> : sentLetter ?
              <div className="letter-flight" role="status"><strong>{t.letterFlightTitle}</strong><span>{sentLetter.title}</span><small>{t.letterArrival(new Date(sentLetter.estimatedArrivalAt).toLocaleString(locale), sentLetter.status)}</small></div> : <>
                <label>{t.letterTitleLabel}<input value={letterTitle} onChange={event => onLetterTitleChange(event.target.value)} /></label>
                <label>{t.letterBodyLabel}<textarea aria-label={t.letterBodyAria} value={letterBody} onChange={event => onLetterBodyChange(event.target.value)} placeholder={t.letterBodyPlaceholder} /></label>
                <AsyncButton className="resonance-primary" busy={visitorBusy} disabled={!letterTitle.trim() || !letterBody.trim()} busyText={t.sendLetterBusy} onClick={onSendLetter}>{t.sendLetterBtn}</AsyncButton></>}
          </div>}
        </>}
      </div>}
    </>}
  </section>;
}

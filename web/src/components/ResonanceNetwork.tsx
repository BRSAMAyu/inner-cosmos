import { useMemo, useState } from "react";
import type { CapsuleMatch, CapsuleQuota, PersonaMessage, PersonaSession, ResonanceStrategy, SlowLetter } from "../api";
import { demoContentText } from "../demoContentLocale";
import type { Locale } from "../i18n";
import { AsyncButton } from "../loading";
import { InlineAudioPlayer } from "./shared/InlineAudioPlayer";

const strategyOrder: ResonanceStrategy[] = ["MIRROR", "COMPLEMENT", "GROWTH_EDGE", "SERENDIPITY", "CONTEXTUAL"];

const COPY: Record<Locale, {
  aria: string; heading: string; count: (n: number) => string; intro: string; strategyAria: string;
  strategy: Record<ResonanceStrategy, string>; emptyMatches: string; railAria: string; resonantNow: string;
  exploreMeet: string; matchCardAria: (pseudonym: string, summary: string) => string;
  entryP: string; enterBusy: string; enterBtn: string;
  realPersonPath: string; practiceCapsule: string; userIdentityNotice: string; seedIdentityNotice: string;
  quotaComfort: string; quotaLow: (remaining: number) => string; quotaExhausted: string; quotaLoading: string; quotaNote: string;
  quotaUnlimited: string; quotaUnlimitedNote: string;
  personaHistAria: string; historyStart: string;
  speakerYou: string; writeToCapsule: string; sendBusy: string; sendTurn: string; letterStepTitle: string;
  letterStepNote: string; seedWarning: string; letterFlightTitle: string; letterArrival: (time: string, status: string) => string;
  letterTitleLabel: string; letterBodyLabel: string; letterBodyAria: string; letterBodyPlaceholder: string;
  letterTitleDetails: string; deliveryPromise: string; deliveryStatus: Record<string, string>;
  sendLetterBusy: string; sendLetterBtn: string; reportSession: string; blockSession: string;
  playCapsuleVoice: string; capsuleVoiceBusy: string; capsuleVoiceAria: string;
  landedBtn: string; landedBusy: string; landedDone: string;
  showMoreMatches: (n: number) => string; showFewerMatches: string;
}> = {
  "zh-CN": {
    aria: "发现共鸣并写一封慢信", heading: "遇见可能聊得来的人", count: n => `为你推荐 ${n} 个共鸣体`,
    intro: "选一种相遇方式，先和共鸣体聊几句；如果真的投缘，再写一封慢信。",
    strategyAria: "选择共鸣匹配方式",
    strategy: { MIRROR: "相似共鸣", COMPLEMENT: "有意义的互补", GROWTH_EDGE: "成长边缘", SERENDIPITY: "温和偶遇", CONTEXTUAL: "阶段同行" },
    emptyMatches: "暂时没有合适的推荐。换一种相遇方式再看看。", railAria: "共鸣候选",
    resonantNow: "最可能聊得来", exploreMeet: "认识一下", matchCardAria: (name, summary) => `${name} · ${summary}`,
    realPersonPath: "聊过后可写信给本人", practiceCapsule: "官方练习共鸣体",
    userIdentityNotice: "由一位真实用户授权形成 · 聊过后可以写信给本人",
    seedIdentityNotice: "官方练习共鸣体 · 没有真人收件人",
    entryP: "可以从一个具体时刻、一件最近发生的事，或一个你真正好奇的问题开始。",
    enterBusy: "正在进入", enterBtn: "先和这个侧影聊几句",
    quotaComfort: "可以自然聊，不用赶进度", quotaLow: r => `今天还可以聊 ${r} 轮`,
    quotaExhausted: "今天的对话额度已用完；明天会自动恢复。你仍可回看这段对话。",
    quotaLoading: "正在确认今天的交流节奏",
    quotaNote: "只有接近每日防刷上限时才会提醒；模型故障不会扣次数。", personaHistAria: "共鸣体对话记录",
    quotaUnlimited: "现场演示不限对话轮次",
    quotaUnlimitedNote: "不会因应用内额度中断；可以自然地继续聊。",
    historyStart: "可以从一个具体时刻开始，而不是交换完整履历。", speakerYou: "你", writeToCapsule: "写给共鸣体",
    sendBusy: "正在发送", sendTurn: "发送这一轮", letterStepTitle: "如果想联系创建者，可以写一封慢信",
    letterStepNote: "这封信会送给创建者本人。共鸣体不会替对方承诺回复，也不会泄露联系方式。",
    seedWarning: "这是官方种子共鸣体，没有对应的真人收件人；你仍可继续对话，但不能把它当作认识真人的入口。",
    letterFlightTitle: "慢信已启程", letterArrival: (t, s) => `预计 ${t} 到达 · 状态 ${s}`,
    letterTitleLabel: "信的题目", letterBodyLabel: "你真正想让对方读到的话", letterBodyAria: "慢信正文",
    letterBodyPlaceholder: "不用总结整段对话，只写你愿意为它负责的那部分。", sendLetterBusy: "正在寄出", sendLetterBtn: "让慢信启程",
    letterTitleDetails: "调整信的题目（可选）", deliveryPromise: "寄出后约 3 分钟抵达。等待不是故障；你可以离开这里，进度会在「连接 → 慢信」继续同步。",
    deliveryStatus: { DRAFT: "草稿", SENT: "已寄出", FLYING: "飞行中", DELIVERED: "已抵达", READ: "对方已读", REPLIED: "对方回信了", DECLINED: "被婉拒", BLOCKED: "被屏蔽", ARCHIVED: "已归档" },
    reportSession: "举报这段对话", blockSession: "屏蔽这个共鸣体",
    playCapsuleVoice: "▶ 听这条回声", capsuleVoiceBusy: "正在合成…",
    capsuleVoiceAria: "听到这个共鸣体的回复（与 Aurora 不同的声音）",
    landedBtn: "这条回复有共鸣", landedBusy: "正在记录", landedDone: "已记录",
    showMoreMatches: n => `再看 ${n} 个`, showFewerMatches: "收起，只看最相关的 3 个"
  },
  "en-SG": {
    aria: "Discover resonance and write a slow letter", heading: "Not swiping cards — understanding why you'd meet", count: n => `${n} candidate${n === 1 ? "" : "s"} right now`,
    intro: "No popularity ranking here. You only see de-identified facets, shared themes and boundaries; confirm with the authorized AI capsule whether you really want to continue before deciding to write to the person.",
    strategyAria: "Choose a resonance matching strategy",
    strategy: { MIRROR: "Similar resonance", COMPLEMENT: "Meaningful complement", GROWTH_EDGE: "Growth edge", SERENDIPITY: "Gentle serendipity", CONTEXTUAL: "Same-season company" },
    emptyMatches: "No safe-enough candidates for now. Inner Cosmos won't fill this with random strangers.", railAria: "Resonance candidates",
    resonantNow: "Alongside now", exploreMeet: "Explore a meeting", matchCardAria: (name, summary) => `${name} · ${summary}`,
    realPersonPath: "You can write to the person after chatting", practiceCapsule: "Official practice facet",
    userIdentityNotice: "Authorized by a real user · you can write to them after chatting",
    seedIdentityNotice: "Official practice facet · no real recipient",
    entryP: "Start with one or two questions that truly matter. It can only use facets the creator explicitly authorized, and won't bring your private Aurora portrait into this conversation.",
    enterBusy: "Entering", enterBtn: "Talk with this facet first",
    quotaComfort: "Talk naturally — there is no need to rush", quotaLow: r => `${r} turn${r === 1 ? "" : "s"} left today`,
    quotaExhausted: "Today's conversation quota is used up; it resets tomorrow. You can still reread this conversation.",
    quotaLoading: "Checking today's conversation rhythm",
    quotaNote: "The limit only appears when you are close to the daily anti-abuse cap; model failures never cost a turn.", personaHistAria: "Capsule conversation log",
    quotaUnlimited: "Unlimited turns in this live demo",
    quotaUnlimitedNote: "No in-app quota will interrupt this conversation.",
    historyStart: "You can start from one concrete moment, rather than exchanging full resumes.", speakerYou: "You", writeToCapsule: "Write to the capsule",
    sendBusy: "Sending", sendTurn: "Send this turn", letterStepTitle: "If you still want to continue, entrust it to time",
    letterStepNote: "This letter goes to the creator themselves. The capsule won't promise a reply on their behalf, nor reveal contact details.",
    seedWarning: "This is an official seed capsule with no real recipient; you can keep talking, but don't treat it as a way to meet a real person.",
    letterFlightTitle: "The slow letter is on its way", letterArrival: (t, s) => `Arrives ~${t} · status ${s}`,
    letterTitleLabel: "Letter title", letterBodyLabel: "What you truly want them to read", letterBodyAria: "Slow letter body",
    letterBodyPlaceholder: "No need to summarize the whole conversation — just write the part you're willing to stand behind.", sendLetterBusy: "Sending", sendLetterBtn: "Send the slow letter",
    letterTitleDetails: "Adjust the letter title (optional)", deliveryPromise: "It arrives in about 3 minutes. Waiting is not a failure; you can leave this page and follow its progress in Connect → Slow letters.",
    deliveryStatus: { DRAFT: "Draft", SENT: "Sent", FLYING: "In flight", DELIVERED: "Delivered", READ: "Read", REPLIED: "Replied", DECLINED: "Declined", BLOCKED: "Blocked", ARCHIVED: "Archived" },
    reportSession: "Report this conversation", blockSession: "Block this capsule",
    playCapsuleVoice: "▶ Hear this echo", capsuleVoiceBusy: "Synthesizing…",
    capsuleVoiceAria: "Hear this capsule's reply spoken (a voice distinct from Aurora)",
    landedBtn: "This landed with me", landedBusy: "Leaving an echo", landedDone: "Echo left",
    showMoreMatches: n => `View ${n} more candidate${n === 1 ? "" : "s"}`, showFewerMatches: "Show only the top 3"
  }
};

export function ResonanceNetwork({ resonanceMatches, resonanceStrategy, visitorBusy, visitorMatch, personaSession,
  personaMessages, personaDraft, personaQuota, letterTitle, letterBody, sentLetter,
  onChooseStrategy, onChooseMatch, onStartPersonaConversation, onPersonaDraftChange, onSendPersonaTurn,
  onLetterTitleChange, onLetterBodyChange, onSendLetter, onReportSession, onBlockSession, personaTurnError = null,
  personaVoiceAudio = null, personaVoiceBusy = false, personaVoiceError = null, onPlayPersonaVoice,
  landed = false, landedBusy = false, onMarkLanded, locale = "zh-CN" }: {
  resonanceMatches: CapsuleMatch[]; resonanceStrategy: ResonanceStrategy; visitorBusy: boolean;
  visitorMatch: CapsuleMatch | null; personaSession: PersonaSession | null; personaMessages: PersonaMessage[];
  personaDraft: string; personaQuota: CapsuleQuota | null; letterTitle: string; letterBody: string; sentLetter: SlowLetter | null;
  onChooseStrategy: (strategy: ResonanceStrategy) => void; onChooseMatch: (capsuleId: number) => void;
  onStartPersonaConversation: () => void; onPersonaDraftChange: (value: string) => void; onSendPersonaTurn: () => void;
  onLetterTitleChange: (value: string) => void; onLetterBodyChange: (value: string) => void; onSendLetter: () => void;
  onReportSession?: () => void; onBlockSession?: () => void; personaTurnError?: string | null; locale?: Locale;
  personaVoiceAudio?: string | null; personaVoiceBusy?: boolean; personaVoiceError?: string | null;
  onPlayPersonaVoice?: () => void;
  landed?: boolean; landedBusy?: boolean; onMarkLanded?: () => void;
}) {
  const t = COPY[locale];
  const [showAllMatches, setShowAllMatches] = useState(false);
  const matchTierLabel = (match: CapsuleMatch) => {
    if (!match.matchTier) return match.resonant ? t.resonantNow : t.exploreMeet;
    if (locale === "en-SG") return match.matchTier === "FULL" ? "Strong resonance"
      : match.matchTier === "PARTIAL" ? "Some resonance" : "Explore this meeting";
    return match.matchTier === "FULL" ? "深度共鸣" : match.matchTier === "PARTIAL" ? "部分共鸣" : "探索相遇";
  };
  const isUserCapsule = visitorMatch?.capsule.capsuleType === "USER_CAPSULE";
  const quotaExhausted = !personaQuota?.unlimited && personaQuota?.remaining === 0;
  const quotaLabel = !personaQuota ? t.quotaLoading
    : personaQuota.unlimited ? t.quotaUnlimited
    : personaQuota.remaining === 0 ? t.quotaExhausted
    : personaQuota.remaining <= 5 ? t.quotaLow(personaQuota.remaining)
    : t.quotaComfort;
  const visibleMatches = useMemo(() => {
    if (showAllMatches || resonanceMatches.length <= 3) return resonanceMatches;
    const selected = visitorMatch
      ? resonanceMatches.find(match => match.capsule.id === visitorMatch.capsule.id)
      : null;
    const ordered = selected
      ? [selected, ...resonanceMatches.filter(match => match.capsule.id !== selected.capsule.id)]
      : resonanceMatches;
    return ordered.slice(0, 3);
  }, [resonanceMatches, showAllMatches, visitorMatch]);
  return <section className="resonance-network" aria-label={t.aria}>
    <div className="resonance-heading"><div><span className="eyebrow">{locale === "en-SG" ? "RESONANCE NETWORK" : "共鸣网络"}</span><h2>{t.heading}</h2></div>
      <span>{t.count(resonanceMatches.length)}</span></div>
    <p className="resonance-intro">{t.intro}</p>
    <div className="strategy-switcher" role="group" aria-label={t.strategyAria}>
      {strategyOrder.map(value =>
        <button type="button" key={value} aria-pressed={resonanceStrategy === value} disabled={visitorBusy}
          onClick={() => { setShowAllMatches(false); onChooseStrategy(value); }}>{t.strategy[value]}</button>)}
    </div>
    {resonanceMatches[0] && <p className="strategy-explanation"><strong>{demoContentText(resonanceMatches[0].strategyLabel, locale)}</strong> · {demoContentText(resonanceMatches[0].strategyDescription, locale)}</p>}
    {resonanceMatches.length === 0 ? <div className="network-empty">{t.emptyMatches}</div> : <>
      <div className="match-rail" role="list" aria-label={t.railAria}>
        {/* W2 UIUX audit: same run-on-naming shape as ProductShellNavigation's five-space tabs, but
            worse here -- with no aria-label this button's accessible name would concatenate its
            badge + pseudonym + the full user-authored intro paragraph + the match summary into one
            unreadable run-on string (live-verified against a real seeded capsule). aria-hidden the
            visual content and give the button itself a short, properly separated aria-label; the
            visual card layout is unchanged. */}
        {visibleMatches.map(match => <button type="button" role="listitem" key={match.capsule.id}
          className={visitorMatch?.capsule.id === match.capsule.id ? "match-card active" : "match-card"}
          aria-label={t.matchCardAria(demoContentText(match.capsule.pseudonym, locale), demoContentText(match.matchSummary, locale))}
          onClick={() => onChooseMatch(match.capsule.id)}><span aria-hidden="true">{matchTierLabel(match)}</span>
          <em className={match.capsule.capsuleType === "USER_CAPSULE" ? "real" : "practice"} aria-hidden="true">
            {match.capsule.capsuleType === "USER_CAPSULE" ? t.realPersonPath : t.practiceCapsule}
          </em>
          <strong aria-hidden="true">{demoContentText(match.capsule.pseudonym, locale)}</strong><p className="ugc-text" aria-hidden="true">{demoContentText(match.capsule.intro, locale)}</p>
          <small aria-hidden="true">{demoContentText(match.matchSummary, locale)}</small></button>)}
      </div>
      {resonanceMatches.length > 3 && <button type="button" className="match-rail-toggle"
        aria-expanded={showAllMatches} onClick={() => setShowAllMatches(value => !value)}>
        {showAllMatches ? t.showFewerMatches : t.showMoreMatches(resonanceMatches.length - visibleMatches.length)}
      </button>}
      {visitorMatch && <div className="visitor-workbench">
        <header><div><span className="identity-notice">{isUserCapsule ? t.userIdentityNotice : t.seedIdentityNotice}</span><h3>{demoContentText(visitorMatch.capsule.pseudonym, locale)}</h3>
          <p className="ugc-text">{demoContentText(visitorMatch.capsule.intro, locale)}</p></div><div className="match-reasons">{visitorMatch.matchReasons.map(reason => <span key={reason}>{demoContentText(reason, locale)}</span>)}</div></header>
        {!personaSession ? <div className="visitor-entry"><p>{t.entryP}</p>
          <AsyncButton className="resonance-primary" busy={visitorBusy} busyText={t.enterBusy} onClick={onStartPersonaConversation}>{t.enterBtn}</AsyncButton></div> : <>
          <div className="visitor-quota"><span>{quotaLabel}</span><small>{personaQuota?.unlimited ? t.quotaUnlimitedNote : t.quotaNote}</small>
            {(onReportSession || onBlockSession) && <div className="persona-safety-actions">
              {onReportSession && <button type="button" className="quiet" onClick={onReportSession}>{t.reportSession}</button>}
              {onBlockSession && <button type="button" className="quiet" onClick={onBlockSession}>{t.blockSession}</button>}
            </div>}
          </div>
          <div className="persona-history" aria-label={t.personaHistAria}>{personaMessages.length === 0 ? <p>{t.historyStart}</p> : (() => {
            // W1 capsule-voice: the play affordance is shown ONLY on the most recent CAPSULE reply --
            // the one the visitor just received and is most likely to want heard aloud.
            const lastCapsuleId = [...personaMessages].reverse().find(m => m.senderType === "CAPSULE")?.id ?? null;
            return personaMessages.map(message => {
              const isLatestCapsule = message.senderType === "CAPSULE" && message.id === lastCapsuleId;
              return <article className={message.senderType === "VISITOR" ? "visitor" : "capsule"} key={message.id}>
                <span>{message.senderType === "VISITOR" ? t.speakerYou : demoContentText(visitorMatch.capsule.pseudonym, locale)}</span>
                <p className="ugc-text">{message.textContent}</p>
                {isLatestCapsule && onPlayPersonaVoice && <div className="capsule-voice">
                  {personaVoiceAudio
                    // autoPlay is safe here: the visitor's tap on "Hear this echo" is the user gesture
                    // that initiated the fetch, so playback on arrival satisfies the autoplay policy.
                    ? <InlineAudioPlayer audio={personaVoiceAudio} autoPlay locale={locale} ariaLabel={t.capsuleVoiceAria} />
                    : <AsyncButton className="quiet" busy={personaVoiceBusy} busyText={t.capsuleVoiceBusy}
                        onClick={onPlayPersonaVoice}>{t.playCapsuleVoice}</AsyncButton>}
                  {personaVoiceError && <span className="voice-error" role="alert">{personaVoiceError}</span>}
                </div>}
              </article>;
            });
          })()}</div>
          <div className="sandbox-composer"><textarea aria-label={t.writeToCapsule} value={personaDraft} onChange={event => onPersonaDraftChange(event.target.value)} />
            <AsyncButton className="resonance-primary" busy={visitorBusy} disabled={!personaDraft.trim() || quotaExhausted} busyText={t.sendBusy} onClick={onSendPersonaTurn}>{t.sendTurn}</AsyncButton></div>
          {personaTurnError && <p className="preview-warning" role="alert">{personaTurnError}</p>}
          {personaMessages.some(message => message.senderType === "CAPSULE") && onMarkLanded && <AsyncButton
            className="resonance-secondary" busy={landedBusy} disabled={landed} busyText={t.landedBusy}
            onClick={onMarkLanded}>{landed ? t.landedDone : t.landedBtn}</AsyncButton>}
          {personaMessages.some(message => message.senderType === "CAPSULE") && visitorMatch.capsule.allowLetterRequest !== false && <div className="slow-letter-compose">
            <div className="capsule-step"><span>✉</span><div><strong>{t.letterStepTitle}</strong><small>{t.letterStepNote}</small></div></div>
            {visitorMatch.capsule.capsuleType !== "USER_CAPSULE" ? <p className="preview-warning">{t.seedWarning}</p> : sentLetter ?
              <div className="letter-flight" role="status"><strong>{t.letterFlightTitle}</strong><span>{sentLetter.title}</span><small>{t.letterArrival(new Date(sentLetter.estimatedArrivalAt).toLocaleString(locale), t.deliveryStatus[sentLetter.status] ?? sentLetter.status)}</small></div> : <>
                <label>{t.letterBodyLabel}<textarea aria-label={t.letterBodyAria} value={letterBody} onChange={event => onLetterBodyChange(event.target.value)} placeholder={t.letterBodyPlaceholder} /></label>
                <p className="letter-delivery-promise">{t.deliveryPromise}</p>
                <details className="letter-title-details"><summary>{t.letterTitleDetails}</summary>
                  <label>{t.letterTitleLabel}<input value={letterTitle} onChange={event => onLetterTitleChange(event.target.value)} /></label>
                </details>
                <AsyncButton className="resonance-primary" busy={visitorBusy} disabled={!letterTitle.trim() || !letterBody.trim()} busyText={t.sendLetterBusy} onClick={onSendLetter}>{t.sendLetterBtn}</AsyncButton></>}
          </div>}
        </>}
      </div>}
    </>}
  </section>;
}

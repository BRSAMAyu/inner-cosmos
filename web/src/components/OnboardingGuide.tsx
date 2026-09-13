import { useEffect, useRef, useState, type CSSProperties } from "react";
import { api } from "../api";
import type { ConsentView } from "../api";
import type { Locale } from "../i18n";
import type { ProductSpace } from "./ProductShell";
import {
  consentAskLiveView,
  onboardingConsentAsks,
  onboardingQuizzesPassed,
  quizOptionFor,
  remainingQuizCount,
  stepQuizPassed
} from "../newUserJourney";

export type GuideDestination = ProductSpace | "voice" | "privacy";

const STEPS = {
  "zh-CN": [
    { title: "先从一句真话开始", body: "Aurora 是首页的主角。你可以倾诉、让她帮你理清，或随时打断；模式只改变这一次怎样合作。", action: "和 Aurora 说", destination: "aurora" },
    { title: "看见它如何成为记忆", body: "一次有意义的对话会成为一颗私密星。点星星能看到来源、系统理解和修改入口。", action: "认识内宇宙", destination: "cosmos" },
    { title: "先体验，再创造自己的侧影", body: "共鸣广场里有官方练习共鸣体。聊几轮，感受不同表达方式，再决定哪些自己的侧面愿意授权。", action: "去共鸣广场", destination: "resonance" },
    { title: "让联系慢一点", body: "对话有共鸣时，可以写一封慢信。公开、联系与真人连接都不会自动发生。", action: "看看慢信", destination: "letters" },
    { title: "把节奏交还给你", body: "在“我的”里选择 Aurora 声线、主动程度、安静时间、外观和数据边界；这套引导也能随时重看。", action: "打开设置", destination: "voice" }
  ],
  "en-SG": [
    { title: "Begin with one honest line", body: "Aurora is the heart of Today. Confide, sort things out, or interrupt at any time; modes only change how this moment works.", action: "Talk with Aurora", destination: "aurora" },
    { title: "See how a moment becomes memory", body: "A meaningful conversation can become a private star. Open it to see sources, interpretation and correction controls.", action: "Meet your cosmos", destination: "cosmos" },
    { title: "Experience a capsule before making yours", body: "The plaza includes official practice capsules. Talk first, then choose which facets of you may be authorised.", action: "Open the plaza", destination: "resonance" },
    { title: "Let connection move slowly", body: "When a conversation resonates, write a slow letter. Sharing and real-person contact never happen automatically.", action: "See slow letters", destination: "letters" },
    { title: "Keep the rhythm yours", body: "Me holds Aurora's voice, initiative, quiet hours, appearance and data boundaries. You can replay this guide there.", action: "Open settings", destination: "voice" }
  ]
} as const;

// CP-09/CP-10 · J01 progressive-consent copy. The consent cards reuse the live consent-center
// text when GET /api/me/consents resolves; the zh fallbacks in newUserJourney.ts mirror the
// backend ConsentPurpose registry verbatim so nothing shown here can drift from real semantics.
type AskCopy = {
  askEyebrow: string; managedTitle: string; stateLabel: string; granted: string; declined: string;
  stateUnavailable: string; grant: string; decline: string; recorded: (grant: boolean) => string;
  decideFailed: string; quizEyebrow: string; quizHint: string; reRead: string;
  remaining: (count: number) => string;
};

const ASK_COPY: Record<Locale, AskCopy> = {
  "zh-CN": {
    askEyebrow: "按需授权",
    managedTitle: "共鸣体授权如何管理",
    stateLabel: "当前状态",
    granted: "已同意",
    declined: "未同意",
    stateUnavailable: "同意状态暂时无法读取",
    grant: "同意",
    decline: "暂不",
    recorded: grant => `已记录你的选择：${grant ? "同意" : "暂不"}`,
    decideFailed: "这次选择未能记录（服务暂不可用）。你的既有设置没有变化，可稍后在「我的 · 隐私与数据」的同意中心修改。",
    quizEyebrow: "理解题",
    quizHint: "答对才能继续——这不是考试，只是确认说明真的被看懂了。",
    reRead: "重看说明",
    remaining: count => `还有 ${count} 道理解题未完成，答对后即可结束引导。`
  },
  "en-SG": {
    askEyebrow: "ASKED WHEN NEEDED",
    managedTitle: "How capsule authorisation is managed",
    stateLabel: "Current state",
    granted: "Granted",
    declined: "Not granted",
    stateUnavailable: "Consent status temporarily unavailable",
    grant: "Allow",
    decline: "Not now",
    recorded: grant => `Your choice is recorded: ${grant ? "allow" : "not now"}`,
    decideFailed: "This choice could not be recorded (the service is unavailable). Your existing settings are unchanged; update it later in the consent centre under Me · Privacy & data.",
    quizEyebrow: "COMPREHENSION CHECK",
    quizHint: "Answer correctly to continue — not a test, just checking the explanation really landed.",
    reRead: "Re-read the explanation",
    remaining: count => `${count} comprehension ${count === 1 ? "question remains" : "questions remain"} before the guide can finish.`
  }
};

// styles.css is owned by another workstream this batch; new ask/quiz surfaces use inline styles.
const ASK_CARD_STYLE: CSSProperties = {
  borderTop: "1px solid rgba(148,163,184,0.35)",
  marginTop: 14, paddingTop: 12
};
const WITHDRAWAL_STYLE: CSSProperties = { margin: "6px 0 0" };
const QUIZ_STYLE: CSSProperties = { border: 0, margin: "12px 0 0", padding: 0 };
const FEEDBACK_WRONG_STYLE: CSSProperties = {
  borderLeft: "3px solid rgba(225,120,120,0.8)", margin: "8px 0 0", padding: "2px 0 2px 10px"
};
const FEEDBACK_RIGHT_STYLE: CSSProperties = {
  borderLeft: "3px solid rgba(110,190,150,0.8)", margin: "8px 0 0", padding: "2px 0 2px 10px"
};
const CONSENT_CONTROLS_STYLE: CSSProperties = {
  display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", marginTop: 10
};

export function onboardingStorageKey(userId: number | string): string {
  return `inner-cosmos:onboarding:v2:${userId}`;
}

export function hasCompletedOnboarding(userId: number | string): boolean {
  try { return localStorage.getItem(onboardingStorageKey(userId)) === "complete"; }
  catch { return false; }
}

export function completeOnboarding(userId: number | string): void {
  try { localStorage.setItem(onboardingStorageKey(userId), "complete"); }
  catch { /* privacy-restricted shells may disable storage */ }
}

export function OnboardingGuide({ open, userId, locale = "zh-CN", onClose, onNavigate,
  loadConsents = api.consents, decideConsent = api.decideConsent }: {
  open: boolean; userId: number | string; locale?: Locale;
  onClose: () => void; onNavigate: (destination: GuideDestination) => void;
  // J01 progressive consent: defaults wire straight to the existing CP-07 consent endpoints so
  // the already-mounted <OnboardingGuide> in AuroraApp gains the flow without caller changes;
  // tests inject fakes through the same seams.
  loadConsents?: () => Promise<ConsentView[]>;
  decideConsent?: (purposeCode: string, grant: boolean) => Promise<unknown>;
}) {
  const [step, setStep] = useState(0);
  const steps = STEPS[locale];
  const current = steps[step];
  const en = locale === "en-SG";
  const askCopy = ASK_COPY[locale];
  const asks = onboardingConsentAsks(locale);
  const stepAsks = asks.filter(ask => ask.stepDestination === current.destination);
  const dialogRef = useRef<HTMLElement>(null);
  const firstActionRef = useRef<HTMLButtonElement>(null);
  const explanationRefs = useRef<Record<string, HTMLElement | null>>({});

  // Live consent-center state (server-authoritative text + granted flags).
  const [consentViews, setConsentViews] = useState<ConsentView[] | null>(null);
  const [consentUnavailable, setConsentUnavailable] = useState(false);
  const [consentBusyPurpose, setConsentBusyPurpose] = useState<string | null>(null);
  const [consentNote, setConsentNote] = useState("");
  const [lastRecorded, setLastRecorded] = useState<Record<string, boolean>>({});

  // Comprehension-quiz state: choice per ask id. Gate predicates live in newUserJourney.ts.
  const [quizChoices, setQuizChoices] = useState<Record<string, string>>({});
  const stepPassed = stepQuizPassed(asks, current.destination, quizChoices);
  const allPassed = onboardingQuizzesPassed(asks, quizChoices);
  const remaining = remainingQuizCount(asks, quizChoices);

  useEffect(() => {
    if (!open) return;
    setStep(0);
    setQuizChoices({});
    setLastRecorded({});
    setConsentNote("");
  }, [open]);
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setConsentViews(null);
    setConsentUnavailable(false);
    loadConsents()
      .then(views => { if (!cancelled) setConsentViews(views); })
      .catch(() => { if (!cancelled) setConsentUnavailable(true); });
    return () => { cancelled = true; };
  }, [open, loadConsents]);
  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    firstActionRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== "Tab") return;
      const controls = Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(
        "button:not([disabled]), [href], [tabindex]:not([tabindex='-1'])"
      ) ?? []);
      if (controls.length === 0) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      previousFocus?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;
  const finish = (navigate = false) => {
    // Completion is only reachable from the last step's gated action: all comprehension
    // quizzes must be answered correctly first (see the disabled state below).
    completeOnboarding(userId);
    onClose();
    if (navigate) onNavigate(current.destination);
  };

  // Records one progressive-consent decision through the existing decide endpoint, then
  // re-reads the server truth. A failure is stated honestly — never shown as recorded.
  const decide = async (purposeCode: string, grant: boolean) => {
    if (consentBusyPurpose) return;
    setConsentBusyPurpose(purposeCode);
    setConsentNote("");
    try {
      await decideConsent(purposeCode, grant);
      setLastRecorded(previous => ({ ...previous, [purposeCode]: grant }));
      setConsentViews(await loadConsents());
    } catch {
      setConsentNote(askCopy.decideFailed);
    } finally {
      setConsentBusyPurpose(null);
    }
  };

  return <div className="onboarding-backdrop" role="presentation"
    onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialogRef} className="onboarding-guide" role="dialog" aria-modal="true"
      aria-label={en ? "Welcome to Inner Cosmos" : "欢迎来到 Inner Cosmos"}>
      <div className="onboarding-cosmos" aria-hidden="true"><i /><i /><i /><span /></div>
      <header>
        <span className="eyebrow">{en ? "A SHORT FIRST ORBIT" : "第一次环游"}</span>
        {/* Closes for now WITHOUT marking onboarding complete (the guide returns next time):
            J01 deliberately offers no completion shortcut past the comprehension quizzes. */}
        <button ref={firstActionRef} type="button" className="quiet" onClick={onClose}>
          {en ? "Later" : "稍后再说"}
        </button>
      </header>
      <div className="onboarding-copy">
        <small>{step + 1} / {steps.length}</small>
        <h2>{current.title}</h2>
        <p>{current.body}</p>
      </div>
      {stepAsks.map(ask => {
        const live = ask.consent ? consentAskLiveView(consentViews, ask.consent.purposeCode) : undefined;
        const description = live?.description ?? ask.consent?.description ?? ask.managedNote;
        const withdrawal = live?.withdrawalEffect ?? ask.consent?.withdrawalEffect;
        const choice = quizChoices[ask.id];
        const chosen = quizOptionFor(ask, choice);
        const recordNote = ask.consent && lastRecorded[ask.consent.purposeCode] !== undefined
          ? askCopy.recorded(lastRecorded[ask.consent.purposeCode]) : null;
        return <article key={ask.id} className="onboarding-ask" data-ask={ask.id}
          style={ASK_CARD_STYLE}
          ref={element => { explanationRefs.current[ask.id] = element; }}
          tabIndex={-1}>
          <span className="eyebrow">{askCopy.askEyebrow}</span>
          <strong>{ask.consent?.title ?? askCopy.managedTitle}</strong>
          <div className="onboarding-explanation">
            <p style={{ margin: "8px 0 0" }}>{description}</p>
            {withdrawal && <p className="muted" style={WITHDRAWAL_STYLE}>{withdrawal}</p>}
          </div>
          {ask.consent && <>
            <div className="onboarding-consent-controls" style={CONSENT_CONTROLS_STYLE}>
              <small>{askCopy.stateLabel}：
                {live ? (live.granted ? askCopy.granted : askCopy.declined)
                  : (consentUnavailable ? askCopy.stateUnavailable : "…")}
              </small>
              <button type="button"
                disabled={consentBusyPurpose !== null}
                aria-label={`${askCopy.grant}: ${ask.consent.purposeCode}`}
                onClick={() => void decide(ask.consent!.purposeCode, true)}>
                {askCopy.grant}
              </button>
              <button type="button"
                disabled={consentBusyPurpose !== null}
                aria-label={`${askCopy.decline}: ${ask.consent.purposeCode}`}
                onClick={() => void decide(ask.consent!.purposeCode, false)}>
                {askCopy.decline}
              </button>
              {recordNote && <small role="status">{recordNote}</small>}
            </div>
            {ask.managedNote && <p className="muted" style={WITHDRAWAL_STYLE}>{ask.managedNote}</p>}
          </>}
          {!ask.consent && ask.managedNote &&
            <p className="muted" style={WITHDRAWAL_STYLE}>{ask.managedNote}</p>}
          {consentNote && <p role="alert" style={WITHDRAWAL_STYLE}>{consentNote}</p>}
          <fieldset className="onboarding-quiz" style={QUIZ_STYLE}>
            <legend>
              <small className="eyebrow">{askCopy.quizEyebrow}</small>
              {ask.quiz.prompt}
            </legend>
            <small className="muted" style={{ display: "block", margin: "2px 0 6px" }}>{askCopy.quizHint}</small>
            {ask.quiz.options.map(option => <label key={option.key}
              style={{ display: "block", margin: "4px 0" }}>
              <input type="radio" name={`${ask.id}-option`} value={option.key}
                checked={choice === option.key}
                onChange={() => setQuizChoices(previous => ({ ...previous, [ask.id]: option.key }))} />
              {" "}{option.label}
            </label>)}
            {chosen && (chosen.correct
              ? <p role="status" className="quiz-feedback correct" style={FEEDBACK_RIGHT_STYLE}>
                  {ask.quiz.correctFeedback}
                </p>
              : <p role="status" className="quiz-feedback wrong" style={FEEDBACK_WRONG_STYLE}>
                  {chosen.wrongFeedback}{" "}
                  <button type="button" className="quiet" style={{ padding: 0 }}
                    onClick={() => explanationRefs.current[ask.id]?.focus()}>
                    {askCopy.reRead}
                  </button>
                </p>)}
          </fieldset>
        </article>;
      })}
      <ol className="onboarding-dots" aria-label={en ? "Guide progress" : "引导进度"}>
        {steps.map((item, index) => <li key={item.title} className={index === step ? "active" : index < step ? "done" : ""}>
          <button type="button" aria-label={`${index + 1}. ${item.title}`} onClick={() => setStep(index)} />
        </li>)}
      </ol>
      <footer>
        <button type="button" className="quiet" disabled={step === 0} onClick={() => setStep(value => value - 1)}>
          {en ? "Back" : "上一步"}
        </button>
        {step < steps.length - 1
          ? <button type="button" className="primary" disabled={!stepPassed}
              title={!stepPassed ? askCopy.quizHint : undefined}
              onClick={() => setStep(value => value + 1)}>
              {en ? "Continue" : "继续"}
            </button>
          : <>
              <small role="status">{remaining > 0 ? askCopy.remaining(remaining) : ""}</small>
              <button type="button" className="primary" disabled={!allPassed}
                onClick={() => finish(true)}>{current.action}</button>
            </>}
      </footer>
    </section>
  </div>;
}

export function GuideCenter({ locale = "zh-CN", onReplay, onNavigate }: {
  locale?: Locale; onReplay: () => void; onNavigate: (destination: GuideDestination) => void;
}) {
  const en = locale === "en-SG";
  const shortcuts: Array<[GuideDestination, string, string]> = en
    ? [["aurora", "Aurora basics", "Conversation, modes, interruption and voice input"],
       ["cosmos", "Understand the cosmos", "Stars, sources, changes and corrections"],
       ["resonance", "Capsules & resonance", "Official examples, authorisation and slow connection"],
       ["letters", "Slow letters & connection", "Write, wait, receive and choose whether to connect"],
       ["voice", "Voice & relationship settings", "Voice, initiative, quiet hours and appearance"],
       ["privacy", "Privacy & data", "Export, delete and authorisation boundaries"]]
    : [["aurora", "Aurora 入门", "对话、模式、打断与语音输入"],
       ["cosmos", "看懂内宇宙", "星星、来源、变化与纠正"],
       ["resonance", "共鸣体与相遇", "官方示例、授权和慢连接"],
       ["letters", "慢信与连接", "写信、等待、收信，再决定是否连接"],
       ["voice", "声线与相处设置", "声线、主动程度、安静时间与外观"],
       ["privacy", "隐私与数据", "导出、删除和授权边界"]];
  return <section className="guide-center" aria-label={en ? "Guide centre" : "引导中心"}>
    <header><div><span className="eyebrow">{en ? "GUIDE CENTRE" : "引导中心"}</span>
      <h1>{en ? "Come back whenever something feels unclear." : "哪里没看懂，就从这里重新出发。"}</h1>
      <p>{en ? "Short, task-based guides — no feature manual to memorise." : "按真实任务组织，不需要背功能说明书。"}</p></div>
      <button type="button" className="primary" onClick={onReplay}>{en ? "Replay first orbit" : "重看首次引导"}</button>
    </header>
    <div className="guide-grid">{shortcuts.map(([destination, title, body], index) =>
      <button type="button" key={destination} onClick={() => onNavigate(destination)}>
        <span>{String(index + 1).padStart(2, "0")}</span><strong>{title}</strong><small>{body}</small><em>→</em>
      </button>)}
    </div>
  </section>;
}

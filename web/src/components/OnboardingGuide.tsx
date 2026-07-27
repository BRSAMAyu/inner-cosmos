import { useEffect, useRef, useState } from "react";
import type { Locale } from "../i18n";
import type { ProductSpace } from "./ProductShell";

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

export function OnboardingGuide({ open, userId, locale = "zh-CN", onClose, onNavigate }: {
  open: boolean; userId: number | string; locale?: Locale;
  onClose: () => void; onNavigate: (destination: GuideDestination) => void;
}) {
  const [step, setStep] = useState(0);
  const steps = STEPS[locale];
  const current = steps[step];
  const en = locale === "en-SG";
  const dialogRef = useRef<HTMLElement>(null);
  const firstActionRef = useRef<HTMLButtonElement>(null);

  useEffect(() => { if (open) setStep(0); }, [open]);
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
    completeOnboarding(userId);
    onClose();
    if (navigate) onNavigate(current.destination);
  };

  return <div className="onboarding-backdrop" role="presentation"
    onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialogRef} className="onboarding-guide" role="dialog" aria-modal="true"
      aria-label={en ? "Welcome to Inner Cosmos" : "欢迎来到 Inner Cosmos"}>
      <div className="onboarding-cosmos" aria-hidden="true"><i /><i /><i /><span /></div>
      <header>
        <span className="eyebrow">{en ? "A SHORT FIRST ORBIT" : "第一次环游"}</span>
        <button ref={firstActionRef} type="button" className="quiet" onClick={() => finish(false)}>
          {en ? "Skip guide" : "跳过引导"}
        </button>
      </header>
      <div className="onboarding-copy">
        <small>{step + 1} / {steps.length}</small>
        <h2>{current.title}</h2>
        <p>{current.body}</p>
      </div>
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
          ? <button type="button" className="primary" onClick={() => setStep(value => value + 1)}>
              {en ? "Continue" : "继续"}
            </button>
          : <button type="button" className="primary" onClick={() => finish(true)}>{current.action}</button>}
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
      </button>)}</div>
  </section>;
}

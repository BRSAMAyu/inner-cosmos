import type { Locale } from "./i18n";

// B4: static strings inlined directly in the AuroraApp shell (Aurora hero, dialog-mode labels, the
// mobile-presence section, the returns/wake-intent cards and the footer). Kept here rather than in
// AuroraApp so the 1000-line shell stays readable and the copy is one localizable table. Transient
// setStatus toast messages in async handlers are intentionally NOT here yet (a separate follow-up).
export type DialogMode = "DAILY_TALK" | "THOUGHT_CLARIFY" | "SOCRATIC" | "ACTION_SPLIT" | "RELATION_REVIEW" | "CAPSULE_SHAPING";

export const APP_COPY: Record<Locale, {
  connecting: string; modeLabel: Record<DialogMode, string>; modeHint: Record<DialogMode, string>;
  heroLine1: string; heroLine2: string; heroP: string; runtimeAria: string;
  runtimeUnderstanding: string; runtimeComposing: string; runtimeSpeaking: string; runtimeHere: string;
  dualCore: string; relationshipMovePrefix: string; repaired: string; modesAria: string; transcribeUnavailable: string;
  mobileAria: string; mobileConnected: string; mobileOffline: string;
  mobileConnectedP: (platform: string, type: string) => string; mobileOfflineP: string; pushBtn: string; micBtn: string;
  returnsAria: string; returnsTitle: string; whenLabel: string; returnTimeAria: string; scheduleBtn: string;
  returnsEmpty: string; postpone: string; cancel: string;
  arrivalAria: string; backToUnfinished: string; matched: string; later: string; stopSimilar: string;
  footerTagline: string; footerSignOut: string;
  heroEyebrow: string; presenceEyebrow: string; returnsEyebrow: string; returnedEyebrow: string;
}> = {
  "zh-CN": {
    connecting: "正在连接你的内宇宙…", heroEyebrow: "内宇宙 · Aurora", presenceEyebrow: "Aurora，与你同在",
    returnsEyebrow: "Aurora 的回来约定", returnedEyebrow: "Aurora 如约回来",
    modeLabel: { DAILY_TALK: "倾诉", THOUGHT_CLARIFY: "整理", SOCRATIC: "追问", ACTION_SPLIT: "行动", RELATION_REVIEW: "关系", CAPSULE_SHAPING: "塑造侧影" },
    modeHint: {
      DAILY_TALK: "只听你说，不急着分析或给建议。",
      THOUGHT_CLARIFY: "把事实、感受、担心与需要一条条理清。",
      SOCRATIC: "一次追问一个关键假设，答案仍由你发现。",
      ACTION_SPLIT: "不做长计划，只选一个十分钟内能开始的动作。",
      RELATION_REVIEW: "分清事实、感受、需要与边界，不替任何人站队。",
      CAPSULE_SHAPING: "从真实故事里提取表达、价值与边界，形成私密侧影。"
    },
    heroLine1: "今天，", heroLine2: "想从哪里说起？",
    heroP: "Aurora 会记住上下文；你也可以随时打断、换一种合作方式。", runtimeAria: "Aurora 当前回应状态",
    runtimeUnderstanding: "正在理解", runtimeComposing: "正在组织", runtimeSpeaking: "正在回应", runtimeHere: "Aurora 已就绪",
    dualCore: "理解与表达双核协作", relationshipMovePrefix: "关系动作 · ", repaired: "回应已通过边界复核",
    modesAria: "对话模式", transcribeUnavailable: "语音转写暂时不可用",
    mobileAria: "移动端连接状态", mobileConnected: "移动端已连接", mobileOffline: "网络暂时离开了",
    mobileConnectedP: (p, t) => `${p} · ${t} · 回到前台时会从持久化时间线续接`,
    mobileOfflineP: "你已经看到的内容会留在这里；网络恢复后，Aurora 会重新读取时间线，不会把断线误当成新对话。",
    pushBtn: "开启回来提醒", micBtn: "准备语音输入",
    returnsAria: "Aurora 的回来约定", returnsTitle: "回来约定", whenLabel: "什么时候合适", returnTimeAria: "回来时间",
    scheduleBtn: "和 Aurora 约好", returnsEmpty: "现在没有约定。需要时，你可以邀请 Aurora 在合适的时候回来。",
    postpone: "晚一小时", cancel: "取消",
    arrivalAria: "Aurora 按约定回来", backToUnfinished: "回到当时没说完的地方", matched: "正合适", later: "晚一点", stopSimilar: "不再提醒这类事",
    footerTagline: "五空间 AppShell · 数据与能力持续保留", footerSignOut: "安全退出"
  },
  "en-SG": {
    connecting: "Connecting to your inner cosmos…", heroEyebrow: "INNER COSMOS · AURORA", presenceEyebrow: "AURORA, WITH YOU",
    returnsEyebrow: "AURORA RETURNS", returnedEyebrow: "AURORA RETURNED",
    modeLabel: { DAILY_TALK: "Confide", THOUGHT_CLARIFY: "Sort out", SOCRATIC: "Probe", ACTION_SPLIT: "Act", RELATION_REVIEW: "Relate", CAPSULE_SHAPING: "Shape capsule" },
    modeHint: {
      DAILY_TALK: "Be heard without rushing into analysis or advice.",
      THOUGHT_CLARIFY: "Separate facts, feelings, worries and needs.",
      SOCRATIC: "Test one key assumption at a time; the answer stays yours.",
      ACTION_SPLIT: "Skip the long plan and choose one ten-minute action.",
      RELATION_REVIEW: "Separate facts, feelings, needs and boundaries without taking sides.",
      CAPSULE_SHAPING: "Use real stories to shape a private voice, values and boundaries."
    },
    heroLine1: "What would you like", heroLine2: "to say today?",
    heroP: "Aurora keeps the context. Interrupt at any time or change how you work together.", runtimeAria: "Aurora's current response state",
    runtimeUnderstanding: "Understanding", runtimeComposing: "Composing", runtimeSpeaking: "Responding", runtimeHere: "Aurora ready",
    dualCore: "Dual-core: understanding + expression", relationshipMovePrefix: "Relationship move · ", repaired: "Response passed boundary review",
    modesAria: "Conversation modes", transcribeUnavailable: "Voice transcription is unavailable right now",
    mobileAria: "Mobile connection status", mobileConnected: "Mobile connected", mobileOffline: "The network stepped away",
    mobileConnectedP: (p, t) => `${p} · ${t} · resumes from the persisted timeline when you return to the foreground`,
    mobileOfflineP: "What you've already seen stays here; when the network returns, Aurora re-reads the timeline and won't mistake a disconnect for a new conversation.",
    pushBtn: "Enable return reminders", micBtn: "Prepare voice input",
    returnsAria: "Aurora's return agreement", returnsTitle: "Return agreement", whenLabel: "When suits you", returnTimeAria: "Return time",
    scheduleBtn: "Agree with Aurora", returnsEmpty: "No agreement right now. When you need it, you can invite Aurora to return at a suitable time.",
    postpone: "An hour later", cancel: "Cancel",
    arrivalAria: "Aurora returned as agreed", backToUnfinished: "Back to what was left unsaid", matched: "Good timing", later: "A bit later", stopSimilar: "Stop reminders like this",
    footerTagline: "Five-space AppShell · data and capabilities preserved", footerSignOut: "Sign out"
  }
};

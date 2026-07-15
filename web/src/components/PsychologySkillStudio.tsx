import type { PsychologyRetention, PsychologySkillManifest, PsychologySkillRun, PsychologySkillSuggestion } from "../api";

export type SkillLocale = "zh-CN" | "en-SG";

export const skillQuestions: Record<SkillLocale, Record<string, Array<[string, string, string]>>> = { "zh-CN": {
  "emotion-needs-clarifier": [
    ["situation", "发生了什么", "只写这一次的具体情境"], ["feeling", "最接近的感受", "可以用自己的词，不必选标签"],
    ["need", "你更想保护或得到什么", "例如：空间、准备感、被理解、选择权"]
  ],
  "values-compass": [
    ["choiceA", "选择 A", "它会把你带向哪里"], ["choiceB", "选择 B", "它会把你带向哪里"],
    ["important", "两边都在保护什么重要的东西", "例如：自主、关系、稳定、成长"]
  ],
  "decision-conflict-map": [
    ["decision", "正在拉扯你的决定", "不需要一次讲完整背景"], ["pullToward", "什么在把你拉近它", "期待、价值或现实收益"],
    ["pullAway", "什么在让你退开", "代价、担心或需要被保护的部分"]
  ]
}, "en-SG": {
  "emotion-needs-clarifier": [
    ["situation", "What happened?", "Describe only this specific situation"], ["feeling", "Closest feeling", "Use your own words; no label is required"],
    ["need", "What do you want to protect or receive?", "For example: space, readiness, understanding, choice"]
  ],
  "values-compass": [
    ["choiceA", "Option A", "Where might it take you?"], ["choiceB", "Option B", "Where might it take you?"],
    ["important", "What matters on both sides?", "For example: autonomy, relationship, stability, growth"]
  ],
  "decision-conflict-map": [
    ["decision", "The decision pulling at you", "You do not need to give the whole background"], ["pullToward", "What draws you towards it?", "Hope, values, or practical benefit"],
    ["pullAway", "What makes you step back?", "Costs, worries, or a part that needs protection"]
  ]
} };

export const skillCopy = {
  "zh-CN": {
    heading: "不是给你一个标签，而是陪你看清一点", count: "项低风险能力", intro: "这些是有边界的反思工具，不是心理诊断。Aurora 可以建议，但只有你明确同意后才能运行。",
    select: "选择一项反思能力", minutes: "约", minuteUnit: "分钟", before: "开始前，你应当知道", reads: "读取", readsValue: "仅本次填写内容", tools: "工具", noTools: "不调用外部工具", evidence: "依据", evidenceValue: "项，可展开查看", theory: "查看理论依据和版本",
    retention: "结果怎样保留", discard: "只看这一次，不保存结果", save: "保存结果，之后可以撤回", profile: "保存并允许以后单独确认是否进入画像", consent: "我知道这不是诊断，并同意只使用本次填写内容运行", busy: "正在整理…", start: "开始这次反思",
    recent: "最近的反思", revoked: "已撤回", escalated: "已暂停并转向安全支持", noSaved: "这次结果没有被保存。", alternative: "另一种可能：", action: "可以试试：", continue: "和 Aurora 继续谈", revoke: "撤回这次结果"
  },
  "en-SG": {
    heading: "Not a label — a little more clarity", count: "low-risk skills", intro: "These are bounded reflection tools, not psychological diagnoses. Aurora may suggest one, but it runs only after your explicit consent.",
    select: "Choose a reflection skill", minutes: "About", minuteUnit: "minutes", before: "Before you begin", reads: "Reads", readsValue: "Only what you enter for this run", tools: "Tools", noTools: "No external tools", evidence: "Evidence", evidenceValue: "references; expand to inspect", theory: "View evidence and version",
    retention: "How should the result be retained?", discard: "View once; do not save the result", save: "Save the result; I can revoke it later", profile: "Save it and ask separately before any profile use", consent: "I understand this is not a diagnosis and consent to using only this run's input for", busy: "Reflecting…", start: "Begin this reflection",
    recent: "Recent reflections", revoked: "Revoked", escalated: "Paused and redirected to safety support", noSaved: "This result was not saved.", alternative: "Another possibility: ", action: "A small step: ", continue: "Continue with Aurora", revoke: "Revoke this result"
  }
} as const;

export function PsychologySkillStudio({ skills, skillRuns, selectedSkill, skillAnswers, skillConsent, skillRetention,
  skillBusy, skillLocale, onLocaleChange, onSelectSkill, onAnswerChange, onRetentionChange, onConsentChange,
  onRun, onContinueWithAurora, onRevokeRun }: {
  skills: PsychologySkillManifest[]; skillRuns: PsychologySkillRun[]; selectedSkill: PsychologySkillManifest | null;
  skillAnswers: Record<string, string>; skillConsent: boolean; skillRetention: PsychologyRetention; skillBusy: boolean;
  skillLocale: SkillLocale; onLocaleChange: (locale: SkillLocale) => void; onSelectSkill: (skillId: string) => void;
  onAnswerChange: (key: string, value: string) => void; onRetentionChange: (value: PsychologyRetention) => void;
  onConsentChange: (checked: boolean) => void; onRun: () => void; onContinueWithAurora: (run: PsychologySkillRun) => void;
  onRevokeRun: (runId: number) => void;
}) {
  const text = skillCopy[skillLocale];
  return <section className="skill-studio" aria-label={skillLocale === "en-SG" ? "Psychology-informed self reflection" : "心理学启发的自我探索"} lang={skillLocale === "en-SG" ? "en-SG" : "zh-CN"}>
    <div className="skill-heading"><div><span className="eyebrow">PSYCHOLOGY SKILLS</span><h2>{text.heading}</h2></div>
      <div className="skill-heading-tools"><span>{skills.length} {text.count}</span><div className="skill-locale" role="group" aria-label="Skill language"><button type="button" className={skillLocale === "zh-CN" ? "active" : ""} aria-pressed={skillLocale === "zh-CN"} onClick={() => onLocaleChange("zh-CN")}>中文</button><button type="button" className={skillLocale === "en-SG" ? "active" : ""} aria-pressed={skillLocale === "en-SG"} onClick={() => onLocaleChange("en-SG")}>English</button></div></div></div>
    <p>{text.intro}</p>
    <div className="skill-layout">
      <div className="skill-catalog" role="tablist" aria-label={text.select}>
        {skills.map(skill => <button type="button" role="tab" aria-selected={selectedSkill?.id === skill.id}
          className={selectedSkill?.id === skill.id ? "active" : ""} key={skill.id}
          onClick={() => onSelectSkill(skill.id)}>
          <strong>{skill.title[skillLocale]}</strong><span>{skill.description[skillLocale]}</span>
          <small>{text.minutes} {skill.estimatedMinutes} {text.minuteUnit} · {skill.riskTier} · v{skill.version}</small>
        </button>)}
      </div>
      {selectedSkill && <div className="skill-runner" role="tabpanel">
        <div className="skill-disclosure"><strong>{text.before}</strong>
          <p>{selectedSkill.limitations[skillLocale]}</p>
          <dl><div><dt>{text.reads}</dt><dd>{text.readsValue}</dd></div><div><dt>{text.tools}</dt><dd>{selectedSkill.allowedTools.length ? selectedSkill.allowedTools.join(", ") : text.noTools}</dd></div><div><dt>{text.evidence}</dt><dd>{selectedSkill.evidence.length} {text.evidenceValue}</dd></div></dl>
          <details><summary>{text.theory}</summary>{selectedSkill.evidence.map(item => <p key={item}>{item}</p>)}<small>{selectedSkill.evaluationSuite}</small></details>
        </div>
        <div className="skill-fields">{(skillQuestions[skillLocale][selectedSkill.id] ?? []).map(([key, label, placeholder]) =>
          <label key={key}>{label}<textarea value={skillAnswers[key] ?? ""} placeholder={placeholder}
            onChange={event => onAnswerChange(key, event.target.value)} /></label>)}</div>
        <fieldset className="skill-retention"><legend>{text.retention}</legend>
          <label><input type="radio" name="skill-retention" checked={skillRetention === "DISCARD_AFTER_SESSION"} onChange={() => onRetentionChange("DISCARD_AFTER_SESSION")} />{text.discard}</label>
          <label><input type="radio" name="skill-retention" checked={skillRetention === "SAVE_RESULT"} onChange={() => onRetentionChange("SAVE_RESULT")} />{text.save}</label>
          <label><input type="radio" name="skill-retention" checked={skillRetention === "PROFILE_ELIGIBLE"} onChange={() => onRetentionChange("PROFILE_ELIGIBLE")} />{text.profile}</label>
        </fieldset>
        <label className="skill-consent"><input type="checkbox" checked={skillConsent} onChange={event => onConsentChange(event.target.checked)} />{text.consent} v{selectedSkill.version}</label>
        <button className="skill-start" type="button" disabled={skillBusy || !skillConsent} onClick={onRun}>{skillBusy ? text.busy : text.start}</button>
      </div>}
    </div>
    {skillRuns.length > 0 && <div className="skill-results" aria-label={skillLocale === "en-SG" ? "My Skill results" : "我的 Skill 结果"}>
      <h3>{text.recent}</h3>{skillRuns.slice(0, 5).map(run => <article key={run.id} className={run.status.toLowerCase()} lang={run.locale === "en-SG" ? "en-SG" : "zh-CN"}>
        <header><strong>{skills.find(skill => skill.id === run.skillId)?.title[run.locale] ?? run.skillId}</strong><span>{run.status === "REVOKED" ? text.revoked : run.status === "ESCALATED" ? text.escalated : `v${run.skillVersion}`}</span></header>
        {run.status !== "REVOKED" && <><p>{String(run.result.summary ?? text.noSaved)}</p>
          {run.result.alternative && <p className="skill-alternative">{text.alternative}{String(run.result.alternative)}</p>}
          {run.result.smallAction && <p className="skill-action">{text.action}{String(run.result.smallAction)}</p>}
          <div className="skill-result-actions"><button type="button" onClick={() => onContinueWithAurora(run)}>{text.continue}</button>
            <button type="button" disabled={skillBusy} onClick={() => onRevokeRun(run.id)}>{text.revoke}</button></div></>}
      </article>)}</div>}
  </section>;
}

export function SkillSuggestionBanner({ suggestion, locale, onOpen, onDismiss }: {
  suggestion: PsychologySkillSuggestion; locale: SkillLocale; onOpen: () => void; onDismiss: () => void;
}) {
  return <aside className="skill-suggestion" aria-label={locale === "en-SG" ? "Aurora optional reflection suggestion" : "Aurora 的可选反思建议"} lang={locale === "en-SG" ? "en-SG" : "zh-CN"}>
    <div><span>{locale === "en-SG" ? "AURORA SUGGESTS · Will not run automatically" : "AURORA SUGGESTS · 不会自动运行"}</span><strong>{suggestion.title}</strong><p>{suggestion.reason}</p></div>
    <div><button type="button" onClick={onOpen}>{locale === "en-SG" ? "I decide whether to open it" : "由我决定是否打开"}</button>
      <button type="button" className="quiet" onClick={onDismiss}>{locale === "en-SG" ? "Not this time" : "这次不要"}</button></div>
  </aside>;
}

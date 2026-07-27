import { useEffect, useState } from "react";
import { api, type DemoPersona } from "../api";
import { localizeDemoPersona } from "../demoContentLocale";
import type { Locale } from "../i18n";

export function DemoPersonaChooser({ compact = false,
  locale = "zh-CN", onEntered }: {
  currentUsername?: string | null;
  compact?: boolean;
  locale?: Locale;
  onEntered: () => Promise<void> | void;
}) {
  const [personas, setPersonas] = useState<DemoPersona[]>([]);
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const [busyPhase, setBusyPhase] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    let alive = true;
    void api.demoPersonas().then(rows => {
      if (alive) setPersonas(rows);
    }).catch(() => {
      if (alive) setPersonas([]);
    });
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    if (!busyKey) {
      setBusyPhase(0);
      return;
    }
    const timer = window.setInterval(() => setBusyPhase(value => Math.min(value + 1, 2)), 650);
    return () => window.clearInterval(timer);
  }, [busyKey]);

  if (personas.length === 0) return null;
  const displayPersonas = personas.map(persona => localizeDemoPersona(persona, locale));

  const enter = async (key: string) => {
    if (busyKey || personas.some(persona => persona.key === key && persona.active)) return;
    setBusyKey(key);
    setError("");
    try {
      await api.enterDemoPersona(key);
      await onEntered();
      try {
        setPersonas(await api.demoPersonas());
      } catch {
        // Entering already succeeded; keep the cards usable if refreshing the
        // active marker is temporarily unavailable.
      }
      setBusyKey(null);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message
        : locale === "en-SG" ? "Couldn't enter this story." : "暂时无法进入这段故事。");
      setBusyKey(null);
    }
  };

  const personaButtons = displayPersonas.map(persona => {
    const active = Boolean(persona.active);
    return <button type="button" key={persona.key} className={active ? "active" : ""}
      aria-current={active ? "true" : undefined}
      aria-busy={busyKey === persona.key ? "true" : undefined}
      disabled={Boolean(busyKey) || active} onClick={() => void enter(persona.key)}>
      <span className="demo-persona-name">{persona.name}</span>
      {!compact && <><strong>{persona.headline}</strong><small>{persona.story}</small>
        <span className="demo-persona-themes">{persona.themes.join(" · ")}</span></>}
      <em>{active
        ? (locale === "en-SG" ? "Current" : "当前")
        : busyKey === persona.key
          ? (locale === "en-SG"
              ? ["Cloning private memories…", "Weaving voice and boundaries…", "Opening your private capsule…"][busyPhase]
              : ["正在复制私密记忆…", "正在编织表达与边界…", "正在生成你的私有共鸣体…"][busyPhase])
          : (locale === "en-SG" ? "Enter" : "切换")}</em>
    </button>;
  });

  if (compact) {
    const activeName = displayPersonas.find(persona => persona.active)?.name
      ?? (locale === "en-SG" ? "Choose a story" : "选择体验角色");
    return <section className="demo-persona-chooser compact"
      aria-label={locale === "en-SG" ? "Demo stories" : "Demo 体验角色"}>
      <details>
        <summary>{locale === "en-SG" ? "Demo story" : "体验角色"} · <strong>{activeName}</strong>
          <span>{locale === "en-SG" ? "Switch" : "切换"}</span></summary>
        <div className="demo-persona-grid">{personaButtons}</div>
      </details>
      {error && <p className="error" role="alert">{error}</p>}
    </section>;
  }

  return <section className="demo-persona-chooser"
    aria-label={locale === "en-SG" ? "Demo stories" : "Demo 体验角色"}>
    <div className="demo-persona-heading">
      <span className="eyebrow">{locale === "en-SG" ? "LIVED-IN DEMO" : "有生活痕迹的体验"}</span>
      <div>
        <strong>{compact
          ? (locale === "en-SG" ? "Switch lived story" : "切换体验角色")
          : (locale === "en-SG" ? "Begin with a life already in motion" : "从一段已经生活了几个月的关系开始")}</strong>
        {!compact && <p>{locale === "en-SG"
          ? "Each story has its own memories, rhythms, capsules and letters. No setup required."
          : "每个角色都有不同的记忆、生活节律、共鸣体与慢信；无需注册或补资料。"}</p>}
      </div>
    </div>
    <div className="demo-persona-grid">{personaButtons}</div>
    {error && <p className="error" role="alert">{error}</p>}
  </section>;
}

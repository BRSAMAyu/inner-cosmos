import { useState } from "react";
import type { DailyRecordDetail, DailyRecordEntry, ThoughtFragmentRow } from "../api";
import type { Locale } from "../i18n";
import { emotionWeatherPresentation } from "../emotionWeather";
import { AsyncButton } from "../loading";

// Phase 3 legacy-page port: src/main/resources/static/pages/daily-record.html.
//
// Real backend quirk this component works around (see api.ts's DailyRecordDetail doc comment):
// GET /api/daily-record/latest has no `id` of its own (it's built from the latest MemoryCard, not
// a DailyRecord row), so accept/edit target `records[index].id` -- the real tb_daily_record id --
// not anything off `detail`. `detail` only ever supplies the richer fragments/emotions/todos view,
// and only for index 0 (the most recent day), since older days have no such VO available.
const COPY: Record<Locale, {
  routeHint: string; heading: string; intro: string; accept: string; viewStarfield: string;
  previous: string; next: string; today: string; themeHeading: string; edit: string; weatherHeading: string;
  eventHeading: string; fragmentsHeading: string; fragmentsIntro: string; relationsHeading: string;
  todosHeading: string; auroraHeading: string; save: string; cancel: string; editThemeLabel: string;
  editEventLabel: string; emptyTheme: string; emptySummary: string; emptyRelations: string; emptyTodos: string;
  emptyFragments: string; emptyToday: string; intensityLabel: string; analysisLabel: string; emptyAurora: string;
}> = {
  "zh-CN": {
    routeHint: "我被整理清楚了", heading: "今日记录卡", intro: "它不是诊断，也不是给你贴标签。它只是在帮你把今天摊平。",
    accept: "接受并保存", viewStarfield: "查看星图", previous: "前一天", next: "后一天", today: "今天",
    themeHeading: "今日主题", edit: "编辑", weatherHeading: "情绪天气", eventHeading: "今日理解",
    fragmentsHeading: "认知碎片", fragmentsIntro: "每一段想法都被温柔地拆解了。", relationsHeading: "关系线索",
    todosHeading: "待办线索", auroraHeading: "Aurora 观察记录", save: "保存", cancel: "取消",
    editThemeLabel: "编辑主题", editEventLabel: "编辑今日理解", emptyTheme: "今天还没有记录",
    emptySummary: "和 Aurora 聊一次天后，这里会出现你的今日记录。", emptyRelations: "今天没有特别的关系线索。",
    emptyTodos: "今天没有强行生成待办。", emptyFragments: "对话结束后会出现认知碎片。",
    emptyToday: "今天还没有记录", intensityLabel: "强度", analysisLabel: "Aurora 理解",
    emptyAurora: "暂时没有独立于原话的 Aurora 观察。"
  },
  "en-SG": {
    routeHint: "Sorted out, clearly", heading: "Today's Record Card", intro: "It isn't a diagnosis, and it doesn't label you. It just helps flatten today out.",
    accept: "Accept and save", viewStarfield: "View starfield", previous: "Previous day", next: "Next day", today: "Today",
    themeHeading: "Today's theme", edit: "Edit", weatherHeading: "Emotion weather", eventHeading: "Today's understanding",
    fragmentsHeading: "Cognitive fragments", fragmentsIntro: "Every thought was gently taken apart.", relationsHeading: "Relationship clues",
    todosHeading: "Todo clues", auroraHeading: "Aurora's observation", save: "Save", cancel: "Cancel",
    editThemeLabel: "Edit theme", editEventLabel: "Edit today's understanding", emptyTheme: "No record yet today",
    emptySummary: "After a chat with Aurora, your record for today will appear here.", emptyRelations: "No particular relationship clues today.",
    emptyTodos: "No todos were forced into existence today.", emptyFragments: "Cognitive fragments appear after a conversation ends.",
    emptyToday: "No record yet today", intensityLabel: "Intensity", analysisLabel: "Aurora's understanding",
    emptyAurora: "There is no Aurora observation independent of your own words yet."
  }
};

const FRAGMENT_LABELS: Record<string, Record<Locale, string>> = {
  FACT: { "zh-CN": "事实", "en-SG": "Fact" },
  FEELING: { "zh-CN": "感受", "en-SG": "Feeling" },
  EMOTION: { "zh-CN": "感受", "en-SG": "Feeling" },
  BELIEF: { "zh-CN": "信念", "en-SG": "Belief" },
  ACTION: { "zh-CN": "行动", "en-SG": "Action" },
  NEED: { "zh-CN": "需要", "en-SG": "Need" },
  WORRY: { "zh-CN": "担忧", "en-SG": "Worry" }
};

function fragmentLabel(fragment: ThoughtFragmentRow, locale: Locale) {
  return FRAGMENT_LABELS[fragment.fragmentType.toUpperCase()]?.[locale]
    ?? (locale === "en-SG" ? "Reflection" : "想法");
}

function sameWords(left: string | null | undefined, right: string | null | undefined) {
  const normalize = (value: string | null | undefined) => (value ?? "").replace(/\s+/g, "").trim();
  return Boolean(normalize(left)) && normalize(left) === normalize(right);
}

function EditableField({ label, value, editLabel, onSave, busy, t }: {
  label: string; value: string; editLabel: string; onSave: (value: string) => void; busy: boolean;
  t: typeof COPY["zh-CN"];
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  if (!editing) {
    return <div className="row gap-sm" style={{ alignItems: "center" }}>
      <span>{value || "—"}</span>
      <button type="button" className="muted" style={{ fontSize: ".82rem" }}
        onClick={() => { setDraft(value); setEditing(true); }}>{t.edit}</button>
    </div>;
  }
  return <div className="field-group">
    <input type="text" aria-label={editLabel} value={draft} onChange={event => setDraft(event.target.value)} style={{ width: "100%" }} />
    <div className="row gap-sm mt-1">
      <AsyncButton busy={busy} onClick={() => { onSave(draft); setEditing(false); }}>{t.save}</AsyncButton>
      <button type="button" onClick={() => setEditing(false)}>{t.cancel}</button>
    </div>
  </div>;
}

export function DailyRecordSection({ records, detail, index, acceptBusy, editBusy, onAccept, onEditField, onSelectIndex, locale = "zh-CN" }: {
  records: DailyRecordEntry[]; detail: DailyRecordDetail | null; index: number;
  acceptBusy: boolean; editBusy: "theme" | "event" | null;
  onAccept: () => void; onEditField: (field: "theme" | "event", value: string) => void;
  onSelectIndex: (index: number) => void; locale?: Locale;
}) {
  const t = COPY[locale];
  const current = records[index] ?? null;
  const isLatest = index === 0;
  const showRich = isLatest && Boolean(detail);
  // Relationship cues ride along on the day's own detail VO (scoped to its memory card). Fetching
  // /api/relation/list here instead would show the user's all-time mentions as if they were today's.
  // `?? []` keeps a cached/older bundle talking to a server that predates the field from crashing.
  const relations = showRich ? detail!.relations ?? [] : [];

  // Editable values come from the actual tb_daily_record row. The richer detail is read-only and
  // can lag behind that row, so it must never overwrite a successful edit on screen.
  const theme = current?.theme || (showRich ? detail!.theme : null);
  const understanding = current?.cognitiveSummary
    || (showRich ? detail!.mainMemory?.summary : null)
    || current?.eventSummary;
  const proposedAuroraNote = showRich ? detail!.auroraSummary : current?.auroraSummary;
  const repeatsUserText = sameWords(proposedAuroraNote, current?.eventSummary)
    || sameWords(proposedAuroraNote, detail?.mainMemory?.summary)
    || Boolean(detail?.fragments.some(fragment => sameWords(proposedAuroraNote, fragment.rawExcerpt)));
  const auroraNote = repeatsUserText ? null : proposedAuroraNote;
  const weather = emotionWeatherPresentation(
    showRich && detail!.emotions.length > 0 ? detail!.emotions[0].weatherType : current?.emotionWeather,
    locale
  );

  return <section className="daily-record-section" aria-label={t.heading}>
    <div className="flex-between" style={{ flexWrap: "wrap", gap: 12 }}>
      <div>
        <span className="route-hint">{t.routeHint}</span>
        <h2>{t.heading}</h2>
        <p className="muted">{t.intro}</p>
      </div>
      {current && <div className="row gap-sm">
        <AsyncButton busy={acceptBusy} onClick={onAccept}>{t.accept}</AsyncButton>
      </div>}
    </div>

    {!current
      ? <p className="empty">{t.emptyToday}<br /><span className="muted">{t.emptySummary}</span></p>
      : <>
          <div className="row gap-sm mb-2">
            <button type="button" disabled={index >= records.length - 1} onClick={() => onSelectIndex(index + 1)}>{t.previous}</button>
            <span className="muted" style={{ fontSize: ".88rem" }}>{index === 0 ? t.today : current.recordDate.slice(0, 10)}</span>
            <button type="button" disabled={index === 0} onClick={() => onSelectIndex(index - 1)}>{t.next}</button>
          </div>

          <div className="grid mb-2">
            <article className="panel">
              <div className="flex-between">
                <div>
                  <h3>{t.themeHeading}</h3>
                  <EditableField label={t.themeHeading} value={theme || ""} editLabel={t.editThemeLabel} busy={editBusy === "theme"}
                    onSave={value => onEditField("theme", value)} t={t} />
                </div>
                <span className="weather-icon" aria-label={weather.label}>{weather.icon}</span>
              </div>
            </article>
            <article className="panel">
              <h3>{t.weatherHeading}</h3>
              {showRich && detail!.emotions.length > 0
                ? <div className="timeline">
                    {detail!.emotions.map(e => <div className="timeline-item" key={e.id}>
                      <strong>{e.emotionName || e.weatherType}</strong>
                      <p>{t.intensityLabel} {e.emotionScore ?? 0}{e.triggerScene ? ` · ${e.triggerScene}` : ""}</p>
                    </div>)}
                  </div>
                : <p className="empty">{locale === "en-SG" ? "No emotion trace yet." : "还没有情绪轨迹。"}</p>}
            </article>
          </div>

          <section className="panel mb-2">
            <div className="flex-between mb-1">
              <h3>{t.eventHeading}</h3>
              <EditableField label="" value={understanding || ""} editLabel={t.editEventLabel} busy={editBusy === "event"}
                onSave={value => onEditField("event", value)} t={t} />
            </div>
          </section>

          {showRich && <section className="panel mb-2">
            <h3>{t.fragmentsHeading}</h3>
            <p className="muted mb-1">{t.fragmentsIntro}</p>
            {detail!.fragments.length === 0
              ? <p className="empty">{t.emptyFragments}</p>
              : <div className="grid">
                  {detail!.fragments.map(f => <article className="card" key={f.id}>
                    <strong>{fragmentLabel(f, locale)}</strong>
                    <p>{f.rawExcerpt || ""}</p>
                    {f.aiAnalysis && !sameWords(f.aiAnalysis, f.rawExcerpt) && !sameWords(f.aiAnalysis, f.reframeText)
                      && <p className="muted"><strong>{t.analysisLabel}：</strong>{f.aiAnalysis}</p>}
                    {f.reframeText && <p className="gold">{f.reframeText}</p>}
                  </article>)}
                </div>}
          </section>}

          <div className="grid mb-2">
            <article className="panel">
              <h3>{t.relationsHeading}</h3>
              {showRich && relations.length > 0
                ? <div className="timeline">
                    {relations.slice(0, 4).map(relation => <div className="timeline-item" key={relation.id}>
                      <strong>{relation.relationLabel}</strong>
                      {(relation.triggerSummary || relation.boundaryHint)
                        && <p>{relation.triggerSummary || relation.boundaryHint}</p>}
                    </div>)}
                  </div>
                : <p className="empty">{t.emptyRelations}</p>}
            </article>
            <article className="panel">
              <h3>{t.todosHeading}</h3>
              {showRich && detail!.todos.length > 0
                ? <div className="timeline">
                    {detail!.todos.map(td => <div className="timeline-item" key={td.id}>
                      <strong>{td.taskName}</strong><p>{td.description || ""}</p>
                    </div>)}
                  </div>
                : <p className="empty">{t.emptyTodos}</p>}
            </article>
          </div>

          <section className="panel mb-2">
            <h3>{t.auroraHeading}</h3>
            <p className="muted" style={{ lineHeight: 1.8 }}>{auroraNote || t.emptyAurora}</p>
          </section>
        </>}
  </section>;
}

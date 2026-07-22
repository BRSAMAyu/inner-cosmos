import { useState } from "react";
import type { DailyRecordDetail, DailyRecordEntry } from "../api";
import type { Locale } from "../i18n";
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
  emptyFragments: string; emptyToday: string; intensityLabel: string;
}> = {
  "zh-CN": {
    routeHint: "我被整理清楚了", heading: "今日记录卡", intro: "它不是诊断，也不是给你贴标签。它只是在帮你把今天摊平。",
    accept: "接受并保存", viewStarfield: "查看星图", previous: "前一天", next: "后一天", today: "今天",
    themeHeading: "今日主题", edit: "编辑", weatherHeading: "情绪天气", eventHeading: "事件概览",
    fragmentsHeading: "认知碎片", fragmentsIntro: "每一段想法都被温柔地拆解了。", relationsHeading: "关系线索",
    todosHeading: "待办线索", auroraHeading: "Aurora 观察记录", save: "保存", cancel: "取消",
    editThemeLabel: "编辑主题", editEventLabel: "编辑事件", emptyTheme: "今天还没有记录",
    emptySummary: "和 Aurora 聊一次天后，这里会出现你的今日记录。", emptyRelations: "今天没有特别的关系线索。",
    emptyTodos: "今天没有强行生成待办。", emptyFragments: "对话结束后会出现认知碎片。",
    emptyToday: "今天还没有记录", intensityLabel: "强度"
  },
  "en-SG": {
    routeHint: "Sorted out, clearly", heading: "Today's Record Card", intro: "It isn't a diagnosis, and it doesn't label you. It just helps flatten today out.",
    accept: "Accept and save", viewStarfield: "View starfield", previous: "Previous day", next: "Next day", today: "Today",
    themeHeading: "Today's theme", edit: "Edit", weatherHeading: "Emotion weather", eventHeading: "Event overview",
    fragmentsHeading: "Cognitive fragments", fragmentsIntro: "Every thought was gently taken apart.", relationsHeading: "Relationship clues",
    todosHeading: "Todo clues", auroraHeading: "Aurora's observation", save: "Save", cancel: "Cancel",
    editThemeLabel: "Edit theme", editEventLabel: "Edit event", emptyTheme: "No record yet today",
    emptySummary: "After a chat with Aurora, your record for today will appear here.", emptyRelations: "No particular relationship clues today.",
    emptyTodos: "No todos were forced into existence today.", emptyFragments: "Cognitive fragments appear after a conversation ends.",
    emptyToday: "No record yet today", intensityLabel: "Intensity"
  }
};

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

  const theme = showRich ? (detail!.theme || current?.theme) : current?.theme;
  const eventSummary = showRich ? (detail!.mainMemory?.summary || current?.eventSummary) : current?.eventSummary;
  const auroraNote = showRich ? detail!.auroraSummary : current?.auroraSummary;
  const weatherIcon = showRich && detail!.emotions.length > 0 ? "🌤️" : (current?.emotionWeather ? "🌤️" : "☁️");

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
                <span className="weather-icon">{weatherIcon}</span>
              </div>
              <p className="gold mt-1">{auroraNote || ""}</p>
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
              <EditableField label="" value={eventSummary || ""} editLabel={t.editEventLabel} busy={editBusy === "event"}
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
                    <strong>{f.fragmentType}</strong>
                    <p>{f.rawExcerpt || ""}</p>
                    {f.reframeText && <p className="gold">{f.reframeText}</p>}
                  </article>)}
                </div>}
          </section>}

          <div className="grid mb-2">
            <article className="panel">
              <h3>{t.relationsHeading}</h3>
              <p className="empty">{t.emptyRelations}</p>
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
            <p className="muted" style={{ lineHeight: 1.8 }}>{auroraNote || ""}</p>
          </section>
        </>}
  </section>;
}

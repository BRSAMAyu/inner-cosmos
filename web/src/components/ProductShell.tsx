import { AppearanceToggle } from "./AppearanceToggle";
import type { Locale } from "../i18n";

export type ProductSpace = "aurora" | "cosmos" | "resonance" | "letters" | "me";

export const productSpaces: Array<[ProductSpace, string, string]> = [
  ["aurora", "今天", "Aurora"], ["cosmos", "内宇宙", "记忆与自我理解"],
  ["resonance", "共鸣", "共鸣体与相遇"], ["letters", "连接", "慢信与关系"],
  ["me", "我的", "控制与边界"]
];

export function initialProductSpace(search = window.location.search): ProductSpace {
  const value = new URLSearchParams(search).get("space");
  return productSpaces.some(([space]) => space === value) ? value as ProductSpace : "aurora";
}

// Real, stable, shareable route per space (see docs/tracks/TRACK-B-COMPLETE-EXPERIENCE.md
// section 5's suggested route model). "letters" keeps its target's Chinese-facing product
// name ("连接") but its path follows the spec's `/connections/letters` slot.
const spacePaths: Record<ProductSpace, string> = {
  aurora: "/aurora",
  cosmos: "/cosmos",
  resonance: "/resonance",
  letters: "/connections/letters",
  me: "/me"
};

export function spacePath(space: ProductSpace): string {
  return spacePaths[space];
}

// Resolves the active space from a router pathname. Matches nested sub-routes too
// (e.g. "/cosmos/starfield") so a future per-space route split (B1's next slice) does not
// need to touch this resolver -- only add real <Route> elements underneath.
export function productSpaceFromPath(pathname: string): ProductSpace {
  const match = productSpaces.find(([space]) => {
    const base = spacePaths[space];
    return pathname === base || pathname.startsWith(`${base}/`);
  });
  return match ? match[0] : "aurora";
}

// A parsed nested resource deep link within a space, e.g. "/resonance/capsule/42" ->
// { space: "resonance", resource: "capsule", id: 42 }. This is what makes a specific capsule,
// letter thread or portrait dimension shareable and back/forward-correct, on top of the existing
// per-space routing. `resource`/`id` are null for a bare space path.
export type ProductResource = { space: ProductSpace; resource: string | null; id: number | null };

export function resourceFromPath(pathname: string): ProductResource {
  const space = productSpaceFromPath(pathname);
  const base = spacePaths[space];
  const rest = pathname === base ? ""
    : pathname.startsWith(`${base}/`) ? pathname.slice(base.length + 1) : "";
  const [resource = "", rawId = ""] = rest.split("/");
  const id = /^\d+$/.test(rawId) ? Number(rawId) : null;
  return { space, resource: resource || null, id };
}

/** Shareable deep link to one capsule inside the resonance space. */
export function capsulePath(id: number): string {
  return `${spacePaths.resonance}/capsule/${id}`;
}

/** Shareable deep link to one slow-letter thread inside the connections space. */
export function letterThreadPath(id: number): string {
  return `${spacePaths.letters}/thread/${id}`;
}

export function ProductShellNavigation({ active, onNavigate }: { active: ProductSpace; onNavigate: (space: ProductSpace) => void }) {
  return <nav className="app-shell-nav" aria-label="Inner Cosmos 五个空间">
    <div className="app-mark"><span aria-hidden="true">✦</span><strong>Inner Cosmos</strong></div>
    <div className="space-tabs">{productSpaces.map(([value, label, description]) =>
      <button type="button" key={value} className={active === value ? "active" : ""}
        aria-current={active === value ? "page" : undefined} onClick={() => onNavigate(value)}>
        <strong>{label}</strong><small>{description}</small>
      </button>)}</div>
  </nav>;
}

const ME_COPY: Record<Locale, {
  ariaLabel: string; eyebrow: string; heading: string; intro: string;
  device: string; deviceNative: string; deviceWeb: string; online: string; offline: string;
  returns: string; returnsValue: (n: number) => string; returnsAction: string;
  understanding: string; understandingValue: (n: number) => string; understandingAction: string;
  resonance: string; resonanceValue: (pub: number, friends: number) => string; resonanceAction: string;
  push: string; mic: string; logout: string;
}> = {
  "zh-CN": {
    ariaLabel: "我的控制与边界", eyebrow: "ME · CONTROL & BOUNDARIES", heading: "由你决定，Aurora 怎样参与。",
    intro: "身份、设备权限、主动回来和数据边界都集中在这里。关闭一项能力不会删除你的创新体验，也不会暗中改写已有记忆。",
    device: "登录与设备", deviceNative: "OIDC + PKCE · 安全存储", deviceWeb: "安全 Web Session",
    online: "当前在线", offline: "当前离线，时间线会在恢复后续接",
    returns: "主动回来", returnsValue: n => `${n} 个有效约定`, returnsAction: "查看和调整",
    understanding: "理解与记忆", understandingValue: n => `${n} 条已确认理解`, understandingAction: "纠正、追溯或撤回",
    resonance: "共鸣与连接", resonanceValue: (p, f) => `${p} 个公开共鸣体 · ${f} 个双向连接`, resonanceAction: "管理授权",
    push: "管理通知权限", mic: "管理麦克风权限", logout: "安全退出这台设备"
  },
  "en-SG": {
    ariaLabel: "My controls and boundaries", eyebrow: "ME · CONTROL & BOUNDARIES", heading: "You decide how Aurora takes part.",
    intro: "Identity, device permissions, proactive returns and data boundaries all live here. Turning a capability off never deletes your experience, nor quietly rewrites existing memories.",
    device: "Login & device", deviceNative: "OIDC + PKCE · secure storage", deviceWeb: "Secure web session",
    online: "Online now", offline: "Offline now — your timeline resumes when you reconnect",
    returns: "Proactive returns", returnsValue: n => `${n} active plan${n === 1 ? "" : "s"}`, returnsAction: "View and adjust",
    understanding: "Understanding & memory", understandingValue: n => `${n} confirmed understanding${n === 1 ? "" : "s"}`, understandingAction: "Correct, trace or withdraw",
    resonance: "Resonance & connection", resonanceValue: (p, f) => `${p} public capsule${p === 1 ? "" : "s"} · ${f} mutual connection${f === 1 ? "" : "s"}`, resonanceAction: "Manage authorization",
    push: "Manage notifications", mic: "Manage microphone", logout: "Sign out of this device"
  }
};

export function MeSpace({ native, connected, wakeIntentCount, activeClaimCount, publicCapsuleCount,
  friendCount, onNavigate, onRequestPush, onRequestMicrophone, onLogout, locale = "zh-CN" }: {
  native: boolean; connected: boolean; wakeIntentCount: number; activeClaimCount: number;
  publicCapsuleCount: number; friendCount: number; onNavigate: (space: ProductSpace) => void;
  onRequestPush: () => void; onRequestMicrophone: () => void; onLogout: () => void; locale?: Locale;
}) {
  const t = ME_COPY[locale];
  return <section className="controls-space" aria-label={t.ariaLabel}>
    <span className="eyebrow">{t.eyebrow}</span><h1>{t.heading}</h1>
    <p>{t.intro}</p>
    <div className="control-grid">
      <article><strong>{t.device}</strong><span>{native ? t.deviceNative : t.deviceWeb}</span><small>{connected ? t.online : t.offline}</small></article>
      <article><strong>{t.returns}</strong><span>{t.returnsValue(wakeIntentCount)}</span><button type="button" onClick={() => onNavigate("aurora")}>{t.returnsAction}</button></article>
      <article><strong>{t.understanding}</strong><span>{t.understandingValue(activeClaimCount)}</span><button type="button" onClick={() => onNavigate("cosmos")}>{t.understandingAction}</button></article>
      <article><strong>{t.resonance}</strong><span>{t.resonanceValue(publicCapsuleCount, friendCount)}</span><button type="button" onClick={() => onNavigate("resonance")}>{t.resonanceAction}</button></article>
    </div>
    <AppearanceToggle />
    {native && <div className="mobile-actions"><button type="button" onClick={onRequestPush}>{t.push}</button><button type="button" onClick={onRequestMicrophone}>{t.mic}</button></div>}
    <button type="button" className="danger-quiet" onClick={onLogout}>{t.logout}</button>
  </section>;
}

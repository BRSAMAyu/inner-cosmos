import { AppearanceToggle } from "./AppearanceToggle";

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

export function MeSpace({ native, connected, wakeIntentCount, activeClaimCount, publicCapsuleCount,
  friendCount, onNavigate, onRequestPush, onRequestMicrophone, onLogout }: {
  native: boolean; connected: boolean; wakeIntentCount: number; activeClaimCount: number;
  publicCapsuleCount: number; friendCount: number; onNavigate: (space: ProductSpace) => void;
  onRequestPush: () => void; onRequestMicrophone: () => void; onLogout: () => void;
}) {
  return <section className="controls-space" aria-label="我的控制与边界">
    <span className="eyebrow">ME · CONTROL &amp; BOUNDARIES</span><h1>由你决定，Aurora 怎样参与。</h1>
    <p>身份、设备权限、主动回来和数据边界都集中在这里。关闭一项能力不会删除你的创新体验，也不会暗中改写已有记忆。</p>
    <div className="control-grid">
      <article><strong>登录与设备</strong><span>{native ? "OIDC + PKCE · 安全存储" : "安全 Web Session"}</span><small>{connected ? "当前在线" : "当前离线，时间线会在恢复后续接"}</small></article>
      <article><strong>主动回来</strong><span>{wakeIntentCount} 个有效约定</span><button type="button" onClick={() => onNavigate("aurora")}>查看和调整</button></article>
      <article><strong>理解与记忆</strong><span>{activeClaimCount} 条已确认理解</span><button type="button" onClick={() => onNavigate("cosmos")}>纠正、追溯或撤回</button></article>
      <article><strong>共鸣与连接</strong><span>{publicCapsuleCount} 个公开共鸣体 · {friendCount} 个双向连接</span><button type="button" onClick={() => onNavigate("resonance")}>管理授权</button></article>
    </div>
    <AppearanceToggle />
    {native && <div className="mobile-actions"><button type="button" onClick={onRequestPush}>管理通知权限</button><button type="button" onClick={onRequestMicrophone}>管理麦克风权限</button></div>}
    <button type="button" className="danger-quiet" onClick={onLogout}>安全退出这台设备</button>
  </section>;
}

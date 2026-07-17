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

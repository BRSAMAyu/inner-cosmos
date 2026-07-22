import type { AdminUserRow } from "../../api";
import type { Locale } from "../../i18n";

// Port of admin.html's "用户" tab (loadUsers()/#userList). UserProfileVO does not carry
// status/email/createdAt (see api.ts's AdminUserRow) so, unlike the legacy static page, this only
// renders fields the backend actually returns.
const COPY: Record<Locale, {
  aria: string; empty: string; bioLabel: string; reachabilityLabel: string;
}> = {
  "zh-CN": { aria: "用户列表", empty: "没有用户数据", bioLabel: "简介", reachabilityLabel: "可触达状态" },
  "en-SG": { aria: "User list", empty: "No user data", bioLabel: "Bio", reachabilityLabel: "Reachability" }
};

export function AdminUsersTab({ users, locale = "zh-CN" }: { users: AdminUserRow[]; locale?: Locale }) {
  const t = COPY[locale];
  return <div className="admin-grid" aria-label={t.aria}>
    {users.length === 0 ? <div className="admin-empty">{t.empty}</div> : users.map(u => <article className="admin-card" key={u.id}>
      <div className="admin-card-head">
        <strong>{u.nickname || u.username || "用户"}</strong>
        <span className="admin-pill">{u.role || "USER"}</span>
      </div>
      <p className="admin-muted">@{u.username}</p>
      {u.bio && <p className="admin-muted">{t.bioLabel}: {u.bio}</p>}
      {u.socialReachabilityStatus && <p className="admin-muted">{t.reachabilityLabel}: {u.socialReachabilityStatus}</p>}
    </article>)}
  </div>;
}

const IC = {
  soundEnabled: JSON.parse(localStorage.getItem("ic_sound") || "false"),
  themeMode: localStorage.getItem("ic_theme_mode") || "auto",  // "auto" | "light" | "dark"
  _darkMedia: null,
  _csrf: null,

  async ensureCsrfToken(force = false) {
    if (IC._csrf && !force) return IC._csrf;
    const res = await fetch("/api/auth/csrf", { credentials: "include" });
    const json = await res.json();
    if (!res.ok || !json.success || !json.data?.token || !json.data?.headerName) {
      throw new Error("Unable to establish request protection.");
    }
    IC._csrf = { token: json.data.token, headerName: json.data.headerName };
    return IC._csrf;
  },

  async secureFetch(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
      const csrf = await IC.ensureCsrfToken();
      headers.set(csrf.headerName, csrf.token);
    }
    return fetch(path, { ...options, method, credentials: "include", headers });
  },

  async api(path, options = {}, retries = 1) {
    const method = (options.method || "GET").toUpperCase();
    // M-025: only retry idempotent GETs — never re-send POSTs (letter/capsule/account) on a
    // transient timeout, which would double side-effects.
    const maxRetries = method === "GET" ? retries : 0;
    for (let attempt = 0; ; attempt++) {
      try {
        const headers = new Headers(options.headers || {});
        if (!headers.has("Content-Type")) headers.set("Content-Type", "application/json; charset=utf-8");
        const res = await IC.secureFetch(path, { ...options, headers });
        // M-025: an expired/invalid session (Spring Security 401/403 on /api/**) should send the
        // user to the login page instead of stranding them with a cryptic toast. (Don't redirect
        // from login/register/index themselves.)
        if (res.status === 401 || res.status === 403) {
          const p = location.pathname;
          if (!p.endsWith("/login.html") && !p.endsWith("/register.html") && !p.endsWith("/index.html")) {
            location.href = "/pages/login.html";
          }
        }
        let json;
        try {
          json = await res.json();
        } catch (e) {
          json = { success: false, message: "连接暂时没有回应，请稍后再试。" };
        }
        if (!json.success && json.message) IC.toast(json.message, "warn");
        return json;
      } catch (error) {
        if (attempt < maxRetries) {
          await new Promise(r => setTimeout(r, 800 * (attempt + 1)));
          continue;
        }
        IC.toast("连接暂时没有回应，请稍后再试。", "warn");
        return { success: false, message: error.message };
      }
    }
  },

  nav() {
    return [
      ["/pages/dashboard.html", "核心"],
      ["/pages/aurora-chat.html", "Aurora"],
      ["/pages/portrait.html", "画像"],
      ["/pages/corrections.html", "我的更正"],
      ["/pages/heart-diary.html", "日记"],
      ["/pages/memory-starfield.html", "星图"],
      ["/pages/todo.html", "待办"],
      ["/pages/echo-plaza.html", "星海"],
      ["/pages/social.html", "好友"],
      ["/pages/slow-letter.html", "慢信"],
      ["/pages/inbox.html", "信箱"],
      ["/pages/emotion-timeline.html", "情绪"],
      ["/pages/themes.html", "主题"],
      ["/pages/relations.html", "关系"],
      ["/pages/beliefs.html", "信念"],
      ["/pages/safety-harbor.html", "避风港"],
      ["/pages/token-usage.html", "用量"],
      ["/pages/settings.html", "设置"],
      ["/pages/admin.html", "管理", true],
      ["/pages/abtest-report.html", "实验", true],
      ["/pages/prompt-versions.html", "Prompt", true],
      ["/pages/model-config.html", "模型", true],
      ["/pages/ai-log.html", "AI 日志", true],
      ["/pages/ai-dev-history.html", "开发记录", true]
    ].map(([href, label, adminOnly]) => `<a href="${href}" data-route="${href}"${adminOnly ? ' data-admin-nav="true"' : ''}>${IC.esc(label)}</a>`).join("");
  },

  mountShell(activeLabel = "") {
    IC.initSystemThemeListener();
    IC.ensureFonts(); // M-029: app-wide typography (was loaded only by index.html)
    IC.applyTimeClass();
    IC.applyTheme();
    IC.ensureFlowStage();
    IC.ensureAmbientSystems();
    const topbar = document.querySelector("[data-topbar]");
    if (topbar) {
      topbar.setAttribute("role", "banner");
      const themeIcon = IC.themeMode === "auto" ? "◐" : (IC.darkTheme ? "☾" : "☼");
      const themeTip = IC.themeMode === "auto" ? "主题：自动跟随系统" : (IC.darkTheme ? "主题：星空（深色）" : "主题：莫兰迪（浅色）");
      topbar.innerHTML = `
        <a class="brand" href="/pages/dashboard.html" aria-label="Inner Cosmos · 返回首页"><span class="brand-mark" aria-hidden="true"></span><span>Inner Cosmos</span></a>
        <nav class="nav" aria-label="主导航">${IC.nav()}</nav>
        <span class="topbar-user" data-current-user hidden style="font-size:.8125rem;color:var(--muted);max-width:9em;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:0 4px"></span>
        <button class="icon-button" type="button" aria-label="${themeTip}" title="${themeTip}" onclick="IC.toggleTheme()">${themeIcon}</button>
      `;
      topbar.querySelectorAll("a").forEach(a => {
        if (a.textContent === activeLabel || location.pathname === new URL(a.href).pathname) a.classList.add("active");
      });
    }
    IC.ensureSkipLink();
    IC.ensureToastRoot();
    IC.ensureAmbientControls();
    IC.attachVoiceInputs();
    IC.bindFlowNavigation();
    IC.enterPage();
    /* Page-level loading mask: hides automatically after 3.5s; pages can call IC.hideLoading() earlier. */
    IC.showLoading();
    /* Asynchronously load user profile so P1-3 cross-page state (auroraName, etc.) renders. */
    IC.loadUserProfile().then(() => {
      IC.applyAuroraNameToDom();
      IC.applyAdminNav();
      IC.applyCurrentUserToDom();
      IC.ensureProactiveStream();
    }).catch(() => {});
  },

  /* App-wide proactive (SSE) subscription. Previously only aurora-chat.html subscribed,
     so Aurora's proactive toasts were invisible everywhere else. Mounting it here makes
     any logged-in page able to surface a proactive greeting. Fully fault-tolerant:
     no EventSource support / not logged in / already started -> no-op, never throws. */
  ensureProactiveStream() {
    try {
      if (typeof EventSource === "undefined") return; // unsupported browser
      // Never start on public/unauthenticated entry pages.
      const path = location.pathname;
      if (path.endsWith("/login.html") || path.endsWith("/register.html") || path.endsWith("/index.html")) return;
      // Only when we actually have a loaded profile (i.e. a live session).
      if (!IC.userProfile) return;
      if (window.__icProactiveStarted) return;

      const begin = () => {
        try {
          if (window.__icProactiveStarted) return;
          if (window.ICProactive && typeof window.ICProactive.start === "function") {
            window.ICProactive.start();
            window.__icProactiveStarted = true;
          }
        } catch (e) { /* never let proactive wiring break the page */ }
      };

      if (window.ICProactive) { begin(); return; }
      // Avoid double-injection if another mount already queued the script.
      if (window.__icProactiveScriptLoading) return;
      window.__icProactiveScriptLoading = true;
      const s = document.createElement("script");
      s.src = "/js/ic-proactive-client.js";
      s.onload = begin;
      s.onerror = () => { window.__icProactiveScriptLoading = false; };
      document.head.appendChild(s);
    } catch (e) { /* fault-tolerant: ignore */ }
  },

  ensureAmbientSystems() {
    const scripts = [
      ["/js/time-system.js", "ICTimeSystem"],
      ["/js/weather-system.js", "ICWeather"],
      ["/js/audio-system.js", "ICAudio"]
    ];
    window.__icAmbientScriptLoading = window.__icAmbientScriptLoading || {};
    scripts.forEach(([src, globalName]) => {
      if (window[globalName] || window.__icAmbientScriptLoading[src]) return;
      window.__icAmbientScriptLoading[src] = true;
      const script = document.createElement("script");
      script.src = src;
      script.onload = () => {
        const system = window[globalName];
        if (system && typeof system.init === "function") {
          system.init();
        }
      };
      document.head.appendChild(script);
    });
  },

  ensureFlowStage() {
    if (document.querySelector(".cosmos-flow-stage")) return;
    const stage = document.createElement("div");
    stage.className = "cosmos-flow-stage";
    stage.setAttribute("aria-hidden", "true");
    stage.innerHTML = `
      <div class="flow-plane flow-plane-a"></div>
      <div class="flow-plane flow-plane-b"></div>
      <div class="flow-plane flow-plane-c"></div>
    `;
    document.body.prepend(stage);
  },

  /* M-029: app-wide calligraphic typography (Noto Serif SC / Cormorant / LXGW WenKai). Was
     loaded only by index.html; mounting once here applies it to all pages. Idempotent. */
  ensureFonts() {
    if (document.getElementById("ic-fonts")) return;
    const frag = document.createDocumentFragment();
    [["https://fonts.googleapis.com", false], ["https://fonts.gstatic.com", true]].forEach(([href, cross]) => {
      const l = document.createElement("link");
      l.rel = "preconnect"; l.href = href; if (cross) l.crossOrigin = "anonymous";
      frag.appendChild(l);
    });
    const g = document.createElement("link");
    g.rel = "stylesheet";
    g.href = "https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@300;400;600&family=Cormorant+Garamond:wght@300;400;600&family=Cormorant+It:wght@400&display=swap";
    frag.appendChild(g);
    const mono = document.createElement("link");
    mono.rel = "stylesheet";
    mono.href = "https://cdn.jsdelivr.net/npm/jetbrains-mono@2.0.0/fonts/jetbrains-mono.css";
    frag.appendChild(mono);
    const s = document.createElement("style");
    s.id = "ic-fonts";
    s.textContent = "@font-face{font-family:'LXGW WenKai';src:local('LXGW WenKai'),local('STKaiti'),local('KaiTi');font-display:swap;}";
    frag.appendChild(s);
    document.head.appendChild(frag);
  },

  enterPage() {
    document.body.classList.add("flow-page-enter");
    requestAnimationFrame(() => document.body.classList.add("flow-page-ready"));
  },

  bindFlowNavigation() {
    document.querySelectorAll("a[href^='/pages/']").forEach(link => {
      if (link.dataset.flowBound === "true") return;
      link.dataset.flowBound = "true";
      link.addEventListener("click", event => {
        if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        const target = new URL(link.href, location.origin);
        if (target.origin !== location.origin || target.pathname === location.pathname) return;
        event.preventDefault();
        document.body.classList.add("flow-page-leave");
        setTimeout(() => { location.href = target.href; }, 220);
      });
    });
  },

  /* ════════ Theme (P2-1: auto / light / dark) ════════ */
  applyTheme() {
    let effectiveDark = false;
    if (IC.themeMode === "dark") effectiveDark = true;
    else if (IC.themeMode === "light") effectiveDark = false;
    else effectiveDark = !!(IC._darkMedia && IC._darkMedia.matches);
    IC.darkTheme = effectiveDark;
    document.body.classList.toggle("dark-star", effectiveDark);
    document.body.dataset.themeMode = IC.themeMode;
  },

  toggleTheme() {
    const next = IC.themeMode === "auto" ? "light" : (IC.themeMode === "light" ? "dark" : "auto");
    IC.themeMode = next;
    localStorage.setItem("ic_theme_mode", next);
    IC.applyTheme();
    const label = next === "auto" ? "已切回自动（跟随系统）" : (next === "light" ? "白天莫兰迪已开启" : "星空主题已开启");
    IC.toast(label);
  },

  initSystemThemeListener() {
    if (IC._darkMedia) return;
    IC._darkMedia = window.matchMedia("(prefers-color-scheme: dark)");
    if (IC._darkMedia.addEventListener) {
      IC._darkMedia.addEventListener("change", () => {
        if (IC.themeMode === "auto") IC.applyTheme();
      });
    } else if (IC._darkMedia.addListener) {
      IC._darkMedia.addListener(() => {
        if (IC.themeMode === "auto") IC.applyTheme();
      });
    }
  },

  applyTimeClass() {
    const fixed = localStorage.getItem("ic_fixed_theme");
    if (fixed && localStorage.getItem("ic_visual_auto") === "false") {
      document.body.classList.remove("time-dawn", "time-morning", "time-noon", "time-afternoon", "time-evening", "time-dusk", "time-night", "time-deep-night");
      document.body.classList.add(fixed);
      return;
    }
    const hour = new Date().getHours();
    const names = ["time-dawn", "time-morning", "time-noon", "time-evening", "time-dusk", "time-night", "time-deep-night"];
    document.body.classList.remove(...names);
    let cls = "time-morning";
    if (hour < 5) cls = "time-deep-night";
    else if (hour < 7) cls = "time-dawn";
    else if (hour < 11) cls = "time-morning";
    else if (hour < 15) cls = "time-noon";
    else if (hour < 18) cls = "time-evening";
    else if (hour < 21) cls = "time-dusk";
    else cls = "time-night";
    document.body.classList.add(cls);
  },

  toggleThemeLegacy() {
    /* kept for backward compat (unused) */
  },

  ensureToastRoot() {
    if (!document.getElementById("toastRoot")) {
      const root = document.createElement("div");
      root.id = "toastRoot";
      root.className = "toast-root";
      root.setAttribute("role", "status");      // M-081: announce toasts to assistive tech
      root.setAttribute("aria-live", "polite");
      root.setAttribute("aria-atomic", "true");
      document.body.appendChild(root);
    }
  },

  /* ════════ Skip Link (a11y) ════════
     Ensures a "跳到主内容" link is the first focusable element, targeting the
     page main landmark (#ic-main or #main). Idempotent — safe to call on every page. */
  ensureSkipLink() {
    if (document.querySelector(".ic-skip-link")) return;
    const target = document.getElementById("ic-main") || document.getElementById("main");
    if (!target) return;
    if (!target.id) target.id = "ic-main";
    /* Make main focusable for the skip target without a visible focus ring. */
    if (target.id !== "ic-main") {
      target.id = "ic-main";
    }
    target.setAttribute("tabindex", "-1");
    const link = document.createElement("a");
    link.className = "skip-link ic-skip-link";
    link.href = "#ic-main";
    link.textContent = "跳到主内容";
    document.body.prepend(link);
  },

  toast(message, type = "info") {
    IC.ensureToastRoot();
    const item = document.createElement("div");
    item.className = `toast ${type}`;
    item.textContent = message || "";
    document.getElementById("toastRoot").appendChild(item);
    setTimeout(() => item.remove(), 3200);
  },

  esc(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  },

  /* ════════ Global User Profile Cache (P1-3) ════════ */
  userProfile: null,
  userProfilePromise: null,

  async loadUserProfile(force = false) {
    if (IC.userProfile && !force) return IC.userProfile;
    if (IC.userProfilePromise && !force) return IC.userProfilePromise;
    IC.userProfilePromise = (async () => {
      try {
        const res = await fetch("/api/user/profile", { credentials: "include" });
        const json = await res.json();
        if (json.success) IC.userProfile = json.data || {};
        else IC.userProfile = {};
      } catch (e) {
        IC.userProfile = {};
      }
      IC.userProfilePromise = null;
      return IC.userProfile;
    })();
    return IC.userProfilePromise;
  },

  auroraDisplayName() {
    return (IC.userProfile && IC.userProfile.auroraName) || "Aurora";
  },

  /* Replace text content of all [data-aurora-name] elements. P1-3. */
  applyAuroraNameToDom() {
    const name = IC.auroraDisplayName();
    document.querySelectorAll("[data-aurora-name]").forEach(el => {
      el.textContent = name;
    });
    const subtitle = document.querySelector("[data-aurora-subtitle]");
    if (subtitle) subtitle.textContent = name;
    document.documentElement.style.setProperty("--aurora-name", name);
  },

  /* Show the signed-in user's nickname in the topbar so "logged in" is visible. */
  applyCurrentUserToDom() {
    const p = IC.userProfile || {};
    const name = p.nickname || p.username;
    document.querySelectorAll("[data-current-user]").forEach(el => {
      if (name) {
        el.textContent = name;
        el.title = "已登录：" + name;
        el.hidden = false;
      } else {
        el.textContent = "";
        el.hidden = true;
      }
    });
  },

  /* Reveal admin-only nav entries when the signed-in user is an admin. */
  applyAdminNav() {
    const isAdmin = IC.userProfile && (IC.userProfile.role === "ADMIN" || IC.userProfile.role === "admin");
    document.body.classList.toggle("is-admin", !!isAdmin);
  },

  /* ════════ Loading State (P1-2) ════════
     Default behavior: full-screen mask with 3 breathing dots. Auto-hides after 6s safety. */
  _loadingAutoHide: null,
  showLoading(message) {
    if (document.getElementById("icLoadingMask")) return;
    const mask = document.createElement("div");
    mask.id = "icLoadingMask";
    mask.className = "ic-loading-mask";
    mask.setAttribute("role", "status");
    mask.setAttribute("aria-live", "polite");
    mask.innerHTML = `
      <div class="ic-loading">
        <div class="ic-loading-dots"><span></span><span></span><span></span></div>
        <p class="ic-loading-text">${IC.esc(message || "正在慢慢呈现…")}</p>
      </div>`;
    document.body.appendChild(mask);
    // M-082: let a stuck loading mask be dismissed by clicking it (recovery), in addition to
    // the 3.5s auto-hide.
    mask.title = "点击关闭";
    mask.addEventListener("click", () => IC.hideLoading());
    if (IC._loadingAutoHide) clearTimeout(IC._loadingAutoHide);
    IC._loadingAutoHide = setTimeout(() => IC.hideLoading(), 3500);
  },

  hideLoading() {
    if (IC._loadingAutoHide) { clearTimeout(IC._loadingAutoHide); IC._loadingAutoHide = null; }
    document.getElementById("icLoadingMask")?.remove();
  },

  /* ════════ Safe Modal (P2-3) ════════ */
  showModalText(text, options = {}) {
    const title = options.title ? `<h2 style="margin:0 0 12px">${IC.esc(options.title)}</h2>` : "";
    const action = options.danger
      ? `<button class="modal-btn-cancel" onclick="IC.closeModal()">取消</button>
         <button class="modal-btn-danger" onclick="IC._modalConfirm()">${IC.esc(options.confirmText || "确认")}</button>`
      : `<button class="modal-btn-cancel" onclick="IC.closeModal()">${IC.esc(options.confirmText || "好的")}</button>`;
    IC.showModal(`
      ${title}
      <p class="muted" style="line-height:1.7">${IC.esc(text)}</p>
      <div class="modal-actions">${action}</div>
    `);
    if (options.onConfirm) {
      IC._modalConfirm = () => { IC.closeModal(); options.onConfirm(); };
    }
  },

  empty(text) {
    return `<div class="empty"><div class="empty-star"></div><p class="muted">${IC.esc(text)}</p></div>`;
  },

  async ensureDemoLogin() {
    try {
      const res = await fetch("/api/auth/current", { credentials: "include" });
      const cur = await res.json();
      if (cur.success) return cur;
    } catch (e) {}
    // M-009: do NOT silently auto-log every visitor into the shared demo account — that
    // exposed demo's private memories/letters to anyone who opened the app. Route to the
    // real login page instead. (The "游客体验 Demo" button on login.html still uses
    // demo/demo123 for convenience; index.html remains a public landing.)
    const path = location.pathname;
    if (!path.endsWith("/login.html") && !path.endsWith("/register.html") && !path.endsWith("/index.html")) {
      location.href = "/pages/login.html";
    }
    return { success: false };
  },

  async guestLogin() {
    return IC.api("/api/auth/login", { method: "POST", body: JSON.stringify({ username: "demo", password: "demo123" }) });
  },

  /* Sign the user out: invalidate the server session, clear local login-related state,
     then return to the login page. Always navigates to login even if the call fails. */
  async logout() {
    try {
      await IC.api("/api/auth/logout", { method: "POST" });
    } catch (e) { /* fall through — we still want to leave the session behind */ }
    try {
      IC.userProfile = null;
      IC.userProfilePromise = null;
      ["ic_user", "ic_user_id", "ic_username", "ic_nickname", "ic_role", "ic_logged_in", "ic_session"]
        .forEach(key => localStorage.removeItem(key));
    } catch (e) {}
    location.href = "/pages/login.html";
  },

  formatTime(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
  },

  weatherIcon(type) {
    const key = String(type || "").toUpperCase();
    if (key.includes("RAIN")) return "雨";
    if (key.includes("STORM")) return "雷";
    if (key.includes("SUN") || key.includes("CLEAR")) return "晴";
    if (key.includes("FOG")) return "雾";
    if (key.includes("SNOW")) return "雪";
    return "云";
  },

  greetingByTime() {
    const hour = new Date().getHours();
    if (hour < 5) return "夜很深了，先把自己放轻一点";
    if (hour < 9) return "早安，今天也从一个小入口开始";
    if (hour < 12) return "上午好，看看内心现在亮在哪里";
    if (hour < 14) return "中午好，给自己留一点呼吸";
    if (hour < 18) return "下午好，慢慢把事情整理成形";
    if (hour < 22) return "晚上好，今天有什么还想被听见";
    return "夜里好，适合把心声轻轻放下";
  },

  showModal(html) {
    IC.closeModal();
    /* Remember the element that opened the modal so focus can return on close. */
    IC._modalTrigger = document.activeElement;
    const wrap = document.createElement("div");
    wrap.id = "icModalRoot";
    wrap.className = "modal-root";
    wrap.innerHTML = `<div class="modal-backdrop" onclick="IC.closeModal()"></div><div class="modal-panel" tabindex="-1">${html}</div>`;
    document.body.appendChild(wrap);
    /* Dialog semantics: role + modal, label from the first heading if present. */
    const panel = wrap.querySelector(".modal-panel");
    const heading = panel && panel.querySelector("h1,h2,h3,h4");
    if (panel) {
      panel.setAttribute("role", "dialog");
      panel.setAttribute("aria-modal", "true");
      if (heading) {
        if (!heading.id) heading.id = "ic-modal-title";
        panel.setAttribute("aria-labelledby", heading.id);
      } else {
        panel.setAttribute("aria-label", "对话框");
      }
    }
    /* Move focus into the dialog. Prefer the first focusable control, else the panel. */
    const focusables = panel ? panel.querySelectorAll("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])") : [];
    const firstFocus = focusables && focusables.length ? focusables[0] : panel;
    if (firstFocus) firstFocus.focus({ preventScroll: true });
    /* Bind Esc-to-close once per modal. */
    IC._modalKeyHandler = (e) => { if (e.key === "Escape") { e.preventDefault(); IC.closeModal(); } };
    wrap.addEventListener("keydown", IC._modalKeyHandler);
  },

  closeModal() {
    const wrap = document.getElementById("icModalRoot");
    if (wrap) {
      if (IC._modalKeyHandler) wrap.removeEventListener("keydown", IC._modalKeyHandler);
      wrap.remove();
    }
    IC._modalKeyHandler = null;
    /* Return focus to the trigger if it is still in the document. */
    const trigger = IC._modalTrigger;
    if (trigger && document.body.contains(trigger) && typeof trigger.focus === "function") {
      try { trigger.focus({ preventScroll: true }); } catch (e) {}
    }
    IC._modalTrigger = null;
  },

  ensureAmbientControls() {
    if (document.getElementById("ambientControls")) return;
    const root = document.createElement("div");
    root.id = "ambientControls";
    root.className = "ambient-controls";
    root.innerHTML = `
      <button class="ambient-btn" id="musicToggle" type="button" aria-label="播放或暂停背景音乐" title="播放/暂停音乐" onclick="IC.toggleMusic()">♫</button>
      <button class="ambient-btn" type="button" aria-label="视觉与天气设置" title="视觉与天气" onclick="IC.toggleVisualPanel()">◐</button>
      <div id="visualPanel" class="ambient-panel" style="display:none">
        <strong>视觉流动</strong>
        <label><span>主题模式</span><select id="themeMode" onchange="IC.setThemeMode(this.value)">
          <option value="auto">自然节奏</option><option value="light">始终白昼</option><option value="dark">始终夜色</option>
        </select></label>
        <label><span>固定时段</span><select id="fixedTheme" onchange="IC.setFixedTheme(this.value)">
          <option value="">跟随时间</option><option value="time-dawn">晨曦</option><option value="time-morning">上午</option><option value="time-noon">正午</option><option value="time-afternoon">午后</option><option value="time-dusk">暮色</option><option value="time-night">夜色</option><option value="time-deep-night">深夜</option>
        </select></label>
        <label><span>天气</span><select id="manualWeather" onchange="IC.setManualWeather(this.value)">
          <option value="">真实天气</option><option value="CLEAR">晴</option><option value="CLOUD">云</option><option value="RAIN">雨</option><option value="STORM">雷雨</option><option value="FOG">雾</option><option value="SNOW">雪</option>
        </select></label>
        <label><span>AI 模型偏好</span><select id="aiProviderPreference" onchange="IC.setAiProviderPreference(this.value)">
          <option value="">自动轮换</option><option value="minimax">MiniMax M3</option><option value="mimo">MiMo V2.5</option><option value="glm">GLM 5.1</option>
        </select></label>
        <p id="ambientWeatherText" class="muted"></p>
      </div>
    `;
    document.body.appendChild(root);
    const visualAuto = document.getElementById("visualAuto");
    const fixedTheme = document.getElementById("fixedTheme");
    const manualWeather = document.getElementById("manualWeather");
    const aiProviderPreference = document.getElementById("aiProviderPreference");
    if (visualAuto) visualAuto.checked = localStorage.getItem("ic_visual_auto") !== "false";
    const themeModeEl = document.getElementById("themeMode");
    if (themeModeEl) themeModeEl.value = localStorage.getItem("ic_theme_mode") || "auto";
    if (fixedTheme) fixedTheme.value = localStorage.getItem("ic_fixed_theme") || "";
    if (manualWeather) manualWeather.value = localStorage.getItem("ic_preferred_weather") || "";
    if (aiProviderPreference) aiProviderPreference.value = localStorage.getItem("ic_ai_provider_preference") || "";
    IC.refreshMusicButton();
    window.addEventListener("weatherChanged", e => {
      const w = e.detail?.weather || {};
      const text = `${w.description || e.detail?.type || "天气"}${w.temperature !== undefined ? " · " + w.temperature + "℃" : ""}`;
      const el = document.getElementById("ambientWeatherText");
      if (el) el.textContent = text;
    });
  },

  toggleMusic() {
    if (!window.ICAudio) {
      IC.toast("音乐系统暂不可用", "warn");
      return;
    }
    const muted = window.ICAudio.toggleMute();
    if (!muted) window.ICAudio.playCurrentBGM();
    IC.refreshMusicButton();
  },

  refreshMusicButton() {
    const btn = document.getElementById("musicToggle");
    if (!btn) return;
    const muted = !window.ICAudio || window.ICAudio.isMuted;
    btn.textContent = muted ? "♫" : "❚❚";
    btn.title = muted ? "播放音乐" : "暂停音乐";
  },

  toggleVisualPanel() {
    const panel = document.getElementById("visualPanel");
    if (!panel) return;
    const isOpen = panel.dataset.open === "true" || getComputedStyle(panel).display !== "none";
    panel.dataset.open = isOpen ? "false" : "true";
    panel.style.display = isOpen ? "none" : "grid";
  },

  setVisualAuto(enabled) {
    localStorage.setItem("ic_visual_auto", enabled ? "true" : "false");
    if (enabled) {
      localStorage.removeItem("ic_fixed_theme");
      IC.applyTimeClass();
      if (window.ICTimeSystem) window.ICTimeSystem.updateTime();
      if (window.ICWeather) window.ICWeather.setAutoWeather();
    }
  },

  setThemeMode(mode) {
    localStorage.setItem("ic_theme_mode", mode);
    if (mode === "auto") {
      localStorage.setItem("ic_auto_theme", "true");
      localStorage.removeItem("ic_fixed_theme");
      localStorage.removeItem("ic_visual_auto");
      if (window.ICTimeSystem) window.ICTimeSystem.updateTime();
      if (window.ICWeather) window.ICWeather.setAutoWeather();
      const fixedTheme = document.getElementById("fixedTheme");
      if (fixedTheme) fixedTheme.value = "";
    } else if (mode === "light") {
      localStorage.setItem("ic_auto_theme", "false");
      localStorage.setItem("ic_visual_auto", "false");
      localStorage.removeItem("ic_fixed_theme");
      if (window.ICTimeSystem) window.ICTimeSystem.updateTime();
    } else if (mode === "dark") {
      localStorage.setItem("ic_auto_theme", "false");
      localStorage.setItem("ic_visual_auto", "false");
      localStorage.setItem("ic_fixed_theme", "time-night");
      if (window.ICTimeSystem) window.ICTimeSystem.updateTime();
    }
    IC.toast(mode === "auto" ? "自然节奏模式" : mode === "light" ? "白昼模式" : "夜色模式", "info");
  },

  setFixedTheme(value) {
    if (!value) {
      localStorage.removeItem("ic_fixed_theme");
      localStorage.setItem("ic_theme_mode", "auto");
      const themeModeEl = document.getElementById("themeMode");
      if (themeModeEl) themeModeEl.value = "auto";
      IC.applyTimeClass();
      if (window.ICTimeSystem) window.ICTimeSystem.updateTime();
      return;
    }
    localStorage.setItem("ic_visual_auto", "false");
    localStorage.setItem("ic_fixed_theme", value);
    localStorage.setItem("ic_theme_mode", "auto");
    const themeModeEl = document.getElementById("themeMode");
    if (themeModeEl) themeModeEl.value = "auto";
    const visualAuto = document.getElementById("visualAuto");
    if (visualAuto) visualAuto.checked = false;
    document.body.classList.remove("time-dawn", "time-morning", "time-noon", "time-afternoon", "time-evening", "time-dusk", "time-night", "time-deep-night");
    document.body.classList.add(value);
    if (window.ICTimeSystem) window.ICTimeSystem.currentTimeState.timeClass = value;
  },

  setManualWeather(value) {
    if (!window.ICWeather) {
      IC.ensureAmbientSystems();
      setTimeout(() => IC.setManualWeather(value), 350);
      return;
    }
    if (!value) window.ICWeather.setAutoWeather();
    else window.ICWeather.setManualWeather(value);
  },

  setAiProviderPreference(value) {
    if (value) {
      localStorage.setItem("ic_ai_provider_preference", value);
      IC.toast(`AI 优先使用 ${value.toUpperCase()}，异常时自动切换备选模型。`);
    } else {
      localStorage.removeItem("ic_ai_provider_preference");
      IC.toast("AI 已切回自动模型轮换。");
    }
  },

  attachVoiceInputs(root = document) {
    if (!navigator.mediaDevices || !window.MediaRecorder) return;
    const selector = "input.voice-input-target, textarea.voice-input-target, textarea:not(.diary-textarea):not([data-no-voice]), input[type='text']:not([data-no-voice])";
    root.querySelectorAll(selector).forEach(field => {
      if (field.dataset.voiceBound === "true" || field.closest(".topbar") || field.type === "password") return;
      if (field.nextElementSibling?.classList?.contains("voice-mini")) {
        field.dataset.voiceBound = "true";
        return;
      }
      field.dataset.voiceBound = "true";
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "voice-mini";
      btn.title = "语音输入";
      btn.setAttribute("aria-label", "语音输入");
      btn.textContent = "🎙";
      btn.onclick = () => IC.recordToField(field, btn);
      field.insertAdjacentElement("afterend", btn);
    });
  },

  async recordToField(field, button) {
    if (!field || !navigator.mediaDevices || !window.MediaRecorder) return;
    if (button.dataset.recording === "true") {
      button._recorder?.stop();
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const chunks = [];
      const recorder = new MediaRecorder(stream, IC.pickRecorderOptions());
      button._recorder = recorder;
      button.dataset.recording = "true";
      button.textContent = "■";
      recorder.ondataavailable = event => { if (event.data?.size) chunks.push(event.data); };
      recorder.onstop = async () => {
        stream.getTracks().forEach(track => track.stop());
        button.dataset.recording = "false";
        button.textContent = "🎙";
        const blob = new Blob(chunks, { type: recorder.mimeType || "audio/webm" });
        const file = await IC.toWavFile(blob, "voice-input.wav");
        const form = new FormData();
        form.append("file", file);
        const res = await IC.secureFetch("/api/asr/transcribe", { method: "POST", body: form });
        const json = await res.json();
        if (json.success && json.data?.text) {
          const text = json.data.text.trim();
          const start = field.selectionStart ?? field.value.length;
          const end = field.selectionEnd ?? field.value.length;
          field.value = field.value.slice(0, start) + text + field.value.slice(end);
          field.dispatchEvent(new Event("input", { bubbles: true }));
          IC.toast("语音已转写，已尽量保留原始表达。", "success");
        } else {
          IC.toast(json.message || "语音识别失败", "warn");
        }
      };
      recorder.start();
    } catch (e) {
      if (button) {
        button.dataset.recording = "false";
        button.textContent = "🎙";
      }
      IC.toast("无法打开麦克风，请检查浏览器授权。", "warn");
    }
  },

  pickRecorderOptions() {
    for (const mimeType of ["audio/wav", "audio/webm;codecs=opus", "audio/webm", "audio/mp4"]) {
      if (MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(mimeType)) return { mimeType };
    }
    return {};
  },

  async toWavFile(blob, name = "voice-input.wav") {
    if (blob.type === "audio/wav") return new File([blob], name, { type: "audio/wav" });
    const arrayBuffer = await blob.arrayBuffer();
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    const audioContext = new AudioContextClass();
    try {
      const audioBuffer = await audioContext.decodeAudioData(arrayBuffer.slice(0));
      return new File([IC.encodeWav(audioBuffer)], name, { type: "audio/wav" });
    } finally {
      if (audioContext.close) audioContext.close();
    }
  },

  encodeWav(audioBuffer) {
    const channels = Math.min(2, audioBuffer.numberOfChannels);
    const length = audioBuffer.length * channels;
    const data = new Float32Array(length);
    for (let i = 0; i < audioBuffer.length; i++) {
      for (let ch = 0; ch < channels; ch++) data[i * channels + ch] = audioBuffer.getChannelData(ch)[i];
    }
    const buffer = new ArrayBuffer(44 + data.length * 2);
    const view = new DataView(buffer);
    const write = (offset, str) => { for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i)); };
    write(0, "RIFF"); view.setUint32(4, 36 + data.length * 2, true); write(8, "WAVE"); write(12, "fmt ");
    view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, channels, true);
    view.setUint32(24, audioBuffer.sampleRate, true); view.setUint32(28, audioBuffer.sampleRate * channels * 2, true);
    view.setUint16(32, channels * 2, true); view.setUint16(34, 16, true); write(36, "data"); view.setUint32(40, data.length * 2, true);
    let offset = 44;
    for (let i = 0; i < data.length; i++, offset += 2) {
      const s = Math.max(-1, Math.min(1, data[i]));
      view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
    }
    return buffer;
  },

  pulse(kind = "soft") {
    if (!IC.soundEnabled || !navigator.vibrate) return;
    const pattern = kind === "settle" ? [20, 40, 12] : 8;
    navigator.vibrate(pattern);
  },

  stagger(container) {
    if (!container) return;
    Array.from(container.children).forEach((child, index) => {
      child.style.animationDelay = `${index * 35}ms`;
    });
  }
};

window.IC = IC;
window.InnerCosmosApi = IC;

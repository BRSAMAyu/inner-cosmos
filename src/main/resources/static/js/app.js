const IC = {
  soundEnabled: JSON.parse(localStorage.getItem("ic_sound") || "false"),
  darkTheme: JSON.parse(localStorage.getItem("ic_dark") || "false"),

  async api(path, options = {}) {
    try {
      const res = await fetch(path, {
        credentials: "include",
        headers: { "Content-Type": "application/json; charset=utf-8", ...(options.headers || {}) },
        ...options
      });
      const json = await res.json();
      if (!json.success && json.message) IC.toast(json.message, "warn");
      return json;
    } catch (error) {
      IC.toast("连接暂时没有回应，请稍后再试。", "warn");
      return { success: false, message: error.message };
    }
  },

  nav() {
    return [
      ["/pages/dashboard.html", "核心"],
      ["/pages/aurora-chat.html", "Aurora"],
      ["/pages/daily-record.html", "记录"],
      ["/pages/heart-diary.html", "日记"],
      ["/pages/memory-starfield.html", "星图"],
      ["/pages/todo.html", "待办"],
      ["/pages/echo-plaza.html", "星海"],
      ["/pages/slow-letter.html", "慢信"],
      ["/pages/inbox.html", "信箱"],
      ["/pages/timeline.html", "时间轴"],
      ["/pages/emotion-timeline.html", "情绪"],
      ["/pages/themes.html", "主题"],
      ["/pages/relations.html", "关系"],
      ["/pages/beliefs.html", "信念"],
      ["/pages/safety-harbor.html", "避风港"],
      ["/pages/settings.html", "设置"],
      ["/pages/admin.html", "管理"]
    ].map(([href, label]) => `<a href="${href}" data-route="${href}">${IC.esc(label)}</a>`).join("");
  },

  mountShell(activeLabel = "") {
    IC.applyTimeClass();
    IC.applyTheme();
    const topbar = document.querySelector("[data-topbar]");
    if (topbar) {
      topbar.innerHTML = `
        <a class="brand" href="/pages/dashboard.html"><span class="brand-mark"></span><span>Inner Cosmos</span></a>
        <nav class="nav">${IC.nav()}</nav>
        <button class="icon-button" title="切换主题" onclick="IC.toggleTheme()">${IC.darkTheme ? "☾" : "☼"}</button>
      `;
      topbar.querySelectorAll("a").forEach(a => {
        if (a.textContent === activeLabel || location.pathname === new URL(a.href).pathname) a.classList.add("active");
      });
    }
    IC.ensureToastRoot();
  },

  applyTheme() {
    document.body.classList.toggle("dark-star", !!IC.darkTheme);
  },

  applyTimeClass() {
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

  toggleTheme() {
    IC.darkTheme = !IC.darkTheme;
    localStorage.setItem("ic_dark", JSON.stringify(IC.darkTheme));
    IC.applyTheme();
    IC.toast(IC.darkTheme ? "星空主题已开启" : "白天莫兰迪主题已开启");
  },

  ensureToastRoot() {
    if (!document.getElementById("toastRoot")) {
      const root = document.createElement("div");
      root.id = "toastRoot";
      root.className = "toast-root";
      document.body.appendChild(root);
    }
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

  empty(text) {
    return `<div class="empty"><div class="empty-star"></div><p class="muted">${IC.esc(text)}</p></div>`;
  },

  async ensureDemoLogin() {
    const cur = await IC.api("/api/auth/current");
    if (cur.success) return cur;
    return IC.api("/api/auth/login", { method: "POST", body: JSON.stringify({ username: "demo", password: "demo123" }) });
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

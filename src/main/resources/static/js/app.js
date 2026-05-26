const IC = {
  soundEnabled: JSON.parse(localStorage.getItem("ic_sound") || "true"),
  audioCtx: null,

  async api(path, options = {}) {
    try {
      const res = await fetch(path, {
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(options.headers || {}) },
        ...options
      });
      const json = await res.json();
      if (!json.success && json.message) {
        IC.toast(json.message, "warn");
      }
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
      ["/pages/daily-record.html", "今日记录"],
      ["/pages/memory-starfield.html", "记忆星图"],
      ["/pages/todo.html", "待办"],
      ["/pages/echo-plaza.html", "星海广场"],
      ["/pages/inbox.html", "慢信箱"],
      ["/pages/ai-log.html", "AI 日志"]
    ].map(([href, label]) => `<a href="${href}" data-route="${href}">${label}</a>`).join("");
  },

  mountShell(title) {
    const topbar = document.querySelector("[data-topbar]");
    if (topbar) {
      topbar.innerHTML = `
        <a class="brand" href="/pages/index.html">Inner Cosmos 内宇宙</a>
        <nav class="nav" aria-label="主导航">
          ${IC.nav()}
          <button class="icon-button" title="切换轻柔音效" aria-label="切换轻柔音效" onclick="IC.toggleSound()">${IC.soundEnabled ? "♪" : "×"}</button>
        </nav>`;
      const path = location.pathname;
      topbar.querySelectorAll("[data-route]").forEach(link => {
        if (link.getAttribute("data-route") === path) link.classList.add("active");
      });
    }
    document.title = title ? `${title} - Inner Cosmos` : "Inner Cosmos";
    document.body.classList.add("page-ready");
    IC.attachInteractionFeedback();
  },

  async ensureDemoLogin() {
    const current = await IC.api("/api/auth/current").catch(() => null);
    if (!current || !current.success) {
      await IC.api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: "demo", password: "demo123" })
      });
    }
  },

  toast(text, tone = "default") {
    let box = document.querySelector(".toast");
    if (!box) {
      box = document.createElement("div");
      box.className = "toast";
      document.body.appendChild(box);
    }
    box.dataset.tone = tone;
    box.textContent = text;
    box.classList.add("show");
    clearTimeout(IC.toastTimer);
    IC.toastTimer = setTimeout(() => box.classList.remove("show"), 2600);
  },

  empty(text) {
    return `<div class="empty"><div class="empty-star"></div><p>${text}</p></div>`;
  },

  pulse(kind = "soft") {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const vibration = {
      soft: 8,
      strong: [12, 24, 12],
      send: [10, 18],
      settle: [20, 42, 12],
      open: 10,
      letter: [18, 36, 18],
      record: [14, 24, 14]
    }[kind] || 8;
    if (!reduceMotion && navigator.vibrate) navigator.vibrate(vibration);
    if (!IC.soundEnabled) return;
    const profile = {
      soft: { notes: [260], volume: 0.010, duration: 0.09, type: "triangle" },
      strong: { notes: [220, 292], volume: 0.012, duration: 0.13, type: "triangle" },
      send: { notes: [286, 356], volume: 0.012, duration: 0.16, type: "sine" },
      settle: { notes: [196, 236, 262], volume: 0.010, duration: 0.34, type: "sine" },
      open: { notes: [246, 294], volume: 0.010, duration: 0.14, type: "triangle" },
      letter: { notes: [174, 220, 260], volume: 0.010, duration: 0.38, type: "sine" },
      record: { notes: [214, 244, 274], volume: 0.009, duration: 0.32, type: "sine" }
    }[kind] || { notes: [260], volume: 0.010, duration: 0.09, type: "triangle" };
    IC.chime(profile);
  },

  chime(profile) {
    try {
      IC.audioCtx = IC.audioCtx || new (window.AudioContext || window.webkitAudioContext)();
      const ctx = IC.audioCtx;
      profile.notes.forEach((frequency, index) => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        const start = ctx.currentTime + index * 0.055;
        const stop = start + profile.duration;
        osc.type = profile.type;
        osc.frequency.setValueAtTime(frequency, start);
        gain.gain.setValueAtTime(0.0001, start);
        gain.gain.linearRampToValueAtTime(profile.volume, start + 0.018);
        gain.gain.exponentialRampToValueAtTime(0.0001, stop);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start(start);
        osc.stop(stop + 0.03);
      });
    } catch (error) {
      // Sound is an enhancement; browser autoplay policy may block it.
    }
  },

  toggleSound() {
    IC.soundEnabled = !IC.soundEnabled;
    localStorage.setItem("ic_sound", JSON.stringify(IC.soundEnabled));
    const btn = document.querySelector(".icon-button");
    if (btn) btn.textContent = IC.soundEnabled ? "♪" : "×";
    IC.toast(IC.soundEnabled ? "轻柔音效已开启" : "轻柔音效已关闭");
    if (IC.soundEnabled) IC.pulse("open");
  },

  attachInteractionFeedback() {
    if (document.body.dataset.feedbackReady) return;
    document.body.dataset.feedbackReady = "true";
    document.addEventListener("click", event => {
      const target = event.target.closest("button,.button,.nav a");
      if (!target || target.disabled) return;
      const tone = target.matches(".voice-pill") ? "strong" : target.matches("[data-letter-action]") ? "letter" : "soft";
      IC.pulse(tone);
    }, { passive: true });
  },

  stagger(container) {
    const root = typeof container === "string" ? document.querySelector(container) : container;
    if (!root) return;
    root.querySelectorAll(".card,.panel,.timeline-item,.bubble").forEach((node, index) => {
      node.style.animationDelay = `${Math.min(index * 34, 260)}ms`;
    });
  }
};

const IC = {
  soundEnabled: JSON.parse(localStorage.getItem("ic_sound") || "true"),
  audioCtx: null,
  darkTheme: JSON.parse(localStorage.getItem("ic_dark") || "false"),
  autoTheme: JSON.parse(localStorage.getItem("ic_auto_theme") || "false"),
  sunsetHour: JSON.parse(localStorage.getItem("ic_sunset_hour") || "18"),
  sunriseHour: JSON.parse(localStorage.getItem("ic_sunrise_hour") || "6"),

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
      ["/pages/daily-record.html", "记录"],
      ["/pages/memory-starfield.html", "星图"],
      ["/pages/todo.html", "待办"],
      ["/pages/echo-plaza.html", "星海"],
      ["/pages/slow-letter.html", "慢信"],
      ["/pages/inbox.html", "信箱"],
      ["/pages/timeline.html", "时间轴"],
      ["/pages/safety-harbor.html", "避风港"],
      ["/pages/settings.html", "设置"],
      ["/pages/admin.html", "管理"]
    ].map(([href, label]) => `<a href="${href}" data-route="${href}">${IC.esc(label)}</a>`).join("");
  },

  applyTheme() {
    const useDark = IC.autoTheme
      ? (new Date().getHours() >= IC.sunsetHour || new Date().getHours() < IC.sunriseHour)
      : IC.darkTheme;

    if (useDark) {
      document.body.classList.add("dark-star");
    } else {
      document.body.classList.remove("dark-star");
    }
  },

  toggleTheme() {
    if (IC.autoTheme) {
      IC.autoTheme = false;
      localStorage.setItem("ic_auto_theme", "false");
    }
    IC.darkTheme = !IC.darkTheme;
    localStorage.setItem("ic_dark", JSON.stringify(IC.darkTheme));
    IC.applyTheme();
    IC.toast(IC.darkTheme ? "星空主题已开启" : "暖雾主题已开启");
  },

  toggleAutoTheme() {
    IC.autoTheme = !IC.autoTheme;
    localStorage.setItem("ic_auto_theme", JSON.stringify(IC.autoTheme));
    if (IC.autoTheme) {
      IC.applyTheme();
      IC.toast("自动切换已启用（日落后切换星空主题）");
    } else {
      IC.toast("自动切换已关闭");
    }
  },

  mountShell(title) {
    IC.applyTheme();
    IC.startThemeChecker();

    // Add time-based class
    IC.applyTimeBasedClass();

    const topbar = document.querySelector("[data-topbar]");
    if (topbar) {
      topbar.innerHTML = `
        <a class="brand" href="/pages/dashboard.html">Inner Cosmos</a>
        <nav class="nav" aria-label="主导航">
          ${IC.nav()}
          <button class="icon-button" title="${IC.autoTheme ? "自动：已启用" : "白昼固定"}" aria-label="主题模式" onclick="IC.toggleAutoTheme()">${IC.autoTheme ? "auto" : "day"}</button>
          <button class="icon-button" title="切换主题" aria-label="切换主题" onclick="IC.toggleTheme()">${IC.darkTheme ? "☾" : "☀"}</button>
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

    // Initialize motion system
    if (window.ICMotion) {
      ICMotion.init();
    }
  },

  applyTimeBasedClass() {
    const hour = new Date().getHours();
    document.body.classList.remove("time-morning", "time-afternoon", "time-evening", "time-night");

    if (hour >= 5 && hour < 12) {
      document.body.classList.add("time-morning");
    } else if (hour >= 12 && hour < 17) {
      document.body.classList.add("time-afternoon");
    } else if (hour >= 17 && hour < 21) {
      document.body.classList.add("time-evening");
    } else {
      document.body.classList.add("time-night");
    }
  },

  startThemeChecker() {
    if (IC.themeCheckInterval) return;
    IC.themeCheckInterval = setInterval(() => {
      if (IC.autoTheme) {
        const hour = new Date().getHours();
        const shouldBeDark = hour >= IC.sunsetHour || hour < IC.sunriseHour;
        if (IC.darkTheme !== shouldBeDark) {
          IC.darkTheme = shouldBeDark;
          IC.applyTheme();
        }
      }
    }, 60000);
  },

  // DEV-ONLY: demo credentials for development/testing
  async ensureDemoLogin() {
    try {
      const r = await IC.api('/api/auth/current');
      if (r.success) { IC.userId = r.data?.id; return r.data; }
    } catch(e) {}
    try {
      const r = await IC.api('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: 'demo', password: 'demo123' })
      });
      if (r.success) { IC.userId = r.data?.id; return r.data; }
    } catch(e) {}
    return null;
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
    return `<div class="empty"><div class="empty-star"></div><p>${IC.esc(text)}</p></div>`;
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

    // Detect touch device
    const isTouch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

    // Add mobile class to html if touch device
    if (isTouch) {
      document.documentElement.classList.add('touch');
      // Prevent double-tap zoom on interactive elements
      document.addEventListener('touchstart', (e) => {
        if (e.target.closest('button, .button, .nav a, a')) {
          // Prevent iOS zoom on double tap
          // But allow natural touch behavior
        }
      }, { passive: true });
    }

    // Touch ripple for mobile
    if (isTouch) {
      document.addEventListener('touchstart', (e) => {
        const target = e.target.closest('button, .button, .nav a, .card, .interactive, .star');
        if (!target) return;

        const touch = e.touches[0];
        const rect = target.getBoundingClientRect();
        const x = touch.clientX - rect.left;
        const y = touch.clientY - rect.top;

        if (window.ICMotion) {
          ICMotion.createRipple(x, y, target);
        }

        // Add active state class
        target.classList.add('touch-active');
        setTimeout(() => target.classList.remove('touch-active'), 200);
      }, { passive: true });
    }

    // Click feedback for all devices
    document.addEventListener("click", event => {
      const target = event.target.closest("button,.button,.nav a");
      if (!target || target.disabled) return;
      const tone = target.matches(".voice-pill") ? "strong" : target.matches("[data-letter-action]") ? "letter" : "soft";
      IC.pulse(tone);
    }, { passive: true });

    // Prevent rubber-banding on scroll
    if (isTouch) {
      document.body.addEventListener('touchmove', (e) => {
        // Allow normal scrolling
      }, { passive: true });
    }
  },

  stagger(container) {
    const root = typeof container === "string" ? document.querySelector(container) : container;
    if (!root) return;
    root.querySelectorAll(".card,.panel,.timeline-item,.bubble").forEach((node, index) => {
      node.style.animationDelay = `${Math.min(index * 34, 260)}ms`;
    });
  },

  esc(text) {
    return String(text || "").replace(/[&<>"']/g, m => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[m]));
  },

  formatTime(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")} ${String(d.getHours()).padStart(2,"0")}:${String(d.getMinutes()).padStart(2,"0")}`;
  },

  greetingByTime() {
    const h = new Date().getHours();
    if (h < 5) return "夜深了，你还在吗";
    if (h < 7) return "黎明前，星光还亮着";
    if (h < 9) return "早安，新的一天慢慢开始";
    if (h < 12) return "上午好，今天你想先做什么";
    if (h < 14) return "中午好，先给自己一点喘息";
    if (h < 17) return "下午好，今天过得怎么样";
    if (h < 19) return "傍晚了，今天可以慢慢收束";
    if (h < 22) return "晚上好，星光在等你";
    return "夜深了，愿你平静";
  },

  weatherIcon(type) {
    const map = { SUN: "☀️", CLOUD: "☁️", RAIN: "🌧️", STORM: "⛈️", FOG: "🌫️", SNOW: "🌨️" };
    return map[type] || "☁️";
  },

  closeModal() {
    const m = document.querySelector(".modal-overlay");
    if (m) m.remove();
  },

  showModal(html) {
    IC.closeModal();
    const overlay = document.createElement("div");
    overlay.className = "modal-overlay";
    overlay.onclick = e => { if (e.target === overlay) IC.closeModal(); };
    overlay.innerHTML = `<div class="modal-box">${html}</div>`;
    document.body.appendChild(overlay);
  },

  // Weather control modal
  showWeatherModal() {
    const currentWeather = window.ICWeather ? window.ICWeather.getWeatherState() : null;
    const weatherTypes = [
      { type: 'CLEAR', label: '晴朗', icon: '☀️' },
      { type: 'CLOUD', label: '多云', icon: '☁️' },
      { type: 'RAIN', label: '下雨', icon: '🌧️' },
      { type: 'STORM', label: '暴风雨', icon: '⛈️' },
      { type: 'FOG', label: '有雾', icon: '🌫️' },
      { type: 'SNOW', label: '下雪', icon: '🌨️' }
    ];

    const weatherButtons = weatherTypes.map(w => `
      <button class="weather-option ${currentWeather?.type === w.type ? 'active' : ''}"
              data-weather="${w.type}">
        <span style="font-size: 1.5rem">${w.icon}</span>
        <span>${w.label}</span>
      </button>
    `).join('');

    IC.showModal(`
      <h2>天气设置</h2>
      <p class="muted mb-1">选择天气效果，或启用自动模式</p>
      <div class="weather-grid">${weatherButtons}</div>
      <div class="row mt-2">
        <button class="button" onclick="window.ICWeather.setAutoWeather()">
          ${currentWeather?.isAuto ? '✓ ' : ''}自动模式
        </button>
        <button class="button" onclick="IC.closeModal()">关闭</button>
      </div>
      <script>
        document.querySelectorAll('.weather-option').forEach(btn => {
          btn.onclick = () => window.ICWeather.setManualWeather(btn.dataset.weather);
        });
      </script>
    `);
  }
};

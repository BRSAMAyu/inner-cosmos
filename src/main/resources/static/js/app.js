const IC = {
  soundEnabled: JSON.parse(localStorage.getItem("ic_sound") || "false"),
  themeMode: localStorage.getItem("ic_theme_mode") || "auto",  // "auto" | "light" | "dark"
  _darkMedia: null,

  async api(path, options = {}, retries = 1) {
    for (let attempt = 0; ; attempt++) {
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
        if (attempt < retries) {
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
      ["/pages/daily-record.html", "记录"],
      ["/pages/heart-diary.html", "日记"],
      ["/pages/memory-starfield.html", "星图"],
      ["/pages/todo.html", "待办"],
      ["/pages/echo-plaza.html", "星海"],
      ["/pages/social.html", "好友"],
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
    IC.initSystemThemeListener();
    IC.applyTimeClass();
    IC.applyTheme();
    IC.ensureFlowStage();
    IC.ensureAmbientSystems();
    const topbar = document.querySelector("[data-topbar]");
    if (topbar) {
      const themeIcon = IC.themeMode === "auto" ? "◐" : (IC.darkTheme ? "☾" : "☼");
      const themeTip = IC.themeMode === "auto" ? "主题：自动跟随系统" : (IC.darkTheme ? "主题：星空（深色）" : "主题：莫兰迪（浅色）");
      topbar.innerHTML = `
        <a class="brand" href="/pages/dashboard.html"><span class="brand-mark"></span><span>Inner Cosmos</span></a>
        <nav class="nav" aria-label="主导航">${IC.nav()}</nav>
        <button class="icon-button" aria-label="${themeTip}" title="${themeTip}" onclick="IC.toggleTheme()">${themeIcon}</button>
      `;
      topbar.querySelectorAll("a").forEach(a => {
        if (a.textContent === activeLabel || location.pathname === new URL(a.href).pathname) a.classList.add("active");
      });
    }
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
    }).catch(() => {});
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
    if (!location.pathname.endsWith("/login.html") && !location.pathname.endsWith("/register.html")) {
      location.href = "/pages/login.html";
    }
    return { success: false };
  },

  async guestLogin() {
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
    const wrap = document.createElement("div");
    wrap.id = "icModalRoot";
    wrap.className = "modal-root";
    wrap.innerHTML = `<div class="modal-backdrop" onclick="IC.closeModal()"></div><div class="modal-panel">${html}</div>`;
    document.body.appendChild(wrap);
  },

  closeModal() {
    document.getElementById("icModalRoot")?.remove();
  },

  ensureAmbientControls() {
    if (document.getElementById("ambientControls")) return;
    const root = document.createElement("div");
    root.id = "ambientControls";
    root.className = "ambient-controls";
    root.innerHTML = `
      <button class="ambient-btn" id="musicToggle" title="播放/暂停音乐" onclick="IC.toggleMusic()">♫</button>
      <button class="ambient-btn" title="视觉与天气" onclick="IC.toggleVisualPanel()">◐</button>
      <div id="visualPanel" class="ambient-panel" style="display:none">
        <strong>视觉流动</strong>
        <label><span>自动跟随时间/天气</span><input id="visualAuto" type="checkbox" checked onchange="IC.setVisualAuto(this.checked)"></label>
        <label><span>固定主题</span><select id="fixedTheme" onchange="IC.setFixedTheme(this.value)">
          <option value="">自动</option><option value="time-morning">白天莫兰迪</option><option value="time-afternoon">午后暖光</option><option value="time-dusk">黄昏</option><option value="time-night">夜色</option>
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
    if (panel) panel.style.display = panel.style.display === "none" ? "grid" : "none";
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

  setFixedTheme(value) {
    if (!value) {
      localStorage.removeItem("ic_fixed_theme");
      IC.applyTimeClass();
      return;
    }
    localStorage.setItem("ic_visual_auto", "false");
    localStorage.setItem("ic_fixed_theme", value);
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
        const res = await fetch("/api/asr/transcribe", { method: "POST", credentials: "include", body: form });
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

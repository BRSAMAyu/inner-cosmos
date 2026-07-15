/* ── Inner Cosmos API Layer ── */
window.InnerCosmosApi = IC;

const API = {
  /* Auth */
  async login(username, password) {
    return IC.api("/api/auth/login", { method: "POST", body: JSON.stringify({ username, password }) });
  },
  async current() {
    return IC.api("/api/auth/current");
  },

  /* Dashboard */
  async dashboardSummary() {
    return IC.api("/api/dashboard/summary");
  },
  async understandingOverview(range = 7) {
    return IC.api(`/api/understanding/overview?range=${range}`);
  },

  /* Aurora Chat */
  async auroraModes() {
    return IC.api("/api/aurora/modes");
  },
  async auroraModeSwitch(sessionId, mode) {
    return IC.api("/api/aurora/mode/switch", {
      method: "POST",
      body: JSON.stringify({ sessionId, mode })
    });
  },
  async auroraGreeting(data) {
    return IC.api("/api/aurora/greeting", { method: "POST", body: JSON.stringify(data) });
  },
  async auroraMessage(data) {
    return IC.api("/api/aurora/message", { method: "POST", body: JSON.stringify(data) });
  },
  async auroraMessageRich(data) {
    return IC.api("/api/aurora/message-rich", { method: "POST", body: JSON.stringify(data) });
  },
  async auroraMemoryContext(sessionId, q) {
    const params = new URLSearchParams();
    if (sessionId) params.set("sessionId", sessionId);
    if (q) params.set("q", q);
    const qs = params.toString();
    return IC.api(`/api/aurora/memory-context${qs ? "?" + qs : ""}`);
  },
  // NOTE: `auroraStream` (the IC.api-based SSE wrapper) was pruned in VS-007 —
  // aurora-chat.html builds the EventSource URL inline (with a stream-stage
  // token) since VS-003, so nothing calls it. Keep `auroraStreamUrl` below as a
  // pure URL-builder utility (currently unused; retained for future SSE callers).
  auroraStreamUrl(sessionId, message, mode) {
    const params = new URLSearchParams({ sessionId, message });
    if (mode) params.set("mode", mode);
    return `/api/aurora/stream?${params.toString()}`;
  },
  async auroraSettle(sessionId) {
    return IC.api(`/api/aurora/settle?sessionId=${sessionId}`, { method: "POST" });
  },
  async auroraRhythmCheck() {
    return IC.api("/api/aurora/rhythm-check", { method: "POST" });
  },
  async setPreferredModel(body) {
    return IC.api("/api/user/preferred-model", { method: "PUT", body: JSON.stringify(body) });
  },
  async setSessionModel(sessionId, provider) {
    return IC.api(
      "/api/aurora/session/" + sessionId + "/model",
      { method: "PUT", body: JSON.stringify({ provider }) }
    );
  },

  /* Dialog Sessions */
  async createSession(data) {
    return IC.api("/api/dialog/session/create", { method: "POST", body: JSON.stringify(data) });
  },
  async sessionMessages(id) {
    return IC.api(`/api/dialog/session/${id}/messages`);
  },
  async finishSession(id) {
    return IC.api(`/api/dialog/session/${id}/finish`, { method: "POST" });
  },

  /* Memory */
  async memoryStarfield() {
    return IC.api("/api/memory/starfield");
  },
  async memoryStarfieldDetail(id) {
    return IC.api(`/api/memory/starfield/${id}/detail`);
  },
  async memoryCards() {
    return IC.api("/api/memory/cards");
  },
  async memoryExtract(sessionId) {
    return IC.api(`/api/memory/extract/${sessionId}`, { method: "POST" });
  },
  async updateMemoryImportance(id, importance) {
    return IC.api(`/api/memory/cards/${id}/importance`, { method: "POST", body: JSON.stringify({ importance }) });
  },
  async archiveMemoryCard(id) {
    return IC.api(`/api/memory/cards/${id}/archive`, { method: "POST" });
  },
  async memoryThemes() {
    return IC.api("/api/memory/themes");
  },
  async dailyRecords() {
    return IC.api("/api/memory/daily-records");
  },
  async acceptDailyRecord(id) {
    return IC.api(`/api/memory/daily-records/${id}/accept`, { method: "POST" });
  },

  /* Belief (B2) */
  async beliefList() {
    return IC.api("/api/belief/list");
  },
  async beliefByCategory(category) {
    return IC.api(`/api/belief/by-category?category=${encodeURIComponent(category)}`);
  },
  async beliefStrong(minStrength) {
    return IC.api(`/api/belief/strong?minStrength=${minStrength || 0.5}`);
  },
  async beliefContradictions() {
    return IC.api("/api/belief/contradictions");
  },

  /* Relation Network (B3) */
  async relationList() {
    return IC.api("/api/relation/list");
  },
  async relationStats() {
    return IC.api("/api/relation/stats");
  },
  async relationHighEmotion() {
    return IC.api("/api/relation/high-emotion");
  },
  async relationTimeline(label) {
    return IC.api(`/api/relation/timeline?label=${encodeURIComponent(label)}`);
  },
  async relationHealth(label) {
    return IC.api(`/api/relation/health?label=${encodeURIComponent(label)}`);
  },

  /* Emotion Timeline (B4) */
  async emotionToday() {
    return IC.api("/api/emotion/timeline/today");
  },
  async emotionRange(start, end) {
    return IC.api(`/api/emotion/timeline/range?start=${start}&end=${end}`);
  },
  async emotionTrend(days) {
    return IC.api(`/api/emotion/timeline/trend?days=${days || 30}`);
  },
  async emotionPatterns(days) {
    return IC.api(`/api/emotion/timeline/patterns?days=${days || 30}`);
  },
  async emotionStability(days) {
    return IC.api(`/api/emotion/timeline/stability?days=${days || 30}`);
  },

  /* ABTest (D5) */
  async abtestActive() {
    return IC.api("/api/abtest/active");
  },
  async abtestAssign(module) {
    return IC.api(`/api/abtest/assign?module=${encodeURIComponent(module)}`);
  },
  async abtestStats(testName) {
    return IC.api(`/api/abtest/stats?testName=${encodeURIComponent(testName)}`);
  },

  /* Token (D4) */
  async tokenDailyUsage() {
    return IC.api("/api/token/daily-usage");
  },
  async tokenEstimate(text) {
    return IC.api(`/api/token/estimate?text=${encodeURIComponent(text)}`);
  },
  async tokenForecast() {
    return IC.api("/api/token/forecast");
  },

  /* Aurora Proactive (B5) */
  async auroraProactiveCheck() {
    return IC.api("/api/aurora/proactive/check", { method: "POST" });
  },
  async auroraProactiveDismiss(id) {
    return IC.api(`/api/aurora/proactive/${id}/dismiss`, { method: "POST" });
  },

  /* Daily Record */
  async latestDailyRecord() {
    return IC.api("/api/daily-record/latest");
  },
  async diaryTranscribe(text) {
    return IC.api("/api/diary/transcribe", { method: "POST", body: JSON.stringify({ text }) });
  },
  async diaryTranscribeAudio(file) {
    const form = new FormData();
    form.append("file", file);
    return API.multipart("/api/diary/transcribe-audio", form);
  },
  async diaryPolish(text, level) {
    return IC.api("/api/diary/polish", { method: "POST", body: JSON.stringify({ text, level }) });
  },
  async diarySubmit(id, content) {
    return IC.api("/api/diary/submit", { method: "POST", body: JSON.stringify({ id, content }) });
  },
  async diaryAnalyze(id) {
    return IC.api(`/api/diary/${id}/analyze`, { method: "POST" });
  },

  /* Capsule */
  async myCapsules() {
    return IC.api("/api/capsule/my");
  },
  async createCapsule(data) {
    return IC.api("/api/capsule/create-from-memory", { method: "POST", body: JSON.stringify(data) });
  },
  async capsuleDetail(id) {
    return IC.api(`/api/capsule/${id}`);
  },
  async updateCapsuleVisibility(id, visibilityStatus, isPublic) {
    return IC.api(`/api/capsule/${id}/visibility`, { method: "POST", body: JSON.stringify({ visibilityStatus, isPublic }) });
  },
  async previewCapsuleFromMemory(data) {
    return IC.api("/api/capsule/preview-from-memory", { method: "POST", body: JSON.stringify(data) });
  },
  async previewUserMirrorCapsule() {
    return IC.api("/api/capsule/user-mirror/preview", { method: "POST" });
  },
  async capsuleBoundary(id) {
    return IC.api(`/api/capsule/${id}/boundary`);
  },
  async capsuleContextPreview(id) {
    return IC.api(`/api/capsule/${id}/context-preview`);
  },
  async updateCapsuleContext(id, data) {
    return IC.api(`/api/capsule/${id}/context`, { method: "POST", body: JSON.stringify(data) });
  },
  async updateCapsuleBoundary(id, data) {
    return IC.api(`/api/capsule/${id}/boundary`, { method: "POST", body: JSON.stringify(data) });
  },
  async archiveCapsule(id) {
    return IC.api(`/api/capsule/${id}/archive`, { method: "POST" });
  },
  async syncPending() {
    return IC.api("/api/capsule/sync/pending");
  },

  /* Plaza */
  async plazaCapsules() {
    return IC.api("/api/plaza/capsules");
  },
  async plazaMatches() {
    return IC.api("/api/plaza/matches");
  },

  /* Social */
  async socialPeople() {
    return IC.api("/api/social/people");
  },
  async socialFriends() {
    return IC.api("/api/social/friends");
  },
  async socialRequests() {
    return IC.api("/api/social/requests");
  },
  async socialRequestFriend(userId, source = "SOCIAL_PAGE") {
    return IC.api("/api/social/friends/request", { method: "POST", body: JSON.stringify({ userId, source }) });
  },
  async socialAcceptFriend(id) {
    return IC.api(`/api/social/friends/${id}/accept`, { method: "POST" });
  },
  async socialDeclineFriend(id) {
    return IC.api(`/api/social/friends/${id}/decline`, { method: "POST" });
  },
  async socialGroups() {
    return IC.api("/api/social/groups");
  },
  async socialCreateGroup(data) {
    return IC.api("/api/social/groups", { method: "POST", body: JSON.stringify(data) });
  },

  /* Persona Chat */
  async createPersonaChat(capsuleId) {
    return IC.api("/api/persona-chat/session/create", { method: "POST", body: JSON.stringify({ capsuleId }) });
  },
  async personaChatMessage(sessionId, message) {
    return IC.api("/api/persona-chat/message", { method: "POST", body: JSON.stringify({ sessionId, message }) });
  },
  async personaChatMessages(sessionId) {
    return IC.api(`/api/persona-chat/session/${sessionId}/messages`);
  },
  /**
   * IC-CAP-001: authoritative per-day quota state for the current visitor on a capsule.
   * Returns { turnCount, dailyLimit, remaining, seed, quotaDate }.
   * The frontend should treat this as the source of truth (the old client-side
   * usedTurns++ counter could be bypassed by opening new sessions).
   */
  async personaChatQuota(capsuleId) {
    return IC.api(`/api/persona-chat/quota?capsuleId=${capsuleId}`);
  },

  /* Letters */
  async letterDraft(data) {
    return IC.api("/api/letters/draft", { method: "POST", body: JSON.stringify(data) });
  },
  async letterSend(id) {
    return IC.api(`/api/letters/${id}/send`, { method: "POST" });
  },
  async letterDeliver(id) {
    // FEATURE-STUB #33 (slow-letter reply-with-letter + threads): no caller yet;
    // kept to preserve the API contract for the upcoming slow-letter delivery control.
    return IC.api(`/api/letters/${id}/deliver`, { method: "POST" });
  },
  async letterRead(id) {
    return IC.api(`/api/letters/${id}/read`, { method: "POST" });
  },
  async letterReply(id) {
    return IC.api(`/api/letters/${id}/reply`, { method: "POST" });
  },
  async letterDecline(id) {
    return IC.api(`/api/letters/${id}/decline`, { method: "POST" });
  },
  async letterBlock(id) {
    return IC.api(`/api/letters/${id}/block`, { method: "POST" });
  },
  async letterArchive(id) {
    return IC.api(`/api/letters/${id}/archive`, { method: "POST" });
  },
  async letterInbox() {
    return IC.api("/api/letters/inbox");
  },
  async letterOutbox() {
    return IC.api("/api/letters/outbox");
  },
  async letterDetail(id) {
    return IC.api(`/api/letters/${id}`);
  },
  async letterReplyWithLetter(id, data) {
    // FEATURE #33 (slow-letter reply-with-letter): wired from inbox.html
    // (openReplyWithLetter). Creates a reply slow letter + thread; the
    // LetterGuardAgent safety gate runs server-side before any insert.
    return IC.api(`/api/letters/${id}/reply-with-letter`, { method: "POST", body: JSON.stringify(data) });
  },
  async letterThreads() {
    // FEATURE #33 (slow-letter conversation-threads): wired from
    // letter-threads.html, which renders the user's slow-letter threads.
    return IC.api("/api/letters/threads");
  },
  async letterThreadLetters(threadId) {
    // FEATURE #33: walk a single conversation (ownership-checked server-side).
    return IC.api(`/api/letters/threads/${threadId}/letters`);
  },
  async letterRequestRewrite(id) {
    // FEATURE #33: gentle rewrite coaching for a draft the writer owns.
    return IC.api(`/api/letters/${id}/request-rewrite`, { method: "POST" });
  },
  async letterReport(id, reason) {
    return IC.api(`/api/letters/${id}/report`, { method: "POST", body: JSON.stringify({ reason }) });
  },

  /* Thought Shredder */
  async shredderProcess(text, originalHandlingMode = "KEEP_ONLY_RESULT") {
    return IC.api("/api/thought-shredder/process", { method: "POST", body: JSON.stringify({ text, originalHandlingMode }) });
  },
  async shredderHistory() {
    return IC.api("/api/thought-shredder/history");
  },
  async shredderSettle(id) {
    return IC.api(`/api/thought-shredder/${id}/settle`, { method: "POST" });
  },
  async shredderDelete(id) {
    return IC.api(`/api/thought-shredder/${id}`, { method: "DELETE" });
  },

  /* Todos */
  async todoList() {
    return IC.api("/api/todos");
  },
  async todoStatus(id, status) {
    return IC.api(`/api/todos/${id}/status`, { method: "POST", body: JSON.stringify({ status }) });
  },
  async todoDelete(id) {
    return IC.api(`/api/todos/${id}`, { method: "DELETE" });
  },
  async todoCreate(data) {
    return IC.api("/api/todos", { method: "POST", body: JSON.stringify(data) });
  },
  async todoUpdate(id, data) {
    return IC.api(`/api/todos/${id}`, { method: "PUT", body: JSON.stringify(data) });
  },
  async todoSplit(id) {
    return IC.api(`/api/todos/${id}/split`, { method: "POST" });
  },

  /* Safety */
  async safetyResources() {
    return IC.api("/api/safety/resources");
  },
  async safetyCheck(data) {
    return IC.api("/api/safety/check", { method: "POST", body: JSON.stringify(data) });
  },

  /* ASR */
  async asrMockTranscribe(hintText) {
    return IC.api("/api/asr/mock-transcribe", { method: "POST", body: JSON.stringify({ hintText }) });
  },
  async asrTranscribeFile(file) {
    const form = new FormData();
    form.append("file", file);
    return API.multipart("/api/asr/transcribe", form);
  },

  /* Admin */
  async adminOverview() {
    return IC.api("/api/admin/overview");
  },
  async adminUsers() {
    return IC.api("/api/admin/users");
  },
  async adminCapsules(status, keyword) {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (keyword) params.set("keyword", keyword);
    const qs = params.toString();
    return IC.api(`/api/admin/capsules${qs ? "?" + qs : ""}`);
  },
  async adminReports(status) {
    const qs = status ? `?status=${status}` : "";
    return IC.api(`/api/admin/reports${qs}`);
  },
  async adminHideCapsule(id, reason = "") {
    return IC.api(`/api/admin/capsules/${id}/hide`, { method: "POST", body: JSON.stringify({ reason }) });
  },
  async adminRestoreCapsule(id, reason = "") {
    return IC.api(`/api/admin/capsules/${id}/restore`, { method: "POST", body: JSON.stringify({ reason }) });
  },
  async adminResolveReport(id, action, reason = "") {
    return IC.api(`/api/admin/reports/${id}/resolve`, { method: "POST", body: JSON.stringify({ action, reason }) });
  },
  async adminAuditLogs() {
    return IC.api("/api/admin/audit-logs");
  },
  async adminSafetyEvents() {
    return IC.api("/api/admin/safety-events");
  },
  async adminModelConfig() {
    return IC.api("/api/admin/model-config");
  },
  async adminUpdateModelConfig(config) {
    return IC.api("/api/admin/model-config", { method: "POST", body: JSON.stringify(config) });
  },

  /* Aurora Self — subjectivity system (M6) */
  getConstitution: () => IC.api("/api/aurora/self/constitution"),
  getStatements: (userId, limit = 10) =>
    IC.api(`/api/aurora/self/statements?userId=${userId}&limit=${limit}`),
  getReflections: (userId, limit = 10) =>
    IC.api(`/api/aurora/self/reflections?userId=${userId}&limit=${limit}`),
  getModel: (userId) => IC.api(`/api/aurora/self/model?userId=${userId}`),
  getCandidates: (userId) => IC.api(`/api/aurora/self/candidates?userId=${userId}`),
  commitCandidate: (userId, candidateId, userConfirmed = true) =>
    IC.api(`/api/aurora/self/commit?userId=${userId}`, {
      method: "POST",
      body: JSON.stringify({ candidateId, userConfirmed, extraEvidence: [] })
    }),
  dismissCandidate: (userId, candidateId) =>
    IC.api(`/api/aurora/self/dismiss?userId=${userId}&candidateId=${candidateId}`, {
      method: "POST"
    }),

  /* AI Logs */
  async aiLogs() {
    return IC.api("/api/ai-logs");
  },
  async aiHealth() {
    return IC.api("/api/ai/health");
  },

  /* IC-EMO-002 — real-time "此刻情绪" for the Aurora mood energy-orb. */
  async auroraMood() {
    return IC.api("/api/aurora/mood");
  },

  /* RUN-005 — Aurora correction feedback loop: the user authoritatively corrects
     Aurora's model of them ("这不太是我"); corrections re-enter the prompt with
     precedence over inferences, so Aurora visibly adapts. */
  async auroraCorrect(newValue, oldValue, reason, opts) {
    const body = { newValue };
    if (oldValue) body.oldValue = oldValue;
    if (reason) body.reason = reason;
    // RUN-006 — opts.targetType/fieldName let the portrait page tag a per-dimension
    // calibration (PORTRAIT_DIM) so it routes into Aurora's soft-coexist prompt block.
    if (opts && opts.targetType) body.targetType = opts.targetType;
    if (opts && opts.fieldName) body.fieldName = opts.fieldName;
    return IC.api("/api/aurora/corrections", { method: "POST", body: JSON.stringify(body) });
  },
  async auroraCorrections() {
    return IC.api("/api/aurora/corrections");
  },

  /* RUN-006 — Aurora's portrait of the user ("Aurora 眼中的你"), now visible & calibratable. */
  async portrait() {
    return IC.api("/api/portrait");
  },
  async portraitHistory(dim) {
    return IC.api("/api/portrait/history?dim=" + encodeURIComponent(dim));
  },

  /* RUN-006 — system notifications (capsule sync done/failed), now surfaced on the dashboard. */
  async notifications() {
    return IC.api("/api/notifications");
  },
  async markNotificationRead(id) {
    return IC.api("/api/notifications/" + encodeURIComponent(id) + "/read", { method: "POST" });
  },

  /* Aurora Goodbye */
  async goodbye(body) {
    return IC.api("/api/aurora/goodbye", { method: "POST", body: JSON.stringify(body) });
  },

  /* Weekly Review */
  async weeklyReviewLatest() {
    return IC.api("/api/daily-record/weekly/latest");
  },
  async weeklyReviewGenerate() {
    return IC.api("/api/daily-record/weekly/generate", { method: "POST" });
  },

  /* User Profile */
  async userProfile() {
    return IC.api("/api/user/profile");
  },
  async updateProfile(data) {
    return IC.api("/api/user/profile", { method: "PUT", body: JSON.stringify(data) });
  },
  async exportData() {
    return IC.api("/api/user/export");
  },
  async deleteAccount() {
    return IC.api("/api/user/account", { method: "DELETE" });
  },

  async multipart(path, formData) {
    try {
      const res = await IC.secureFetch(path, {
        method: "POST",
        body: formData
      });
      const json = await res.json();
      if (!json.success && json.message) {
        IC.toast(json.message, "warn");
      }
      return json;
    } catch (error) {
      IC.toast("连接暂时没有响应，请稍后再试。", "warn");
      return { success: false, message: error.message };
    }
  }
};

window.API = API;

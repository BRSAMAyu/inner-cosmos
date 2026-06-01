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

  /* Aurora Chat */
  async auroraModes() {
    return IC.api("/api/aurora/modes");
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
  async auroraStream(sessionId, message) {
    return IC.api(`/api/aurora/stream?sessionId=${sessionId}&message=${encodeURIComponent(message)}`);
  },
  async auroraSettle(sessionId) {
    return IC.api(`/api/aurora/settle?sessionId=${sessionId}`, { method: "POST" });
  },
  async auroraRhythmCheck() {
    return IC.api("/api/aurora/rhythm-check", { method: "POST" });
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

  /* Daily Record */
  async latestDailyRecord() {
    return IC.api("/api/daily-record/latest");
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
  async capsuleBoundary(id) {
    return IC.api(`/api/capsule/${id}/boundary`);
  },
  async updateCapsuleBoundary(id, data) {
    return IC.api(`/api/capsule/${id}/boundary`, { method: "POST", body: JSON.stringify(data) });
  },
  async archiveCapsule(id) {
    return IC.api(`/api/capsule/${id}/archive`, { method: "POST" });
  },

  /* Plaza */
  async plazaCapsules() {
    return IC.api("/api/plaza/capsules");
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

  /* Letters */
  async letterDraft(data) {
    return IC.api("/api/letters/draft", { method: "POST", body: JSON.stringify(data) });
  },
  async letterSend(id) {
    return IC.api(`/api/letters/${id}/send`, { method: "POST" });
  },
  async letterDeliver(id) {
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
    return IC.api(`/api/letters/${id}/reply-with-letter`, { method: "POST", body: JSON.stringify(data) });
  },
  async letterThreads() {
    return IC.api("/api/letters/threads");
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
  async adminHideCapsule(id) {
    return IC.api(`/api/admin/capsules/${id}/hide`, { method: "POST" });
  },
  async adminRestoreCapsule(id) {
    return IC.api(`/api/admin/capsules/${id}/restore`, { method: "POST" });
  },
  async adminResolveReport(id, action) {
    return IC.api(`/api/admin/reports/${id}/resolve`, { method: "POST", body: JSON.stringify({ action }) });
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

  /* AI Logs */
  async aiLogs() {
    return IC.api("/api/ai-logs");
  },
  async aiHealth() {
    return IC.api("/api/ai/health");
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
  }
};

window.API = API;

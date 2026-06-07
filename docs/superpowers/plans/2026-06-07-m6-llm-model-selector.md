# M6: LLM 模型选择器 (会话级 + 用户级 + 系统默认) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick an LLM provider/model from a 5-option dropdown in the chat header. The choice is locked to the active session (also persisted as user-level default) so all Aurora responses in that session come from the chosen model.

**Architecture:** `SessionModelRouter` is a new layer that resolves a per-session LLM provider/model. It consults `tb_dialog_session.preferred_model` first, then `tb_user_profile.preferred_model`, then `LlmConfig.activeProvider()`. LlmConfig exposes a `Map<String, LlmClient> namedClients` bean (one per provider) plus the existing default `llmClient` for non-routed callers. Frontend sends `preferredModel` when starting a session; backend stores it and serves it on every chat turn.

**Tech Stack:** Spring `@Configuration` (named beans), MyBatis-Plus (existing mappers), vanilla JS dropdown.

**Depends on:** none. Standalone, can ship any time after M0 schema is in.

---

## File Structure

**Modified:**
- `src/main/java/com/innercosmos/config/LlmConfig.java` — add `namedClients` bean, `defaultLlmClient` bean, keep `llmClient` for back-compat
- `src/main/java/com/innercosmos/entity/DialogSession.java` — add `preferredModel` field
- `src/main/java/com/innercosmos/entity/UserProfile.java` — add `preferredModel` field
- `src/main/resources/schema.sql` — ALTER `tb_dialog_session`, `tb_user_profile`
- `src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java` — call `SessionModelRouter.resolve(sessionId)` before LLM call
- `src/main/resources/static/pages/aurora-chat.html` — model dropdown in header
- `src/main/resources/static/js/api.js` — add `chatStart({ preferredModel })`

**New services:**
- `src/main/java/com/innercosmos/ai/router/SessionModelRouter.java`
- `src/main/java/com/innercosmos/ai/router/ResolvedModel.java` (record)

**New controller:**
- `src/main/java/com/innercosmos/controller/UserPreferenceController.java` (`PUT /api/user/preferred-model`)

**Tests:**
- `src/test/java/com/innercosmos/ai/router/SessionModelRouterTest.java`

---

## Tasks

### Task 1: Schema additions

**Files:**
- Modify: `src/main/resources/schema.sql`

- [ ] **Step 1: Add preferredModel to tb_dialog_session and tb_user_profile**

```sql
ALTER TABLE tb_dialog_session ADD COLUMN preferred_model VARCHAR(32);
ALTER TABLE tb_user_profile   ADD COLUMN preferred_model VARCHAR(32);
```

(H2 doesn't support `ADD COLUMN IF NOT EXISTS`. Wrap in `@PostConstruct` migration or a `try { ... } catch (Exception ignore) {}` block on startup. For this dev/demo project, drop and re-create the table is acceptable; see Risks.)

---

### Task 2: Update entities

**Files:**
- Modify: `src/main/java/com/innercosmos/entity/DialogSession.java`
- Modify: `src/main/java/com/innercosmos/entity/UserProfile.java`

- [ ] **Step 1: Add field to DialogSession**

```java
public String preferredModel;   // null = use user / system default
```

- [ ] **Step 2: Add field to UserProfile**

```java
public String preferredModel;   // null = use system default
```

---

### Task 3: LlmConfig — namedClients map

**Files:**
- Modify: `src/main/java/com/innercosmos/config/LlmConfig.java`

- [ ] **Step 1: Refactor factory to expose 5 named beans**

Add a new bean:

```java
@Bean(name = "namedLlmClients")
public Map<String, LlmClient> namedLlmClients(AiLogService aiLogService, Executor aiExecutor) {
    Map<String, LlmClient> m = new LinkedHashMap<>();
    LlmClient minimax = createProviderClient("minimax", false, aiLogService, aiExecutor);
    if (minimax != null) m.put("MINIMAX", minimax);
    LlmClient mimo = createProviderClient("mimo", false, aiLogService, aiExecutor);
    if (mimo != null) m.put("MIMO", mimo);
    LlmClient glm = createProviderClient("glm", false, aiLogService, aiExecutor);
    if (glm != null) m.put("GLM", glm);
    LlmClient deepseek = createProviderClient("deepseek", false, aiLogService, aiExecutor);
    if (deepseek != null) m.put("DEEPSEEK", deepseek);
    m.put("MOCK", new MockLlmClient(aiExecutor));
    return m;
}
```

Keep existing `llmClient` bean (just rename internally to `defaultLlmClient` for clarity; or leave the bean name `llmClient` to avoid breaking all `@Autowired LlmClient` references). The map is the new opt-in surface.

- [ ] **Step 2: Expose `public LlmClient createProviderClient`**

The existing `createProviderClient` is already `private`; change to `public` so `SessionModelRouter` can also call it for `MOCK` if needed (or just inject the map).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/innercosmos/config/LlmConfig.java src/main/resources/schema.sql src/main/java/com/innercosmos/entity/DialogSession.java src/main/java/com/innercosmos/entity/UserProfile.java
git -c commit.gpgsign=false commit -m "feat(M6): schema + entities + named LLM client map"
```

---

### Task 4: SessionModelRouter

**Files:**
- Create: `src/main/java/com/innercosmos/ai/router/SessionModelRouter.java`
- Create: `src/main/java/com/innercosmos/ai/router/ResolvedModel.java`

- [ ] **Step 1: Define ResolvedModel record**

```java
public record ResolvedModel(String provider, String model, LlmClient client) {}
```

- [ ] **Step 2: Implement router**

```java
@Component
public class SessionModelRouter {
  @Autowired(required = false) @Qualifier("namedLlmClients")
  private Map<String, LlmClient> named;
  @Autowired DialogSessionMapper sessionMapper;
  @Autowired UserProfileMapper userMapper;
  @Autowired LlmConfig llmConfig;

  public ResolvedModel resolve(Long userId, Long sessionId) {
    // 1) session-level
    String sessionPref = null;
    if (sessionId != null) {
      var s = sessionMapper.selectById(sessionId);
      if (s != null) sessionPref = s.preferredModel;
    }
    String chosen = sessionPref;
    // 2) user-level
    if (chosen == null || chosen.isBlank()) {
      var u = userMapper.selectById(userId);
      if (u != null) chosen = u.preferredModel;
    }
    // 3) system default
    if (chosen == null || chosen.isBlank()) chosen = llmConfig.activeProvider().toUpperCase();
    LlmClient client = pick(chosen);
    String modelName = modelNameFor(chosen);
    return new ResolvedModel(chosen, modelName, client);
  }

  public void setSessionPreference(Long sessionId, String provider) {
    var s = sessionMapper.selectById(sessionId);
    if (s == null) return;
    s.preferredModel = provider == null ? null : provider.toUpperCase();
    sessionMapper.updateById(s);
  }

  private LlmClient pick(String provider) {
    if (named != null && named.containsKey(provider.toUpperCase())) return named.get(provider.toUpperCase());
    return named != null ? named.get(llmConfig.activeProvider().toUpperCase()) : null;
  }
  private String modelNameFor(String provider) {
    return switch (provider.toUpperCase()) {
      case "MINIMAX"  -> llmConfig.minimax.model;
      case "MIMO"     -> llmConfig.mimo.model;
      case "GLM"      -> llmConfig.glm.model;
      case "DEEPSEEK" -> llmConfig.deepseek.model;
      default         -> llmConfig.model;
    };
  }
}
```

- [ ] **Step 3: Test**

```java
@SpringBootTest
class SessionModelRouterTest {
  @MockBean DialogSessionMapper sessionMapper;
  @MockBean UserProfileMapper userMapper;
  @Autowired LlmConfig llmConfig;
  @Autowired Map<String, LlmClient> namedLlmClients;
  @Autowired SessionModelRouter router;

  @Test void sessionLevelOverridesUser() {
    var s = new DialogSession(); s.id = 100L; s.preferredModel = "DEEPSEEK";
    var u = new UserProfile(); u.id = 1L; u.preferredModel = "MINIMAX";
    when(sessionMapper.selectById(100L)).thenReturn(s);
    when(userMapper.selectById(1L)).thenReturn(u);
    assertThat(router.resolve(1L, 100L).provider()).isEqualTo("DEEPSEEK");
  }

  @Test void userLevelUsedWhenSessionEmpty() {
    var s = new DialogSession(); s.id = 100L; s.preferredModel = null;
    var u = new UserProfile(); u.id = 1L; u.preferredModel = "MIMO";
    when(sessionMapper.selectById(100L)).thenReturn(s);
    when(userMapper.selectById(1L)).thenReturn(u);
    assertThat(router.resolve(1L, 100L).provider()).isEqualTo("MIMO");
  }

  @Test void systemDefaultUsedWhenBothEmpty() {
    when(sessionMapper.selectById(any())).thenReturn(new DialogSession());
    when(userMapper.selectById(any())).thenReturn(new UserProfile());
    assertThat(router.resolve(1L, null).provider()).isEqualTo(llmConfig.activeProvider().toUpperCase());
  }

  @Test void unknownProviderFallsBackToSystemDefault() {
    var s = new DialogSession(); s.id = 100L; s.preferredModel = "NOPE";
    when(sessionMapper.selectById(100L)).thenReturn(s);
    when(userMapper.selectById(any())).thenReturn(new UserProfile());
    assertThat(router.resolve(1L, 100L).client()).isNotNull();
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/innercosmos/ai/router/
git -c commit.gpgsign=false commit -m "feat(M6): SessionModelRouter — session > user > system default resolution"
```

---

### Task 5: Wire router into AuroraAgentServiceImpl

**Files:**
- Modify: `src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java`

- [ ] **Step 1: Inject router + use it at LLM call site**

Find the existing `LlmClient llm` autowire and the `llm.chat(...)` call in the chat handler:

```java
@Autowired SessionModelRouter modelRouter;
// ... in the chat method, before building LlmRequest:
var resolved = modelRouter.resolve(userId, sessionId);
log.info("Using LLM: provider={}, model={}", resolved.provider(), resolved.model());
LlmRequest req = new LlmRequest(...).withProvider(resolved.provider()).withModel(resolved.model());
String reply = resolved.client().chat(req).content();
```

(If `LlmRequest` doesn't have `withProvider` / `withModel` setters yet, add them in `src/main/java/com/innercosmos/ai/client/LlmRequest.java`. This is a small additive change.)

- [ ] **Step 2: Add `lastProvider` log to `tb_ai_interaction_log`**

The existing `aiLogService` records provider. Make sure the chosen provider name is in the request metadata so it lands in the log row.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java src/main/java/com/innercosmos/ai/client/LlmRequest.java
git -c commit.gpgsign=false commit -m "feat(M6): route Aurora chat through SessionModelRouter per session"
```

---

### Task 6: User preference controller (persist as user default)

**Files:**
- Create: `src/main/java/com/innercosmos/controller/UserPreferenceController.java`

- [ ] **Step 1: Controller**

```java
@RestController
@RequestMapping("/api/user")
public class UserPreferenceController extends BaseController {
  @Autowired UserProfileMapper userMapper;
  @Autowired SessionModelRouter router;

  @PutMapping("/preferred-model")
  public ApiResponse<Boolean> setPreferredModel(@RequestBody Map<String, String> body, HttpSession session) {
    Long userId = currentUserId(session);
    var u = userMapper.selectById(userId);
    if (u == null) throw new ApiException(404, "user not found");
    u.preferredModel = body.get("provider");
    userMapper.updateById(u);
    return ApiResponse.ok(true);
  }
}
```

---

### Task 7: Frontend — model dropdown in chat header

**Files:**
- Modify: `src/main/resources/static/pages/aurora-chat.html`
- Modify: `src/main/resources/static/js/api.js`

- [ ] **Step 1: HTML — add dropdown next to existing chat header**

```html
<label class="model-picker">
  模型
  <select id="modelPicker" onchange="onModelChange()">
    <option value="">系统默认</option>
    <option value="MINIMAX">MiniMax M3</option>
    <option value="MIMO">Mimo v2.5</option>
    <option value="GLM">GLM-4 Flash</option>
    <option value="DEEPSEEK">DeepSeek v4 Flash</option>
    <option value="MOCK">Mock (本地)</option>
  </select>
</label>
```

- [ ] **Step 2: JS — wire it up**

```js
async function onModelChange() {
  const provider = document.getElementById("modelPicker").value;
  // 1) persist as user default
  await API.setPreferredModel({ provider: provider || null });
  // 2) bind to current session
  if (window.sid) await API.setSessionModel(sid, provider);
  IC.toast(provider ? "已切换到 " + provider : "已切回系统默认", "success");
}
async function startChat(preferredCapsule) {
  const r = await API.chatStart({ capsuleId: preferredCapsule?.id, preferredModel: document.getElementById("modelPicker").value || null });
  window.sid = r.data.sessionId;
  return r.data;
}
```

- [ ] **Step 3: api.js additions**

```js
setPreferredModel: (body) => IC.api("/api/user/preferred-model", { method: "PUT", body: JSON.stringify(body) }),
setSessionModel:   (sessionId, provider) => IC.api(`/api/aurora/session/${sessionId}/model`, { method: "PUT", body: JSON.stringify({ provider }) }),
```

(For `setSessionModel`, add the corresponding `PUT /api/aurora/session/{id}/model` endpoint that calls `router.setSessionPreference(id, provider)`. Trivial 3-line controller; add it next to `UserPreferenceController`.)

- [ ] **Step 4: Sanitize model value to one of 5 enum values**

In the router, if `chosen` is anything other than the 5 enum values, fall back to system default. This prevents the dropdown from accepting arbitrary user input via the API.

- [ ] **Step 5: Commit + restart**

```bash
git add src/main/java/com/innercosmos/controller/UserPreferenceController.java src/main/resources/static/pages/aurora-chat.html src/main/resources/static/js/api.js
git -c commit.gpgsign=false commit -m "feat(M6): model selector dropdown + per-session lock + user default"
```

---

## Acceptance Criteria

- Open aurora-chat, pick `DEEPSEEK` from dropdown, send a message → response comes back; `tb_ai_interaction_log.last_provider = "DEEPSEEK"`
- Pick `MINIMAX`, switch to a fresh `tb_dialog_session` → that session's `preferred_model = "MINIMAX"`
- Pick `MIMO`, log out, log in as same user → dropdown reopens with `MIMO` selected (user-level default)
- Pick empty `""` (system default) → Aurora uses whatever `llm.provider` is set to in `application.yml`
- Frontend dropdown has exactly 5 options; no other providers selectable

## Dependencies

- none (standalone; can be implemented any time after M0 schema is merged)

## Risks

- H2 `ALTER TABLE ADD COLUMN` fails if column exists → wrap in try/catch on startup, or use a `SchemaInitializer` runner
- Some `LlmClient` impls (`MockLlmClient`) cannot be created without arguments → guard `createProviderClient` to return null if key is empty, and only put non-null clients in the map
- Per-session switching mid-conversation may cause inconsistent tone in the same chat history → acceptable; we lock at the session boundary, not the turn boundary
- Frontend dropdown sends raw provider name → server-side enum validation already in router (Task 7 step 4)


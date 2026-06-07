# M5: 模式感知 Prompt (DAILY_TALK / THOUGHT / SOCRATIC 真生效) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 3 existing Aurora modes (DAILY_TALK / THOUGHT_CLARIFY / SOCRATIC) actually affect the prompt in prod mode (currently only `AuroraContentLibrary` mock data respects them). Mode switch must not lose context.

**Architecture:** Inject a mode-specific segment into `PromptBuilder` based on `tb_dialog_session.current_mode`. On mode switch, insert a hidden "system turn" preserving history. Optionally trigger a brief Aurora "mode-acknowledgement" via the M2 delivery channel.

**Tech Stack:** Existing LLM clients, MyBatis-Plus.

**Depends on:** M1 (uses portrait for mode tuning), M2 (proactive channel for mode-acknowledgement).

---

## File Structure

**New services:**
- `src/main/java/com/innercosmos/ai/mode/ModeStrategy.java`
- `src/main/java/com/innercosmos/ai/mode/DAILY_TALK_Strategy.java`
- `src/main/java/com/innercosmos/ai/mode/THOUGHT_CLARIFY_Strategy.java`
- `src/main/java/com/innercosmos/ai/mode/SOCRATIC_Strategy.java`
- `src/main/java/com/innercosmos/ai/mode/ModeRegistry.java`
- `src/main/java/com/innercosmos/ai/mode/ModeSwitchService.java`

**New controller:**
- `src/main/java/com/innercosmos/controller/AuroraModeController.java` (`POST /api/aurora/mode/switch`)

**Modified:**
- `src/main/java/com/innercosmos/entity/DialogSession.java` — add `current_mode` field
- `src/main/resources/schema.sql` — add `current_mode VARCHAR(16) DEFAULT 'DAILY_TALK'` to `tb_dialog_session`
- `src/main/java/com/innercosmos/ai/prompt/PromptBuilder.java` — call `ModeRegistry.getStrategy(mode).segment()`
- `src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java` — route to mode's strategy
- `src/main/resources/static/pages/aurora-chat.html` — mode button shows current state; switch triggers POST
- `src/main/java/com/innercosmos/ai/prompt/AuroraContentLibrary.java` — keep but mark as legacy (only used when LLM is mock mode)

**Tests:**
- `src/test/java/com/innercosmos/ai/mode/ModeRegistryTest.java`

---

## Tasks

### Task 1: ModeStrategy interface

**Files:**
- Create: `src/main/java/com/innercosmos/ai/mode/ModeStrategy.java`

- [ ] **Step 1: Define interface**

```java
public interface ModeStrategy {
  String name();      // DAILY_TALK / THOUGHT_CLARIFY / SOCRATIC
  String segment();   // prompt segment injected when this mode is active
  double temperature(); // LLM temperature for this mode
  boolean requiresMultiTurnAcknowledgement();  // should mode-switch trigger a proactive ack
}
```

---

### Task 2: 3 mode implementations

- [ ] **Step 1: `DAILY_TALK_Strategy.java`**

```java
@Component
public class DAILY_TALK_Strategy implements ModeStrategy {
  public String name() { return "DAILY_TALK"; }
  public String segment() {
    return """
      【当前模式: 今日倾诉】
      角色: 朋友式陪伴。
      行为: 先接住当下情绪, 共鸣, 不急着分析或给建议。
      问句类型: 开放式、"听起来..."、"你当时..."。
      节奏: 慢, 留白, 让人把话说完。
      """;
  }
  public double temperature() { return 0.85; }
  public boolean requiresMultiTurnAcknowledgement() { return false; }
}
```

- [ ] **Step 2: `THOUGHT_CLARIFY_Strategy.java`**

```java
@Component
public class THOUGHT_CLARIFY_Strategy implements ModeStrategy {
  public String name() { return "THOUGHT_CLARIFY"; }
  public String segment() {
    return """
      【当前模式: 思维整理】
      角色: 结构化协作者。
      行为: 把混乱内容拆成 5 栏: 事实 / 感受 / 担心 / 需要 / 下一步。
      问句类型: 闭合式确认、"具体是...?"、"你想达成的下一步是...?"。
      节奏: 中等, 一轮一栏, 不跳。
      不要给建议, 先帮 ta 把现状看清楚。
      """;
  }
  public double temperature() { return 0.55; }
  public boolean requiresMultiTurnAcknowledgement() { return true; }
}
```

- [ ] **Step 3: `SOCRATIC_Strategy.java`**

```java
@Component
public class SOCRATIC_Strategy implements ModeStrategy {
  public String name() { return "SOCRATIC"; }
  public String segment() {
    return """
      【当前模式: 苏格拉底追问】
      角色: 温和的提问者。
      行为: 一次只问一个关键假设, 帮助 ta 自己看清。
      问句类型: "如果...会怎样?"、"你说的 X, 我听到的是 Y, 对吗?"、"你最担心...的什么?"。
      节奏: 慢, 每轮只推进一个核心假设。
      不要直接给结论, 也不要给建议, 你的角色是镜子。
      """;
  }
  public double temperature() { return 0.65; }
  public boolean requiresMultiTurnAcknowledgement() { return true; }
}
```

---

### Task 3: ModeRegistry

- [ ] **Step 1: Implement**

```java
@Component
public class ModeRegistry {
  private final Map<String, ModeStrategy> byName;
  public ModeRegistry(List<ModeStrategy> all) {
    byName = all.stream().collect(Collectors.toMap(ModeStrategy::name, s -> s));
  }
  public ModeStrategy get(String mode) {
    return byName.getOrDefault(mode, byName.get("DAILY_TALK"));
  }
  public List<String> names() { return List.of("DAILY_TALK", "THOUGHT_CLARIFY", "SOCRATIC"); }
}
```

---

### Task 4: PromptBuilder injection

**Files:**
- Modify: `src/main/java/com/innercosmos/ai/prompt/PromptBuilder.java`

- [ ] **Step 1: Add mode segment**

In the method that builds the system prompt, before returning:

```java
String modeSegment = modeRegistry.get(currentMode).segment();
return baseSystemPrompt + "\n\n" + modeSegment;
```

Also pass `temperature = modeRegistry.get(currentMode).temperature()` into the LLM request.

---

### Task 5: ModeSwitchService + hidden system turn

**Files:**
- Create: `src/main/java/com/innercosmos/ai/mode/ModeSwitchService.java`

- [ ] **Step 1: Implement**

```java
@Service
public class ModeSwitchService {
  @Autowired ChatSessionMapper sessionMapper;
  @Autowired ChatMessageMapper messageMapper;
  @Autowired ProactiveDeliveryChannel deliveryChannel;
  @Autowired ModeRegistry modeRegistry;
  @Autowired LlmClient llm;

  public void switchTo(Long userId, Long sessionId, String newMode) {
    var sess = sessionMapper.selectById(sessionId);
    String oldMode = sess.currentMode == null ? "DAILY_TALK" : sess.currentMode;
    if (oldMode.equals(newMode)) return;
    // 1) update session
    sess.currentMode = newMode;
    sessionMapper.updateById(sess);
    // 2) insert hidden system turn
    var turn = new ChatMessage();
    turn.sessionId = sessionId; turn.userId = userId; turn.speaker = "SYSTEM";
    turn.role = "SYSTEM"; turn.kind = "MODE_TRANSITION";
    turn.content = "--- 用户希望切换到「" + newMode + "」模式 ---\n"
        + modeRegistry.get(newMode).segment()
        + "\n注意: 用户原本在进行 " + oldMode + ", ta 不是否定之前的陪伴, 是想升级到下一步。承接这份信任。";
    turn.visibleToUser = false;
    messageMapper.insert(turn);
    // 3) optionally trigger Aurora acknowledgement via proactive channel
    if (modeRegistry.get(newMode).requiresMultiTurnAcknowledgement()) {
      String ackPrompt = "用户切到了 " + newMode + " 模式, 请给一句自然的承接, 然后用这个模式的特点回应。";
      String ack = llm.chat(new LlmRequest("MODE_ACK", ackPrompt, false)).content();
      deliveryChannel.push(userId, ack, "mode_acknowledgement");
    }
  }
}
```

- [ ] **Step 2: Controller**

```java
@PostMapping("/api/aurora/mode/switch")
public ApiResponse<Boolean> switchMode(@RequestBody Map<String, String> body, HttpSession session) {
  var userId = currentUserId(session);
  Long sessionId = Long.parseLong(body.get("sessionId"));
  String mode = body.get("mode");
  modeSwitch.switchTo(userId, sessionId, mode);
  return ApiResponse.ok(true);
}
```

---

### Task 6: Frontend — show current mode + click to switch

**Files:**
- Modify: `src/main/resources/static/pages/aurora-chat.html`

- [ ] **Step 1: Highlight active mode**

```js
function buildModes() {
  document.getElementById("modes").innerHTML = modeCopy.entries().map(([k, m]) =>
    `<button data-mode="${k}" class="${k === mode ? 'active' : ''}" onclick="setMode('${k}')">${m.label}</button>`
  ).join("");
}
async function setMode(newMode) {
  if (newMode === mode) return;
  await API.auroraModeSwitch({ sessionId: sid, mode: newMode });
  mode = newMode;
  buildModes();
  IC.toast("已切换到 " + modeCopy[newMode].label, "success");
}
```

- [ ] **Step 2: Add `API.auroraModeSwitch` in api.js**

```js
auroraModeSwitch: (body) => IC.api("/api/aurora/mode/switch", { method: "POST", body: JSON.stringify(body) })
```

- [ ] **Step 3: Commit + copy + restart**

```bash
git add src/main/java/com/innercosmos/ai/mode/ src/main/java/com/innercosmos/controller/AuroraModeController.java src/main/java/com/innercosmos/ai/prompt/PromptBuilder.java src/main/resources/static/pages/aurora-chat.html src/main/resources/static/js/api.js
git -c commit.gpgsign=false commit -m "feat(M5): mode-aware prompt — DAILY_TALK/THOUGHT/SOCRATIC each route their own segment + temperature + ack"
```

---

## Acceptance Criteria

- Switch to THOUGHT_CLARIFY → Aurora's next response uses 5-栏 structure ("事实是..." / "感受上..." etc.)
- Switch to SOCRATIC → Aurora asks a single Socratic question, doesn't give advice
- Mode switch visible in chat header (active class)
- Aurora's response is different in temperature (response length / tone varies) per mode
- Original conversation history preserved across switch (no messages lost)

## Dependencies

- M1, M2 (proactive channel for ack)

## Risks

- Hidden system turn may confuse LLM if too long → keep under 200 tokens
- Temperature 0.85 for DAILY_TALK may produce very long responses → cap max_tokens
- Mode ack via deliveryChannel may arrive after user already sent a new message → LLM's next turn should be the source of truth

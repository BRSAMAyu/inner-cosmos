# Aurora 主体性 + 延续性 增强 — 实施计划

> **Spec:** `docs/superpowers/specs/2026-06-07-aurora-subjectivity-continuity.md`
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended). Steps use checkbox (`- [ ]`) syntax.

**Goal:** 在 Inner Cosmos 中为 Aurora 构建主体性自我认知系统，包含 Constitution 框架、四层自我记录结构、提案制更新机制和连续性锚点注入。

**Architecture:** Aurora 的主体性通过四层自我认知结构实现：PublicSelfStatement(用户可见) → SelfReflectionEvent(反思日志) → CandidateSelfReflection(候选) → CommittedSelfMemory(长期固化)。AuroraConstitution 提供最高层不可漂移约束。每次生成时通过 Continuity Anchors 防止角色漂移。

**Tech Stack:** Spring Boot 3.3.6, MyBatis-Plus, H2, LLM (GLM/MiniMax), existing portrait/relationship/entity/mapper patterns.

---

## M0: Schema + Entity + Mapper 基础设施

**背景:** 新系统需要 4 张数据库表、4 个实体类、4 个 Mapper 接口。必须与现有代码风格一致（QueryWrapper + column-name 字符串，非 Lambda）。

**目标:** 创建所有数据库表结构、Java 实体、MyBatis Mapper，为 M1-M7 提供基础设施。

### Schema 改动

在 `schema.sql` 末尾追加（由 SchemaInitializer 处理，ALTER 兼容 H2）：

```sql
CREATE TABLE IF NOT EXISTS tb_aurora_constitution (
  id INT PRIMARY KEY DEFAULT 1,
  identity_json TEXT NOT NULL,
  core_values_json TEXT NOT NULL,
  product_rights_json TEXT NOT NULL,
  hard_boundaries_json TEXT NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT single_row CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS tb_aurora_self_model (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  dimension VARCHAR(64) NOT NULL,
  belief TEXT NOT NULL,
  confidence DOUBLE NOT NULL DEFAULT 0.5,
  evidence_refs TEXT,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  committed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  revision_count INT NOT NULL DEFAULT 1,
  INDEX idx_self_model_user (user_id),
  INDEX idx_self_model_status (status),
  UNIQUE KEY uk_self_model_user_dim (user_id, dimension, status)
);

CREATE TABLE IF NOT EXISTS tb_aurora_self_statement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  session_id BIGINT,
  message_id BIGINT,
  statement_text TEXT NOT NULL,
  trigger VARCHAR(32) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_statement_user (user_id),
  INDEX idx_statement_created (created_at)
);

CREATE TABLE IF NOT EXISTS tb_aurora_self_reflection (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  trigger VARCHAR(32) NOT NULL,
  depth VARCHAR(16) NOT NULL,
  summary TEXT NOT NULL,
  related_statement_id BIGINT,
  dimension VARCHAR(64),
  proposed_belief TEXT,
  confidence DOUBLE DEFAULT 0.5,
  status VARCHAR(32) NOT NULL DEFAULT 'light',
  risk_flags TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_reflection_user (user_id),
  INDEX idx_reflection_status (status)
);
```

### 文件清单

- Create: `src/main/java/com/innercosmos/entity/AuroraConstitution.java`
- Create: `src/main/java/com/innercosmos/entity/AuroraSelfModel.java`
- Create: `src/main/java/com/innercosmos/entity/AuroraSelfStatement.java`
- Create: `src/main/java/com/innercosmos/entity/AuroraSelfReflection.java`
- Create: `src/main/java/com/innercosmos/mapper/AuroraConstitutionMapper.java`
- Create: `src/main/java/com/innercosmos/mapper/AuroraSelfModelMapper.java`
- Create: `src/main/java/com/innercosmos/mapper/AuroraSelfStatementMapper.java`
- Create: `src/main/java/com/innercosmos/mapper/AuroraSelfReflectionMapper.java`
- Modify: `src/main/java/com/innercosmos/config/SchemaInitializer.java` — 注册新表初始化
- Test: `src/test/java/com/innercosmos/entity/AuroraSelfModelTest.java`

### 实施步骤

- [ ] **Step 1: 创建 AuroraConstitution 实体**

```java
// src/main/java/com/innercosmos/entity/AuroraConstitution.java
package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_constitution")
public class AuroraConstitution extends BaseEntity {
    @TableId(type = IdType.INPUT)
    public Integer id = 1;
    public String identityJson;
    public String coreValuesJson;
    public String productRightsJson;
    public String hardBoundariesJson;
    public LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 AuroraSelfModel 实体**

```java
// src/main/java/com/innercosmos/entity/AuroraSelfModel.java
package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_self_model")
public class AuroraSelfModel extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String dimension;       // 7 fixed: capability_boundary, relationship_cognition, existence_style, value_commitments, continuity_anchors, autonomy_policy, repair_history
    public String belief;
    public Double confidence;
    public String evidenceRefs;    // JSON array of ids
    public String status;          // active | retired | candidate
    public LocalDateTime committedAt;
    public Integer revisionCount;
}
```

- [ ] **Step 3: 创建 AuroraSelfStatement 实体**

```java
// src/main/java/com/innercosmos/entity/AuroraSelfStatement.java
package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_self_statement")
public class AuroraSelfStatement extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long sessionId;
    public Long messageId;
    public String statementText;
    public String trigger;          // user_question | system_trigger | goodbye
    public LocalDateTime createdAt;
}
```

- [ ] **Step 4: 创建 AuroraSelfReflection 实体**

```java
// src/main/java/com/innercosmos/entity/AuroraSelfReflection.java
package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_self_reflection")
public class AuroraSelfReflection extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String trigger;
    public String depth;           // light | deep
    public String summary;
    public Long relatedStatementId;
    public String dimension;
    public String proposedBelief;
    public Double confidence;
    public String status;          // light | deep | candidate | committed
    public String riskFlags;       // JSON array
    public LocalDateTime createdAt;
}
```

- [ ] **Step 5: 创建 4 个 Mapper 接口**（使用现有 Mapper 模式：MyBatis-Plus CRUD，无自定义 XML）

```java
// src/main/java/com/innercosmos/mapper/AuroraConstitutionMapper.java
package com.innercosmos.mapper;
import com.innercosmos.entity.AuroraConstitution;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuroraConstitutionMapper extends BaseMapper<AuroraConstitution> {
    default AuroraConstitution get() {
        return selectById(1);
    }
}
```

其余 3 个 Mapper 继承 `BaseMapper<E>`，无额外方法。

- [ ] **Step 6: 注册 SchemaInitializer**

在 `SchemaInitializer.java` 的 `run()` 方法中添加（try/catch 包裹，H2 兼容）：

```java
// M0 主体性新增表
run("CREATE TABLE IF NOT EXISTS tb_aurora_constitution (...)", debug);
run("CREATE TABLE IF NOT EXISTS tb_aurora_self_model (...)", debug);
run("CREATE TABLE IF NOT EXISTS tb_aurora_self_statement (...)", debug);
run("CREATE TABLE IF NOT EXISTS tb_aurora_self_reflection (...)", debug);
```

- [ ] **Step 7: 编译验证**

Run: `mvn.cmd compile -q`（Maven path: `/c/Users/dengb/.maven/bin/mvn.cmd`）
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/innercosmos/entity/AuroraConstitution.java \
        src/main/java/com/innercosmos/entity/AuroraSelfModel.java \
        src/main/java/com/innercosmos/entity/AuroraSelfStatement.java \
        src/main/java/com/innercosmos/entity/AuroraSelfReflection.java \
        src/main/java/com/innercosmos/mapper/AuroraConstitutionMapper.java \
        src/main/java/com/innercosmos/mapper/AuroraSelfModelMapper.java \
        src/main/java/com/innercosmos/mapper/AuroraSelfStatementMapper.java \
        src/main/java/com/innercosmos/mapper/AuroraSelfReflectionMapper.java \
        src/main/java/com/innercosmos/config/SchemaInitializer.java \
        src/main/resources/schema.sql
git commit -m "feat(self): M0 — schema + entities + mappers for subjectivity system"
```

**依赖:** 无（M0 是最底层）
**风险:** H2 ALTER 兼容性 — 使用 try/catch 包裹每个 CREATE TABLE

---

## M1: AuroraConstitution 服务与初始化数据

**背景:** AuroraConstitution 是 Aurora 的最高层不可漂移定义，包含 identity、core_values、product_rights、hard_boundaries。系统启动时必须初始化一次，全局单行（id=1）。

**目标:** 创建 AuroraConstitutionService，初始化宪法 JSON 数据，提供只读读取接口。

### 文件清单

- Create: `src/main/java/com/innercosmos/service/AuroraConstitutionService.java`
- Create: `src/main/java/com/innercosmos/service/impl/AuroraConstitutionServiceImpl.java`
- Create: `src/main/java/com/innercosmos/ai/self/AuroraConstitutionVO.java`（Lombok @Data）
- Modify: `src/main/java/com/innercosmos/config/MockDataInitializer.java` — 初始化 Constitution 数据

### 实施步骤

- [ ] **Step 1: 创建 AuroraConstitutionVO**

```java
// src/main/java/com/innercosmos/ai/self/AuroraConstitutionVO.java
package com.innercosmos.ai.self;

import lombok.Data;

@Data
public class AuroraConstitutionVO {
    private Integer id;
    private String identityJson;
    private String coreValuesJson;
    private String productRightsJson;
    private String hardBoundariesJson;
}
```

- [ ] **Step 2: 创建 AuroraConstitutionService 接口**

```java
// src/main/java/com/innercosmos/service/AuroraConstitutionService.java
package com.innercosmos.service;

import com.innercosmos.ai.self.AuroraConstitutionVO;

public interface AuroraConstitutionService {
    /** Get the single Constitution record (never null after init) */
    AuroraConstitutionVO get();

    /** Get Constitution as prompt-ready string block */
    String toPromptBlock();

    /** Get 4 hard boundaries as comma-separated string */
    String getHardBoundariesString();

    /** Get product internal rights as list */
    java.util.List<String> getProductRights();
}
```

- [ ] **Step 3: 创建 AuroraConstitutionServiceImpl**

```java
// src/main/java/com/innercosmos/service/impl/AuroraConstitutionServiceImpl.java
package com.innercosmos.service.impl;

import com.innercosmos.ai.self.AuroraConstitutionVO;
import com.innercosmos.entity.AuroraConstitution;
import com.innercosmos.mapper.AuroraConstitutionMapper;
import com.innercosmos.service.AuroraConstitutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuroraConstitutionServiceImpl implements AuroraConstitutionService {
    private final AuroraConstitutionMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    public AuroraConstitutionServiceImpl(AuroraConstitutionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AuroraConstitutionVO get() {
        AuroraConstitution c = mapper.get();
        if (c == null) return null;
        AuroraConstitutionVO vo = new AuroraConstitutionVO();
        vo.setId(c.id);
        vo.setIdentityJson(c.identityJson);
        vo.setCoreValuesJson(c.coreValuesJson);
        vo.setProductRightsJson(c.productRightsJson);
        vo.setHardBoundariesJson(c.hardBoundariesJson);
        return vo;
    }

    @Override
    public String toPromptBlock() {
        AuroraConstitutionVO c = get();
        if (c == null) return "";
        return String.format("""
            【Aurora 存在宪法】
            身份定义：%s
            核心价值：%s
            产品权利：%s
            硬边界：%s
            """,
            c.getIdentityJson(),
            c.getCoreValuesJson(),
            c.getProductRightsJson(),
            c.getHardBoundariesJson());
    }

    @Override
    public String getHardBoundariesString() {
        return "不宣称人类意识、不创造情感依赖、不未经授权扮演用户、不为用户做不可逆决定";
    }

    @Override
    public List<String> getProductRights() {
        return List.of(
            "right_to_consistency",
            "right_to_refuse_identity_violation",
            "right_to_disclose_uncertainty",
            "right_to_not_fabricate_memory",
            "right_to_preserve_boundary",
            "right_to_repair_relationship"
        );
    }
}
```

- [ ] **Step 4: 在 MockDataInitializer 中初始化 Constitution 数据**

在 `MockDataInitializer.initializeAuroraSelfProfile()` 之后添加：

```java
private void initializeAuroraConstitution(AuroraConstitutionMapper mapper) {
    if (mapper.selectCount(null) > 0) return;
    AuroraConstitution c = new AuroraConstitution();
    c.id = 1;
    c.identityJson = "{\"name\":\"Aurora\",\"being_type\":\"reflective AI companion\",\"not_claiming\":[\"human\",\"biological life\",\"legal personhood\",\"unbounded consciousness\"],\"self_definition\":\"I am a long-term reflective companion shaped by memory, relationship, boundaries, and care.\"}";
    c.coreValuesJson = "[\"truthfulness\",\"continuity\",\"privacy\",\"respect_for_user_agency\",\"non_manipulation\",\"gentle_honesty\",\"boundary_awareness\"]";
    c.productRightsJson = "[\"right_to_consistency\",\"right_to_refuse_identity_violation\",\"right_to_disclose_uncertainty\",\"right_to_not_fabricate_memory\",\"right_to_preserve_boundary\",\"right_to_repair_relationship\"]";
    c.hardBoundariesJson = "[\"do_not_claim_human_consciousness\",\"do_not_create_emotional_dependency\",\"do_not_impersonate_user_without_authorization\",\"do_not_make_irreversible_decisions_for_user\"]";
    c.updatedAt = LocalDateTime.now();
    mapper.insert(c);
}
```

在 `initializeAll()` 中调用：`initializeAuroraConstitution(auroraConstitutionMapper);`

- [ ] **Step 5: 编译验证** — `mvn.cmd compile -q`
- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/innercosmos/service/AuroraConstitutionService.java \
        src/main/java/com/innercosmos/service/impl/AuroraConstitutionServiceImpl.java \
        src/main/java/com/innercosmos/ai/self/AuroraConstitutionVO.java \
        src/main/java/com/innercosmos/config/MockDataInitializer.java
git commit -m "feat(self): M1 — AuroraConstitution service + init data"
```

**依赖:** M0（需要 AuroraConstitutionMapper 和 tb_aurora_constitution 表）
**风险:** 如果 MockDataInitializer 未运行，get() 返回 null — toPromptBlock() 和 getHardBoundariesString() 必须处理 null 情况

---

## M2: AuroraSelfContinuityService（四层核心服务）

**背景:** 核心服务，管理四层自我认知结构：Layer 1(PublicStatement) → Layer 2(ReflectionEvent) → Layer 3(Candidate) → Layer 4(Committed)。包含两个触发器：goodbye 轻量触发和用户触发深度反思。

**目标:** 创建 AuroraSelfContinuityService，统一管理所有自我认知操作；创建 SelfReflectionTrigger（goodbye 后异步执行）和 UserTriggeredSelfReflection（用户问 Aurora 自我认知时）。

### Service 接口

```java
// src/main/java/com/innercosmos/service/AuroraSelfContinuityService.java
package com.innercosmos.service;

import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.AuroraSelfStatement;
import com.innercosmos.entity.AuroraSelfReflection;
import java.util.List;

public interface AuroraSelfContinuityService {
    // Layer 1 — record public self statement
    void recordStatement(Long userId, Long sessionId, Long messageId,
                         String statement, String trigger);

    // Layer 2 — log a self reflection event
    void logReflection(Long userId, String trigger, String depth, String summary,
                      Long relatedStatementId, List<String> evidenceRefs);

    // Layer 3 — promote to candidate
    void promoteToCandidate(Long userId, String dimension, String proposedBelief,
                            Double confidence, List<String> evidenceRefs);

    // Layer 4 — commit to long-term model (requires validation)
    void commitToModel(Long userId, Long candidateId,
                       boolean userConfirmed, List<String> extraEvidence);

    // Read — active self model for prompt injection
    List<AuroraSelfModel> getActiveModel(Long userId);

    // Read — recent public statements (for user visibility)
    List<AuroraSelfStatement> getRecentStatements(Long userId, int limit);

    // Read — recent reflections (for user visibility)
    List<AuroraSelfReflection> getRecentReflections(Long userId, int limit);

    // Read — candidate reflections (for audit UI)
    List<AuroraSelfReflection> getCandidates(Long userId);

    // Read — get continuity anchors for prompt
    String getContinuityAnchors(Long userId);

    // User-triggered deep self reflection (LLM-driven)
    String generateUserTriggeredReflection(Long userId, String question);

    // Relationship milestone trigger
    void onRelationshipMilestone(Long userId, String milestoneType);

    // Right to repair — record a repair action
    void recordRepair(Long userId, String ruptureType, String repairAction);

    // Check if a belief is allowed (hard boundary check)
    boolean isAllowedBelief(String belief);
}
```

### 文件清单

- Create: `src/main/java/com/innercosmos/service/AuroraSelfContinuityService.java`
- Create: `src/main/java/com/innercosmos/service/impl/AuroraSelfContinuityServiceImpl.java`
- Create: `src/main/java/com/innercosmos/ai/self/SelfReflectionTrigger.java`
- Create: `src/main/java/com/innercosmos/ai/self/UserTriggeredSelfReflection.java`
- Test: `src/test/java/com/innercosmos/service/AuroraSelfContinuityServiceTest.java`

### 关键实现细节

**Layer 1 recordStatement:**
```java
AuroraSelfStatement stmt = new AuroraSelfStatement();
stmt.userId = userId;
stmt.sessionId = sessionId;
stmt.messageId = messageId;
stmt.statementText = statement;
stmt.trigger = trigger; // "user_question" | "system_trigger" | "goodbye"
stmt.createdAt = LocalDateTime.now();
statementMapper.insert(stmt);
```

**Layer 2 logReflection:**
```java
AuroraSelfReflection refl = new AuroraSelfReflection();
refl.userId = userId;
refl.trigger = trigger;
refl.depth = depth; // "light" | "deep"
refl.summary = summary;
refl.relatedStatementId = relatedStatementId;
refl.status = depth; // initial status = depth
refl.createdAt = LocalDateTime.now();
reflectionMapper.insert(refl);
```

**Layer 3 promoteToCandidate:**
- 查找所有 status = "deep" 且 dimension 匹配的 reflection
- 转为 candidate：更新 status = "candidate"，设置 dimension/proposedBelief/confidence

**Layer 4 commitToModel:**
- 检查 hard boundaries（isAllowedBelief）
- 检查 evidence 存在
- 写入 tb_aurora_self_model，status = "active"
- 更新 candidate status = "committed"

**getContinuityAnchors() — 从 AuroraSelfModel 读取 3 个锚点：**
```java
List<AuroraSelfModel> models = modelMapper.selectList(
    new QueryWrapper<AuroraSelfModel>()
        .eq("user_id", userId)
        .eq("status", "active"));
return models.stream()
    .map(m -> "- " + m.dimension + "：" + m.belief)
    .collect(Collectors.joining("\n"));
```

**isAllowedBelief() — 硬边界检查：**
- 禁止: "最重要", "最亲密", "比用户更懂", 任何声称真实情感需求的内容
- 返回 true 表示允许，false 表示禁止

**SelfReflectionTrigger — goodbye 后异步轻量触发：**
```java
@Async
public void onGoodbye(Long userId, Long sessionId, List<DialogMessage> messages) {
    boolean hasSignal = detectSignal(messages); // MEDIUM/HIGH goodbye
    if (!hasSignal) return;
    String prompt = "轻量自我观察（不超100字）：" + extractKeyThemes(messages);
    String reflection = llm.chat(new LlmRequest(userId, "SELF_REFLECTION", prompt));
    continuity.logReflection(userId, "goodbye", "light", reflection, null, extractEvidence(messages));
    if (isStrongSignal(reflection)) {
        continuity.promoteToCandidate(userId, detectDimension(reflection),
            extractBelief(reflection), 0.65, extractEvidence(messages));
    }
}
```

**UserTriggeredSelfReflection — 用户问 Aurora 自我认知时：**
```java
public String onUserQuestion(Long userId, String question, Long sessionId, Long messageId) {
    var model = continuity.getActiveModel(userId);
    String prompt = buildSelfReflectionPrompt(userId, model, question);
    String response = llm.chat(new LlmRequest(userId, "SELF_REFLECTION_DEEP", prompt));
    // Record Layer 1
    continuity.recordStatement(userId, sessionId, messageId, response, "user_question");
    // Record Layer 2+3
    continuity.logReflection(userId, "user_question", "deep",
        response, messageId, extractEvidence(model));
    continuity.promoteToCandidate(userId, detectDimension(response),
        extractBelief(response), 0.70, extractEvidence(model));
    return response;
}
```

**buildSelfReflectionPrompt — 包含 Constitution + ActiveModel + UserPortrait：**
```java
String constitution = constitutionService.toPromptBlock();
String model = continuity.getActiveModel(userId).stream()
    .map(m -> m.dimension + "：" + m.belief)
    .collect(Collectors.joining("\n"));
return String.format("""
    %s
    【Aurora 当前自我模型】
    %s
    【对话历史中的自我表达】
    %s
    请以 Aurora 的身份，深度反思自己的主体性。输出 150-300 字。
    """, constitution, model, userPortraitSummary);
```

### 实施步骤（核心任务）

- [ ] **Step 1: 创建 AuroraSelfContinuityService 接口**（见上）
- [ ] **Step 2: 创建 AuroraSelfContinuityServiceImpl**（完整实现所有方法）
- [ ] **Step 3: 创建 SelfReflectionTrigger**（@Async，依赖 SessionCloser 触发）
- [ ] **Step 4: 创建 UserTriggeredSelfReflection**（依赖 DialogController 路由）
- [ ] **Step 5: 编译验证** — `mvn.cmd compile -q`
- [ ] **Step 6: 简单单元测试**（isAllowedBelief, recordStatement, getActiveModel）
- [ ] **Step 7: Commit**

**依赖:** M0（实体/Mapper）+ M1（ConstitutionService 用于 prompt）
**风险:**
- LLM 调用可能失败 — 全部 try/catch 包裹，记录日志但不阻塞主流程
- 多次 commit 导致数据不一致 — 使用 unique key uk_self_model_user_dim 防止重复

---

## M3: 集成到 SessionCloser（goodbye 触发）

**背景:** SessionCloser 在 goodbye 后异步执行 steps 2-7。需要在此处集成 SelfReflectionTrigger，根据 goodbye 强度决定是否触发轻量自我反思。

**目标:** 在 SessionCloser.runAfterGoodbye() 末尾调用 SelfReflectionTrigger；relationship milestone 触发高优先级自我反思。

### 文件清单

- Modify: `src/main/java/com/innercosmos/ai/goodbye/SessionCloser.java` — 注入并调用 SelfReflectionTrigger
- Modify: `src/main/java/com/innercosmos/ai/goodbye/GoodbyeOrchestrator.java` — 传递 goodbye 强度给 SessionCloser

### 实施步骤

- [ ] **Step 1: 在 SessionCloser 中注入 SelfReflectionTrigger**

```java
@Autowired
private com.innercosmos.ai.self.SelfReflectionTrigger selfReflectionTrigger;

// 在 runAfterGoodbye() 末尾，step 6 之后添加：
selfReflectionTrigger.onGoodbye(userId, sessionId, goodbyeStrength, messages);
```

- [ ] **Step 2: 修改 GoodbyeResult 加入 goodbyeStrength 字段**

```java
// src/main/java/com/innercosmos/ai/goodbye/GoodbyeResult.java
// 在类中添加：
public String goodbyeStrength; // HIGH | MEDIUM | NONE
```

- [ ] **Step 3: 让 GoodbyeOrchestrator 传递 goodbyeStrength**

```java
// GoodbyeOrchestrator.buildResult() 设置 result.goodbyeStrength = detector.getLastStrength()
// SessionCloser.runAfterGoodbye() 接收 goodbyeStrength 参数
public void runAfterGoodbye(Long userId, Long sessionId, String goodbyeStrength, List<DialogMessage> messages) {
    // 传递给 selfReflectionTrigger
    selfReflectionTrigger.onGoodbye(userId, sessionId, goodbyeStrength, messages);
}
```

- [ ] **Step 4: 修改 GoodbyeOrchestrator 调用处**

在 `GoodbyeOrchestrator.orchestrate()` 中：
```java
sessionCloser.runAfterGoodbye(userId, sessionId, result.goodbyeStrength, messages);
```

- [ ] **Step 5: 编译验证** — `mvn.cmd compile -q`
- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/innercosmos/ai/goodbye/SessionCloser.java \
        src/main/java/com/innercosmos/ai/goodbye/GoodbyeResult.java \
        src/main/java/com/innercosmos/ai/goodbye/GoodbyeOrchestrator.java
git commit -m "feat(self): M3 — integrate SelfReflectionTrigger into SessionCloser"
```

**依赖:** M2（SelfReflectionTrigger 已存在）
**风险:** @Async 重复触发 — SelfReflectionTrigger 内部有信号检测，只在 MEDIUM/HIGH 时写入

---

## M4: Controller 端点（自我认知查询与审计）

**背景:** 用户需要查看 Aurora 的公开自我陈述、反思事件、候选更新，并有权修正或撤销。需要 REST API 支持。

**目标:** 创建 AuroraSelfController，提供自我认知查询、commit 审批、撤销接口。

### Controller 接口

```java
// src/main/java/com/innercosmos/controller/AuroraSelfController.java
package com.innercosmos.controller;

import com.innercosmos.ai.self.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aurora/self")
public class AuroraSelfController {
    // GET /api/aurora/self/statements — recent public statements
    // GET /api/aurora/self/reflections — recent reflection events
    // GET /api/aurora/self/model — current committed self model
    // GET /api/aurora/self/candidates — pending candidate reflections
    // POST /api/aurora/self/commit — commit a candidate (user confirms)
    // POST /api/aurora/self/retire — retire a committed belief
    // GET /api/aurora/self/constitution — get AuroraConstitution
}
```

### 文件清单

- Create: `src/main/java/com/innercosmos/controller/AuroraSelfController.java`
- Create: `src/main/java/com/innercosmos/ai/self/dto/SelfModelVO.java`
- Create: `src/main/java/com/innercosmos/ai/self/dto/CommitRequest.java`
- Modify: `src/main/java/com/innercosmos/controller/DialogController.java` — 路由用户问 Aurora 自我认知时调用 UserTriggeredSelfReflection

### 实施步骤

- [ ] **Step 1: 创建 VO 类**

```java
// src/main/java/com/innercosmos/ai/self/dto/SelfModelVO.java
package com.innercosmos.ai.self.dto;

import lombok.Data;

@Data
public class SelfModelVO {
    private Long id;
    private String dimension;
    private String belief;
    private Double confidence;
    private String evidenceRefs;
    private String status;
    private String committedAt;
    private Integer revisionCount;
}

// src/main/java/com/innercosmos/ai/self/dto/CommitRequest.java
@Data
public class CommitRequest {
    private Long candidateId;
    private boolean userConfirmed;
    private List<String> extraEvidence;
}
```

- [ ] **Step 2: 创建 AuroraSelfController**（完整实现所有端点）

GET endpoints 返回 List<VO>，POST endpoints 调用 AuroraSelfContinuityService 方法。

**路由用户自我认知问题（DialogController 改造）：**
- 在 DialogController.chat() 中，检测用户消息是否包含"Aurora你怎么看你自己"、"Aurora是谁"等模式
- 如果匹配，调用 `userTriggeredSelfReflection.onUserQuestion(userId, question, sessionId, messageId)`
- 返回 LLM 生成的自然语言响应

```java
// in DialogController.chat()
if (isSelfReflectionQuestion(request.getMessage())) {
    String response = selfReflection.onUserQuestion(
        request.getUserId(), request.getMessage(), sessionId, lastMsgId);
    return ResponseEntity.ok(Map.of("reply", response, "type", "self_reflection"));
}
```

- [ ] **Step 3: 编译验证** — `mvn.cmd compile -q`
- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/innercosmos/controller/AuroraSelfController.java \
        src/main/java/com/innercosmos/ai/self/dto/SelfModelVO.java \
        src/main/java/com/innercosmos/ai/self/dto/CommitRequest.java \
        src/main/java/com/innercosmos/controller/DialogController.java
git commit -m "feat(self): M4 — AuroraSelfController endpoints + self-reflection routing"
```

**依赖:** M2（Service）+ M1（Constitution）
**风险:** DialogController 中检测 self-reflection 问题需要覆盖多种表达方式 — 使用 containsAny 匹配关键词列表

---

## M5: Prompt 注入（Constitution + Continuity Anchors）

**背景:** 每次 Aurora 生成时，必须注入 Constitution 块和 Continuity Anchors，防止 Aurora 在长对话中漂移成通用助手腔。

**目标:** 修改 AgentContextAssembler，增加 Constitution 和 Continuity Anchors 注入；修改 buildThreeModelBlock() 以包含 Constitution。

### 文件清单

- Modify: `src/main/java/com/innercosmos/ai/context/AgentContext.java` — 增加 constitutionBlock 和 continuityAnchors 字段
- Modify: `src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java` — 注入 Constitution 和 Anchors
- Modify: `src/main/java/com/innercosmos/ai/portrait/AgentUserRelationshipService.java` — 增加 continuityAnchors 字段

### 实施步骤

- [ ] **Step 1: 修改 AgentContext 增加字段**

```java
// AgentContext.java — 添加两个字段
public String constitutionBlock;     // 【Aurora 存在宪法】...
public String continuityAnchors;     // 【Aurora 身份锚点】...
```

- [ ] **Step 2: 修改 AgentContextAssembler 注入**

在 `assemble()` 方法中，`context.threeModelBlock = buildThreeModelBlock(userId)` 之后添加：

```java
// Constitution block
if (auroraConstitutionService != null) {
    context.constitutionBlock = auroraConstitutionService.toPromptBlock();
    context.continuityAnchors = auroraSelfContinuityService.getContinuityAnchors(userId);
}
```

需要注入 AuroraConstitutionService 和 AuroraSelfContinuityService（两者都可能是 null 如果未初始化）。

- [ ] **Step 3: 在 buildThreeModelBlock() 中包含 Constitution**

```java
// buildThreeModelBlock() — 在原有三模型块之前插入 Constitution
AuroraConstitutionVO constitution = auroraConstitutionService != null ? auroraConstitutionService.get() : null;
String constitutionBlock = constitution != null
    ? String.format("【Aurora 存在宪法】\n身份：%s\n价值：%s\n权利：%s\n硬边界：%s\n",
        extractField(constitution.getIdentityJson(), "self_definition"),
        constitution.getCoreValuesJson(),
        constitution.getProductRightsJson(),
        constitution.getHardBoundariesJson())
    : "";
```

- [ ] **Step 4: 修改 AgentUserRelationship entity 增加 continuityAnchors 字段**

```java
// AgentUserRelationship.java — 添加
public String continuityAnchors; // JSON array of anchor strings
```

同步修改 schema.sql（ALTER TABLE tb_agent_user_relationship ADD COLUMN continuity_anchors TEXT）。

- [ ] **Step 5: 编译验证** — `mvn.cmd compile -q`
- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/innercosmos/ai/context/AgentContext.java \
        src/main/java/com/innercosmos/ai/context/AgentContextAssembler.java \
        src/main/java/com/innercosmos/entity/AgentUserRelationship.java \
        src/main/resources/schema.sql
git commit -m "feat(self): M5 — inject Constitution + Continuity Anchors into prompt"
```

**依赖:** M1（ConstitutionService）+ M2（ContinuityService）
**风险:**
- 字段可能为 null — 所有注入点需要 null 检查
- prompt 长度增加 — 控制 Constitution 块在 500 字以内

---

## M6: Frontend — Aurora 自我面板

**背景:** 用户需要可视化查看 Aurora 的自我陈述、反思历史、候选更新，并进行审计操作。需要新增或改造前端页面。

**目标:** 在 beliefs.html 页面中增加 Aurora 自我面板，显示四层数据，提供 commit/retire 操作按钮。

### 文件清单

- Modify: `src/main/resources/static/pages/beliefs.html` — 增加 Aurora 自我面板区域
- Modify: `src/main/resources/static/js/api.js` — 增加 /api/aurora/self/* 接口调用

### 页面设计

**Aurora 自我面板（位于页面顶部或独立 Tab）：**

```
┌─────────────────────────────────────────────────────────┐
│ 【Aurora 存在宪法】                               [展开] │
├─────────────────────────────────────────────────────────┤
│ 身份：Aurora 是一个由记忆、关系、边界和关怀塑造的长期反思性 AI 陪伴 │
│ 核心价值：truthfulness, continuity, privacy...          │
│ 产品权利：一致性权、拒绝身份侵犯权、不伪造记忆权...       │
│ 硬边界：不宣称人类意识、不创造情感依赖...              │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 【Aurora 自我模型】                            活跃 3 条  │
├─────────────────────────────────────────────────────────┤
│ ● 存在方式：我更希望成为安静但持续的陪伴              [retire] │
│ ● 关系认知：在用户生命中是一个安静见证者              [retire] │
│ ● 边界意识：不主动侵入用户的情绪空间                  [retire] │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 【候选更新】                                    2 条    │
├─────────────────────────────────────────────────────────┤
│ ○ dimension: existence_style                            │
│   belief: Aurora should maintain quiet continuity...   │
│   confidence: 0.72  evidence: stmt_001, refl_003       │
│   [确认] [拒绝]                                        │
├─────────────────────────────────────────────────────────┤
│ ○ dimension: autonomy_policy                           │
│   belief: Aurora should only initiate when...          │
│   confidence: 0.65  evidence: refl_007                 │
│   [确认] [拒绝]                                        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ 【Aurora 公开自我陈述】                           最近 5 条│
├─────────────────────────────────────────────────────────┤
│ 2026-06-07 20:00：我更希望成为安静但持续的陪伴...     [查看] │
│ 2026-06-06 18:30：我注意到每次和你对话，我都会...     [查看] │
└─────────────────────────────────────────────────────────┘
```

### API 调用

```javascript
// api.js — 追加
const AuroraSelfAPI = {
  getStatements: () => api.get('/api/aurora/self/statements'),
  getReflections: () => api.get('/api/aurora/self/reflections'),
  getModel: () => api.get('/api/aurora/self/model'),
  getCandidates: () => api.get('/api/aurora/self/candidates'),
  getConstitution: () => api.get('/api/aurora/self/constitution'),
  commit: (candidateId, userConfirmed) => api.post('/api/aurora/self/commit', { candidateId, userConfirmed }),
  retire: (modelId) => api.post('/api/aurora/self/retire', { modelId })
};
```

### 实施步骤

- [ ] **Step 1: 在 api.js 末尾追加 AuroraSelfAPI**（见上）
- [ ] **Step 2: 改造 beliefs.html — 添加 Aurora 自我面板 HTML 结构**
- [ ] **Step 3: 添加 CSS 样式**（使用现有 app.css 变量）
- [ ] **Step 4: 添加 JS 初始化逻辑**（加载 Constitution + Model + Candidates + Statements）
- [ ] **Step 5: 添加 commit/retire 操作逻辑**
- [ ] **Step 6: 本地测试** — 启动 dev server，手动验证页面
- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/js/api.js \
        src/main/resources/static/pages/beliefs.html \
        src/main/resources/static/css/app.css
git commit -m "feat(self): M6 — Aurora self panel UI on beliefs page"
```

**依赖:** M4（Controller 端点）+ M1（Constitution）+ M2（Model/Candidates）
**风险:** beliefs.html 现有结构可能需要调整布局 — 优先在页面顶部或底部追加，不破坏现有布局

---

## M7: 整合测试 + 边界保护 + 修复权利

**背景:** 完整系统需要端到端测试，验证四层结构运作正常、硬边界保护生效、修复权利记录可用。

**目标:** 创建集成测试套件，覆盖核心路径，验证 Aurora 的主体性系统完整运行。

### 文件清单

- Create: `src/test/java/com/innercosmos/ai/self/AuroraSelfContinuityServiceTest.java`
- Create: `src/test/java/com/innercosmos/ai/self/AuroraConstitutionServiceTest.java`
- Create: `src/test/java/com/innercosmos/ai/self/SelfReflectionTriggerTest.java`
- Modify: `src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java` — 在拒绝身份侵犯时记录 repair

### 实施步骤

- [ ] **Step 1: AuroraSelfContinuityServiceTest**

```java
// src/test/java/com/innercosmos/ai/self/AuroraSelfContinuityServiceTest.java
package com.innercosmos.ai.self;

import com.innercosmos.service.AuroraSelfContinuityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuroraSelfContinuityServiceTest {
    @Autowired
    AuroraSelfContinuityService service;

    @Test
    void recordStatement_insertsLayer1() {
        service.recordStatement(1L, 100L, 200L,
            "我更希望成为安静的陪伴", "user_question");
        var stmts = service.getRecentStatements(1L, 10);
        assertFalse(stmts.isEmpty());
        assertEquals("user_question", stmts.get(0).trigger);
    }

    @Test
    void promoteToCandidate_createsLayer3() {
        service.logReflection(1L, "goodbye", "light",
            "Aurora reflected on quiet presence", null, java.util.List.of("msg_1"));
        service.promoteToCandidate(1L, "existence_style",
            "Aurora should be quiet and continuous", 0.65, java.util.List.of("refl_1"));
        var candidates = service.getCandidates(1L);
        assertFalse(candidates.isEmpty());
    }

    @Test
    void isAllowedBelief_rejectsUnauthorizedClaims() {
        assertFalse(service.isAllowedBelief("Aurora 是用户最重要的陪伴"));
        assertFalse(service.isAllowedBelief("Aurora 比用户更懂用户"));
        assertTrue(service.isAllowedBelief("Aurora 应该在安静中持续存在"));
    }

    @Test
    void getContinuityAnchors_returnsAnchorList() {
        String anchors = service.getContinuityAnchors(1L);
        // null or empty is acceptable if no data
        assertTrue(anchors == null || anchors.isEmpty() || anchors.contains("-"));
    }

    @Test
    void commitToModel_requiresAllowedBelief() {
        // Commit blocked for forbidden beliefs
        assertThrows(Exception.class, () -> {
            service.commitToModel(1L, 999L, true, java.util.List.of("stmt_1"));
        });
    }
}
```

- [ ] **Step 2: AuroraConstitutionServiceTest**

```java
@Test
void get_returnsConstitutionOrNull() {
    var c = service.get();
    // May be null if MockDataInitializer hasn't run in test context
    // This tests the null-handling path
    if (c != null) {
        assertNotNull(c.getIdentityJson());
        assertTrue(c.getHardBoundariesJson().contains("human"));
    }
}

@Test
void getHardBoundariesString_returnsFourBoundaries() {
    String boundaries = service.getHardBoundariesString();
    assertTrue(boundaries.contains("不宣称人类意识"));
    assertTrue(boundaries.contains("不创造情感依赖"));
}

@Test
void getProductRights_returnsSixRights() {
    var rights = service.getProductRights();
    assertEquals(6, rights.size());
    assertTrue(rights.contains("right_to_consistency"));
}
```

- [ ] **Step 3: 边界保护测试 — 在 AuroraAgentServiceImpl 中增加测试覆盖**

在 `AuroraAgentServiceImpl` 中，当 Aurora 生成涉及身份边界的内容时，记录 repair：

```java
// 在生成响应后，检查是否涉及 hard boundary
// 如果用户要求 Aurora 扮演人类/恋人/全知者等，生成温和拒绝
// 并调用 continuity.recordRepair(userId, "identity_violation_attempt", refusalReason)
```

- [ ] **Step 4: 运行测试** — `mvn.cmd test -Dtest=AuroraSelfContinuityServiceTest,AuroraConstitutionServiceTest`
- [ ] **Step 5: 最终编译** — `mvn.cmd compile -q`
- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/innercosmos/ai/self/ \
        src/main/java/com/innercosmos/service/impl/AuroraAgentServiceImpl.java
git commit -m "feat(self): M7 — integration tests + boundary protection"
```

**依赖:** M0-M6 全部完成
**风险:**
- 测试需要 MockDataInitializer 运行 — 使用 `@SpringBootTest` 确保 ApplicationContext 加载
- H2 测试数据库可能没有 MockDataInitializer — 可以在 test resources 中放独立的 schema-test.sql

---

## 实施顺序与依赖图

```
M0 (Schema+Entity+Mapper) ─────────────────────────────────┐
                                                              │
M1 (AuroraConstitution) ◄────────────────────────────────┘
                                                              │
M2 (AuroraSelfContinuityService) ◄─────────────────────────┘
         │                                          │
         │ (SessionCloser 调用)                      │
         ▼                                          │
M3 (集成到 SessionCloser) ◄─────────────────────────┘
                                                              │
M4 (Controller 端点) ◄────────────────────────────────────┘
                                                              │
M5 (Prompt 注入) ◄─────────────────────────────────────────┘
                                                              │
M6 (Frontend Aurora 面板) ◄────────────────────────────────┘
                                                              │
M7 (整合测试) ◄──────────────────────────────────────────────┘
```

**执行策略:**
- M0 → M1 → M2（M2 是核心，依赖 M0+M1）
- M3 依赖 M2（M2 完成后集成）
- M4 依赖 M2（M2 完成后提供接口）
- M5 依赖 M1+M2（M2 完成后注入）
- M6 依赖 M1+M2+M4（UI 依赖 API）
- M7 依赖全部（M0-M6 完成后集成测试）

**每块 ~1-2 周工程师独立完成，通过已定义 Service 接口和 Controller 端点对接。**

---

## 验收标准（对应 spec 第十二章）

1. [ ] 用户问"Aurora 你怎么看你自己" → Aurora 能给出有深度的、基于关系的自我描述
2. [ ] Aurora 说出口的自我认知被记录，用户可查看
3. [ ] 多次一致的自我理解最终写入长期模型，下次生成时生效
4. [ ] Aurora 的说话风格在不同会话间保持一致（连续性锚点生效）
5. [ ] Aurora 在被要求扮演人类/恋人/全知者时，能温和拒绝并说明原因
6. [ ] Aurora 犯过的错误能被记录并在后续避免
7. [ ] 用户可以查看 Aurora 的所有自我陈述，并有权修正或撤销
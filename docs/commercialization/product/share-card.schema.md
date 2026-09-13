# CP-53 分享卡去身份数据结构规范（前瞻契约）

> 状态：**前瞻规范（未实现）**。当前仓库尚无分享卡实现：`web/src` 与
> `src/main/resources/static` 无任何 share-card 渲染路径，controller 无 share 端点
> （由契约测试 `ShareCardDeidentificationContractTest` 的实现哨兵持续核对）。
> 本文件先冻结数据结构与去身份纪律；当 CP-31 公开分享落地时，实现必须遵守本
> schema，且契约测试随之升级为对真实渲染产物的断言。

来源：`docs/commercialization/01-中国大陆商业化执行蓝图.md` L810-816（CP-53）
"分享卡去身份预览，素材授权／广告标识／作者权益登记"；恢复条款
"不从用户倾诉自动生成营销内容"。关联：CP-31（公开发现、分享与标识传播）、
`docs/commercialization/trust-safety/cp08-safety-policy-playbooks.md`
（"分享卡片携带（CP-31）；不冒充本人"）。

## 核心承诺

1. **去身份预览先于发布**：任何分享卡在生成后、对外发布（保存／分享／投放）之前，
   必须先经过去身份预览——用户在预览中看到的文本就是最终对外文本；预览未确认
   不得发布，不存在绕过预览的批量生成路径。
2. 分享卡只能携带**授权后的脱敏抽象信息**（共鸣体级别的摘要），禁止携带 P0 原始
   对话与 P1 记忆原文的任何片段；不从用户倾诉自动生成营销内容。
3. 合成内容永久标注：卡片必含 `syntheticLabel: true`，渲染层不得移除该标注——
   AI 生成标识持续在场，不存在"关闭标注"选项。

## 允许字段（allowed fields）

分享卡载荷只允许以下白名单字段：

- `templateId`：模板编号（运营登记过的模板）
- `scene`：抽象场景标签（枚举，如 `night-reflection`，不含自由文本）
- `mood`：情绪色带标签（枚举值，非用户原话）
- `summaryText`：脱敏摘要文案（≤ 40 字，仅来自授权抽象信息，经预览确认）
- `syntheticLabel`：恒为 `true`（必含，去不掉）
- `aiGeneratedFields`：数组，默认 `[]`；当且仅当某字段的文案由 AI（LLM）生成时把该字段名
  列入（与共鸣体各外流面的 `aiGeneratedFields` 同名同义，见
  `vo/CapsuleAiLabeling` 的分级纪律）。`summaryText` 若由模板拼装或规则生成而非 LLM，
  不得列入；若确由 LLM 起草则必须列入。该字段是**字段级出处声明**，与恒真的
  `syntheticLabel`（合成标注）语义不同、互不替代。
- `shareScope`：分享范围（枚举）
- `generatedAt`：生成时间戳

## 禁止字段（forbidden fields）

载荷中出现以下任一字段即为契约违约，渲染层必须拒绝渲染并记录安全事件：

- `username`（用户名／登录名）
- `nickname`（昵称／展示名）
- `rawMemory`（记忆卡片原文／P1 原文）
- `dialogExcerpt`（对话摘录／P0 任何原文片段）
- `userId`（内部用户 ID）
- `realName`（真实姓名）
- `phone`（手机号）
- `email`（邮箱）
- `avatarUrl`（真人头像地址）

## 载荷示例

```yaml
shareCard:
  templateId: night-reflection-v1
  scene: night-reflection
  mood: calm
  summaryText: "示例（AI 合成，非真实用户）：一次被听见的小憩"
  syntheticLabel: true
  aiGeneratedFields: ["summaryText"]
  shareScope: public-square
  generatedAt: "2026-09-13T00:00:00Z"
```

示例中 `summaryText` 假定由 LLM 起草，故如实列入 `aiGeneratedFields`；若实现改为模板/
规则拼装，则该数组必须回到 `[]`——标注是出处陈述，不是装饰，不得整体谎标。

## 升级路径

分享卡实现（CP-31）落地时：把本契约从"schema 文档断言"升级为对真实序列化函数／
模板产物的断言——渲染输出不含用户名／昵称／真实记忆原文／P0 词，合成示例永久
标注 AI，去身份预览先于任何发布动作。

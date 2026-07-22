# ADR-0003：用 ArchUnit 层边界测试落地 ARCH-MODULES，暂不迁移 Spring Modulith

- 状态：Accepted
- 日期：2026-07-22
- 决策范围：G0 / ARCH-MODULES
- 证据：`src/test/java/com/innercosmos/architecture/ControllerLayerShortcutArchTest.java`、`src/test/java/com/innercosmos/architecture/DomainBoundaryArchitectureTest.java`

## 背景

验收清单 ARCH-MODULES 的目标文本写的是"Module boundaries are executable through Spring Modulith and
architecture tests"，但当前代码库是纯粹的按层分包（`controller/service/service.impl/mapper/entity/vo/dto`），
不是按业务领域（feature）分包 —— 全项目只有一个 `com.innercosmos` 顶层包，controller、service、mapper 各自
一个包，跨越所有产品域（Aurora、Capsule、Letters、Social、Psychology…）。Spring Modulith 的核心价值（每个业务
模块一个顶层包、模块间显式声明依赖、`@ApplicationModuleTest` 验证模块边界）要求先把代码重新组织成按领域分包，
这是一次大规模、高风险的结构性重写，而不是一次可以增量完成的小改动。

代码库里已经存在一个手写的、基于源码文本正则扫描的架构测试
`DomainBoundaryArchitectureTest`（覆盖 entity/mapper/service/safety 不得向上依赖），证明"用测试固化层边界"这个
方向本身是对的、也已经在用；但它只检查"下层向上层依赖"，没有检查"上层跳过中间层向下依赖"——审计发现 6 个
controller（`SocialController`、`AuroraChatController`、`UserController`、`UserPreferenceController`、
`DiaryController`、`PortraitController`）直接注入了 mapper，1 个 controller
（`DailyRecordController`）直接注入了 `service.impl` 包下的具体类（`WeeklyReviewV2Service`，一个没有对应接口、
错放在 impl 包里的具体服务），都绕开了"controller → service 接口 → mapper"的既定约定。

## 决策

**不做 Spring Modulith 迁移。** 采用 **ArchUnit（字节码级）层边界测试**，在现有的
`com.innercosmos.architecture` 测试包下新增 `ControllerLayerShortcutArchTest`，与已有的
`DomainBoundaryArchitectureTest` 互补，两者共同构成"可执行的模块边界"：

- `DomainBoundaryArchitectureTest`（保留不动）：entity/mapper/service/safety 不得反向依赖上层。
- `ControllerLayerShortcutArchTest`（新增）：controller 不得直接依赖 mapper 包，也不得直接依赖
  `service.impl` 包下的具体类——必须经过 `service` 包下的接口。

新测试对上述 7 个已发现的违规做了**明确列出、不会被静默扩大**的白名单
（`GRANDFATHERED_MAPPER_ACCESS`、`GRANDFATHERED_IMPL_ACCESS`），并额外加了两条"白名单必须仍然真实违规"的
反向断言——一旦某个 controller 被重构掉了对 mapper/impl 的直接依赖，这条断言会失败，逼着白名单跟着缩小，
防止白名单变成可以无限堆积技术债的口子。

`ARCH-MODULES` 状态更新为 `IN_PROGRESS`，`remaining` 字段记录：7 个已知违规待逐个重构（每个都需要在对应
service 接口上补一个方法，而不是简单转发 mapper 调用）、`WeeklyReviewV2Service` 缺少接口需要一并修正。

## 备选方案

- **Spring Modulith 全量迁移**：按领域重新分包 + `@ApplicationModuleTest`。价值更高（能验证领域内聚、
  防止领域间偷偷耦合），但要求把 60+ 张表对应的 controller/service/mapper/entity 全部重新归类到领域包，
  工作量和回归风险与本项目"小改动、不阻塞后续排期"的目标不成比例，故未选择；作为未来选项保留（见"后续"）。
- **只保留现有正则扫描测试、不引入 ArchUnit**：省事，但正则扫描无法识别未显式 `import`（如全限定名引用）
  的依赖，也无法表达"某个具体类"级别的白名单（正则天然按包/字符串匹配），ArchUnit 基于真实字节码依赖图，
  更精确、更不容易被绕过，因此额外引入。

## 影响与边界

- 新增 test-scope 依赖 `com.tngtech.archunit:archunit-junit5:1.3.0`（不进入生产 jar）。
- 不改变任何生产行为；只新增测试，且新测试当前全绿（白名单覆盖了已知违规）。
- 不阻止未来任何时候重新评估 Spring Modulith——如果产品域进一步增多、团队规模扩大到需要强制模块边界，
  可以在按领域分包完成后把 ArchUnit 规则升级为 Modulith 的 `ApplicationModules.verify()`。

## 后续（记录为 ARCH-MODULES 的 remaining，不在本 ADR 范围内完成）

1. 逐个重构 7 个已列入白名单的 controller：为对应 service 接口新增方法，删除对 mapper/`service.impl` 的
   直接注入，并从两个白名单集合中移除该类（反向断言会强制这一步不被遗漏）。
2. 给 `WeeklyReviewV2Service` 补一个 `service` 包下的接口，使其符合"service 包放接口、service.impl 包放
   实现"的既定约定。
3. 若未来确有多团队并行、领域边界频繁被越界破坏的信号，重新评估 Spring Modulith 迁移。

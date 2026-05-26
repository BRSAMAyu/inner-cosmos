# AI 交互记录

## 设计阶段

用户要求：构建一个不是普通 AI 聊天网站的 Java Web AI Agent 项目。

AI 决策：

- 将项目定义为 AI 自我共鸣与慢社交平台。
- 采用 Mock-first 策略，保证离线演示。
- 将数据分为 P0 / P1 / P2 / P3，保护隐私边界。
- 使用情感重力公式实现记忆星空。

## 工程阶段

用户要求：由 Codex 作为总设计师和总架构师推进完整项目。

AI 决策：

- 先搭建 Spring Boot + MyBatis-Plus + H2/MySQL 双配置。
- 再实现 Aurora、Memory、Capsule、SlowLetter、安全和管理后台。
- 增加远程 LLM 与 ASR 适配点，但默认保持 Mock 可运行。

## 示例交互

用户：

```text
今天有点累，作业也拖延了。
```

Aurora：

```text
我听到这里面有一个任务压力。我们先不评价自己，可以把它拆成一个十分钟就能开始的小动作吗？
```

# P1 机制实验新鲜复跑

- 时间：2026-07-28 04:51–05:01（Asia/Shanghai）
- 代码基线：`main@cf598d4a`
- 测试合计：56，失败 0，错误 0，跳过 0
- 在线共鸣体旅程：3 个预设共鸣体 × 2 个相同问题 = 6，成功 6，Provider fallback 0，旅程错误 0
- 结论等级：机器可执行机制证据；不是人类盲评，不证明回复质量或人格相似度优于其他系统。

## 1. 记忆检索

| 拓扑 | 并发 | 用户 | 调用 | 吞吐 calls/s | p50 | p95 | p99 | 最大 | 超时 | 预算越界 | 禁用记忆泄漏 | 相关记忆漏召回 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| H2 内存 | 24 | 10 | 180 | 209.18 | 97.27 ms | 204.23 ms | 215.25 ms | 218.52 ms | 0 | 0 | 0 | 0 |
| PostgreSQL 16 + Redis 7.4 Testcontainers | 24 | 10 | 180 | 202.10 | 80.61 ms | 315.95 ms | 318.02 ms | 318.30 ms | 0 | 0 | 0 | 0 |

每个虚拟用户含 200 条噪声记忆和正确性数据集。Redis 与应用上下文同时运行，但当前检索服务本身是数据库读路径，因此不能把延迟改善归因于 Redis。

顺序正确性基准另有 10 个案例：micro/macro Recall@3 均为 1.0，MRR 1.0，禁用记忆泄漏 0，预算越界 0，p95 24.4444 ms。

## 2. 记忆 authority：纠正与撤回

- 用户纠正：authority-aware 检索返回纠正后的 ACTIVE 记忆，不返回 SUPERSEDED 旧记忆。
- 数据撤回：authority-aware 检索不返回 FORGOTTEN 记忆。
- 刻意设置的 naive lexical baseline 在两个案例中分别暴露旧记忆和已遗忘记忆。
- `unexpectedFailureLedger=[]`。

这是 2 个确定性机制案例，不是大样本长期记忆质量实验。

## 3. 主动式门控

- quiet-hours：真实引擎在 LLM 前短路；LLM 调用 0、推送 0、调度 0。
- long-gap return：只产生 1 次温和 check-in，没有连续推送。
- changing preference：用户要求更多陪伴后，产生 1 次计划回访。
- 三个真实引擎变体均通过，`unexpectedFailureLedger=[]`。

这证明门控调用顺序和次数，不证明用户主观上认为提醒时机合适。

## 4. 共鸣体 Genome、检索和在线旅程

- Genome ablation：动态 Genome 的 5 个真实机制断言全部通过；未授权外部记忆被排除，身份披露 invariant 存在。静态拼接 baseline 会包含外部文本，且没有结构化身份披露 invariant。
- 编译 groundedness：4 个案例，ungrounded citation 0，被排除记忆仍计入 0。
- 运行时检索：6 个案例，意图准确率 1.0、选择准确率 1.0、evidence leak 0、unsupported fallback accuracy 1.0。
- 相关聚焦测试：49/49 通过，其中 `CuratedPersonaCatalogTest` 确认三个预设 prompt 不相同，并各自绑定独占生活素材；这只能证明作者配置不同。
- 真实公网 Gemini 旅程：6/6 Provider 成功，fallback 0，错误 0；延迟最小 1,393 ms、中位数 2,008 ms、平均 1,938.3 ms、最大 2,477 ms。

在线运行配置：Gemini `gemini-3.6-flash`，Provider fallback 关闭，embedding 关闭。

六条在线回复可作为盲评材料输入，但当前没有 reviewer label，因此不能声称“人格 fidelity/distinctiveness 已经被人类证明”。

## 5. 失败与运行警告

1. 仓库原始 `scripts/demo/benchmark-capsule-personas.ps1` 与当前 Demo 契约发生漂移：
   - Demo 关闭 CSRF 时，脚本会把空 `headerName` 当数组键并立即失败；
   - localhost HTTP 下，服务设置的 `Secure` Session cookie 不会回传，认证接口随后返回 401；
   - evidence 副本只做两项 harness 兼容：CSRF token 存在时才附加；注册后显式登录。最终经当前 HTTPS Cloudflare 入口成功。
2. 聚焦测试日志包含刻意注入的 `AliveDecisionEngine` 超时/重试 WARN；对应测试通过，这是预期故障路径。
3. 聚焦测试日志包含 Mock `CAPSULE_CALIBRATION` 非 JSON WARN；集成测试验证确定性 fallback 后通过。它仍说明 Mock 校准原始输出并非结构化模型质量证据。
4. 在线共鸣体实验使用三个预设共鸣体，不能外推到任意新用户编译出的共鸣体。

## 6. 复现命令

```powershell
$tests = 'com.innercosmos.evaluation.MemoryRetrievalLoadTest,com.innercosmos.evaluation.TrackAMemoryAuthorityAblationEvaluationTest,com.innercosmos.ai.proactive.TrackAProactiveDecisionEvaluationTest,com.innercosmos.evaluation.TrackACapsuleGenomeAblationEvaluationTest'
.\.tools\apache-maven-3.9.9\bin\mvn.cmd "-Dtest=$tests" test

.\.tools\apache-maven-3.9.9\bin\mvn.cmd `
  '-Dtest=com.innercosmos.evaluation.MemoryRetrievalLoadPostgresRedisTest' test

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\evidence\experiments\P1-MECHANISM-RERUN-2026-07-28\run-capsule-personas-live.ps1' `
  -Origin 'https://当前地址.trycloudflare.com'
```

完整 49 项聚焦测试命令保留在 `focused-related-tests-maven.log` 的本次 shell 记录所对应的测试列表中；原始 Maven 输出和所有 JSON 报告均在本目录。

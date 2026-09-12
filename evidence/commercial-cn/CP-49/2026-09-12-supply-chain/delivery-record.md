# CP-49 + CP-38 首个可编码增量 — 供应链唯一权威与工作负载角色清单（检查点 29）

## CP-49（供应链）

1. **问题实证**：仓库同时存在 `web/package-lock.json` 与 `web/pnpm-lock.yaml`（声明 packageManager: pnpm@11.9.0），且 7 个 devDependency 是 `latest` 浮动区间——两个解析真相 + 不可复现供应链
2. **修复**：删除 npm lockfile（pnpm-lock.yaml 为唯一权威）；7 个 latest 按锁定文件已解析版本钉死（jest-dom 7.0.0、testing-library/react 16.3.2、@types/node 26.1.1、@types/react 19.2.17、@types/react-dom 19.2.3、vitejs/plugin-react 6.0.4、jsdom 29.1.1）；`pnpm install` 同步 lockfile（0 下载，仅元数据）
3. **CI 合同 `PackageManagerContract.test.ts` 4/4**（随 web 套件执行）：packageManager 钉精确版本；恰一个 lockfile（npm/yarn lockfile 回潮即败）；零 latest/*/空区间；workflow 禁 npm install/ci
4. **`scripts/supply-chain/README.md`**：SBOM（CycloneDX maven 聚合 + pnpm 产物级）与 CVE（pnpm audit --prod / OWASP dependency-check / Trivy）命令、发布归档要求（SBOM 随版本入 evidence）、诚实边界（CVE 扫描需网络+key，为 CI/operator 门禁）
5. web 全量 **742/742 绿**（新增 4 合同测试），tsc 零错误

## CP-38（工作负载角色）

`deploy/k8s/extensions/roles/{api,ai,worker,scheduler}/`：base 的 kustomize 派生（nameSuffix 隔离），同一不可变镜像、`INNER_COSMOS_RUNTIME_ROLE` 选角：
- **api**（3 副本）：纯用户 HTTP/SSE，调度被应用侧禁用（RuntimeSchedulingConfiguration 只在 all/worker/scheduler 启用），grace 45s SSE 排空
- **ai**（2 副本，2Gi）：Provider 调用池，grace 90s 在途轮次收尾；成本由 ProviderSpendGuard（CP-40）逐用户封顶
- **worker**（2 副本）：结算/事件/outbox，幂等 handler+Redis 租约下多副本安全
- **scheduler**（1 副本，最小资源）：时间驱动任务，租约为正确性机制，grace 120s 让已领取任务跑完
- 探针沿用 base 三段式（readiness 含依赖、liveness 仅进程内——外部抖动摘流量不重启）；README 含角色矩阵与验收映射
- **诚实边界**：多副本争抢/租约过期/节点排空/预算封顶需真实集群演练（CP-50A operator 门禁）；DB 连接池总和核验需真实 PG 参数

## 状态

- CP-49: IN_PROGRESS（唯一 lockfile+钉版+CI 合同+SBOM/CVE 命令落地；SBOM 实际生成与镜像签名随发布流程）
- CP-38: IN_PROGRESS（角色清单落地；真实集群演练为 operator 门禁）

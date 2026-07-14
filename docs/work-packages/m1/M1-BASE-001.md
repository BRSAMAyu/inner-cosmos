# M1-BASE-001 — Java 21 / Boot 3.5 与回归基线

```yaml
status: EVALUATED
workstream: verification-integration
depends_on: [M1-GOV-001, M1-SEC-001, M1-SEC-002, M1-SEC-003]
human_gate: independent-review-required-for-verified
evidence_dir: evidence/m1/M1-BASE-001/
```

## 目标

在红线修复后的统一基线上完成 Java 21 与 Spring Boot 3.5.x 升级，建立可重复、不可静默跳过的 M1 CI 回归门。

> 2026-07-15 reconciliation: 外部密钥轮换仍是独立人类门禁，但不再阻止
> 可逆的 Java/Boot 基线工程。Builder 已完成迁移与评测；独立复核前不标记
> `VERIFIED`。

## Owned paths

- `pom.xml`
- Maven wrapper/项目内 Maven 与 Java 版本配置
- CI workflow、测试报告归档、构建说明
- 只为兼容升级所需的最小源码/测试修复

## 实施任务

1. 固定 Java 21 toolchain 和 Maven 版本；
2. 升级到当时选定的最新 Boot 3.5 patch，并记录依赖差异；
3. 清理或解释 Java agent、弃用 API、Spring Security 自动配置警告；
4. Surefire XML 作为实际执行数权威，禁止 `-DskipTests` 和隐藏 skipped；
5. 对比升级前后 613 基线、启动时间、关键 API 与容器运行；
6. 生成 SBOM 与依赖漏洞报告；Critical/High 必须关闭或有到期豁免。

## 验收

- Java 21 上 clean checkout 执行 613+ 测试，0 failure / error / skipped；
- `mvn verify`、可执行 jar、Docker build 和容器 smoke 全部通过；
- 未夹带 Boot 4、WebFlux、JPA 或大规模业务重写；
- 证据中记录精确 JDK、Maven、Boot、OS、SHA。

## 验证命令

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd clean verify
docker build -t inner-cosmos:m1-base .
git diff --check
```

## 回滚

保留升级前可构建 tag/commit；若 3.5 在时间盒内无法达到零回归，则通过 ADR 暂留当前 Boot 3.3.6，但 Java 21、安全修复和测试门不得丢失。

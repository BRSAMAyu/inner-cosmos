# CP-49 供应链基线（可编码部分）

**唯一权威**：`web/packageManager: pnpm@<exact>` + `web/pnpm-lock.yaml`；`package-lock.json` 已删除。CI 合同 `web/src/PackageManagerContract.test.ts`（随 web 测试套件执行）在以下情况失败：双 lockfile 回潮、packageManager 未钉版本、任何 `latest`/`*`/空版本区间、workflow 里出现 npm install/ci。

## SBOM 生成（CycloneDX，发布流程必跑）

```powershell
# 后端（聚合全模块，输出 target/bom.json + bom.xml）
./mvnw org.cyclonedx:cyclonedx-maven-plugin:2.8.0:makeAggregateBom
# 前端（产物级，输出 web/cyclonedx.json）
cd web; pnpm dlx @cyclonedx/cyclonedx-npm --output-file cyclonedx.json
```

SBOM 必须随发布归档到 `evidence/commercial-cn/CP-49/<version>/`；同 SHA 构建的 SBOM 差异需要解释。

## CVE / 支持周期清单

```powershell
cd web; pnpm audit --prod          # 生产依赖漏洞（CI 快速门）
# 后端：OWASP dependency-check（需 NVD API key，operator/CI 环境）或 Trivy 扫描最终镜像
# ./mvnw org.owasp:dependency-check-maven:check -DnvdApiKey=$env:NVD_KEY
```

基线清单随发布记录：`docs/commercialization/supply-chain/` 存每次发布的三元组（直接依赖版本 / SBOM SHA / 已知 CVE 及处置）。

## 诚实边界

- CVE 扫描需网络与 NVD key——CI/operator 门禁，本仓库提供命令与合同，不伪造扫描结果
- 镜像签名/digest 拉回验证/内部镜像校验属 release-image.yml 与 CP-49 验收流程（主分支保护下实际核验）

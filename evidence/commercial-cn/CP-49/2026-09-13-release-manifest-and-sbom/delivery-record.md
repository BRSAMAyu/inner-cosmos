# CP-49/CP-41 发布清单与 SBOM 归档接线（检查点 36）

## 交付物

### 1. 版本化发布清单生成器 `scripts/release/generate-release-manifest.ps1`

- 输入版本 tag（v 前缀语义版本强校验）；绑定发布身份：git SHA（40hex 强校验）、
  backend 版本（pom.xml）、web 版本（package.json）、生成时间（UTC）
- 产物摘要：CycloneDX SBOM（backend target/bom.json + web/cyclonedx.json）与
  web/dist 入口/资产文件的 SHA-256
- **诚实规则**：运行时缺失的产物进入 `absent` 数组且**绝不带占位摘要**——清单
  里没有任何伪造值
- 本地实跑验证：对真实 HEAD（7e484d69）生成 v0.1.0-rc.2 清单，web/dist 与两个
  SBOM 当时不存在 → 如实 absent

### 2. 清单契约断言器 `scripts/release/assert-release-manifest.ps1`（fail-closed）

- schema/version tag/git SHA 形状；**活体漂移守卫**（清单内版本必须等于当下
  pom.xml/package.json——防清单与仓库脱钩）；每个摘要 64hex 且文件存在；
  absent 项不得同时出现于 artifacts
- 本地实跑通过（release-manifest contract OK）

### 3. 发布工作流接线（`.github/workflows/release-image.yml` verify 作业）

1. 生成双 SBOM：`cyclonedx-maven-plugin:2.8.0:makeAggregateBom` +
   `@cyclonedx/cyclonedx-npm`
2. 以发布 tag 生成清单并跑断言器
3. `upload-artifact`（SHA 钉 v7.0.1，与 java-baseline 同钉法）归档清单+双 SBOM，
   `if-no-files-found: error`——缺文件即发布失败
4. 归档物为 evidence/CP-49/<version>/ 的回填来源（回填动作本身按仓库纪律由
   operator/agent 在发布后以工作流产物为源执行）

### 4. 契约测试 `ReleaseManifestScriptContractTest` 2/2

锁定：脚本存在且含诚实词汇（absent 列表/SHA256/版本校验/schema id）；工作流必须
包含双 SBOM 生成、按 tag 调生成器、断言器、if-no-files-found: error、SHA 钉扎的
upload-artifact——CI 无法在本仓库执行工作流，契约测试文本级钉住接线不被静默删除。

## 回归

- 后端 **1629/1629 绿**（+2），2 个 Docker 门控跳过（本轮前 1627 全绿 + 本契约）
- Web 742/742 绿、tsc 干净（未改动前端，沿用本轮已验证结果）

## 诚实边界（operator 门禁）

- 工作流实际执行（tag 推送/手动 dispatch）需 GitHub 环境；NVD key 的 CVE 扫描、
  镜像签名密钥策略不变仍为 operator 门禁

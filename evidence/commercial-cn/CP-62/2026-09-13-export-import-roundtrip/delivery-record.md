# CP-62 数据可迁移性：导出 v2 + 导入往返六项验收（检查点 41，主线程交付）

蓝图 L893 迁移往返验收的机械落地——此前仓库**零导入实现**（最大单块可编码缺口）。

## 交付物

### 1. 导出格式 v2（`com.innercosmos.export.ExportPackage` + `UserDataExportService`）

- 信封：schemaVersion=2（白名单）/ sourceUserId（ownerGuard 基准）/ exportedAt /
  **每节 SHA-256**（记录规范 JSON——键排序、JSR310 ISO 日期）/ **整包完整性摘要**
- 四节：memoryCards（**纠正链随行**：versionNo/supersededById/provenanceRefs/
  consentScope）、echoCapsules（原可见性记录为 wasPublic 数据）、slowLettersSent
  （仅本人寄出的信；收到的信属于寄信人，不导出）、voiceTranscriptions（媒体引用）
- 每记录：importKey 自然键 / sourceRef 溯源 / ownerUserId / occurredAt；**源行 id
  不入包**；媒体以引用清单随行（mediaType/referencedBy），不内联不丢失

### 2. 导入 `UserDataImportService.validateThenImport`（V47 `tb_data_import_receipt`）

蓝图 L893 六项逐条机械化：
1. **格式/来源/时间/纠正/媒体验证**：schemaVersion 白名单 + 每节摘要 + 整包摘要
   重算（损坏包拒绝 BAD_REQUEST）
2. **越权资料阻断**（FORBIDDEN）：每记录 ownerUserId ≠ 包源用户即整包拒绝——
   契约测试用"重签名合法包裹走私他人记录"的诚实形状攻击验证
3. **重复导入幂等**：tb_data_import_receipt 自然键（section+sourceKey+target），
   二次导入全跳过零新增
4. **不自动公开共鸣体**：isPublic 强制 false、visibilityStatus=PRIVATE（无论源可见性）
5. **不寄信**：导入的信一律 DRAFT，不进入任何投递管线
6. **不复活已撤回用途/第三方权限**：导入只落四节数据，grant/permission 永不出现在
   任何导入回执节

### 3. 端点 `UserDataPortabilityController`

GET `/api/me/data/export-package` / POST `/api/me/data/import-package`——
**退出/迁移不绑购买**：两条路径均不查询任何支付/订阅状态。

### 4. 契约 `ExportImportRoundTripTest` 2/2

往返保持（含纠正链与媒体引用、目标私有默认）、重导入幂等（回执 4 行零新增）、
篡改摘要拒、重签名走私拒（FORBIDDEN）、坏 schemaVersion 拒、导入无 grant 节。

## 实机验证

Postgres 基线 **47 迁移 / 105 表 / 98 身份列 / v20 链 28**（BaselineTest 4/4 +
ApplicationSmokeTest 1/1 实跑）；后端全量 **1649/1649 绿**；Web 750/750（未触及）。

## 诚实边界（operator 门禁）

独立查看器 UI、非作者接班发布/恢复/退款的**真实演练**、停服预告→法定留存→最终
销毁全流程演练、媒体 blob 的物理打包（当前为引用清单）——后续批次/operator 门禁。

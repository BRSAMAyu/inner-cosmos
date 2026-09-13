# CP-41 大陆厂商推送服务端接入 + 出站修复（检查点 35）

## 方法学纠偏（先记录）

本轮第一版在未盘点现状的情况下新建了 tb_device_push_token/tb_push_delivery 重复
子系统，与既有 V21 移动推送体系（`/api/v1/devices` 注册 + 加密 token 保护 + claim/
退避投递 + APNs/FCM/本地网关）**撞名撞职责**，触发 H2 文件库的表结构冲突后全部
回滚（含 V46 初稿、schema 孪生、通知 deep_link 改动）。教训落规则：动表前先 grep
运行时 initializer 与既有迁移。最终交付改为在既有体系上补 CP-41 的真实缺口。

## 交付物

### 1. 五家大陆厂商传输网关（既有 PushGateway 体系，operator 门禁）

`service/push/{Xiaomi,Oppo,Vivo,Honor,Huawei}PushGateway.java`——transport
XIAOMI/OPPO/VIVO/HONOR/HUAWEI，凭据随各家渠道合同由 operator 注入
（`inner-cosmos.push.<vendor>.*` / 环境变量）；未配置前每个 send 一律
`SendResult.failed(false,false,"EXTERNAL_CREDENTIAL_GATE")`——**绝不伪造
providerMessageId（不伪造发送）**，不撤销 token（凭据门与 token 有效性无关）。

### 2. 注册与调度链路放开（V46 迁移，PG 实机验证）

- `DeviceRegistrationRequest.transport` 白名单 @Pattern 与
  `tb_device_registration.ck_device_transport` CHECK 同步加入五家厂商值
  （V46 `ALTER TABLE ... DROP/ADD CONSTRAINT` + H2 孪生）
- 唤醒意图 fan-out 本就按 enabled/revoked 设备不分传输通道投递——厂商问题在
  **投递时**裁决而非入队时，业务消息不因厂商关闭而丢失（单厂商故障可关闭对应
  SDK 而保住业务消息）

### 3. 既有真 bug 修复：`PushDeliveryServiceImpl.enqueueWakeIntent` H2 语法断裂

原实现 `ON CONFLICT ... DO NOTHING` 在 H2 MySQL 模式**根本无法解析**（隔离环境
复现 org.h2 JDBCSyntaxErrorException）——投递入站在 H2 上是潜在运行期失败。按
JdbcOutboxRepository 同款方言分支修复：PG 保留 ON CONFLICT，H2 走 INSERT IGNORE
（同一"唯一键跳过"幂等语义）。大陆厂商契约测试逼出该缺陷。

### 4. 契约测试 5/5

- 五网关注册且 transport 唯一（Spring 上下文）
- 无凭据发送 fail-closed：不 delivered、不可重试、不撤销 token、无伪造
  providerMessageId、错误类 EXTERNAL_CREDENTIAL_GATE
- 作业级：厂商凭据门可见失败 + LOCAL_EVIDENCE 照常投递（含可解密 token 的真实
  PushTokenProtector 路径）
- 注册白名单含五厂商值（@Pattern 反射断言）
- 唤醒意图 fan-out 覆盖厂商传输设备（真实 FK 行为：tb_user/tb_wake_intent 依赖）

## 实机验证

- 后端 **1627/1627 绿**（+5），2 个 Docker 门控跳过
- Postgres 基线 **46 迁移 / 104 表 / 97 身份列 / v20 链 27**（BaselineTest 4/4 +
  ApplicationSmokeTest 1/1 实跑）
- Web **742/742 绿**，tsc 干净

## 诚实边界（operator 人工门禁）

- 各厂商 REST 协议常量（endpoint/鉴权头）在渠道合同与密钥到手后接线——不凭
  记忆伪造端点；真机矩阵（三运营商/前后台/杀进程/通知关闭）与厂商 SDK 按需
  初始化（客户端侧）同为 operator 门禁

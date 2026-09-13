# CP-45/46 支付渠道沙箱适配层（检查点 32）

把微信支付 / 支付宝的异步回调格式适配到检查点 31 建成的 `PaymentCallbackVerifier`
验签内核与 `tb_payment_event` 追加式账本上。无需真实渠道：适配的是两家的**文档化
回调形状**，沙箱确定性签名，全部负路径 fail-closed。

## 交付物

### 1. 渠道适配层 `com.innercosmos.payments.channel`

- **`ChannelCallbackAdapter` 接口 + `ChannelCallback` 规范化记录**：适配器只做格式
  编解码（provider() / decode() / ackBody()），不做信任判断（验签在内核）也不做
  权益判断（状态映射在 `ChannelEventType`）——三层分离，服务端权威
- **`WeChatPayCallbackAdapter`**：微信支付 v3 通知信封（id/event_type/resource{mchid,
  out_trade_no, amount{total|refund, currency}, success_time}）+ `Wechatpay-Timestamp/
  Signature` header；canonicalBody = 原始 JSON 字节（签名绑定到达内容）；退款事件
  **必须**带 `amount.refund`（缺失=畸形，绝不拿 total 猜退款额）；非 CNY = 畸形
- **`AlipayCallbackAdapter`**：form-encoded 通知（notify_id/trade_status/out_trade_no/
  total_amount 元/gmt_payment +08:00/app_id）；canonicalBody = 业务参数排序串
  （排除签名自身）；沙箱契约显式命名 `sandbox_timestamp`/`sandbox_sign`——不可能与
  生产 RSA2 验签混淆（生产验签是 operator 提供支付宝公钥的人工门禁）；元→分
  换算 `longValueExact`（分以下小数=畸形）
- **`ChannelEventType` 闭合状态映射**：微信 `TRANSACTION.SUCCESS`、支付宝
  `TRADE_SUCCESS/TRADE_FINISHED` → `PAYMENT_SUCCEEDED`；微信 `REFUND.SUCCESS`、
  支付宝 `REFUND_SUCCESS` → `REFUND_SUCCEEDED`；**其余一切（CLOSED/ABORMAL/
  WAIT_BUYER_PAY/未来新状态）→ 空 → 隔离待查单**（"不确定支付先查单，禁止盲重扣"）
- **`ChannelCallbackIngestService`**：decode → 商户校验 → 验签 → 状态映射 → 账本
  记录，顺序即信任顺序；商户号/app_id 与 operator 配置比对，**空白配置=全拒**
  （未配置渠道绝不信任 payload 自称身份）；验签失败/商户不符零账本写入
- **`ChannelCallbackController`** `POST /api/payments/callbacks/{provider}`：匿名可达
  （SecurityConfig permitAll + CSRF 豁免——该路径的全部信任是 fail-closed 验签，
  网络可达性不构成任何入账能力）；ACCEPTED 回渠道字面成功应答；隔离态回 200+失败
  应答让渠道幂等重试（事实不丢）；401/403/400/404 对应验签/商户/畸形/未知渠道
- **配置**（application.yml，env 注入，默认空=关门）：`INNER_COSMOS_PAYMENTS_CALLBACK_SECRET`、
  `INNER_COSMOS_PAYMENTS_WECHATPAY_MCHID`、`INNER_COSMOS_PAYMENTS_ALIPAY_APP_ID`

### 2. 契约测试 `ChannelCallbackSandboxContractTest` 13/13

- 正路径：微信支付/退款（退款额=refund 字段≠订单总额，净额 4900-1900=3000）、
  支付宝 49.00 元→4900 分、部分退款 19.50→1950 分、TRADE_FINISHED 终态捕获
- fail-closed 矩阵：篡改 body / 过期时间戳 / 错商户号 / **空白商户配置** / **空白密钥** /
  非法金额 / 非 CNY / 畸形 JSON / 缺签名 header / 未知渠道 slug——每条负路径断言零账本行
- 幂等：同 notify_id 重发两次 ACCEPTED，净额只计一次
- 隔离：TRANSACTION.CLOSED / REFUND.ABNORMAL / WAIT_BUYER_PAY / TRADE_CLOSED /
  未知新状态全部隔离，零账本行
- 支付宝签名覆盖排序串：加未签名参数 / 签后改金额 → 验签拒绝
- 端点接线：MockMvc 无会话无 CSRF token 直达（证明 permitAll+CSRF 豁免）且上下文
  空白配置下 403+渠道 FAIL 应答、零账本行

## 诚实边界（operator 人工门禁）

- 生产渠道验签是各自的非对称体系（微信平台证书 RSA-SHA256 over
  `timestamp\nnonce\nbody\n`、支付宝 RSA2 over 排序参数串）——需要 operator 提供的
  渠道证书/公钥，本轮沙箱 HMAC 内核是两渠道统一的可验证替代，**不冒充生产验签**
- 真实持牌服务商合同、小额验收交易（CP-47 结算样本）、订单目录与期望金额校验
  （CP-45 订单/商品/支付尝试表）在后续批次/人工门禁

## 回归

- 后端 **1612/1612 绿**（较上批 +14：渠道 13 + 运行手册 1），2 个 Docker 门控跳过
- Web **742/742 绿**，`tsc -b` 干净

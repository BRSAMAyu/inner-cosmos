# CP-53 落地页价值主张结构 + 分享卡去身份契约（检查点 44，后台 agent 交付）

## 交付物

### 1. `docs/commercialization/product/landing-copy.deck.yml` + 契约 8/8

蓝图 CP-53 验收"落地页说得出具体任务、AI身份和隐私边界"的结构化落盘：
- 六章节：hero（一句话价值，**必须命中 ≥3 个具体任务词**且全卷黑名单封禁空泛词：
  懂你/最懂/灵魂伴侣/心灵伴侣/贴心闺蜜/完美恋人）、what-it-is（AI 身份声明："不是
  真人/不是医疗/不做诊断"≥2 处）、privacy-boundary（三行：P0 对话私密/共鸣体仅
  授权抽象信息/数据权利无付费墙）、install-path（安装/注册/取消——路径与真实实现
  逐字对齐：`/app/aurora/`、`/downloads/inner-cosmos-demo.apk`、取消=账户设置→删除
  账户，全部 PENDING_VERIFICATION 待 operator 按真实发布核对）、trust-notes、
  evidence-discipline（**不从用户倾诉自动生成营销内容**、当前无任何真实证言、示例
  永久标 AI、违规素材撤下条款）
- 素材登记 3 条：authorized 全 null、ad_label_required 强制——授权是 operator 事实

### 2. `share-card.schema.md` 去身份前瞻契约 + 契约 4/4

**现状结论（grep 全量证据）**：仓库无任何分享卡实现（web/src、static、controller
零命中）——走前瞻契约分支：
- 字段白名单 7 个（必含 `syntheticLabel: true` 恒真不可移除）+ 禁止黑名单 9 项
  （username/nickname/rawMemory/dialogExcerpt 等）；核心承诺：**去身份预览先于
  发布**、禁 P0/P1 原文、不从倾诉生成营销内容
- **实现哨兵**：契约测试扫描仓库文件名，一旦出现 share-card 实现文件即失败并
  要求升级为渲染产物断言——前瞻契约不会在实现落地后被遗忘绕过

## 回归

后端全量 **1686/1686 绿**（本包 +12），web 750/750。

## 诚实边界（operator 门禁）

素材授权/广告投放/真实证言采集（须用户同意且与示例区分）/渠道实验执行——品牌与
增长 operator 域。

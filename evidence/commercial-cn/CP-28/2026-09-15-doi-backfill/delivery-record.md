# CP-28 旧三技能 evidence 补 DOI — 增量（§2-24）

- 日期：2026-09-15；实施：后台 agent（skills manifest 域）

## 交付

- `values-compass.v1.json`：Schwartz 1992 价值观理论补 `DOI 10.1016/S0065-2601(08)60281-6`；BPNT 补 Deci & Ryan 2000 `DOI 10.1207/S15327965PLI1104_01`；
- `decision-conflict-map.v1.json`：autonomy support 补 Su & Reeve 2011 元分析 `DOI 10.1007/s10648-010-9142-7`；
- **逐条 Crossref API 验证命中**（标题/作者/卷期页全对）；Miller 1944《Experimental Studies of Conflict》为书章（Hunt 主编），Crossref 无记录——**如实标「未检索到 DOI，保留原文引注」，不编造**；
- 过程中否决一次记忆偏差：10.1037/a0021580 直查是 PTSD 论文而非 Su & Reeve——Crossref 直验避免了错 DOI。

## 测试

PsychologySkillRegistryTest 4/4 + SkillDisarmament/SkillExpertReview 契约绿。

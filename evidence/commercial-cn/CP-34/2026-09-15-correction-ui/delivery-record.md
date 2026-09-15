# CP-34 纠错提案前端 UI — 第二增量（登记项闭环）

- 日期：2026-09-15；实施：后台 agent（web 域）

## 交付

LettersInbox 往来标签选中线程下方：incoming PROPOSED 提案卡（接受/婉拒）、outgoing 状态列表（待对方/已应用/被婉拒[含理由原文]/已撤回，仅 PROPOSED 可撤回）、最小提案表单。Hook 随 openThread 并行加载、独立 error 不拖垮信件、决策后静默刷新。路径差异：实际基路径是单数 /api/relation（按代码）。

## 测试

LettersInbox 28/28（+3）、useConnectionsAndLetters 57/57（+7）；全量 web 869/869。

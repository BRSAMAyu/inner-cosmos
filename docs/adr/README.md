# Architecture Decision Records

架构决策记录（ADR）用于保存影响 M1 及后续里程碑的不可逆或高成本决策。编号采用四位序号，例如 `0001-use-mysql-for-m1.md`。

## 状态

- `PROPOSED`：正在评审，尚未生效。
- `ACCEPTED`：已生效。
- `SUPERSEDED`：已由后续 ADR 取代；不得删除原记录。
- `REJECTED`：已评审但未采用。

新 ADR 从 [template.md](template.md) 复制。任何 `ACCEPTED` ADR 的变更必须通过新的 superseding ADR 完成。

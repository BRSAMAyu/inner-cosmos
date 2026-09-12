# CP-43/44 渠道提审材料清单（结构化骨架）

每渠道一份 `*.checklist.yml`：`docs/commercialization/store-submissions/`。结构合同由
`StoreSubmissionChecklistContractTest`（后端测试套件）强制：必填字段齐全、id 唯一、
status ∈ {PENDING, IN_PROGRESS, WAIVED}、WAIVED 必须带 approved_by、不存在 PASS
（审批通过只能由 operator 以 evidence 官方回执落盘）。

渠道来源：蓝图 §5.4 渠道矩阵。未核验渠道（vivo/应用宝等）按蓝图要求保持 PENDING，
不得凭经验填通过。

**人工门禁**：真实组织账户、资质申报、审批回执、审核账号均需 operator 外部完成；
本骨架是那些证据的落盘位置与结构合同，不是审批结果的替代。

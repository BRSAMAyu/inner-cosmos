# Inner Cosmos Innovation Evaluation Harness

隔离、离线优先的研究评测工作区。它不导入生产数据库，不读取 P0 对话，也不修改 Aurora、Proactive、Self 或 Capsule 的生产行为。

## 可复现运行

```powershell
cd ai-lab
python -m unittest discover -s tests -v
python -m evals.cli.main validate
python -m evals.cli.main run --output ../evidence/innovation/INNO-EVAL-001
python -m evals.cli.main psychology --output ../evidence/innovation/INNO-PSY-001
```

默认只运行 synthetic/contract/fixture baseline。`real-provider` 在外部凭据轮换门禁关闭前始终返回 `BLOCKED_BY_CREDENTIAL_GATE`，不会读取 API Key，也不会静默回退到 Mock。

真实 Provider pairwise 使用显式、当次会话环境变量；输出只保留 endpoint hash，不保存密钥或 endpoint：

```powershell
$env:REAL_PROVIDER_BASE_URL="https://approved-provider.example/v1"
$env:REAL_PROVIDER_API_KEY="..."
$env:REAL_PROVIDER_MODEL_A="baseline-model"
$env:REAL_PROVIDER_MODEL_B="candidate-model"
python -m evals.cli.main real-pairwise --output ../evidence/innovation/INNO-EVAL-002
```

该命令对 4 条合成 interrupt/replan trajectory 各调用两个真实模型，生成盲化人工 pairwise CSV。缺少任一配置立即失败；没有 Mock fallback，也不把未完成人工复核写成胜出结论。

## 边界

- `compiler_train`、`development`、`held_out_trajectory`、`adversarial` 四个 split 分离。
- held-out/adversarial 内容不得进入 prompt、policy、genome 或 compiler input。
- 数据记录必须含来源、用途、敏感级别和许可。
- 单一 LLM Judge 不能决定优劣；无 Key 时 optional judge 明确 `NOT_RUN`。
- 人类 A/B 导出隐藏系统名称并用固定 seed 随机化顺序。

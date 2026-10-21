# Manual Acceptance — apollo-repair-retention-master

## Epoch 2 — 2026-10-21T23:02:59+08:00

- Master plan: docs/plans/2026-10-21/apollo-repair-retention-master.md (sha256 7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda)
- Reviewed code boundary: 962fcfe924ad7562a4ea917d174180df82ac3b5c..aba2430dabe1237f114708033b02776f83ee79a6
- Machine report epoch: machine-verification.md, Epoch 2
- Machine result: PASS / PROGRESSING
- Status: PENDING

以下仅取自主计划人工验收 A-1/A-2/A-3；覆盖它们明确引用的子计划人工项。机器测试不填写人类结果；后续结果及签收追加保存，不改写本待验记录。部署后生产对照仍待完成，须按主计划原有授权条件执行。

| ID | Mandatory | Check / prerequisites / steps | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | YES | 失败可解释且可恢复；前置：测试环境可控制Apollo Mock，01/02已部署。；步骤：1. 分别触发明确额度422、未知422和429；2. 查看逐条详情；3. 重启并推进冷却时钟；4. 将探测响应改200。；覆盖：I-1/I-2；01/02全部人工项。 | 错误分三类；未知不冒充额度；冷却中DETAIL新增0，单探测后恢复；其他通道继续；旧attempt不变。 | PENDING | — | — | — |
| A-2 | YES | 漏查修复不放宽同名保护；前置：测试环境含分页上限、Nicolas、同名歧义、SELF直接准入和APOLLO_UNCLEAR样本。；步骤：1. 依03/04清单运行；2. 读取新trace和旧记录；3. 对照固定输入评估报告。；覆盖：I-1/I-2；03/04/05全部人工项。 | 上限后机构查询执行但完整性仍保守；Nicolas固定样本改善1人；仅姓名/同名歧义不准入；既有SELF/APOLLO_UNCLEAR语义保持；旧版本不变。 | PENDING | — | — | — |
| A-3 | YES | 统计与留档闭环；前置：测试run含1条PROJECTED和1条ALREADY_PRESENT，已有论文样本；完成06。；步骤：1. 打开运行中及结束后详情；2. 检查逐条诊断和对照报告；3. 对比原样本hash。；覆盖：I-1/I-2/I-3；03/05/06跨路径。 | 自动准入2、晋升1；两个读取入口一致；旧原文缺失显示NOT_RECORDED；样本hash不变；刷新无写操作。 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: aba2430dabe1237f114708033b02776f83ee79a6
- Reporter: —
- Timestamp: —
- Note: 待人类逐项给出 verdict 与证据/说明，并明确接受或拒绝此产品边界。

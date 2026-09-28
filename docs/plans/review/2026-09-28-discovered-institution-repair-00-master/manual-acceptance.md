# Manual Acceptance — discovered-institution-repair-00-master

## Epoch 2 — 2026-09-28T22:00:00+08:00

- Reviewed code boundary: `d90084841d400e75eb0f2b6c4c6726e54307260a..5096e0618f7ecdc2224b9b472effd812d5b96828`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 在测试环境导入 JATS 脚注/ROR/邮箱混合机构样本及多机构样本；运行发现任务并打开两名专家资料。 | 前者仅显示来源明确机构；后者机构为空且不猜选第一家；两者任职为空。 | PENDING |  |  |  |
| A-2 | Yes | 候选层放入一名有机构但缺证据的新发现专家，及一名来源证实且 `filterResult=PASSED` 的新发现专家；查看发送预估、运行测试发送，再把第一名作为 NEW 重试。 | 预估和两条路径仅包含第二名；第一名无新邮件记录。 | PENDING |  |  |  |
| A-3 | Yes | 准备一名原本满足首发配置的非新发现候选专家；查看预估并执行测试首发。 | 该专家仍计入预估，发送行为与修复前一致。 | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: `5096e0618f7ecdc2224b9b472effd812d5b96828`
- Governing master identity required: `2f7fdb23cdfa016795827416d9d43f73d19798457a9c32c20605ea3a42d404c6`
- Retroactively authorized files: N/A
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING

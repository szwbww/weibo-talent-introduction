# Manual Acceptance — master: docs/plans/2026-08-10/00-main-plan-sender-binding.md

## Epoch 1 — 2026-08-11T10:06:26+08:00

- Reviewed code boundary: e6662677cc715421566006bbb90e3d47a75302b6..60e8e3c04400643dbd27abc6a826cf20df250d19
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| J-1 | Yes | 对已绑定账号 `X` 的专家禁用 `X` 后，在详情页选模板发送；查询发送后新增 OUTBOUND `mail_record` 并检查收件箱。 | 发送端显示含 `X` 与 `DISABLED` 的 400；发送后新增 OUTBOUND 记录为 0；未收到邮件。 | PENDING | — | — | — |
| J-2 | Yes | 新专家跑首封并记录账号 `A`；回信后人工回复；创建并确认会议；用 P4 换绑到 `B`；再发模板、再回信并人工回复。 | 首封/会议/新模板的 `sender_account_code` 为 `A`/`A`/`B`；两次回复均为 `A`；换绑后列表显示「发送账号已变更」。 | PENDING | — | — | — |
| J-3 | Yes | 禁用名下至少 5 位专家的账号 `A`（其中一位已有主动换绑标记）；跑材料提醒；用 P4 把 A 批量迁到 `C`；刷新列表和账号池；再跑材料提醒。 | 首次任务 COMPLETED 且各专家记录 `A/DISABLED` 跳过；`migrated` 等于 A 的绑定数；列表改为 C 且仅原先一位保留标记；A 绑定数为 0、C 相应增加；再次发送均从 C 成功。 | PENDING | — | — | — |
| J-4 | Yes | 在 `expert_contact` 有至少 1000 条绑定记录时，记录绑定分布；跑 100 人首封批量；比较增量。 | 新增 100 条显著偏向原存量最少账号；原存量最多账号在权重高且额度充足时不为 0。 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: 60e8e3c04400643dbd27abc6a826cf20df250d19
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING

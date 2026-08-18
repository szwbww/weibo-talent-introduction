# Manual Acceptance — auto-reply-convergence

## Epoch 1 — 2026-08-18

- Reviewed code boundary: `45835259dee5b0407385c457cb0420c31017b8e3..1d4eede453a8ffbff23a8d8122c609613c8890ea`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 01-A1 | Yes | 研究匹配来信两侧结论 | 预览 `QA_GAP`/`QA_GROUNDING_GAP`；工作台 `UNSUPPORTED`；两侧一致。 | PENDING | | | |
| 01-A2 | Yes | 训练知识生效 | 新增命中训练条目后，自动预览正文变化且可见其影响。 | PENDING | | | |
| 01-A3 | Yes | 无联系人 fail-closed | 无 500/预览失败；非 `QA_AUTO_REPLIED`；保留既有阻断标记。 | PENDING | | | |
| 01-A4 | Yes | 预览反事实/只读 | 正文照常显示、显示阻断标记；连点 3 次无状态或记录写入。 | PENDING | | | |
| 01-A5 | Yes | 既有可自动回复回归 | `QA_AUTO_REPLIED`、规则 ID 不变、正文非空。 | PENDING | | | |
| 01-A6 | Yes | 会议/人工分支回归 | 会议为邀请结果；ASK_FUNDING 为 `MANUAL_HANDOFF`/`HANDLE_RISKY_QUESTION`；均非 QA。 | PENDING | | | |
| 02-A1 | Yes | 单一预览渲染器 | 一处预览；诉求数/文本/顺序/grounding 与工作台逐项一致；预览无控件且无生成按钮。 | PENDING | | | |
| 02-A2 | Yes | 闸门只标记 | 显示 `AUTO_REPLY_DISABLED` pill，正文仍完整显示。 | PENDING | | | |
| 02-A3 | Yes | AUTO_PREVIEW 只读 | Tab 无可交互控件；反复打开不改变 `trust_reply_workbench_state` 行数或版本。 | PENDING | | | |
| 02-A4 | Yes | 无联系人降级 | 显示精确降级文案；无裸 4xx；其他详情正常。 | PENDING | | | |
| 02-A5 | Yes | 训练宿主回归 | 模拟页逐项生成、采用、整合、保存评估全程正常。 | PENDING | | | |
| 02-A6 | Yes | 生产工作台采用回归 | 工作台整合后草稿进入人工回复编辑器并显示采用提示。 | PENDING | | | |
| 02-A7 | Yes | UI 目测 | 预览与相邻折叠卡视觉同构；只读/闸门样式符合计划；无 inline style。 | PENDING | | | |
| 03-A1 | Yes | 影子模式样本且不发信 | 新增 SHADOW 日志；CRS 0–100、分项和允许 ±0.05；`ready_to_send=0`；OUTBOUND 不变。 | PENDING | 记录 03/O-1：特定三等分输入可出现 0.1 显示差异。 | | |
| 03-A2 | Yes | 两开关全关 | 无新日志、无 LLM 调用；`MANUAL_HANDOFF`/`AI_AUTO_REPLY_DISABLED`。 | PENDING | | | |
| 03-A3 | Yes | 日志失败不阻断 | WARN 出现；来信仍 `MANUAL_REVIEW` 正常落库；无 500；验收后恢复表。 | PENDING | | | |
| 03-A4 | Yes | 分数可解释 | `history_score=7.00`；coverage 手算允许 ±0.05；计数关系和 BLOCKED consistency 符合计划。 | PENDING | | | |
| 03-A5 | Yes | 预览不写日志 | 打开/预览 5 次后日志行数不变。 | PENDING | | | |
| 03-A6 | Yes | 自动回复开启回归 | 仍自动发送且日志 `ready_to_send=1`；低 CRS 不影响发送。 | PENDING | | | |

## Human Sign-off

- Decision: PENDING
- Boundary: `1d4eede453a8ffbff23a8d8122c609613c8890ea`
- Reporter: user
- Timestamp: PENDING
- Note: Docker-backed Flyway migration integration was explicitly waived for machine review by the user; no manual item is pre-marked passed.

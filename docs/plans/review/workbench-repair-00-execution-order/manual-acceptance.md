# Manual Acceptance — workbench-repair-00-execution-order

## Epoch 1 — 2026-08-20T01:31:04Z

- Reviewed code boundary: 3bd132cb429a6928aa0eaa7c9f72d733d6905a15..8ee03a9b207227890bca01da272207ff9a22f943
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 01-A-1 | Yes | 点击页签不再报错且焦点正确 | 无 `querySelector` SyntaxError；焦点落在当前页签。 | PENDING | — | — | — |
| 01-A-2 | Yes | 键盘方向键导航 | 箭头键切换后焦点持续留在当前页签。 | PENDING | — | — | — |
| 01-A-3 | Yes | 切换页签不重新加载数据 | 6 次切换无 bootstrap 请求，已有状态保留。 | PENDING | — | — | — |
| 01-A-4 | Yes | 事实拖拽排序后的焦点恢复 | 排序后焦点仍在同一事实的拖拽把手。 | PENDING | — | — | — |
| 01-A-5 | Yes | 视觉无变化 | 页签样式、间距、内容区与基线一致。 | PENDING | — | — | — |
| 02-A-1 | Yes | 多问题来信的整合正文分段 | 多事实答案各自成段、段间有空行。 | PENDING | — | — | — |
| 02-A-2 | Yes | 单事实摘要仍是一段 | 单事实答案内部不注入空行。 | PENDING | — | — | — |
| 02-A-3 | Yes | 未编辑直接发送 | Gmail 网页版收件正文保留段落。 | PENDING | — | — | — |
| 02-A-4 | Yes | 编辑后再发送 | 编辑后外发正文仍保留段落。 | PENDING | — | — | — |
| 02-A-5 | Yes | 存量未完成草稿降级 | 升级后安全 STALE，不错误恢复。 | PENDING | — | — | — |
| 02-A-6 | Yes | 信任门禁未放松 | 信任不足时仍走既有拒绝/门禁。 | PENDING | — | — | — |
| 02-A-7 | Yes | 重复答案仍被拒 | 重复答案仍被系统拒绝。 | PENDING | — | — | — |
| 03a-A-1 | Yes | 改一条摘要的事实 | 仅该条失效，其余锁定回答保留。 | PENDING | — | — | — |
| 03a-A-2 | Yes | 拖动事实顺序 | 仅对应摘要失效。 | PENDING | — | — | — |
| 03a-A-3 | Yes | 确认框范围 | 仅真会丢失本条时弹窗，文案说明其余保留。 | PENDING | — | — | — |
| 03a-A-4 | Yes | 事实换绑 | 两端失效，第三条不变。 | PENDING | — | — | — |
| 03a-A-5 | Yes | 部分失效后整合发送 | 重新生成失效项后整合成功，无 409/整体过期。 | PENDING | — | — | — |
| 03a-A-6 | Yes | 刷新后部分恢复 | 保留项恢复，失效项为空，并提示保留/丢弃数。 | PENDING | — | — | — |
| 03a-A-7 | Yes | 部署前存量草稿 | 存量锁定项整体安全作废，无错误恢复。 | PENDING | — | — | — |
| 03a-A-8 | Yes | 事实唯一归属 | 已绑定事实在其他摘要不可添加。 | PENDING | — | — | — |
| 03a-A-9 | Yes | 来源变化全量重置 | 新来信触发既有整体重置。 | PENDING | — | — | — |
| 03a-A-10 | Yes | 审计聚合指纹 | 审计 `evidenceSetVersion` 存在、非空、64 位十六进制。 | PENDING | — | — | — |
| 03b-A-1 | Yes | 编辑训练知识 | 已锁定回答不整体清空；按计划的局部状态呈现。 | PENDING | — | — | — |
| 03b-A-2 | Yes | 同专家新增来信 | 回复台不再整体重置；按计划的局部状态呈现。 | PENDING | — | — | — |
| 03b-A-3 | Yes | 一键重跑 | 只重跑 context-stale 项，无 bootstrap/整体 reset。 | PENDING | — | — | — |
| 03b-A-4 | Yes | 画像变化 | 仅研究匹配条目受影响。 | PENDING | — | — | — |
| 03b-A-5 | Yes | 来信正文变化 | 仍触发全量重置。 | PENDING | — | — | — |
| 03b-A-6 | Yes | 画像不足研究匹配 | 仍为 UNSUPPORTED，不降级为自动回答。 | PENDING | — | — | — |
| 03b-A-7 | Yes | 生成质量 | 内容和风格无系统性变化。 | PENDING | — | — | — |
| 03b-A-8 | Yes | 自动回复路 | 自动回复决策与部署前一致。 | PENDING | — | — | — |
| 03b-A-9 | Yes | 03a 局部失效回归 | 03a A-1、A-4、A-5 仍通过。 | PENDING | — | — | — |

The exact procedures and expected outcomes are the respective `A-*` sections in the four ordered child plans named by the master plan.

## Human Sign-off

- Decision: PENDING
- Boundary: 8ee03a9b207227890bca01da272207ff9a22f943
- Reporter: —
- Timestamp: —
- Note: —

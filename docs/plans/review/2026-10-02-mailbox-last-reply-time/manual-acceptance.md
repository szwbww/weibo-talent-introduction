# Manual Acceptance — mailbox-last-reply-time

Checklist source: `docs/plans/2026-10-02/mailbox-last-reply-time.md` §人工验收清单 (authoritative; the plan's A-n text governs if any wording here differs).

## Epoch 4 — 2026-10-03

- Reviewed code boundary: `bf19fdfcb24336a41106d1c46fa7147bc6546892`..`d3c7709f0f2d2460f5b6eed015a443ab83f94558`
- Machine report epoch: 4
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | YES | 日期、星期、时区可见 | 列表“上次回复 2026-10-02 星期五 17:59”；详情“专家上次回复 … 北京时间”；设备时区改为 America/Los_Angeles 后仍相同；不悬停可见 | | | | |
| A-2 | YES | 更晚发件与空值边界 | A“2026-10-01 星期四 20:48”；B（null+0）“尚未回复”；C（null+2、2026-02-30）“回复时间暂不可用”；D“2025-12-31 星期三 23:58”；无当前时间兜底 | | | | |
| A-3 | YES | 刷新同步且不覆盖草稿 | 两处依次 18:05、18:06；正文草稿保留、目标来信编号不变、无发送、编辑器不闪退 | | | | |
| A-4 | YES | 既有过滤和非目标视图 | acc-a/acc-b 分别显示 10-01 20:48 / 10-02 17:59；搜索与页签仍由原筛选决定成员；分页仍 20 条；待匹配不新增时间栏 | | | | |
| A-5 | YES | 现有详情操作与布局 | 原标签/计数/账号/材料值不变且可用；时间位置与字号/强调色符合 S-1/S-2/S-3；窄屏不截断、无横向溢出 | | | | |
| A-6 | YES | 迟到回包与切换专家 | A 列表保留 18:05；B 详情显示 B 的 10:30；离开后无残留节点，返回按新回包显示 | | | | |
| A-7 | YES | 来信绑定和处理时间不混淆 | 始终显示收件时间 T；绑定/标记/撤销不改变显示；新来信到达并刷新后才更新 | | | | |
| A-8 | YES | 缓存更新与范围回归 | 11 项资源均为 `v=20261002-mailbox-last-reply`；新行有 S-3 样式；专家列表/任务钻取无新增时间栏；无新 API/轮询/DB 写入 | | | | |

## Human Sign-off
- Decision: PENDING
- Boundary: d3c7709f0f2d2460f5b6eed015a443ab83f94558
- Reporter: (awaiting human)
- Timestamp: (pending)
- Note: Machine review epoch 4 PASS; human acceptance A-1..A-8 has not been performed by the agent.

# Manual Acceptance — master: docs/plans/2026-08-16/00-execution-order.md

## Epoch 2 — 2026-08-17

- Reviewed code boundary: `edda3e4e67e8b4511f3c7ca76b09926c56e4f69a..3867f61b26b5584b54ac52e540360f7aa8122492`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-M1 | yes | With ≥200 historical executions including completed `MANUAL_INITIAL_OUTREACH`, `AUTO_REPLY_ALL`, and `EXPERT_ENRICHMENT`: open 任务记录, inspect first page/type/metrics, expand all three, then use the mail drilldown. | <2s first page; exactly 50 rows and pager; Chinese type names; correct metric semantics; all three expand; mail drilldown filters to this execution and count matches sent count. | PENDING | — | — | — |
| A-M2 | yes | Expand a completed `EXPERT_ENRICHMENT` execution and attempt its drilldown entry. | Grey disabled entry, text `该任务无个体明细`, no request or action. | PENDING | — | — | — |
| A-M3 | yes | Open 顶部导航「轮询日志」. | Modal shows 10 recent polls with account/fetch/reply/next-poll values, unchanged behavior. | PENDING | — | — | — |
| A-M4 | yes | Start a small bulk outreach, open progress while running, switch to 批次明细. | Batch details render as before, with no duplicate batches. | PENDING | — | — | — |
| A-M5 | yes | Open the view using `app.js:5276` with a `CANDIDATE_OPERATOR_STATUS_SYNC` execution. | Data renders; browser console has no `undefined` or `.map is not a function`. | PENDING | — | — | — |
| A-M6 | yes | Insert 91-day-old `task_execution` and orphan `task_progress_log`; trigger retention; query both tables and task records. | Both old rows deleted; ≤90-day data kept; `TASK_AUDIT_RETENTION` audit has the total deleted count. | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `3867f61b26b5584b54ac52e540360f7aa8122492`
- Reporter: —
- Timestamp: —
- Note: —

# Fast-P Ledger — master: docs/plans/2026-08-16/00-execution-order.md

- Status: RUNNING
- Master plan: docs/plans/2026-08-16/00-execution-order.md (commit 65b8de831a5f0edeafeae5683a2f15b79f7000a3)
- Amendments: A1
- Master base: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a
- Branch: fast/2026-08-16-execution-order
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-16T00:00:00+08:00
- Current child: a1
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: a1 epoch 2, base edda3e4e67e8b4511f3c7ca76b09926c56e4f69a, next action dispatch implementer with amended brief (T-D4)

## Baseline

Approved execution start: master base `edda3e4e67e8b4511f3c7ca76b09926c56e4f69a` (branch `main`, commit `fix: compact batch task console layout`). Plans seeded on the fast branch in commit `65b8de831a5f0edeafeae5683a2f15b79f7000a3` (docs-only, plan files under `docs/plans/2026-08-16/`).

Baseline command results, run in the retained fast worktree at the master base (node checks) and in the main worktree at the same SHA (mvn test, relocated to avoid target/ races with child implementers):

- `node --check src/main/resources/static/app.js` -> exit 0 (APPJS_OK)
- `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` -> exit 0, fail 0
- `node --test src/test/js/*.test.js` -> exit 0, fail 0
- `git diff --check` -> clean
- `JAVA_HOME=...zulu-11... mvn test` -> exit 0, `Tests run: 2456, Failures: 0, Errors: 0, Skipped: 4`, `BUILD SUCCESS`. Baseline fully green (no pre-existing failures to compare against).

JDK 11 (zulu-11) verified: `openjdk version "11.0.15"`. Node `v25.7.0`.

Cache key chain (authoritative, from master plan): current(unstarted) `20260816-v1-batch-console-list-layout`; A1 `20260817-v1-batch-console-row-drawer`; A2 `20260817-v2-batch-manual-log-entry`; A3 `20260817-v3-expert-list-entry-move`; B1 `20260817-v4-task-records-paging`; B2 `20260817-v5-task-type-catalog`; B3 n/a; B4 `20260817-v6-task-drilldown`; B5 n/a.

Migration chain (authoritative): current max `V99__add_gate_filter_enabled_to_batch_send_task_config.sql`; B1 `V100__add_task_execution_indexes.sql`; B3 `V101__add_task_execution_id_to_mail_record.sql`; B5 `V102__add_task_progress_log_created_at_index.sql`. A family: none.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| a1 | docs/plans/2026-08-16/a1-batch-list-row-and-drawer-visual.md | commit:d32a4cfb45e32b0932955290260839858b959c79 | none | 2 | IMPLEMENTING | edda3e4e67e8b4511f3c7ca76b09926c56e4f69a | | 0 | — | | | epoch 1 PLAN_CONFLICT amended via A1; 5 authorized files modified, uncommitted |
| a2 | docs/plans/2026-08-16/a2-batch-manual-log-reachability.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | a1 | 0 | PENDING | | | 0 | — | | | |
| a3 | docs/plans/2026-08-16/a3-expert-list-rename-and-entry-move.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | a1,a2 | 0 | PENDING | | | 0 | — | | | |
| b1 | docs/plans/2026-08-16/b1-task-execution-list-performance.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | a1,a2,a3 | 0 | PENDING | | | 0 | — | | | |
| b2 | docs/plans/2026-08-16/b2-task-type-catalog-semantics.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | b1 | 0 | PENDING | | | 0 | — | | | |
| b3 | docs/plans/2026-08-16/b3-mail-record-execution-link-backend.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | b2 | 0 | PENDING | | | 0 | — | | | |
| b4 | docs/plans/2026-08-16/b4-task-drilldown-frontend.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | b3 | 0 | PENDING | | | 0 | — | | | |
| b5 | docs/plans/2026-08-16/b5-task-audit-retention.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | b1 | 0 | PENDING | | | 0 | — | | | |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-16/a1-batch-list-row-and-drawer-visual.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | commit:d32a4cfb45e32b0932955290260839858b959c79 | a1 阶段C T-C2 + 验收 A-8（实时消息裸值）与 a1 验证命令 通过判据（batchManualExecutionLog.test.js 须 fail 0） | 计划要求未授权测试文件保持绿，T-C2 却必然使其红；授权同步该文件 :331-349 断言为裸消息（T-D4），accountCode 转义断言不动 | HUMAN:ask 选项「Amend: authorize test edit, assert raw message」2026-08-16 |

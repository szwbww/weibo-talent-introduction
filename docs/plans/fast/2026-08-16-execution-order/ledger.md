# Fast-P Ledger — master: docs/plans/2026-08-16/00-execution-order.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-08-16/00-execution-order.md (commit 65b8de831a5f0edeafeae5683a2f15b79f7000a3)
- Amendments: A1,A2,A3,A4,A5,A6
- Master base: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a
- Branch: fast/2026-08-16-execution-order
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-16T00:00:00+08:00
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: b5 epoch 2, base d32ccb282d88a6e6182bb579acbc0b65d74995eb, implementation 2856a71c62252358d417b0f63810e547e66075f0 retained, next action apply T3-8 lock updates as round 1 fix then verify
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: b2 epoch 2, base ad005d98b706ceed67b34c96a89e642334ca819a, next action dispatch implementer with amended brief (14 files; metricLabel decisions carried)

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
| a1 | docs/plans/2026-08-16/a1-batch-list-row-and-drawer-visual.md | commit:d32a4cfb45e32b0932955290260839858b959c79 | none | 2 | LIGHT_PASS_WITH_NOTES | edda3e4e67e8b4511f3c7ca76b09926c56e4f69a | 9dfbd5e1bae6d3dcb5dfe1beb85265af5a4bdabd | 0 | — | 9dfbd5e1bae6d3dcb5dfe1beb85265af5a4bdabd | 03ea6672b8e3e9f57954e70cd3ad93c383681887 | epoch 1 PLAN_CONFLICT resolved via amendment A1; RECORD_ONLY O-1: renderErrorSamples pre-existing substring truncation |
| a2 | docs/plans/2026-08-16/a2-batch-manual-log-reachability.md | commit:5f361ed5c8c0bcb8fd747f43bfe0ffa9daf2bdeb | a1 | 2 | LIGHT_PASS | 9dfbd5e1bae6d3dcb5dfe1beb85265af5a4bdabd | bb07586b758357ad21794e17b7e99f200abeed5b | 0 | — | bb07586b758357ad21794e17b7e99f200abeed5b | 8d497b05585bb46e33694ec8fa1d5d1ea3b23cba | epoch 1 scope pause resolved via amendment A2; RECORD_ONLY O1/O2 in verify-log carried to handoff |
| a3 | docs/plans/2026-08-16/a3-expert-list-rename-and-entry-move.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | a1,a2 | 1 | LIGHT_PASS | bb07586b758357ad21794e17b7e99f200abeed5b | e1ce1cbf1eeaba87e670771f23c25f2d2293a768 | 0 | — | e1ce1cbf1eeaba87e670771f23c25f2d2293a768 | b662e185fdd053011824977c603b6a32d79b5053 | RECORD_ONLY O-1 (surefire count reporting artifact) in verify-log |
| b1 | docs/plans/2026-08-16/b1-task-execution-list-performance.md | commit:9c1e78a6d549ae16a6f45ff7499d6e340e39d476 | a1,a2,a3 | 2 | LIGHT_PASS_WITH_NOTES | e1ce1cbf1eeaba87e670771f23c25f2d2293a768 | ad005d98b706ceed67b34c96a89e642334ca819a | 0 | — | ad005d98b706ceed67b34c96a89e642334ca819a | 5e49c0c947de7293a48b4be31150d0778d062a15 | epoch 1 scope pause resolved via amendment A3; RECORD_ONLY O-1/O-2/O-3 in verify-log |
| b2 | docs/plans/2026-08-16/b2-task-type-catalog-semantics.md | commit:38ce7ad494397d168663036e9252b3d6bf1c2089 | b1 | 2 | LIGHT_PASS_WITH_NOTES | ad005d98b706ceed67b34c96a89e642334ca819a | 7885ac04378f553376711184b6596bc2906a9ad1 | 1 | 7ca26a1 | 7ca26a1 | 816cd31cbdcdda409660f02735cd30303523a051 | epoch 1 scope pause resolved via amendment A4; round 1 F-1 whitespace fix; RECORD_ONLY O-1..O-3 |
| b3 | docs/plans/2026-08-16/b3-mail-record-execution-link-backend.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | b2 | 1 | LIGHT_PASS | 7ca26a1 | eb27b8d84a4286ce3ef92ca40acf98d761168121 | 0 | — | eb27b8d84a4286ce3ef92ca40acf98d761168121 | 199d02a4877a3f9a08b23e548f99127d72b31b17 | RECORD_ONLY O-1 (Flyway unexecuted, no Docker) / O-2 / O-3 |
| b4 | docs/plans/2026-08-16/b4-task-drilldown-frontend.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | b3 | 1 | LIGHT_PASS_WITH_NOTES | eb27b8d84a4286ce3ef92ca40acf98d761168121 | d32ccb282d88a6e6182bb579acbc0b65d74995eb | 0 | — | d32ccb282d88a6e6182bb579acbc0b65d74995eb | d130fe81e53f16936bd36f665ec416ab1f9163f5 | RECORD_ONLY O-1/O-2/O-3 in verify-log |
| b5 | docs/plans/2026-08-16/b5-task-audit-retention.md | commit:771c8555b1a8a2bf286249df50acfc7a66436f3a | b1 | 2 | LIGHT_PASS | d32ccb282d88a6e6182bb579acbc0b65d74995eb | 2856a71c62252358d417b0f63810e547e66075f0 | 1 | 4d7f206a4f506104af73f3e63e4fceea3d857ef7 | 4d7f206a4f506104af73f3e63e4fceea3d857ef7 | 2091a440b7aeaf88bf81c4ce522e9c59826b3b4e | epoch 1 scope pause resolved via amendment A6 (12th file); round 1 = T3-8 lock updates; RECORD_ONLY O-1/O-2/O-3 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-16/a1-batch-list-row-and-drawer-visual.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | commit:d32a4cfb45e32b0932955290260839858b959c79 | a1 阶段C T-C2 + 验收 A-8（实时消息裸值）与 a1 验证命令 通过判据（batchManualExecutionLog.test.js 须 fail 0） | 计划要求未授权测试文件保持绿，T-C2 却必然使其红；授权同步该文件 :331-349 断言为裸消息（T-D4），accountCode 转义断言不动 | HUMAN:ask 选项「Amend: authorize test edit, assert raw message」2026-08-16 |
| A2 | docs/plans/2026-08-16/a2-batch-manual-log-reachability.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | commit:5f361ed5c8c0bcb8fd747f43bfe0ffa9daf2bdeb | a2 阶段B T2-B5（triggerTypeLabel 文案）+ 共享审计 X-2 同步规则 vs a2 变更文件清单 | T2-B5 使 I-2 套件执行的 openBatchConfigLogs 路径调用 triggerTypeLabel，无 stub 时套件红；按 X-2 授权 createLogSandbox 补一行函数桩（T2-C4），不改任何断言 | HUMAN:ask 选项「Amend: authorize the one-line stub sync」2026-08-16 |
| A3 | docs/plans/2026-08-16/b1-task-execution-list-performance.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | commit:9c1e78a6d549ae16a6f45ff7499d6e340e39d476 | b1 T0-3（listExecutions 分页签名）vs b1 变更文件清单 | 旧签名唯一测试调用方 TaskExecutionServiceTest.kt 不在 9 文件清单内，签名变更必然 test-compile 红；授权第 10 个文件并重写该旧用例适配分页 API | HUMAN:ask 选项「Amend: authorize TaskExecutionServiceTest.kt as file 10」2026-08-16 |
| A4 | docs/plans/2026-08-16/b2-task-type-catalog-semantics.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | commit:38ce7ad494397d168663036e9252b3d6bf1c2089 | b2 T1-5/T1-6（controller 新依赖）+ I1-2（第 5 列新语义）vs b2 变更文件清单（10 文件上限） | 新依赖使 3 个既有测试文件编译/上下文失败，N0-1 断言与 I1-2 渲染语义冲突（实测互斥）；授权 4 个测试文件（总数 14），N0-1 第 5 列按新语义更新、其余六列逐字保留 | HUMAN:ask 选项「Amend: authorize the 4 test files (14 total)」2026-08-16 |
| A5 | docs/plans/2026-08-16/b5-task-audit-retention.md | commit:65b8de831a5f0edeafeae5683a2f15b79f7000a3 | commit:50a4532ca58cdcaadb3285a9e44395e8d494fea3 | b5 T3-6 处置（11>10 拆分 vs 完整）vs 实际合并历史（b2 catalog 无 TASK_AUDIT_RETENTION 预留） | 拆分方案前提（P1 预留条目）未成立，拆分会使 A3-1 记录页可见性验收不满足；经人工批准采用 11 文件完整方案，T3-6 内联完成 | HUMAN:ask 选项「Amend: 11 files, complete T3-6 inline」2026-08-16 |
| A6 | docs/plans/2026-08-16/b5-task-audit-retention.md | commit:50a4532ca58cdcaadb3285a9e44395e8d494fea3 | commit:771c8555b1a8a2bf286249df50acfc7a66436f3a | b5 T3-6（catalog 17 条目）vs b2 既有 TaskExecutionSummaryExtractorTest.kt 的精确相等锁断言（:219/:235/:199-201） | 锁断言按 16 条目设计，17 条目必然使其红且全量回归门禁失败；授权第 12 个文件做三条机械锁更新（T3-8），不改锁语义 | HUMAN:ask 选项「Amend: authorize the 3 lock-updates (12th file)」2026-08-16 |

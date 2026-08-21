# Fast-P Ledger — master: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md

- Status: RUNNING
- Master plan: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md (commit 2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd)
- Amendments: A1
- Master base: bb34ca2001d0abeac3bd7a8fc13995769e14143e
- Branch: fast/ui-tweaks-00-execution-order
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-ui-tweaks-00-execution-order
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-21T00:00:00Z
- Current child: p1
- Waiting role: VERIFIER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- Approved execution start: main HEAD `bb34ca2` (2026-08-21; plans authored against this tree, untracked at plan-writing time; seeded into the fast branch as `2cbf6d3` docs-only commit).
- Master plan declares Git baseline `main` with only `docs/releases.json` local change and untracked files; no uncommitted source changes. Approved MASTER_BASE = `bb34ca2`; seed commit `2cbf6d3` (plans only, under `docs/plans/`) sits between master base and child p1 base.
- Plans authored against the 2026-08-21 worktree; per prior run convention, symbol/DOM anchors are authoritative for locating change points, line numbers are cross-check only (children P1-P4 bump the same triad so line numbers shift).
- Baseline results (2026-08-21, fast worktree, product tree = bb34ca2):
  - `node --test src/test/js/*.test.js`: exit 0; fail 0, cancelled 0 (node v25.7.0; plans recorded v22.23.2 at authoring time).
  - `node --check app.js / task-modal-runtime.js / trust-reply-workbench.js`: exit 0, no output.
  - `git diff --check`: exit 0, no output.
  - `mvn test` (JAVA_HOME zulu-11): exit 0; `Tests run: 2693, Failures: 0, Errors: 0, Skipped: 4` (pre-existing @Disabled integration tests); `node --test` executed in test phase (680 JS tests pass).
  - `docker info`: UNAVAILABLE. P4's required `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` cannot run as written; repo convention (ProgrammeIdentityFactsMigrationTest docstring) substitutes text-level migration assertions, and P4 already includes a text-level V107 assertion in QaRuleManagementServiceTest. Verifier decision deferred to P4.
- Child IDs p1..p4 = master plan rows 1..4 (P1..P4). Serial cache-key triad (K-frontend-cache-key-triad) makes execution order mandatory; plans otherwise zero code coupling.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| p1 | docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | none | 2 | LIGHT_VERIFYING | bb34ca2001d0abeac3bd7a8fc13995769e14143e | 9b90e41c678c396c7e720832c58e162e717f34da | 1 | 53e12b979025e1df5f36736b2baf30d9e0bc688e | 53e12b979025e1df5f36736b2baf30d9e0bc688e | — | epoch 2 (A1 approved 2026-08-21); round 1 FIXED 53e12b9; implementer P1Implementer-2 (epoch 1, also round-1 fixer); verifier dispatch pending |
| p2 | docs/plans/2026-08-21/ui-tweaks-02-overlay-and-dialog-contrast.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p1 | 1 | PENDING | — | — | 0 | — | — | — | 工作台遮罩补全 + 确认弹窗对比度；5 files |
| p3 | docs/plans/2026-08-21/ui-tweaks-03-manual-reply-subject-prefill.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p2 | 1 | PENDING | — | — | 0 | — | — | — | 人工富文本回复主题预填；4 files |
| p4 | docs/plans/2026-08-21/qa-gate-visibility.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p3 | 1 | PENDING | — | — | 0 | — | — | — | QA 门禁可见化 + V107；10 files, 2 subsystems |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:c7b91635195f6007b297dc42777128db81559107 | P1 计划 I-4 + K-ui-removal-retires-obsolete-contract-tests（删 UI 必须成组删干净并退休契约测试） | P1 遗漏第二个断言已删 AUTO_PREVIEW 标识的契约测试 unmatchedQaReplySource.test.js，全量 JS 门禁失败；文件预算 8/8 需扩为 9 个授权文件 | HUMAN:user selected "Approve amendment A1: add test as 9th authorized file, flip 4th case to retirement guard" (ask p1_amendment, 2026-08-21) |

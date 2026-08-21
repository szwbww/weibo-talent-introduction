# Fast-P Ledger — master: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md (commit 5b6c6ffe777b00c447c81cc38700f80f9ec07fdb)
- Amendments: A1, A2, A3
- Master base: bb34ca2001d0abeac3bd7a8fc13995769e14143e
- Branch: fast/ui-tweaks-00-execution-order
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-ui-tweaks-00-execution-order
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-21T00:00:00Z
- Current child: p3
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: P3 implementer proved that pre-existing src/test/js/expertProfileAbsence.test.js:384 (showUnmatchedDetail sandbox case) fails with ReferenceError: buildManualReplySubject is not defined after p3's mandated S-1 change — createRendererSandbox (lines 326-349) loads a fixed extractFunction list that excludes the new buildManualReplySubject. The file is NOT in p3's authorized 6-file list and the plan never mentions it/sandbox. Fix is one line (register buildManualReplySubject in the sandbox); requires amendment A4 adding it as the 7th authorized file. This is the vm-sandbox variant of the A1/A2 pattern (plan blind spot on dependent tests), not covered by A3 (A3 covers run-created triad-asserting tests only).
- Resume from: 0ad6b3b (p3 base; partial uncommitted p3 edits retained: app.js, index.html, batchSendTaskConsoleVisualFix.test.js, checkRepliesRelocation.test.js, overlayAndDialogContrast.test.js, new manualReplySubjectPrefill.test.js)

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
| p1 | docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | none | 2 | LIGHT_PASS_WITH_NOTES | bb34ca2001d0abeac3bd7a8fc13995769e14143e | 9b90e41c678c396c7e720832c58e162e717f34da | 1 | 53e12b979025e1df5f36736b2baf30d9e0bc688e | 53e12b979025e1df5f36736b2baf30d9e0bc688e | b525450eeae6375db4ec64f1ca4e96360f941378 | epoch 2 (A1 approved 2026-08-21); round 1 FIXED; verifier P1Verifier-2; RECORD_ONLY O-1 (A1 guard asserts 2 extra absence tokens, benign); implementer P1Implementer-2 |
| p2 | docs/plans/2026-08-21/ui-tweaks-02-overlay-and-dialog-contrast.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p1 | 2 | LIGHT_PASS | 53e12b979025e1df5f36736b2baf30d9e0bc688e | 0ad6b3b188e4cef69229fc3e5a06f1251d343db9 | 0 | — | 0ad6b3b188e4cef69229fc3e5a06f1251d343db9 | 69a6ee46684c3e90e4a3161505ccca5e13df5e1f | epoch 2 (A2+A3 approved 2026-08-21); verifier P2Verifier; RECORD_ONLY O-1 (uncommitted doc-only edits at verify time, attributed to 0ad6b3b); implementer P2Implementer |
| p3 | docs/plans/2026-08-21/ui-tweaks-03-manual-reply-subject-prefill.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p2 | 1 | PAUSED_FOR_HUMAN | 0ad6b3b188e4cef69229fc3e5a06f1251d343db9 | — | 0 | — | — | — | 人工富文本回复主题预填；6 files (4 plan + 2 per A3)；implementer P3Implementer paused on unlisted expertProfileAbsence.test.js sandbox ReferenceError — amendment A4 pending |
| p4 | docs/plans/2026-08-21/qa-gate-visibility.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p3 | 1 | PENDING | — | — | 0 | — | — | — | QA 门禁可见化 + V107；10 files, 2 subsystems |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:c7b91635195f6007b297dc42777128db81559107 | P1 计划 I-4 + K-ui-removal-retires-obsolete-contract-tests（删 UI 必须成组删干净并退休契约测试） | P1 遗漏第二个断言已删 AUTO_PREVIEW 标识的契约测试 unmatchedQaReplySource.test.js，全量 JS 门禁失败；文件预算 8/8 需扩为 9 个授权文件 | HUMAN:user selected "Approve amendment A1: add test as 9th authorized file, flip 4th case to retirement guard" (ask p1_amendment, 2026-08-21) |
| A2 | docs/plans/2026-08-21/ui-tweaks-02-overlay-and-dialog-contrast.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:5b6c6ffe777b00c447c81cc38700f80f9ec07fdb | P2 计划 I-8 + K-frontend-cache-key-triad（三键断言同步规则） | P1 新增测试 checkRepliesRelocation.test.js:11,57 硬编码 v9 键，P2 bump v10 后全量 JS 门禁失败；文件预算 5/5 需扩为 6 个授权文件 | HUMAN:user selected "Approve A2 + A3 (recommended)" (ask p2_amendment, 2026-08-21) |
| A3 | docs/plans/2026-08-21/ui-tweaks-00-execution-order.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:5b6c6ffe777b00c447c81cc38700f80f9ec07fdb | master 计划「为什么必须按序」缓存键同步约定 + K-frontend-cache-key-triad | 本 run 每个后续子计划都需同步此前子计划新增的三键断言测试，避免逐计划重复暂停 | HUMAN:user selected "Approve A2 + A3 (recommended)" (ask p2_amendment, 2026-08-21) |

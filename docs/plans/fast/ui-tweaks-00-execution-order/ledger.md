# Fast-P Ledger — master: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md (commit 737b4b1a974a5ef40e5f46f2aa8b8b3c09e8c4fa)
- Amendments: A1, A2, A3, A4, A5
- Master base: bb34ca2001d0abeac3bd7a8fc13995769e14143e
- Branch: fast/ui-tweaks-00-execution-order
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-ui-tweaks-00-execution-order
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-21T00:00:00Z
- Current child: N/A
- Waiting role: N/A
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
| p1 | docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md | commit:c7b91635195f6007b297dc42777128db81559107 | none | 2 | LIGHT_PASS_WITH_NOTES | bb34ca2001d0abeac3bd7a8fc13995769e14143e | 9b90e41c678c396c7e720832c58e162e717f34da | 1 | 53e12b979025e1df5f36736b2baf30d9e0bc688e | 53e12b979025e1df5f36736b2baf30d9e0bc688e | ecc0d3f9479c3e4fe06c3f7987cdcb74703d5056 | epoch 2 (A1 approved 2026-08-21); round 1 FIXED; verifier P1Verifier-2; RECORD_ONLY O-1 (A1 guard asserts 2 extra absence tokens, benign); implementer P1Implementer-2 |
| p2 | docs/plans/2026-08-21/ui-tweaks-02-overlay-and-dialog-contrast.md | commit:737b4b1a974a5ef40e5f46f2aa8b8b3c09e8c4fa | p1 | 2 | LIGHT_PASS | 53e12b979025e1df5f36736b2baf30d9e0bc688e | cc9037dcc9c194e2e80f22274ee0d3e90c22da04 | 0 | — | cc9037dcc9c194e2e80f22274ee0d3e90c22da04 | dbe3429323ca13deb73afe1297555f8543b81156 | epoch 2 (A2+A3 approved 2026-08-21); verifier P2Verifier; RECORD_ONLY O-1 (uncommitted doc-only edits at verify time, attributed to cc9037d); implementer P2Implementer |
| p3 | docs/plans/2026-08-21/ui-tweaks-03-manual-reply-subject-prefill.md | commit:a4446c562a95ef472d1a0dc2aecd0d857a5b41ee | p2 | 2 | LIGHT_PASS | cc9037dcc9c194e2e80f22274ee0d3e90c22da04 | 34acb52e22f24eeed88fd50c49c880653281cfe6 | 0 | — | 34acb52e22f24eeed88fd50c49c880653281cfe6 | da32b606b5df4a3d90a5ba9524e991e4f4f7c1f7 | epoch 2 (A4 approved 2026-08-21); verifier P3Verifier; RECORD_ONLY O-1 (S-1 line drift, verbatim content OK), O-2 (mvn node record via exec plugin); implementer P3Implementer |
| p4 | docs/plans/2026-08-21/qa-gate-visibility.md | commit:93ae957436931b06758c31b316f4c997400e6908 | p3 | 2 | LIGHT_PASS | 34acb52e22f24eeed88fd50c49c880653281cfe6 | 42dae13143051dac3f8333ee2445b1d7f9e6b047 | 1 | c13b12d8c25652b5047889c4075aba6c9c4a5bbf | c13b12d8c25652b5047889c4075aba6c9c4a5bbf | c450813d7ecf0e976f3bc88175c3c324d1bb7505 | epoch 2 (A5 approved 2026-08-21); round 1 FIXED c13b12d (T11); verifier P4Verifier2 (attempt 1 crashed pre-report); RECORD_ONLY O-1 (Flyway docker run unexecutable, human-accepted text-level V107 substitute), O-2 (brief A5 :102 mis-citation, deletion correct, contract in qaCoverageKeyEditor.test.js:138,143); implementer P4Implementer |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:c7b91635195f6007b297dc42777128db81559107 | P1 计划 I-4 + K-ui-removal-retires-obsolete-contract-tests（删 UI 必须成组删干净并退休契约测试） | P1 遗漏第二个断言已删 AUTO_PREVIEW 标识的契约测试 unmatchedQaReplySource.test.js，全量 JS 门禁失败；文件预算 8/8 需扩为 9 个授权文件 | HUMAN:user selected "Approve amendment A1: add test as 9th authorized file, flip 4th case to retirement guard" (ask p1_amendment, 2026-08-21) |
| A2 | docs/plans/2026-08-21/ui-tweaks-02-overlay-and-dialog-contrast.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:737b4b1a974a5ef40e5f46f2aa8b8b3c09e8c4fa | P2 计划 I-8 + K-frontend-cache-key-triad（三键断言同步规则） | P1 新增测试 checkRepliesRelocation.test.js:11,57 硬编码 v9 键，P2 bump v10 后全量 JS 门禁失败；文件预算 5/5 需扩为 6 个授权文件 | HUMAN:user selected "Approve A2 + A3 (recommended)" (ask p2_amendment, 2026-08-21) |
| A3 | docs/plans/2026-08-21/ui-tweaks-00-execution-order.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:737b4b1a974a5ef40e5f46f2aa8b8b3c09e8c4fa | master 计划「为什么必须按序」缓存键同步约定 + K-frontend-cache-key-triad | 本 run 每个后续子计划都需同步此前子计划新增的三键断言测试，避免逐计划重复暂停 | HUMAN:user selected "Approve A2 + A3 (recommended)" (ask p2_amendment, 2026-08-21) |
| A4 | docs/plans/2026-08-21/ui-tweaks-03-manual-reply-subject-prefill.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:a4446c562a95ef472d1a0dc2aecd0d857a5b41ee | P3 计划 I-1/S-1（新增顶层镜像函数）+ K-dom-stub-tests-hide-dangling-refs（vm 沙箱固定函数清单） | P3 的 S-1 让 showUnmatchedDetail 调用新增顶层函数 buildManualReplySubject，既有沙箱测试 expertProfileAbsence.test.js:384 因固定函数清单不含它而 ReferenceError；文件预算 6/6 需扩为 7 | HUMAN:user selected "Approve amendment A4 (recommended)" (ask p3_amendment, 2026-08-21) |
| A5 | docs/plans/2026-08-21/qa-gate-visibility.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:93ae957436931b06758c31b316f4c997400e6908 | P4 计划 I-3/T6（loadQa 必须请求 /api/qa/coverage-keys）+ K-ui-removal-retires-obsolete-contract-tests | 既有 qaFactCardEditor.test.js:99-104 断言 loadQa 不请求 coverage-keys，与 I-3/T6 冲突；同文件 :102 已有正向用例，属陈旧契约测试；文件清单需追加为第 11 个授权文件 | HUMAN:user selected "Approve A5: delete stale absence case (recommended)" (ask p4_amendment, 2026-08-21) |

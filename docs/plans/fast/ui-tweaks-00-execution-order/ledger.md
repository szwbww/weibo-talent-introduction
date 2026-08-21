# Fast-P Ledger — master: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-08-21/ui-tweaks-00-execution-order.md (commit 5b6c6ffe777b00c447c81cc38700f80f9ec07fdb)
- Amendments: A1, A2, A3, A4
- Master base: bb34ca2001d0abeac3bd7a8fc13995769e14143e
- Branch: fast/ui-tweaks-00-execution-order
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-ui-tweaks-00-execution-order
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-21T00:00:00Z
- Current child: p4
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: Two items. (1) p4 implementation 12b3b7d is committed and its named tests pass, but the full JS suite fails at unlisted src/test/js/qaFactCardEditor.test.js:99-104 ("loadQa does not request coverage-keys endpoint" asserts the endpoint is NOT requested — contradicts p4 I-3/T6 which mandates loadQa request /api/qa/coverage-keys; the file ALREADY contains the positive twin case at :102, pre-existing state from commit 6d2f77c). Fix requires amendment A5 adding it as the 14th authorized file. (2) The plan's required Flyway migration integration run (mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true) is unexecutable: docker info exit 1, no docker socket; repo convention (ProgrammeIdentityFactsMigrationTest docstring) substitutes text-level migration assertions, and p4's plan file #9 already mandates V107 text assertions in QaRuleManagementServiceTest (implemented; 60 tests pass). Classification of that single required command needs human decision.
- Resume from: b9c8e1d (p4 base; implementation 12b3b7d committed and retained)

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
| p3 | docs/plans/2026-08-21/ui-tweaks-03-manual-reply-subject-prefill.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p2 | 2 | LIGHT_PASS | 0ad6b3b188e4cef69229fc3e5a06f1251d343db9 | b9c8e1d4f933dbb6fe12c169a7dfe79aa1830589 | 0 | — | b9c8e1d4f933dbb6fe12c169a7dfe79aa1830589 | 63a228a24134a3f6de8d510ce272d68941f06229 | epoch 2 (A4 approved 2026-08-21); verifier P3Verifier; RECORD_ONLY O-1 (S-1 line drift, verbatim content OK), O-2 (mvn node record via exec plugin); implementer P3Implementer |
| p4 | docs/plans/2026-08-21/qa-gate-visibility.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | p3 | 1 | PAUSED_FOR_HUMAN | b9c8e1d4f933dbb6fe12c169a7dfe79aa1830589 | 12b3b7d712edab5888cd79007befe21a81db5f97 | 0 | — | 12b3b7d712edab5888cd79007befe21a81db5f97 | — | QA 门禁可见化 + V107；13 files (10 plan + 3 per A3)；implementer P4Implementer; gates fail at unlisted qaFactCardEditor.test.js stale absence case — amendment A5 pending; Flyway docker command unexecutable (docker unavailable) — classification pending human |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:c7b91635195f6007b297dc42777128db81559107 | P1 计划 I-4 + K-ui-removal-retires-obsolete-contract-tests（删 UI 必须成组删干净并退休契约测试） | P1 遗漏第二个断言已删 AUTO_PREVIEW 标识的契约测试 unmatchedQaReplySource.test.js，全量 JS 门禁失败；文件预算 8/8 需扩为 9 个授权文件 | HUMAN:user selected "Approve amendment A1: add test as 9th authorized file, flip 4th case to retirement guard" (ask p1_amendment, 2026-08-21) |
| A2 | docs/plans/2026-08-21/ui-tweaks-02-overlay-and-dialog-contrast.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:5b6c6ffe777b00c447c81cc38700f80f9ec07fdb | P2 计划 I-8 + K-frontend-cache-key-triad（三键断言同步规则） | P1 新增测试 checkRepliesRelocation.test.js:11,57 硬编码 v9 键，P2 bump v10 后全量 JS 门禁失败；文件预算 5/5 需扩为 6 个授权文件 | HUMAN:user selected "Approve A2 + A3 (recommended)" (ask p2_amendment, 2026-08-21) |
| A3 | docs/plans/2026-08-21/ui-tweaks-00-execution-order.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:5b6c6ffe777b00c447c81cc38700f80f9ec07fdb | master 计划「为什么必须按序」缓存键同步约定 + K-frontend-cache-key-triad | 本 run 每个后续子计划都需同步此前子计划新增的三键断言测试，避免逐计划重复暂停 | HUMAN:user selected "Approve A2 + A3 (recommended)" (ask p2_amendment, 2026-08-21) |
| A4 | docs/plans/2026-08-21/ui-tweaks-03-manual-reply-subject-prefill.md | commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd | commit:d17578b470368901ead0bc40285ba40f9f363b66 | P3 计划 I-1/S-1（新增顶层镜像函数）+ K-dom-stub-tests-hide-dangling-refs（vm 沙箱固定函数清单） | P3 的 S-1 让 showUnmatchedDetail 调用新增顶层函数 buildManualReplySubject，既有沙箱测试 expertProfileAbsence.test.js:384 因固定函数清单不含它而 ReferenceError；文件预算 6/6 需扩为 7 | HUMAN:user selected "Approve amendment A4 (recommended)" (ask p3_amendment, 2026-08-21) |

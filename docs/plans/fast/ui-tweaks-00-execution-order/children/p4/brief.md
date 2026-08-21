# Child p4 Brief — QA 事实编辑框门禁可见化改造

- Exact approved plan (authoritative contract; read in full first):
  `docs/plans/2026-08-21/qa-gate-visibility.md`
  (identity commit:2cbf6d36518aa54dd1f0dd6e69291aa7cfb6e7fd)
- Master plan (ordering and global constraints):
  `docs/plans/2026-08-21/ui-tweaks-00-execution-order.md`
- Child base (product boundary): `b9c8e1d4f933dbb6fe12c169a7dfe79aa1830589` (p3 terminal Code head)
- Cache key for this child: `20260821-v12-qa-coverage-gate` (I-8; P3 set v11)

## Global constraints (from master plan)

1. Symbol/DOM anchors authoritative; line numbers cross-check only (P1-P3 shifted line numbers).
2. Out of scope (master plan §已明确不做 1-4): workbench AUTO_PREVIEW mode; backend
   AutoReplyPreviewService/endpoint; mailbox resumeProgressPollingIfNeeded; hardcoded `未填写` label.
3. P4 modifies backend QA service/controller + new migration V107 AND frontend static resources —
   the only child touching Kotlin; zero overlap with P1-P3 file sets except the triad
   (index.html/app.js/batchSendTaskConsoleVisualFix.test.js), where P4 takes the v12 values.
4. Interaction prototype `docs/mockups/qa-fact-editor-gate-preview.html` exists in the MAIN worktree
   only (untracked, not seeded): read it at
   `/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/mockups/qa-fact-editor-gate-preview.html`
   if needed; never create/write it in this worktree.
5. Commit locally as `feat(fast-p): implement p4`; exclude fast-p evidence files.

## Authorized files (14 — 10 plan + 3 per A3 + 1 per A5)

| # | File | Action |
|---|---|---|
| 1 | src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt | modify (I-1, 3 read queries, controlled group id/name) |
| 2 | src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt | modify (3 fields + 2 read-only endpoints) |
| 3 | src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt | modify (listCoverageAuthorities) |
| 4 | src/main/resources/db/migration/V107__strip_controlled_keys_from_program_overview.sql | NEW (I-6; baseline guard + updated_at=updated_at) |
| 5 | src/main/resources/static/index.html | modify (S-1/S-4/S-6 skeletons + triad bump) |
| 6 | src/main/resources/static/styles.css | modify (append S-1..S-6 CSS only; no existing block edits) |
| 7 | src/main/resources/static/app.js | modify (T6/T7) |
| 8 | src/test/js/qaCoverageKeyEditor.test.js | modify (flip 4 absence assertions; add collection contract + four-state cases) |
| 9 | src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt | modify (real rule-24 fixture; unpaired pass-through; V107 text assertions; :810 semantics) |
| 10 | src/test/js/batchSendTaskConsoleVisualFix.test.js | modify (triad assertions) |
| 11 | src/test/js/checkRepliesRelocation.test.js | modify (A3: key v11→v12 in CACHE_KEY + I-3 assertion) |
| 12 | src/test/js/overlayAndDialogContrast.test.js | modify (A3: key v11→v12 in triad assertion) |
| 13 | src/test/js/manualReplySubjectPrefill.test.js | modify (A3: key v11→v12 in triad assertion) |
| 14 | src/test/js/qaFactCardEditor.test.js | modify (T11, amendment A5) |

Amendment A5 (approved 2026-08-21): T11 — DELETE the stale case `loadQa does not request coverage-keys
endpoint` (qaFactCardEditor.test.js :99-104, asserts `!apiCalls.includes("/api/qa/coverage-keys")`,
contradicts I-3/T6); the file's positive case `loadQa fetches coverage-keys metadata plus gate
endpoints` (:102) already covers the contract. All other cases verbatim.
Flyway classification (HUMAN, 2026-08-21): the plan-required `mvn test -Dtest=FlywayMigrationIntegrationTest
-DmigrationIt=true` is unexecutable (no Docker); accepted substitute = text-level V107 assertions in
QaRuleManagementServiceTest (plan file #9), recorded as RECORD_ONLY at verification.

Budget note: the plan states "文件数 10（上限 10）"; rows 11-13 are authorized by amendment A3
(approved 2026-08-21, master plan §三键断言测试的跨计划同步规则) — the ≤10 figure was the
planning-time decomposition criterion for the original four plans, and the approved A3 rule
explicitly applies to p4 ("each subsequent child (p3, p4)"). The three files carry only the
cache-key literal change to `20260821-v12-qa-coverage-gate`; all other assertions verbatim.

## Key invariants (plan sections are authoritative)

- I-1: gate triggers iff coverage keys are exactly a controlled group (not merely contain one).
- I-2: frontend evaluation is advisory; backend is authoritative.
- I-3: saveQaRule payload explicitly sends `coverageKeys`, empty selection sends `[]`.
- I-4: unpicking a key only removes it from this rule; controlled-group definition is not UI-changeable.
- I-5: last-authority-source determination is advisory only (warn, never block).
- I-6: V107 changes only rule 24's coverage_keys; body and timestamps untouched
  (updated_at=updated_at guard).
- I-7: coverage-key checkboxes all resident in DOM; key values with dots must be quoted in
  querySelector (`[data-coverage-key="fees.policy"]`) — never unquoted, never `'#'+key`.
- I-8: triad `?v=` same value `20260821-v12-qa-coverage-gate`; batchSendTaskConsoleVisualFix.test.js verbatim.
- S-1..S-6: all new CSS appended verbatim per plan; existing rule blocks untouched;
  no new inline `style=` in index.html (grep count non-increasing); no `var(--panel-bg)` translucent
  backgrounds where opaque required.

## Required commands (run all; record exact outputs)

```bash
node --test src/test/js/qaCoverageKeyEditor.test.js
node --test src/test/js/batchSendTaskConsoleVisualFix.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaRuleManagementServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
git diff --check
```

Environment note: `docker info` reports UNAVAILABLE on this machine; the last command
(`-DmigrationIt=true`, requires Testcontainers Docker) cannot run as written. Repo convention
(ProgrammeIdentityFactsMigrationTest docstring) uses text-level migration assertions instead, and
this plan's file #9 already requires V107 text assertions in QaRuleManagementServiceTest. Run the
other 10 commands fully; record the Docker command's unavailability precisely in the execution
report. The controller handles the classification of that single unexecutable command.

Pass criteria: node --test exit 0 + `# fail 0`; node --check exit 0 no output; mvn test exit 0 with
`Tests run: N, Failures: 0, Errors: 0` + `node --test` record; mvn clean package exit 0 with WAR;
git diff --check exit 0.

## Downstream interfaces

- Triad value exactly `20260821-v12-qa-coverage-gate` — final value for this run.
- New endpoints/response fields are P4-local; no later child consumes them.

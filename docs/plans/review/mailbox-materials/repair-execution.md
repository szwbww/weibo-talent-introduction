# Repair Execution — V-2 (mailbox-chat status/level selector catalogs)

- Plan: `docs/plans/fix/00-mailbox-materials-master/repair.md`
- Plan SHA-256: `3f9bffdd2b833580a24547d3f20ede8775bee577608483774d52c03794e8b6d6`
- Finding: V-2 (P1) — `mailbox-chat.js:765-766` reads `global.operatorStatusOptions` / `global.indexLevelOptions` (IIFE `global` = `window` in browser), while `app.js` held the catalogs as top-level `const` (not `window` properties), so selectors rendered empty and `saveSettings` issued no POST.
- Approval source: user invoked `$execute-p docs/plans/fix/00-mailbox-materials-master/repair.md` (exact Human Approval clause of the repair plan).
- Executor: `RepairExec`
- Execution epoch: NEW (no prior execution evidence for this plan identity)
- Pre-execution code SHA: `009d9bab431c75184b00659905f80dcde91e3166`
- Post-execution code SHA: `9625769f12b48293470ff91f75e6e0bc09f0a162` (product commit `fix(mailbox-chat): expose host option catalogs`)
- Evidence HEAD: `9625769f12b48293470ff91f75e6e0bc09f0a162` (docs evidence commit for this handoff follows on top)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials`
- Target branch: `fast/mailbox-materials`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`

## Changed Files (product commit, exactly the two Authorized Files)

- `src/main/resources/static/app.js` — after the existing top-level `const operatorStatusOptions` / `const indexLevelOptions` declarations, publish the SAME arrays as `window.operatorStatusOptions = operatorStatusOptions;` and `window.indexLevelOptions = indexLevelOptions;` (+1 comment). No recreation/transformation/move of the arrays; all other app.js bytes unchanged.
- `src/test/js/mailboxChatBehavior.test.js` — fixture now publishes the same catalog values (`OPERATOR_STATUS_CATALOG` 6 rows / `INDEX_LEVEL_CATALOG` 3 rows, value-for-value mirroring app.js) on the chat sandbox global by default (`catalogs:false` reproduces the V-2 empty state); new `describe("mailbox chat status/level catalog selectors (V-2)")` with three tests: (1) both selectors render every catalog option (value=enumerated, label=Chinese) and are non-empty; (2) changing status/level issues exactly the established `POST /api/expert-contacts/1/operator-status` with payload keys `{ operatorStatus, operatorName }` and `POST /api/expert-contacts/1/index-level` with `{ targetLevel, operatorName }` (no new endpoints, no invented keys) plus success status toast; (3) catalog-absent state renders empty selectors (V-2 symptom observable in harness) and app.js source text contains both `window.* = <const>;` publish statements (DOM-stub guard: no test executes whole app.js).

## Verification Commands (run freshly in this invocation, final state)

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| 2 | `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| 3 | `node --test src/test/js/mailboxChatBehavior.test.js` | PASS | exit 0; 24 tests pass / 0 fail (9 suites), incl. 3 new V-2 tests |
| 4 | `node --test src/test/js/*.test.js` | PASS | exit 0; 733 pass / 0 fail, 137 suites (was 730/136 before this repair) |
| 5 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; `BUILD SUCCESS` (02:42 min); exec-plugin Node 733/733 pass inside Maven |

## Clean-State Evidence

- Pre-execution: HEAD `5763759e2413bd2f32032abffa016f27760e6c0` (docs-only repair-plan commit), working tree clean, branch `fast/mailbox-materials`.
- Product commit `9625769f12b48293470ff91f75e6e0bc09f0a162` staged only `src/main/resources/static/app.js` + `src/test/js/mailboxChatBehavior.test.js` (98 insertions, 0 deletions); `git status --porcelain` clean after commit; commit is HEAD of the target worktree and an ancestor of branch `fast/mailbox-materials`.
- No amend / history rewrite / push / merge performed.

## Deviations

- None. All repair tasks (R-1) implemented exactly within the two Authorized Files; no backend/schema/styles/cache-key/Flyway/mailbox-chat.js changes; no fallback literals, extra endpoints, or altered status semantics.

## Post-Execution State

- `READY_FOR_VERIFICATION` — implementation complete, all required commands ran freshly and green, product commit on branch. Handoff file appended per Review-Fast-P Execution Handoff item 3; docs-only evidence commit follows per item 4.

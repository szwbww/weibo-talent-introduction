# Fast-P Ledger — master: docs/plans/2026-08-14/expert-mail-preview-main.md

- Status: RUNNING
- Master plan: docs/plans/2026-08-14/expert-mail-preview-main.md (commit 7a5dbdb)
- Amendments: N/A
- Master base: f3917cec4833199fcc9af5603e8630bb50590f9e
- Branch: fast/expert-mail-preview
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-14T04:46:00Z
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| p1-snippet-name | docs/plans/2026-08-14/expert-mail-preview-p1-snippet-name.md | commit:7a5dbdb | none | 1 | LIGHT_PASS_WITH_NOTES | 7a5dbdb | 15633f7a | 0 | — | 15633f7a | 6ba9a66 | First: reply snippet name + unified labels. RECORD_ONLY: R-1 plan-audit gap (3rd site resolveBlocks) completed in-file; R-2 tier-3 defensive JS label-mapping vs Kotlin raw type; R-3 2 baseline JS failures in batchManualExecutionLog.test.js. |
| p2-detail-tab | docs/plans/2026-08-14/expert-mail-preview-p2-detail-tab.md | commit:7a5dbdb | p1 | 1 | LIGHT_PASS_WITH_NOTES | 15633f7a | c2acd4f | 0 | — | c2acd4f |  | Depends on p1 refDisplayName fix. RECORD_ONLY: O-1 I-5 grep literal 2 vs actual 3 (lazy-load selector; equality 3==3 holds); O-2 test group 1 count method; O-3 master-vs-child wording on new-panel block description (panel renders per child spec; 3 name surfaces consistent); O-4 pre-existing duplicate scrollBackToContactsList. |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|

## Baseline
- node --test src/test/js/batchManualExecutionLog.test.js at base tree (f3917ce, src identical to 7a5dbdb): 17 tests, 15 pass, 2 fail — `ReferenceError: buildManualExecutionSnapshot is not defined` in `confirmManualExecution` tests (extraction gap: test loads only confirmManualExecution, not its dependency). Reproduced by controller 2026-08-14 and by implementer /tmp/basejs.
- git diff f3917ce..7a5dbdb -- src/main/resources/static/app.js: empty (seed commit plans-only); batchManualExecutionLog.test.js untouched by p1.
- Full mvn test / mvn clean package at base were NOT run (25-min build); implementer's fresh run at 15633f7a exits 1 solely on the same 2 baseline JS failures; WAR builds with -DskipNodeTests=true.


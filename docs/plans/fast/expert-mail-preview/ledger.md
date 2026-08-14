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
- Current child: p1-snippet-name
- Waiting role: VERIFIER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| p1-snippet-name | docs/plans/2026-08-14/expert-mail-preview-p1-snippet-name.md | commit:7a5dbdb | none | 1 | LIGHT_VERIFYING | 7a5dbdb | 15633f7a | 0 | — | 15633f7a |  | First: reply snippet name + unified labels. |
| p2-detail-tab | docs/plans/2026-08-14/expert-mail-preview-p2-detail-tab.md | commit:7a5dbdb | p1 | 1 | PENDING |  |  | 0 | — |  |  | Depends on p1 refDisplayName fix. |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|

## Baseline
- node --test src/test/js/batchManualExecutionLog.test.js at base tree (f3917ce, src identical to 7a5dbdb): 17 tests, 15 pass, 2 fail — `ReferenceError: buildManualExecutionSnapshot is not defined` in `confirmManualExecution` tests (extraction gap: test loads only confirmManualExecution, not its dependency). Reproduced by controller 2026-08-14 and by implementer /tmp/basejs.
- git diff f3917ce..7a5dbdb -- src/main/resources/static/app.js: empty (seed commit plans-only); batchManualExecutionLog.test.js untouched by p1.
- Full mvn test / mvn clean package at base were NOT run (25-min build); implementer's fresh run at 15633f7a exits 1 solely on the same 2 baseline JS failures; WAR builds with -DskipNodeTests=true.


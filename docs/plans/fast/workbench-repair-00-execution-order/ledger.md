# Fast-P Ledger — master: docs/plans/2026-08-19/workbench-repair-00-execution-order.md

- Status: RUNNING
- Master plan: docs/plans/2026-08-19/workbench-repair-00-execution-order.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Amendments: N/A
- Master base: 3bd132cb429a6928aa0eaa7c9f72d733d6905a15
- Branch: fast/workbench-repair-00-execution-order
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-19T14:28:51Z
- Current child: 01-tab-focus-selector
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- JDK: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (zulu-11).
- Full suite command: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`.
- Baseline run: `mvn test` at 3bd132cb429a6928aa0eaa7c9f72d733d6905a15 -> exit 0, BUILD SUCCESS (node --test 658 pass / 0 fail), 2026-08-19, ~2:21 min.
- Plans seeded on branch at b830ec208e9fe51bd693436f92158f1fde76622b (docs/plans/ only, pre-child).

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---:|---|---|---:|---|---|---|---:|---|---|---|---|
| 01-tab-focus-selector | docs/plans/2026-08-19/workbench-repair-01-tab-focus-selector.md | commit:b830ec208e9fe51bd693436f92158f1fde76622b | none | 1 | LIGHT_PASS_WITH_NOTES | b830ec208e9fe51bd693436f92158f1fde76622b | 3d719ad7f8143d185d890dbac0fc6ed5da5e3ce1 | 0 | — | 3d719ad7f8143d185d890dbac0fc6ed5da5e3ce1 | — | verifier Ver01TabFocus; RECORD_ONLY O-1/O-2: plan-internal acceptance counts (instanceId 5 vs 4, tabId( 7 vs 6) contradicted by plan's own verbatim T1 comment text; implementation byte-faithful |
| 02-claim-paragraphs | docs/plans/2026-08-19/workbench-repair-02-claim-paragraphs.md | commit:b830ec208e9fe51bd693436f92158f1fde76622b | none | 1 | PENDING | — | — | 0 | — | — | — | — |
| 03a-per-request-evidence-version | docs/plans/2026-08-19/workbench-repair-03a-per-request-evidence-version.md | commit:b830ec208e9fe51bd693436f92158f1fde76622b | none | 1 | PENDING | — | — | 0 | — | — | — | 02 recommended before 03a (both touch assemble); no hard dependency |
| 03b-source-version-split | docs/plans/2026-08-19/workbench-repair-03b-source-version-split.md | commit:b830ec208e9fe51bd693436f92158f1fde76622b | 03a-per-request-evidence-version | 1 | PENDING | — | — | 0 | — | — | — | hard dependency on 03a per master plan |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|

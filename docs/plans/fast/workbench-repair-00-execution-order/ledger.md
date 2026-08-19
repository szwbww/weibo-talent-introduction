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
- Current child: N/A
- Waiting role: N/A
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
| 02-claim-paragraphs | docs/plans/2026-08-19/workbench-repair-02-claim-paragraphs.md | commit:b830ec208e9fe51bd693436f92158f1fde76622b | none | 1 | LIGHT_PASS_WITH_NOTES | 3d719ad7f8143d185d890dbac0fc6ed5da5e3ce1 | 055d313d250053d7cbd917884745571b9580b9b4 | 0 | — | 055d313d250053d7cbd917884745571b9580b9b4 | — | verifier Ver02ClaimParagraphs; RECORD_ONLY O-1: plan line anchors shifted by plan's own mandated I-3 comment (content byte-faithful); O-2: uncommitted ledger state is controller docs |
| 03a-per-request-evidence-version | docs/plans/2026-08-19/workbench-repair-03a-per-request-evidence-version.md | commit:b830ec208e9fe51bd693436f92158f1fde76622b | none | 1 | LIGHT_PASS_WITH_NOTES | 055d313d250053d7cbd917884745571b9580b9b4 | e2ad440157017fb6ced066fe63ad2d5e104a8296 | 0 | — | e2ad440157017fb6ced066fe63ad2d5e104a8296 | — | verifier Ver03aPerRequestEvidenceVersion; RECORD_ONLY O-1 (T6 literal assemble-payload deletion unexecutable vs C-4 frozen DTO; field kept on wire, server ignores; I-3 intent met), O-2 (v1 locks restore whole-STALE per I-6/A-7 intent) |
| 03b-source-version-split | docs/plans/2026-08-19/workbench-repair-03b-source-version-split.md | commit:b830ec208e9fe51bd693436f92158f1fde76622b | 03a-per-request-evidence-version | 1 | LIGHT_PASS_WITH_NOTES | e2ad440157017fb6ced066fe63ad2d5e104a8296 | 8ee03a9b207227890bca01da272207ff9a22f943 | 0 | — | 8ee03a9b207227890bca01da272207ff9a22f943 | — | verifier Ver03bSourceVersionSplit; RECORD_ONLY O-1 (I-1 acceptance grep hits plan-mandated contextVersion sha256Hex(mailHistory) line; substance holds), O-2 (03a resetVersions() grep 4 textual lines from 03b comment; functional sites unchanged) |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|

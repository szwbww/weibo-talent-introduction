# Fast-P Ledger — master: docs/plans/2026-08-19/00-grounded-coverage-master.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-08-19/00-grounded-coverage-master.md (commit af1723f37021328f8ffa61261504727e514fbb4b)
- Amendments: A1
- Master base: af1723f37021328f8ffa61261504727e514fbb4b
- Branch: fast/grounded-coverage
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-19T12:10:00Z
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- JDK: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (zulu-11); Maven 3.9.11.
- Full suite command: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`.
- Baseline run: `mvn test` at af1723f -> exit 0 (2563 tests, 2026-08-19, ~136s).
- Note: master plan file contains one stray NUL byte (copy-paste of `\u0000` from TrustReplyWorkbenchService.kt:1551 quoting). Doc artifact only; child plan 03 has the same single-NUL pattern.

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---:|---|---|---:|---|---|---|---:|---|---|---|---|
| 01-fact-and-catalog | docs/plans/2026-08-19/01-fact-and-catalog.md | commit:af1723f37021328f8ffa61261504727e514fbb4b | none | 1 | LIGHT_PASS_WITH_NOTES | af1723f37021328f8ffa61261504727e514fbb4b | f5c09382744c0da8a537610af6145974ee1fcaf4 | 0 | — | f5c09382744c0da8a537610af6145974ee1fcaf4 | ac278783f2969b8d357df95bd35c356a31cc0a02 | V105 (4 statements) + 3 intents + 2 coverage keys; RECORD_ONLY O-1..O-5 in verify-log (O-2: production Funding support keywords lack letter substring — plan-internal tension, surfaced for review) |
| 02-unrecognized-request-detection | docs/plans/2026-08-19/02-unrecognized-request-detection.md | commit:e578e206cdf71a03b65891ae596d5e888ab20dba | 01-fact-and-catalog | 2 | LIGHT_PASS_WITH_NOTES | f5c09382744c0da8a537610af6145974ee1fcaf4 | 533a02fce781ff09693d630c0d029f0d93c7d58a | 0 | — | 533a02fce781ff09693d630c0d029f0d93c7d58a | 90c95af3d763c91b5b0bb31205c6667acbc12063 | epoch 1 verifier PAUSE (Gate 1 scope) -> HUMAN-approved A1; epoch 2 re-verify LIGHT_PASS_WITH_NOTES; O-1/O-3 carried, O-2 ratified by A1 |
| 03-fact-order-drag | docs/plans/2026-08-19/03-fact-order-drag.md | commit:af1723f37021328f8ffa61261504727e514fbb4b | none | 1 | LIGHT_PASS | 533a02fce781ff09693d630c0d029f0d93c7d58a | 8c2ec53f4e97d06acb89b81bfb5a388a9d49a566 | 0 | — | 8c2ec53f4e97d06acb89b81bfb5a388a9d49a566 | 2f538463a3049d0d961390d89effe2e157e13cb0 | frontend-only; drag spike PASS (real Chromium); no RECORD_ONLY |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-19/02-unrecognized-request-detection.md | commit:af1723f37021328f8ffa61261504727e514fbb4b | commit:e578e206cdf71a03b65891ae596d5e888ab20dba | C-1 field mandates vs stale 变更文件清单 attribution (plan audit self-cites AiReplyDraftService.kt:362) | widen authorized files with AiReplyDraftService.kt data classes; ratify shadow fields, in-service enumeration wiring, select()-based auto-path log | HUMAN:user approved A1 via ask 2026-08-19 |

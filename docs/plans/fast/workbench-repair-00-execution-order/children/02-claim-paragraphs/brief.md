# Fast-P Child Brief — 02-claim-paragraphs

- Master: docs/plans/2026-08-19/workbench-repair-00-execution-order.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Plan: docs/plans/2026-08-19/workbench-repair-02-claim-paragraphs.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order
- Branch: fast/workbench-repair-00-execution-order
- Child base (product): 3d719ad7f8143d185d890dbac0fc6ed5da5e3ce1 (= child 01 terminal Code head)
- Prior child: 01-tab-focus-selector (LIGHT_PASS_WITH_NOTES, evidence cf583f1). No product output from 01 affects 02 beyond the unchanged workbench DOM.
- Downstream: 03a and 03b both touch `materializeVersion`/`assemble`; the `CLAIM_PARAGRAPH_SEPARATOR` constant and its four reference sites (3 production + 1 test mirror) are the downstream interface they rely on — must be exactly the plan's T1/T2/T3/T4 shape.

## Approved contract

The plan file is the complete approved contract. Read it fully from disk before starting and treat its bytes as authoritative:

- Authorized files (exactly 3):
  1. `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` — T1: add `CLAIM_PARAGRAPH_SEPARATOR = "\n\n"` constant in companion; T2: change :1552 to `joinToString(CLAIM_PARAGRAPH_SEPARATOR)`.
  2. `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` — T3: change :1286 and :1331 to use `AiReplyDraftService.CLAIM_PARAGRAPH_SEPARATOR`; :1153 keeps single-space join, add the I-3 comment line only.
  3. `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` — T4: change :1204 mirror to reference the constant; T5: add the 4 new test cases.
- Invariants: I-1 (single constant authority, no literals), I-2 (composer never formats; do NOT modify AiReplyPointByPointComposer.kt), I-3 (:1153 keeps `" "`), I-4 (OMIT/ACKNOWLEDGE_PENDING/ANSWER_FROM_OPERATOR_INPUT branches byte-unchanged), I-5 (no html=false changes; PendingMailOperationService untouched).
- Prohibited files (must NOT change): `AiReplyPointByPointComposer.kt`, `AiReplyPointByPointComposerTest.kt`, `MailContentService.kt`, `PendingMailOperationService.kt`, `app.js`, `trust-reply-workbench.js`, any `db/migration/*.sql`, `TrustReplyWorkbenchStateStore.kt`.

## Execution rules (fast-p)

- Use `execute-p` against this exact plan. Run the plan identity gate (sha256) and worktree gate; the worktree above is the target.
- Modify only the three authorized files. Preserve every invariant listed in the plan.
- Run every required command from the plan's 验证命令 section freshly (JDK 11 zulu-11):
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='TrustReplyWorkbenchServiceTest+AiReplyPointByPointComposerTest+AiReplyDraftServiceTest+PendingMailOperationServiceTrustWorkbenchTest'`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` (full)
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
  - `git diff --check`
- Commit the implementation locally as a single commit:
  `feat(fast-p): implement 02-claim-paragraphs`
  Only the three authorized files in that commit. Do NOT include any file under `docs/plans/fast/`.
- Do not touch `docs/plans/`, the ledger, other child plans, or any file outside the authorized list.
- Do not review later children, repair unrelated behavior, push, merge, amend, or rewrite history.

## Reporting

Write the full `execute-p` result (Execution Result, Task Status, Commands, Changed Files, Deviations, Freshness, Remaining Blocker) by APPENDING to `docs/plans/fast/workbench-repair-00-execution-order/children/02-claim-paragraphs/execution.md` (file starts empty).

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the implementation commit SHA, a command summary, and the report path.

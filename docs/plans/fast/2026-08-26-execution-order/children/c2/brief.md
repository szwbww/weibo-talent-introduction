# Child Brief — c2 · 未识别诉求进入判定 + 孤儿 coverage key 修复

- Plan: `docs/plans/2026-08-26/02-unrecognized-asks-and-orphan-keys.md` (Plan identity: `commit:ee0749d3beedea7e26f4bf4e097b3d33a1684b7d`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order`
- Branch: `fast/2026-08-26-execution-order`
- Child base (product boundary): MUST be c1's terminal Code head (see ledger); not the master base.
- Master plan: `docs/plans/2026-08-26/00-execution-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the 5 authorized files listed in the plan's `## 变更文件清单`. Nothing else. Do not touch `docs/plans/fast/**`. Do not touch any other product/test file.
3. Preserve every invariant I-1..I-5 and every `What must NOT change` item exactly as written.
4. If child c1 (LLM fact retrieval) has landed, plan 02's T1.3 requires composing the 02 status cap with 01's I-6 UNSUPPORTED→PARTIAL lift in ONE status expression, with both plan numbers in comments. Verify the actual state of `buildRequestFact` in the worktree first and compose accordingly.
5. `AiReplyIntentCatalog.kt` change is EXACTLY one added line (`alternativeCoverageKeys = listOf("application.required_materials")` on `application.next_stages`) — no other change to that file.
6. No Flyway migration. `QaCoverageKeyCatalog.kt` entries appended at the very end of `listOf(...)` with the specified comment.

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# new tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaCoverageKeyIntentParityTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionUnrecognizedStatusTest
# affected existing tests (must stay green)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSelectionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyIntentCatalogTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaRuleManagementServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TrustReplyWorkbenchItemFlowTest
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# hygiene
git diff --check
```

The plan notes: if `AiReplyIntentCatalogTest` / `QaRuleManagementServiceTest` do not exist in this repo, substitute actual test class names found via `src/test/kotlin/**/*Test.kt` and record the substitution — do NOT skip the check.

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; `git diff --check` exit 0.

## Downstream interfaces

- `buildRequestFact` status expression must remain ONE composed expression (01's lift + 02's cap) so child c3's orchestration sees final statuses.
- `QaCoverageKeyCatalog.normalizeAndValidate` ordering unchanged for existing keys.
- No change to any `RequestIntentDefinition` key/title/aliases; `requestKey` hashes must stay byte-identical.

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c2
```

Write the full execution report to `docs/plans/fast/2026-08-26-execution-order/children/c2/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

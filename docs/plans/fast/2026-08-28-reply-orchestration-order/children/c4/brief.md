# Child Brief — c4 · 13-letter-orchestrator（一次编排 LLM 调用 + 六道校验）

- Plan: `docs/plans/2026-08-28/13-letter-orchestrator.md` (Plan identity: `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order`
- Branch: `fast/2026-08-28-reply-orchestration-order`
- Child base (product boundary): c2 terminal Code head (branch HEAD at dispatch time is the true base)
- Master plan: `docs/plans/2026-08-28/10-reply-orchestration-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the 5 authorized files in the plan's `## 变更文件清单`: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestrator.kt` (new), `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloser.kt` (modify ONLY step 3 per T-1/T-4), `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyValidationDiagnostic.kt` (add exactly 5 new validation-code constants), `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestratorTest.kt` (new), `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloserTest.kt` (add T-5.4). Nothing else. Do not touch `docs/plans/fast/**`. Do NOT modify `AiReplyDraftService.kt` — if the seam forces it, stop and report `PLAN_CONFLICT` (per plan text).
3. This child REPLACES step 3 (topic grouping) of c2's `AiReplyLetterCloser` with one orchestration LLM call; steps 1, 2, 4, 5 stay. The deterministic result remains the fallback whenever the orchestrator returns null (I-8) — assemble must never fail because of orchestration.
4. All six validations (G1 source closure / G2 exactly-once / G3 verbatim controlled+frozen / G4 zero action in paragraph / G5 action reconciliation / G6 plan consistency) are server-side pure functions in the orchestrator's parser — never prompt-constraint substitutes (I-7). Failure reuses the existing `AiReplyValidationIssue` + retry mechanism.
5. Verbatim expectations MUST be sourced from `QaCoverageKeyCatalog.controlledGroups()` and `PlanFact.body` at runtime — never hardcoded literals on the validation side (IP-2). Test expectation strings also come from those constants; grep-assert that the test file contains no hardcoded canonical literal.
6. Preserve every invariant I-1..I-8 and every `What must NOT change` item exactly as written.

## Global constraints (master plan 10)

- **G-1 (frozen rules)**: `qa_rule` id ∈ {1, 3, 21, 24} — bodies immutable; the orchestrator's verbatim slot check must treat them as frozen slots (no rewording, no truncation).
- **G-4 (controlled groups exact-set)**: G1 `{confidentiality.materials}` / G2 `{fees.policy}` / G3 `{contract.party, contract.terms}` / G4 `{ip.arrangements}` — demand-side 2026-08-28 confirmed online bodies byte-match the canonicals in `QaCoverageKeyCatalog.kt:19-42`.
- **G-7 (requestKey hash purity)**: `requestKey` inputs unchanged; `paragraphPlan` grouping info must never enter the hash.

## Verbatim bodies (demand-side baselines 2026-08-28; fixtures MUST use these, never invented)

- G1 canonical (also `QaCoverageKeyCatalog.kt:24`): `Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction.`
- G2 canonical (`:28`): `We never charge any fees throughout the entire process.`
- G3 canonical (`:33`): `After selection, you will sign a labor contract directly with the matched enterprise, and you may review the full terms before making any commitment.`
- G4 canonical (`:40`): `Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement.`
- **id 1 `About the talent program`**: body contains `${researchFields|your field}` and `${recentWorkTitle|your recent research}` placeholders; last paragraph is action sentence `Would you be open to learning more about the program and the possible cooperation format?`
- **id 3 `Application criteria`**: second paragraph contains double hyphen `--`: `We can discuss fit first -- no documents needed at this stage.`
- **id 21 `Meeting arrangement`**: second paragraph contains action sentence `Could you please let us know when you would be available?` and en dash `15–20` (U+2013). Full body: `We would like to arrange a brief Zoom meeting to learn more about your professional background and research interests, and to introduce ourselves briefly.` / `The meeting will take approximately 15–20 minutes. Could you please let us know when you would be available? We will arrange the meeting according to your time zone.`
- Normalization is whitespace-only (collapse runs of whitespace to single space + trim). NEVER touch `--`, `–`, or `${...}` — mutation of any of them must fail the verbatim check (test cases: `${...}` rewritten → fail; `--` → `—` → fail; `15–20` en dash → hyphen → fail).

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# this plan's tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyLetterOrchestratorTest,AiReplyLetterCloserTest
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; `git diff --check` exit 0.

## Downstream interfaces (for later children)

- c5 (15-workbench-three-step) consumes the `paragraphPlan` / `facts` / `paragraphs` protocol: `paragraphPlan: List<ParagraphPlanEntry>` (`topic` + `factIds` + optional `gapCondition`), facts with `id` (`f<ruleId>` / `x<n>`), `topicOrder`, orchestration response `{"paragraphs":[{"topic","factIds","text"}],"actionText"}`.
- c6 (16-unsupported-index) channel A safety depends ENTIRELY on this child's validation G1 source closure (`ORCH_FACT_ID_UNKNOWN`): sample propositions have no legal factId and get rejected. Channel A stays OFF until this child lands.

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c4
```

Write the full execution report to `docs/plans/fast/2026-08-28-reply-orchestration-order/children/c4/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

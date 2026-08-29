# Child Brief — c2 · 12-letter-closer（assemble 处确定性收口）

- Plan: `docs/plans/2026-08-28/12-letter-closer.md` (Plan identity: `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order`
- Branch: `fast/2026-08-28-reply-orchestration-order`
- Child base (product boundary): c1 terminal Code head (ledger `c1 Code head`; initially `de228e17cc0134a7c11dea7cbf82054e8d249f99` until c1 lands — branch HEAD at dispatch time is the true base)
- Master plan: `docs/plans/2026-08-28/10-reply-orchestration-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the authorized files in the plan's `## 变更文件清单` (5 files after amendment A1, HUMAN-approved 2026-08-28T14:32:17Z): `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloser.kt` (new), `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` (modify ONLY the `:1466-1468` segment — the `orderedAnswers` construction at `verifyAssembly`), `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloserTest.kt` (new), `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` (add T-4.8 only), `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` (A1: update the 5 obsolete assertions that assert pre-plan-12 raw text — per-item paragraphs, no dedup — to the plan-12 closing contract: dedup by sourceRuleIds, topic grouping, single CTA). Nothing else. Do not touch `docs/plans/fast/**`. Do not touch `AiReplyDraftService.kt` / `AiReplyGroundedContentPlanner.kt` / `AiReplyGroundedDraftMaterializer.kt` (they are explicitly NOT in the list).
3. Zero new LLM calls. All logic in `AiReplyLetterCloser` is pure/deterministic. Reuse `AiReplyActionPolicy.detectActions` — do not write a second action regex.
4. Preserve every invariant I-1..I-7 and every `What must NOT change` item exactly as written.
5. Premise evidence (from `docs/knowledge/llm/K-oneclick-assembles-by-concatenation.md`, demand-side 2026-08-28): one-click preview = `autoRun()` → `runItemSequence(keys)` (serial, one `POST /workbench/generations/stream` per item) → `assemble()` → `POST /workbench/assemble` → `verifyAssembly()` → `orderedAnswers = versions.mapNotNull { answerText }` (`:1466-1468`) → `composeLockedItems(orderedAnswers, resolvedFrame)` (`:1472`). `AiReplyPointByPointComposer.composeLockedItems` (`:34-44`) concatenates `salutation/greeting/ack/每条 answerText/closing` with `"\n\n"` — zero dedup, zero action reconciliation, zero whole-letter view. `TrustReplyWorkbenchService.kt:1462-1464` comment: cross-item duplicate-claim check was deliberately removed. `/api/ai-training/simulate` has NO frontend call site and is not the one-click path.

## Global constraints (master plan 10)

- **G-1 (frozen rules)**: `qa_rule` id ∈ {1, 3, 21, 24} hand-adjusted by requester; no column of those rows may be modified by any code this run. For this child: their BODIES must not be reworded, split, or trimmed by the closer — special-cased per I-5.
- **G-3**: body rewrites require verbatim baseline guards; not applicable here (no writes), but the closer must not rewrite frozen/controlled bodies.
- **G-4 (controlled groups exact-set)**: G1 `{confidentiality.materials}`, G2 `{fees.policy}`, G3 `{contract.party, contract.terms}`, G4 `{ip.arrangements}`.
- **G-7 (requestKey hash purity)**: `requestKey = sha256(sourceVersion, index, requestText, intentKeys)` — this child must not change the hash inputs; `versionId` algorithm and `TrustReplyItemVersion` fields untouched.

## Frozen-body danger notes (demand-side verbatim baselines 2026-08-28; fixtures MUST come from these, never invented — K-named-fixture-must-use-real-row)

- **id 21 `Meeting arrangement`** body (full, verbatim; note the en dash U+2013 in `15–20`): paragraph 1: `We would like to arrange a brief Zoom meeting to learn more about your professional background and research interests, and to introduce ourselves briefly.` paragraph 2: `The meeting will take approximately 15–20 minutes. Could you please let us know when you would be available? We will arrange the meeting according to your time zone.` Its final sentence is an action sentence; the closer must not delete/edit it (I-5): when it is the letter's only action source it stays as the CTA and no extra CTA is added; when another action wins, drop the removal and record a warning.
- **id 1 `About the talent program`** body contains the variable placeholders `${researchFields|your field}` and `${recentWorkTitle|your recent research}`, and its last paragraph is the action sentence `Would you be open to learning more about the program and the possible cooperation format?` The closer must never split or rewrite text containing `${...}` (truncation breaks rendering).
- **id 3 `Application criteria`** body contains a double hyphen `--` (`We can discuss fit first -- no documents needed at this stage.`) — normalization must not touch it (same as `--`, `–`, `${...}`); this child's normalization only collapses whitespace.

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# this plan's tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiReplyLetterCloserTest,TrustReplyWorkbenchServiceTest
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; `git diff --check` exit 0.

## Downstream interfaces (for later children)

- c4 (13-letter-orchestrator) will REPLACE step 3 of `AiReplyLetterCloser` (topic-grouping into paragraphs) with one orchestration LLM call. Keep the five-step structure explicit (expand claims → `sourceRuleIds` dedup → topic grouping → CTA closing → escape hatch) so c4 can swap step 3 only. `TrustReplyWorkbenchService` callers keep receiving a plain `List<String>` of ordered answers — `composeLockedItems` contract unchanged.
- c6 (16-unsupported-index) archives per-item `itemVersions` (pre-closing answerText); keep `raw` (post-closing) and per-item versions distinct (IP-4).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c2
```

Write the full execution report to `docs/plans/fast/2026-08-28-reply-orchestration-order/children/c2/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

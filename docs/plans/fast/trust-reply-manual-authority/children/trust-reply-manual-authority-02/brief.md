# Fast-P Child Brief — trust-reply-manual-authority-02

- Plan (exact approved contract): `docs/plans/2026-08-24/02-manual-fact-authority-workbench.md` (approved identity `commit:8dc7c96`)
- Master plan: `docs/plans/2026-08-24/00-trust-reply-manual-authority-master.md`
- Depends on: trust-reply-manual-authority-01. Must be based on child 01's terminal code head (see dispatch); never parallel-edit shared files from the original baseline.
- Master I-4: child 02 is `IMPLEMENTED_NOT_RELEASABLE` until child 03 passes (atomic R2). Do not claim release.

## Scope and master constraints

- Master I-1/I-2: explicit workbench matrix is the final fact set; identity chain `RequestFactItem.factRuleIds/boundRuleIds → requestFactSelections → selection.sendQaRuleIds → assemble.canonicalFactIds` (03 continues to SendPayload and DB).
- Master I-3: intent mismatch/unrecognized/UNSUPPORTED/duplicate-fact are diagnostics only — never hard gates that delete facts, filter handling, or block assembly/send.
- Master I-6: auto, null selection, legacy flat selection keep strict matching via `QaFactKeywordMatcher`/intent assignment; only `selectForWorkbench(selectionsByRequest=...)` takes manual authority.
- Master I-7: ONLY the 10 authorized files below; new file/field/store/action/API/CSS → stop, PLAN_CONFLICT.
- Master I-8: run directed tests + Node test + `git diff --check`; record exits.
- Style contract S-1: reuse existing DOM/CSS (`.trust-reply-field`, `.muted`, `.trust-reply-fact-picker-option`, `.trust-reply-fact-state`); no new class, no inline style, no mobile/overlay/dialog changes; dropped-hint replaced by fixed mismatch hint text “人工选择已生效；系统未匹配到对应意图，已记录供后续优化。”; no `used` fact state produced.

## Authorized files (from plan 02)

| File | Change |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | matrix final facts, natural diagnostics, remove cross-request duplicate gate |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | RequestFactItem fields, mechanical prerequisites, verbatim/mixed generation |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedContentPlanner.kt` | supported + residual general claims |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 7 handlings, DTO, lock/assemble, canonical ids, remove cross-item duplicate hard gate |
| `src/main/resources/static/trust-reply-workbench.js` | full 7 options, duplicate facts selectable, mismatch hint |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | matrix authority, diagnostics, auto/legacy regression |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | 4 fact prerequisites, verbatim/mixed ordering |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | 7 handlings, generate/lock/restore/assemble |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | DTO, canonical matrix, cross-request duplicate |
| `src/test/js/trustReplyWorkbench.test.js` | picker, 7 options, hint, payload contract |

No DB migration, CSS, mail-send code changes. Keep ALL child-01 regression fixtures in the shared test files.

## Child-plan invariants (02 I-1..I-8)

- I-1: per-request `RequestFactItem.factRuleIds == boundRuleIds == requestFactSelections.factRuleIds`, order element-wise; `sendQaRuleIds` = first-occurrence ordered union across requests; never re-derive from intent/claim/keyword.
- I-2: natural status/intents/strict facts still computed; manual facts split into `intentMatchedFactRuleIds` + `intentMismatchFactRuleIds`; union in manual order == `factRuleIds`; diagnostics never enter authorization/allowedHandlings/version-reject/send-pruning.
- I-3: `allowedHandlings` = all 7 `TrustReplyItemHandling` values for every coverage; status only drives `recommendedHandling` and hints.
- I-4: fact-required handlings (`ANSWER_WITH_EVIDENCE`, `ANSWER_SUPPORTED_PART`, `ANSWER_FACTS_VERBATIM`, `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT`) fail at generate/lock with `TRUST_REPLY_FACT_REQUIRED` when empty; instruction handlings require non-empty ≤500-char instruction; OMIT/ACKNOWLEDGE_PENDING structural checks unchanged; options never hidden/disabled by status.
- I-5: grounded planner emits supported-intent claims first, then residual manual facts into same-request `general.answer` claim; UNSUPPORTED with manual facts still generates `general.answer` (no early-return loss).
- I-6: same fact bindable to multiple requests; no `TRUST_REPLY_FACT_ALREADY_ASSIGNED` / cross-item `TRUST_REPLY_DUPLICATE_CLAIM`; per-item duplicates/invalid/disabled/NEVER/empty-answerBody still hard-rejected.
- I-7: `TrustReplyAssembleResponse.canonicalFactIds` = `selection.sendQaRuleIds`; verbatim/operator/ack/omit must not drop manual facts from canonical audit.
- I-8: auto/null/legacy flat selection paths unchanged.

## Required commands

- `mvn -q -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchServiceTest test` (JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)
- `node --test src/test/js/trustReplyWorkbench.test.js`
- `git diff --check`

## Downstream interfaces

- `TrustReplyWorkbenchService.kt` is consumed by 03: verified assembly must expose internal selection; keep `assemble(request)` semantics stable, canonical ids from `selection.sendQaRuleIds`.
- `intentMatchedFactRuleIds`/`intentMismatchFactRuleIds`/natural status/`unrecognizedAsks` are consumed by 04 diagnostics.
- Coverage DTO: replace `droppedFactRuleIds` with `intentMatchedFactRuleIds`/`intentMismatchFactRuleIds`; matrix serialization stays `requestKey + factRuleIds` only.
- Do not modify mail send (`PendingMailOperationService`, `ManualReplySendAttemptService`), schema, or stores.

## Procedure

Same as brief 01: execute-p against the exact plan; implement 5 stages; run all required commands; commit locally ONLY the 10 authorized files as `feat(fast-p): implement trust-reply-manual-authority-02`; append result to the child execution.md; return `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + command summary + report path. Do not touch later children, push, merge, amend, squash, rewrite history; skip formatters/linters/full suite.

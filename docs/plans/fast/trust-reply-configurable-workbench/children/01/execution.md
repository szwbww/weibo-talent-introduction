# Execution Report — trust-reply-configurable-workbench-01

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-configurable-workbench/docs/plans/fast/trust-reply-configurable-workbench/children/01/brief.md`
- Plan SHA-256: `86a68e2b9576b0b47cc876cd7c8bdc1d19d33a280ff039f4331f4b7781606a22`
- Execution ID: `.../children/01/brief.md@86a68e2b9576b0b47cc876cd7c8bdc1d19d33a280ff039f4331f4b7781606a22`
- Execution epoch: NEW
- Approval basis: current invocation (fast-p master workflow child 01)
- Executor: ImplChild01
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-configurable-workbench`
- Target branch: `fast/trust-reply-configurable-workbench`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-configurable-workbench@fast/trust-reply-configurable-workbench@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/trust-reply-configurable-workbench`
- Pre-execution code SHA: `931e724042d9ceee9f75d4cacb45fd3ba29462a5`
- Post-execution code SHA: see Implementation commit below
- Evidence HEAD: N/A (evidence committed separately by controller; this report and the brief are outside the implementation commit)

## What changed

### Main code (4 files)

1. `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`
   - Added the workbench-only matrix entry `selectForWorkbench(inboundText, selectionsByRequest, requestedFactIds, researchProfileSufficient)` with three modes:
     - **Matrix mode** (`selectionsByRequest` per canonical request index): global rule-id uniqueness check (`TRUST_REPLY_FACT_ALREADY_ASSIGNED`), each request pools only its own explicit rules, result `factRuleIds` must equal the explicit list else `TRUST_REPLY_FACT_SELECTION_INVALID` (I-3).
     - **Legacy flat mode**: every id assigned to the first request that keyword-matches and accepts it into supported evidence; consumed ids removed from the pool; unconsumed ids → `TRUST_REPLY_FACT_SELECTION_INVALID` (I-4).
     - **Auto mode**: same unique-consumption semantics over matchable rules.
   - `sendQaRuleIds` = ordered union of per-request `factRuleIds` (never fed back into per-request pools, I-1).
   - Both fields present → `TRUST_REPLY_FACT_SELECTION_AMBIGUOUS`.
   - Existing `select` behavior untouched (non-workbench consumers unchanged).
   - Empty lists allowed and produce intent-derived statuses without fabricated fallback facts.

2. `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`
   - New domain type `TrustReplyRequestFactSelection(requestKey, factRuleIds)`.
   - `requestFactSelections` added to bootstrap/generation/save-state/adjust-item/assemble requests; canonical `requestFactSelections` added to bootstrap response, saved state, assemble response, and the durable payload — all with defaults so downstream construction points (PendingMailOperationService, UnmatchedInboundMailController, AiTrainingController, app.js) compile unchanged.
   - `TrustReplyRuleMetadata.answerBody` added for plan 03 display; never enters matching/identity/claim validation.
   - The two old selection resolvers were unified into one `resolveCanonicalSelection` that resolves canonical requestKeys, validates the matrix shape (`TRUST_REPLY_REQUEST_KEY_INVALID` for blank/duplicate/unknown keys, `TRUST_REPLY_FACT_SELECTION_INVALID` for incomplete matrix or non-positive ids, `TRUST_REPLY_FACT_SELECTION_AMBIGUOUS` for both inputs), then resolves matrix/legacy/auto and returns `ResolvedQaRules + canonical matrix + evidenceSetVersion`.
   - Mapping-sensitive evidence version (I-5): `SHA-256(baseEvidenceVersion + NUL + mappingCanonical)` where `mappingCanonical` encodes the canonical `requestKey -> ordered factRuleIds` sequence. The OMIT fast path computes the identical base version locally and adds the same mapping canonical.
   - `FULL_DRAFT` carrying `requestFactSelections` fails closed with `TRUST_REPLY_OPERATION_INVALID` (I-7); `ADJUST_ITEM` forwards the matrix.
   - `bootstrap`/`saveState`/`restoreSavedState`/`assemble` all carry the same canonical matrix; restore compares sourceVersion, mapping-sensitive evidence version, canonical mapping (v2) or normalized flat union (v1), and locked snapshots (I-6).

3. `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt`
   - `SCHEMA_VERSION` → `trust-reply-workbench-state-v2`; added `LEGACY_SCHEMA_VERSION` (`trust-reply-workbench-state-v1`).
   - `decodePayload` now returns v1 (legacy flat union, normalized by the business layer) and v2 (canonical matrix) payloads; unknown/corrupt → null (bootstrap surfaces `INVALID`).
   - Physical SQL, optimistic concurrency, delete, prune, and payload size behavior unchanged.

4. `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`
   - HTTP DTO `TrustReplyRequestFactSelectionHttpRequest(requestKey?, factRuleIds?)` and `requestFactSelections` on bootstrap/generation/assemble/save-state requests; mapped to the domain in `toDomain()`.

### Tests (5 files, all under the authorized 变更文件清单)

5. `QaFactSelectionServiceTest.kt` — matrix/legacy/auto unique assignment, cross-request duplicate rejection, wrong-request rejection, disabled/NEVER/blank rejection, empty lists, unconsumed legacy ids, duplicate flat ids, ambiguous input.
6. `TrustReplyWorkbenchServiceTest.kt` — matrix input resolution, mapping-sensitive version (same union, different binding → different version; repeatable), FULL_DRAFT fail-closed, ADJUST_ITEM matrix forwarding, ambiguous/unknown/blank/duplicate-key/non-positive/incomplete-matrix contract codes, v1 payload restore with flat normalization, v2 stored-matrix drift → STALE, and the full bootstrap→adjust→save→restore→assemble chain carrying one canonical matrix.
7. `TrustReplyWorkbenchItemFlowTest.kt` — matrix assemble round-trip, tampered flat union rejected, ambiguous assemble input, same rule bound to two requests → `TRUST_REPLY_FACT_ALREADY_ASSIGNED` (I-2); fixtures now compute mapping-sensitive evidence versions.
8. `TrustReplyWorkbenchStateStoreTest.kt` — **new file at the authorized path** (the plan lists it as 修改 but it did not exist at base 931e724; created to satisfy Task 5 coverage #6): v1/v2 codec, unknown/corrupt → null, v2 write round-trip, payload size limit, optimistic update/insert/delete conflicts, prune.
9. `TrustReplyWorkbenchControllerTest.kt` — HTTP matrix round-trips on bootstrap/assemble/state, stable 422 codes for ambiguous/duplicate-assignment, bootstrap response serialization of the matrix.

## Invariants evidence

- **I-1** (matrix is selection authority): `requestFactSelections` drives every workbench entry; `requestedFactIds`/`canonicalFactIds` are compat input / ordered audit union only. Test: `bootstrap accepts matrix input and resolves per request`, `bootstrap adjust save restore assemble carry one canonical matrix`.
- **I-2** (one assignment per fact): duplicates rejected with 422 `TRUST_REPLY_FACT_ALREADY_ASSIGNED` before stateStore/composer/LLM. Tests: `matrix mode rejects the same rule assigned to two requests`, `legacy flat rejects duplicate ids as already assigned`, `assemble rejects the same source rule bound to two requests as already assigned`, plus the post-canonicalization guard in the resolver.
- **I-3** (explicit assignment must match the specified request): `matrix mode rejects a fact that matches another request only`, `matrix mode rejects disabled never and blank facts`.
- **I-4** (legacy flat normalization): `legacy flat assigns each id exactly once to the first accepting request`, `legacy flat rejects ids not consumed by any request`, `workbench selection rejects both matrix and legacy fields`.
- **I-5** (mapping-sensitive identity): `same fact union bound to different requests changes evidence version` + repeatability assertion; OMIT local path produces the identical base version (`adjust item materializes OMIT version...` passes).
- **I-6** (v1→v2 durable upgrade): `bootstrap restores v1 payload after flat normalization`, `bootstrap marks stale when stored v2 matrix drifts from current`, StateStore v1/v2 codec tests, unknown schema `INVALID`.
- **I-7** (no FULL_DRAFT for matrix clients): `full draft with matrix fails closed and adjust item forwards matrix`.
- Downstream constructibility: full `mvn test` passes, including AiTrainingSimulateTest, PendingMailOperationServiceTrustWorkbenchTest, UnmatchedInboundTrustWorkbenchTest, AiTrainingEvaluationServiceTest (unchanged, compile against defaulted new fields).
- `QaFactSelectionService.select` non-workbench tests unchanged and passing; old-frontend `node --test src/test/js/*.test.js` passes untouched.

## Commands (run freshly, after final implementation state)

| Command | Result | Exit code / counts |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test` | PASS | exit 0; Tests run: 2092, Failures: 0, Errors: 0, Skipped: 4 (pre-existing skips); BUILD SUCCESS |
| `node --test src/test/js/*.test.js` | PASS | exit 0; tests 413, pass 413, fail 0 |
| `git diff --check` | PASS | exit 0 (clean) |
| Targeted: `mvn test -Dtest='QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchStateStoreTest,TrustReplyWorkbenchControllerTest'` | PASS | exit 0; Tests run: 110, Failures: 0, Errors: 0 |

## Changed files (implementation commit)

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt`
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStoreTest.kt` (created; authorized path listed in the plan's 变更文件清单)
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`

Implementation commit subject: `feat(fast-p): implement trust-reply-configurable-workbench-01` (docs/plans/fast/** excluded; this report is outside the implementation commit).

## Deviations

- `TrustReplyWorkbenchStateStoreTest.kt` is listed as 修改 (modify) in the plan but did not exist at base 931e724; it was created at the exact authorized path because Task 5 requires state-store v1/v2 codec, concurrency and payload-limit coverage. No other deviation.

## Freshness

- Plan identity rechecked: YES (SHA-256 unchanged `86a68e2b…`)
- Worktree identity rechecked: YES (root/branch/HEAD unchanged `931e724`)
- Reported commits reachable from target branch: YES (committed on `fast/trust-reply-configurable-workbench` in this worktree)
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`

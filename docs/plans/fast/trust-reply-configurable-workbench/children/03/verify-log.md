# Verification Log — trust-reply-configurable-workbench-03

- Verdict: **LIGHT_PASS_WITH_NOTES**
- Boundary: c99c3aa..82a23b4 (implementation commit 82a23b4, parent 7a3b256; docs commits 4c2f01a/7a3b256 inside the range are controller evidence commits, excluded from the implementation commit per brief)
- Verifier: VerifyChild03 (fresh verifier, no writer connection)
- Date: 2026-08-05
- Plan: children/03/brief.md (SHA-256 a24eafad485191171ec0a7205f807e01b81fd6922c60462e8c56f6c2f02915c1)
- Executor report: children/03/execution.md (READY_FOR_VERIFICATION, 2 disclosed deviations)

## Gate 1 — Authorized scope: PASS

`git show 82a23b4 --stat` changes EXACTLY the 10 files in the 变更文件清单:
trust-reply-workbench.js, styles.css, app.js, index.html, AiTrainingController.kt,
trustReplyWorkbenchSharedMount.test.js, trustReplyWorkbench.test.js,
aiReplyReviewConfirmation.test.js, batchSendTaskConsoleVisualFix.test.js,
AiTrainingSimulateTest.kt (+1821 −277). No other product/test file touched by the
implementation commit; docs/plans/fast/** changes live only in the separate docs
commits as the brief requires.

## Gate 2 — Plan and invariants: PASS

Code + test evidence (worktree at 82a23b4):

- **I-1 shared state, activePage only toggles visibility**: `setActivePage` only
  sets `state.activePage` + render + focus; no bootstrap/reset path. Test
  "renders two equal tabs with unique panel ids and switches pages without
  re-bootstrap" (asserts bootstrap call count unchanged); dual-mount isolation
  test. Business state (requests, matrix, frame, versions, locks, assembly)
  lives in one `createInstance` state.
- **I-2 per-request factOwnerById + disabled/owner label + full matrix payload**:
  `factOwnerById()`/`availableFactsFor()` derived from `requests[].factRuleIds`;
  picker renders `data-state="used"` disabled with `已用于摘要 N` (owner.index+1),
  `selected`→已选择, `pending`→保存中; `addFact` rejects DOM-tamper add of an
  owned fact. `serializeRequestFactSelections()` sent on bootstrap/generation/
  state/assemble; grep confirms `requestedFactIds`/`selectedFactIds` have zero
  occurrences in static assets. Tests: chips/used-owner/release; "sends the full
  matrix and frame snapshot on every generation, state and assemble payload"
  asserts `!("requestedFactIds" in payload)` on every payload.
- **I-3 fact change confirm+delete durable state+resetVersions+bootstrap vs frame
  change invalidateAssembly only**: `changeRequestFacts` confirms when generated
  state exists, deletes durable state with the old matrix (test asserts delete
  payload carries old matrix), `resetVersions()`, re-bootstrap. `onFrameChange`
  snapshots previous, `invalidateAssembly()` only, `assemblyStale=true`, keeps
  locks, persists new frame via PUT state with locks, rolls back on failure.
  Tests: "cancels a destructive fact change without touching state or DOM",
  "confirms a destructive fact change, deletes durable state, resets versions
  and re-bootstraps", "frame change clears only the assembly, keeps locks and
  persists the new frame" (asserts `data-locked="true"` survives, STALE shown,
  complete disabled).
- **I-4 LOCAL preview never written to assembly/adopt/send; complete only on
  identity-matched server assembly**: `renderFrameLocalPreview` derives from
  frame option content + `resolvedVersion(request)?.answerText` only; never
  touches `state.assembly`. `previewState()` = CURRENT only when
  `assemblyIdentityMatches` (source/evidence/frame); `complete()` returns unless
  CURRENT. Fixed labels 配置预览 · 尚未服务端整合 / 服务端整合完成 /
  配置已变化 · 请重新整合. Tests: CURRENT-only completion gate, resolved-only
  preview, STALE/complete-disabled assertions.
- **I-5 canonical matrix/frame/locks through saveAiTrainingEvaluation +
  buildTrustReplyAssemblySnapshot + adopt + unedited send**: app.js diff shows
  both functions deep-copy `requestFactSelections` (each `factRuleIds` list) +
  `frameSnapshot` (selection + version) + lockedItems; no
  `requestedFactIds || canonicalFactIds` fallback (grep: only remaining
  `canonicalFactIds` are the qaRuleIds audit/UI union in adoptTrustReplyAssembly,
  which the plan explicitly retains). Controller `toDomain()` maps new nullable
  DTO fields into child-01/02 domain types. Tests: aiReplyReviewConfirmation
  asserts deep matrix/frame/no-fallback; AiTrainingSimulateTest asserts HTTP→
  domain mapping + legacy-client compat.
- **I-6 resolvedVersionId only**: `resolvedVersion()` reads
  `request.resolvedVersionId` exclusively; `currentResolvedVersions()` collects
  resolved ids only; preview test asserts local preview derived from resolved
  versions only.
- **I-7 tablist/tab/tabpanel ARIA + Left/Right/Home/End + instance-id + no
  per-keystroke full render**: `nav[role=tablist]` + two `button[role=tab]` with
  `aria-controls/aria-selected`, roving `tabindex` 0/-1, `section[role=tabpanel]`
  with `hidden`; ids prefixed `state.instanceId` (tabId/panelId); keydown
  ArrowLeft/Right/Home/End delegates to setActivePage; focus moves to target tab.
  `onInput` still uses `syncInstructionUi` (header/version/answer/actions +
  summary innerHTML only; textarea node never rebuilt) — test asserts selection
  start/end stable and zero writes after first invalidation across 20 IME chars.
- **I-8 ADJUST_ITEM-only on frozen grounded allowlist**: `generateMissingGrounded`
  freezes `frozenKeys=[...allowlist]`, iterates per requestKey via
  `requestItemVersion`; `generateItemPayload` always `operation: "ADJUST_ITEM"`;
  grep confirms `FULL_DRAFT` absent from static assets. Tests assert
  ADJUST_ITEM ordering with full matrix per item, zero FULL_DRAFT, durable
  per-item saves before assemble, PARTIAL/UNSUPPORTED locks preserved
  (cancellation/identity-mismatch/duplicate tests).
- **Style S-1..S-4 + S-6 verbatim**: programmatic comparison of the five CSS
  fences in brief.md (S-1 1967B, S-2 615B, S-3 3857B, S-4 1269B, S-6 762B)
  against styles.css — exact normalized match for all five.
- **Style S-5 retirements**: `.trust-reply-layout`, toolbar
  `.compose-rule-list[data-role="facts"]` (+muted child), and all
  `.trust-reply-fact-option` rules fully removed from CSS (removed-lines diff
  matches exactly these rule sets); no `[data-role="fact"]` checkbox entry in
  renderToolbar; global `.tabs/.tab` (1/3 selector lines), `.mailbox-segmented-
  control` (9), `.button` (14), `.compose-panel` (3), plus
  `.trust-reply-item`/`.trust-reply-summary`, identical base vs current.

## Gate 3 — Required commands: PASS

Run freshly in the worktree at 82a23b4, in order:

| Command | Result | Evidence |
|---|---|---|
| node --test (4 child files) | exit 0 | 85 pass / 0 fail |
| node --test src/test/js/*.test.js | exit 0 | 429 pass / 0 fail (baseline 413; +16 from this child's new coverage) |
| git diff --check | exit 0 | clean |
| mvn test (zulu-11) | exit 0 | BUILD SUCCESS; surefire totals tests=2119 failures=0 errors=0 skipped=4 (baseline 2118 run / 4 skipped; +1 new compat test) |

Matches the implementer's execution report and the recorded baseline.

## Gate 4 — Downstream interfaces: PASS

- Final child confirmed by ledger.md (children table ends at child 03; no later
  children).
- must-NOT-change list: other pages' global styles zero diff (Gate 2 S-5);
  aiReplyLoadingFeedback.test.js and aiTrainingUnsupportedAnswers.test.js both
  pass inside the full node suite (429 pass includes them).
- app.js/AiTrainingController transport matches children 01/02 domain DTOs:
  `TrustReplyRequestFactSelection` / `TrustReplyFrameSelection` /
  `TrustReplyFrameSnapshot` exist unchanged in TrustReplyWorkbenchService.kt
  (child 01/02 contract); only AiTrainingController.kt changed among backend
  files, additively, mapping the new nullable HTTP DTO fields into those domain
  types; old clients without the fields stay compatible (nulls).

## AUTO_FIX

N/A — no proven four-gate violation; every required contract is met and both
deviations are accounted for as RECORD_ONLY below.

## RECORD_ONLY

1. **S-5 doc-comment keeps the retired class name string** (deviation 1):
   `.trust-reply-layout` is fully retired from markup and CSS (zero selector
   usage; only a `// S-5: …` comment in renderShell and a CSS comment remain).
   The comment retains the string because the unlisted
   aiReplyLoadingFeedback.test.js:895 asserts
   `workbenchJsSource.includes("trust-reply-layout")`. A comment is not a usage
   point per S-5, and editing that test would violate Gate 1. Disclosed,
   harmless, keeps an unlisted-but-green suite green. Not a violation.
2. **Locked-item copy inlined in saveAiTrainingEvaluation** (deviation 2):
   the inline map has exact field parity with `copyTrustReplyLockedItem`
   (requestKey/versionId/handling/answerText/claims spread/model/generationKind/
   evidenceSetVersion/sourceVersion/operatorInstructionHash||""/
   operatorInstruction||""), so I-5's payload content contract is fully met.
   The helper call was inlined because the unlisted
   aiTrainingUnsupportedAnswers.test.js extracts the function body and runs it
   in a bare vm sandbox where `copyTrustReplyLockedItem` is undefined
   (ReferenceError). Mechanism deviation only; disclosed; suite green. Not a
   violation.

## Required Action

**COMPLETE_CHILD**

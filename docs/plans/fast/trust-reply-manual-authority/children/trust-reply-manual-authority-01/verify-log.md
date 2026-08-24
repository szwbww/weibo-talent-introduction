# Light Verification Log — trust-reply-manual-authority-01

Append-only, one report per attempt.

## Light Verification: LIGHT_PASS_WITH_NOTES

- Child: trust-reply-manual-authority-01 (plan `docs/plans/2026-08-24/01-mail-request-extraction-correctness.md`, approved identity commit:8dc7c96)
- Boundary: 99cef49a37f79b409504e89cd5cd942370966c39..7989af65e5c62d414d5a4557d79e3f06007bc4f9 (implementation commit `feat(fast-p): implement trust-reply-manual-authority-01`, parent = plan-seeding 8dc7c96)
- Verifier: Verify01 (lightweight four-gate, read-only; report is the only write)

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | `git diff --name-status 8dc7c96..7989af6` = exactly the 6 Authorized Files (QaRequestExtractor.kt, AiReplyIntentCatalog.kt, QaFactSelectionService.kt, QaRequestExtractorTest.kt, QaFactSelectionServiceTest.kt, TrustReplyWorkbenchServiceTest.kt). No other product/test file touched; boundary delta vs master additionally contains only the plan-seeding docs commit (expected per brief: base 99cef49 + plan-seeding). |
| 2. Plan + invariants I-1..I-5 | PASS | Stage 1: `BULLET_LINE_PATTERN = Regex("^(?:[-*•]\\s+|\\d+[.)]\\s+)")` (QaRequestExtractor.kt:340) — whitespace required after symbol AND numeric markers. New tests QaRequestExtractorTest.kt:219 (sanitized live fixture → 1×QUESTION, offset round-trip `foldLike(substring)==text`), :243 (5 legal markers in order), :265 (`*Name*`/`-not a list` rejected), :283 (indented continuation folds). I-1 CRLF/CR offset tests (:165-211) unchanged and green. Stage 2: QaFactSelectionService.kt:511-522 derives `absoluteMatchedSpans` by adding `range.first` (= `RequestUnit.startOffset`, :608/:612) to each local span, passes them to `claimed()` (InboundAskEnumerator.kt:41-44 unchanged); local `matchedSpans` still feed intent/status. New regression QaFactSelectionServiceTest.kt:1485: request starts at offset>0, claimed=3 / unrecognized=1 / enumerated conserved. `MatchedIntentSpan` comment corrected to "ranges index the string passed to matchIntentsWithSpans; caller MUST rebase" (AiReplyIntentCatalog.kt:29-39). I-4: change confined to the shadow `unrecognizedAsks` derivation; pre-existing `shadow enumeration never changes status counts or fact ids` (QaFactSelectionServiceTest.kt:1325) green. Stage 3/I-5: new test TrustReplyWorkbenchServiceTest.kt:1003 — implicit old matrix (5 signature keys) → bootstrap returns default matrix + `savedState.status=STALE` + empty locked, no 422 (existing bootstrap fallback, TrustReplyWorkbenchService.kt:456-470); explicit same matrix still throws 422. |
| 3. Required commands | PASS | Fresh in worktree: `JAVA_HOME=…zulu-11.jdk… mvn -q -Dtest=QaRequestExtractorTest,QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest test` → exit 0; surefire: QaRequestExtractorTest 18 / QaFactSelectionServiceTest 66 / TrustReplyWorkbenchServiceTest 63 (147 total), failures=0 errors=0 skipped=0. `git diff --check` → exit 0. Baseline had zero failures (combined directed union exit 0). |
| 4. Downstream interfaces | PASS | requestKey/STALE: no change to TrustReplyWorkbenchService.kt / TrustReplyWorkbenchStateStore / schema / bootstrap semantics (STALE reuses pre-existing fallback); no new 02 fact fields (QaFactSelectionService.kt delta = local val only); shared test fixtures QaFactSelectionServiceTest.kt (+49) and TrustReplyWorkbenchServiceTest.kt (+88) appended only, no deletions, pre-existing tests all green; QaRequestExtractor offset contract untouched (regex-only change; offset mapping unchanged). |

### AUTO_FIX
- None.

### RECORD_ONLY
- Stage-3 explicit-matrix assertion uses `TRUST_REPLY_REQUEST_KEY_INVALID` (TrustReplyWorkbenchServiceTest.kt:1003 comment + :1084 assert) instead of the plan's literal `TRUST_REPLY_FACT_SELECTION_INVALID`. Actual pre-existing code (TrustReplyWorkbenchService.kt:1798-1801) throws REQUEST_KEY_INVALID for any unknown requestKey — checked before the incomplete-set check that yields FACT_SELECTION_INVALID (:1802-1809). Plan acceptance "显式脏矩阵仍 422" is satisfied (TrustReplyWorkbenchException, UNPROCESSABLE_ENTITY); the plan-literal code remains covered by the pre-existing test `bootstrap fails closed when implicit saved fact selection is unusable` (TrustReplyWorkbenchServiceTest.kt:971-996, legacy requestedFactIds path). Deviation is plan-text vs code-reality, documented by implementer, fail-closed preserved, and the only "repair" would require editing a non-authorized file (TrustReplyWorkbenchService.kt) — no plan violation, no AUTO_FIX.

### Required Action
- COMPLETE_CHILD

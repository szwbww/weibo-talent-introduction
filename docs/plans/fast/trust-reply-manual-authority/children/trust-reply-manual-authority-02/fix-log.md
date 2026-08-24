# Fix Log — trust-reply-manual-authority-02

Append-only, one section per round.

## Epoch 2 — Round 1/3
- Findings: A1
- Before: 78e17225
- Fix commit: ce2bc4a
- Authorized files changed: src/test/js/trustReplyWorkbenchSharedMount.test.js
- Commands: mvn -q -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchServiceTest test (JAVA_HOME=zulu-11) -> exit 0 (Kotlin: 366 tests, 0 fail; exec-plugin node-test phase globbed src/test/js/*.test.js: 731 tests, 0 fail, includes rewritten "keeps a cross-request fact selectable in the picker and releases facts on remove"); node --test src/test/js/trustReplyWorkbench.test.js -> exit 0 (31 tests, 0 fail); git diff --check -> exit 0
- Result: FIXED
- Notes: Test 2347-2398 rewritten to plan 02 I-6/S-1/阶段5 contract: two-request fixture and remove-fact/release flow kept; a fact bound to request A now asserted selectable (data-state="available", no disabled) in request B's picker, with doesNotMatch for data-state="used", 已用于摘要 1, and scoped disabled option; re-bootstrap payload still asserted to release the fact from the matrix. Sibling tests untouched. Commit excludes docs/plans/fast/ (ledger.md and this log stay uncommitted).

## Epoch 2 — Round 2/3
- Findings: O1 (AUTO_FIX)
- Before: ce2bc4a
- Fix commit: f6f577f
- Authorized files changed: src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt, src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt
- Commands: mvn -q -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchServiceTest test (JAVA_HOME=zulu-11) -> exit 0 (Kotlin: 367 tests, 0 fail — QaFactSelectionServiceTest 69, AiReplyDraftServiceTest 183, TrustReplyWorkbenchItemFlowTest 52, TrustReplyWorkbenchServiceTest 63; exec-plugin node-test phase globbed src/test/js/*.test.js: 731 tests / 112 suites, 0 fail); node --test src/test/js/trustReplyWorkbench.test.js -> exit 0 (31 tests, 0 fail); git diff --check -> exit 2 (sole offender: pre-existing uncommitted docs/plans/fast/.../verify-log.md:34 trailing blank line from round-1 verification, outside authorized files and excluded from fix commits; scoped `git diff --check` on the two authorized files -> exit 0)
- Result: FIXED
- Notes: Plan 02 I-6 per-item clause restored: resolveMatrixSelection now rejects a duplicated fact id within a SINGLE request's explicitIds with the existing invalid-matrix error (422 TRUST_REPLY_FACT_SELECTION_INVALID), placed inside the matrix path's validateExplicitSelection block only — legacy flat path keeps checkWorkbenchUniqueness and the strict select() explicit path, auto/null paths untouched (I-8). Cross-request reuse of the same id remains accepted. New regression test covers both clauses: [1,1] in one request -> 422 TRUST_REPLY_FACT_SELECTION_INVALID; same id 1 in two different requests -> accepted with sendQaRuleIds=[1]. Commit excludes docs/plans/fast/ (ledger.md, verify-log.md, this log stay uncommitted).

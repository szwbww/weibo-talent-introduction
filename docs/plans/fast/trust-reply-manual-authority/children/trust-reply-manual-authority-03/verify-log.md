# Light Verification Log — trust-reply-manual-authority-03

Append-only, one report per attempt.

---

## Light Verification: LIGHT_PASS_WITH_NOTES

- Child: trust-reply-manual-authority-03 (approved plan `docs/plans/2026-08-24/03-manual-fact-authority-live-send.md`)
- Boundary: `f6f577f..d43a4db3e90a61be97c75748fa8b3c44b423c341` (implementation commit `feat(fast-p): implement trust-reply-manual-authority-03`)
- Verifier: Verify03 (lightweight four-gate verification, read-only except this log)

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | Implementation commit `d43a4db` touches exactly the 4 authorized files (`TrustReplyWorkbenchService.kt`, `PendingMailOperationService.kt`, `PendingMailOperationServiceTrustWorkbenchTest.kt`, `ManualReplySendAttemptServiceTest.kt`). Range `f6f577f..d43a4db` additionally contains 3 docs-only files from intermediate `5bc7c03` (child-02 verification recording: child-02 fix-log.md/verify-log.md, ledger.md) — expected fast-p bookkeeping, not code. No migration/controller/frontend changes; other two `mailRecordQaRuleRepository.save` sites (`ManualExpertMailService.kt:92`, `AutoMailReplyService.kt:637`) untouched; child-01/02 fixtures preserved (child-02 `selection.sendQaRuleIds` canonical audit, preflight snapshot `operatorAuthorizedActions(sourceRef)`, V23/V24/V42 SQL unchanged). |
| 2. Plan + invariants I-1..I-7 | PASS | I-1: `verifyAssembly` runs at top of `sendManualRichReply` (before suppression / `prepareAndClaim` / SMTP / `finalizeSuccess`); source guard `LIVE_INBOUND`+id → 422; `TrustReplyWorkbenchException` mapped to its status (stale → 409 CONFLICT). I-2: `qaRuleIds.orEmpty() != response.canonicalFactIds` → stable 422 pre-claim; no client-id adoption, no auto-recommend fallback, no partial pruning (assembly path uses `CanonicalFactResolution(verifiedCanonical, emptyList())`). I-3: legacy `canonicalizeFactRuleIds` body byte-identical (only call-site wired), catch-`IllegalArgumentException` + degraded warnings intact, `carriesQa` = client-submitted `qaRuleIds` on legacy path. I-4: length/placeholder/suppression/high-risk/trust-rhetoric/sensitive-action/confirmations all still run on rendered subject/text/html; `verifiedSelection ?: strict select(...)` replaces only the fact-selection data source. I-5: operator actions derived from `operatorAuthorizedActionsFromVerifiedVersions(response.itemVersions)` (server-materialized, versionId-verified versions); client `lockedItems` never read in send path; empty versions → fail-closed empty set. I-6: `SendPayload.canonicalQaRuleIds` = verified canonical (closed-loop test incl. mismatch fact 20L); `finalizeSuccess` (unchanged) writes `mail_record_qa_rule` by ordinal — new test pins exact ordinal order 10/20/30 → 0/1/2; cross-request dedupe via canonical union (plan 02) unchanged. I-7: `prepareAndClaim`/SMTP/finalize/failure-recovery/idempotency untouched (`ManualReplySendAttemptService.kt` not in diff); verification failure throws before claim → attempt not burned (verifyNoInteractions on `manualReplySendAttemptService`/`mailDeliveryService`/`emailSuppressionService`). |
| 3. Required commands (fresh) | PASS | `JAVA_HOME=...zulu-11... mvn -q -Dtest=PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest test` → exit 0 (whole run incl. exec-plugin node-test phase: 731 node tests pass/0 fail; Surefire: TrustWorkbenchTest 47 tests/0 failures, ManualReplySendAttemptServiceTest 17 tests/0 failures). `git diff --check` → exit 0. |
| 4. Downstream interfaces | PASS | `VerifiedTrustReplyAssembly` is `internal data class(response, selection)` — JVM-only, no HTTP field, `TrustReplyAssembleResponse` field set unchanged. `selection` + `response.itemVersions` + canonical matrix (`canonicalFactIds`/`requestedFactIds`) available for child-04 diagnostics. Verified response in scope at `finalizeSuccess` call site; `recordSendAudit` unchanged, no diagnostics written by 03 (seam preserved). `collectSafetyFindings` nullable `verifiedSelection` (default null; legacy strict select verbatim). Archive reuses pre-send verified result; post-send second `assemble` deleted (tests verify `never().assemble(...)`); edited body → replay-mismatch archive skip retained. |

Deviation evaluation (flagged by implementer): `returns SENT with failed archive when source mismatches` and `returns SENT with failed archive when stale replay rejected` (plus `ignores an assembly that points at another inbound`) were rewritten to pre-claim 422/409. **Aligned, not weakened**: the old tests encoded pre-03 behavior (mail still SENT, archive FAILED post-hoc), which plan I-1/I-7 explicitly eliminates ("stale/tampered assembly 在 prepareAndClaim 前失败"; acceptance: "任一 stale/tamper 时，在发送副作用前失败"). New assertions are stricter (stable status code + `verifyNoInteractions` on all side-effecting services); archive-skip property preserved; still-reachable archive-FAILED path (body-edit replay mismatch) remains covered.

### AUTO_FIX
(none)

### RECORD_ONLY
- Boundary range includes 3 docs-only files from intermediate commit `5bc7c03` (child-02 verification recording) — implementation commit itself is exactly the 4 authorized files; no action needed.
- Plan-mandated behavior change: source-mismatch/stale/tampered assembly now fails pre-claim with stable 422/409 instead of sending with post-hoc archive FAILED; the two rewritten tests align with I-1/I-7 (evaluated above, not weakened).

### Required Action
- COMPLETE_CHILD

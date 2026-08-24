# Fast-P Child Brief — trust-reply-manual-authority-03

- Plan (exact approved contract): `docs/plans/2026-08-24/03-manual-fact-authority-live-send.md` (approved identity `commit:8dc7c96`)
- Master plan: `docs/plans/2026-08-24/00-trust-reply-manual-authority-master.md`
- Depends on: trust-reply-manual-authority-01, trust-reply-manual-authority-02. Base = child 02's terminal code head (see dispatch). Atomic R2 with 02 — do not claim release.

## Scope and master constraints

- Master I-1/I-2: `matrix → verifiedAssembly.canonicalFactIds → SendPayload.canonicalQaRuleIds → mail_record_qa_rule(ordinal)` end-to-end identity; client ids must equal verified canonical exactly.
- Master I-3: semantics diagnostics never become hard gates; hard gates remain rule availability/version/source/safety/suppression/placeholder/confirm/idempotency.
- Master I-4: 02+03 one release artifact/rollback unit.
- Master I-6: no-assembly path (auto/legacy/null) keeps current `canonicalizeFactRuleIds` degraded logic verbatim.
- Master I-7: ONLY the 4 authorized files below; new file/field/API → stop, PLAN_CONFLICT.
- Master I-8: run directed tests + `git diff --check`; record exits.

## Authorized files (from plan 03)

| File | Change |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | internal `VerifiedTrustReplyAssembly(response, selection)`, public `assemble` reuses verify path |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | pre-SMTP verification, fact-source branch, safety/archive reuse |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | assembly send loop, tamper/stale/safety/legacy regression |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | payload canonical ids vs association-table ordinal exactness |

No DB migration, controller, frontend changes. Do not modify the other two `mailRecordQaRuleRepository.save` sites (`ManualExpertMailService.kt:92`, `AutoMailReplyService.kt:637`).

## Child-plan invariants (03 I-1..I-7)

- I-1: assembly re-verified server-side before ANY send side effect — before suppression / `prepareAndClaim` / SMTP / DB success.
- I-2: with assembly: `canonicalFactIds = verifiedAssembly.response.canonicalFactIds`; client `qaRuleIds` must equal it element-wise else stable 422/409 before claim; never silently adopt client ids, fall back to auto-recommend, or partially prune.
- I-3: no assembly → existing `canonicalizeFactRuleIds` (catch `IllegalArgumentException` only; unavailable/mismatch become confirmable warnings; usable subset in association; `carriesQa` judged by client-submitted qaRuleIds).
- I-4: rendered subject/text/html still run full length/placeholder/suppression/high-risk-fact/trust-rhetoric/sensitive-action/normal-strong-confirm checks; verified assembly only replaces the fact-selection data source.
- I-5: operator-action authorization derived from verifyAssembly-passed locked versions, never from client lockedItems read directly.
- I-6: `SendPayload.canonicalQaRuleIds` == verified canonical facts; `finalizeSuccess` writes `mail_record_qa_rule` by ordinal; cross-request duplicates appear once (first occurrence) satisfying the unique key.
- I-7: idempotency/transaction semantics unchanged; assembly validation failure must not burn an attempt.

## Required commands

- `mvn -q -Dtest=PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest test` (JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home)
- `git diff --check`

## Downstream interfaces

- `VerifiedTrustReplyAssembly` (internal, JVM-only; no new HTTP field) consumed by 04 diagnostics: `selection + response.itemVersions + canonical matrix`.
- `PendingMailOperationService` passes verified diagnostics to `recordSendAudit` (04 adds that; 03 must keep a seam: verified response available at the `finalizeSuccess` call site — do not preempt 04 by writing diagnostics, but do NOT delete verified-response availability).
- `collectSafetyFindings` gains nullable verified selection; legacy path keeps strict `select`.
- Archive: `archiveLiveUnsupportedAnswers` reuses pre-send verified result; only archive when actually-sent body still equals assembly product; edited body sends but does not archive; delete post-send second `assemble`.

## Procedure

Same as brief 01: execute-p against the exact plan; 5 stages; run all required commands; commit locally ONLY the 4 authorized files as `feat(fast-p): implement trust-reply-manual-authority-03`; append result to child execution.md; return `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + command summary + report path. Skip formatters/linters/full suite; do not touch later children or rewrite history.

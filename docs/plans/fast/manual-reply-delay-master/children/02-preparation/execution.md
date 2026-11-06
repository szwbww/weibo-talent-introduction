# 02-preparation Execution Report

## Agent Availability Events

- 2026-11-04T12:05:38Z — child 02-preparation, role IMPLEMENTER, attempt 1, error `[opencode-go/deepseek-v4.1-flash] getaddrinfo ENOTFOUND opencode.ai`, code head unchanged at 9d7a563d4ed3ed65d4aeaace7e8abf945382c631, action RETRY. No product files modified.

## Execution Result: READY_FOR_VERIFICATION

- Plan (child): `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-02-preparation.md`
- Child plan SHA-256: `2849fccb0b6b864773506061745c779a9c78ce094715a20b52d2992ec564b951` (7,689 bytes, includes amendment A1 committed as `ab3ad56`)
- Master plan SHA-256: `9df55143ff99d59e2a958e6703930a8ed69231b9e6b195ed749b7b2c8eb8ff5d`
- Execution ID: `manual-reply-delay-02-preparation.md@2849fccb0b6b864773506061745c779a9c78ce094715a20b52d2992ec564b951`
- Execution epoch: NEW (prior evidence contained only an agent-availability event; no product file existed for this child)
- Executor: Impl02PreparationRetry
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`
- Target branch: `fast/manual-reply-delay-master`
- Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`
- Pre-execution code SHA: `ab3ad56713a74664b0f34f66a138206cb14666ea`
- Post-execution code SHA: `72906c0c5e36393429320817ea0de4f7d6f3ff05` (`feat(fast-p): implement 02-preparation`)
- Evidence HEAD: N/A (fast-p evidence stays uncommitted per brief)
- Implementation boundary: `ab3ad56..72906c0`, 8 authorized files (7 changed + 1 new)

Amendment A1 (`ab3ad56`) added exactly one extra authorized file, `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt`, limited to the three RAG success cases that asserted Pending's `mailRecordRagFactRepository.saveAll`.

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 extract prepare (I-3/I-4) | IMPLEMENTED | `PendingMailOperationService.kt`, `DelayedManualReplyModels.kt` | `internal prepareManualRichSend(...) : PreparedManualReplyOutcome`; `PreparedManualReply` (internal runtime: source/account/files) + `PreparedManualReplyOutcome.Completed/Ready`; `DelayedManualReplySnapshotFactory.fromPrepared` explicit whitelist |
| T2 extract deliverPrepared (I-3/I-4/I-5) | IMPLEMENTED | `PendingMailOperationService.kt` | `internal deliverPrepared(prepared, claim, delayedRef = null)` only consumes an already persisted `ClaimedAttempt`; no public send recursion; both public entries call prepare → `prepareAndClaim` → deliver in the original order |
| T3 RAG into finalizeSuccess (I-5) | IMPLEMENTED | `ManualReplySendAttemptService.kt`, `PendingMailOperationService.kt` | `SendPayload.ragEvidence` (server-only, out of fingerprint); `finalizeSuccess` writes ordered `mail_record_rag_fact` after the SENT record id, before attempt SENT, same `REQUIRES_NEW` tx; QA/RAG mutual exclusion `require` at method entry; Pending `saveAll` removed |
| T4 regression (I-3/I-4/I-5) | IMPLEMENTED | 5 test files | 4 pre-existing/new classes 176 tests green; RagSendBridgeTest 11 green; snapshot matrix 11 tests; MySQL IT 27 green |

## Key decisions

1. **Prepare/deliver seam keeps byte-identical behaviour.** `prepareManualRichSend` holds every existing step before claim (placeholders, render, review-content comparison, meeting rebuild, safety findings/confirmation, attachment resolution, payload build, suppression, `findCompletedByPayload`). The synchronous entries then run `prepareAndClaim` and `deliverPrepared`, so claim classification, SMTP thread headers, finalize/audit/archive order and exception mapping are unchanged.
2. **Explicit outcome type, no null sentinel.** `PreparedManualReplyOutcome.Completed(result)` is returned by the two idempotent-completion hits (`completedBinding` stays in the entry, `findCompletedByPayload` moved into prepare); `Ready(prepared)` is the only path to claim/SMTP. `deliverPrepared` cannot be re-entered from a public send.
3. **`delayedRef` is a server-only token.** `deliverPrepared` takes `DelayedDispatchRef?` (01 model) with default `null`; this stage has no queue row and keeps the old completion logic. It never enters an HTTP-bound DTO.
4. **RAG fact rows join the SENT transaction.** `finalizeSuccess` writes `MailRecordRagFact(mailRecordId, factCode, ordinal, corpusFingerprint)` in request order, after the mail record id exists and before `updateStatusAndError(SENT)`; empty `factCodes` writes no rows; `require(ragEvidence == null || canonicalQaRuleIds.isEmpty())` runs before any write. A write failure propagates and leaves the attempt `DELIVERY_IN_PROGRESS` for the existing UNKNOWN classification.
5. **Snapshot factory is a whitelist mapper, not entity serialization.** `DelayedManualReplySnapshotFactory.fromPrepared` copies only payload/source/restore/evidence/confirmation fields. `MailSenderAccount` (incl. SMTP/IMAP passwords), `ExpertContact`, `InboundMailProcessing`, attachment bytes and paths are never read. DRAFT maps `ValidatedDraftSendRef` field by field; REVIEW maps the APPROVED frozen row; LEGACY captures the raw request for the original detail editor. `verifiedContentSha256` binds final subject/text/html + ICS semantic hash + ordered attachment hashes.
6. **LEGACY conversation replies fail closed.** 01's `DelayedLegacyRestore` is keyed by a positive real inbound id, so a conversation reply with no saved draft cannot be restored without losing its original context; the factory throws `DelayedManualReplySnapshotException`. Drafted/reviewed conversation replies are fully representable. Flagged for 03/07 (delayed conversation replies must carry a draft reference).
7. **A1 test rewrite (authorized).** The three `RagSendBridgeTest` success cases now assert `SendPayload.ragEvidence.factCodes` order/fingerprint and `verifyNoInteractions(mailRecordRagFactRepository)` (Pending no longer writes rows); all other assertions in that file are unchanged. Persistence/order-in-transaction is asserted in `ManualReplySendAttemptServiceTest`.

## Commands (all freshly run in this invocation, final code state = `72906c0`)

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `mvn -q -DskipTests test-compile` | 0 | main + test sources compile (Java 11 / zulu-11), no output |
| 2 | `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest,DelayedManualReplySnapshotTest,RagSendBridgeTest'` | 0 | `Tests run: 187, Failures: 0, Errors: 0, Skipped: 0` — 43 / 60 / 62 / 11 / 11; JS suite 2038 pass (exec-maven-plugin) |
| 3 | `mvn test -Dtest=MailReplyDraftSendIntegrationTest -DmysqlIt=true` | 0 | `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0` (Testcontainers MySQL, Docker available) |

Additional safety (not part of the required set): `mvn test -Dtest='MailboxConversationControllerTest,UnmatchedInboundTrustWorkbenchTest'` exit 0, `Tests run: 13, Failures: 0, Errors: 0, Skipped: 1` (1 pre-existing gated skip).

Iteration history (same code path, superseded): first run of command 2 exposed 2 snapshot-assertion mistakes and 4 Mockito matcher NPEs in the new attempt tests; both were test-authoring defects (not product defects) and were fixed. A temporary standalone run of `RagSendBridgeTest` reproduced exactly the 3 authorized A1 failures before the rewrite.

## Changed Files (committed in `72906c0`)

- `src/main/kotlin/.../mail/service/PendingMailOperationService.kt` — prepare/deliver split, `PreparedManualReply(Outcome)`, RAG evidence on payload, Pending-side RAG write removed; `mailRecordRagFactRepository` constructor parameter retained for signature compatibility and documented as unused.
- `src/main/kotlin/.../mail/service/ManualReplySendAttemptService.kt` — `RagEvidence`, `SendPayload.ragEvidence`, top-of-method QA/RAG mutual exclusion, in-transaction RAG write in `finalizeSuccess`, nullable RAG repository dependency.
- `src/main/kotlin/.../mail/service/DelayedManualReplyModels.kt` — `DelayedManualReplySnapshotFactory` explicit whitelist mapping (no schema/codec change).
- `src/test/kotlin/.../mail/service/PendingMailOperationServiceTest.kt` — RAG evidence-on-payload case (43 tests).
- `src/test/kotlin/.../mail/service/ManualReplySendAttemptServiceTest.kt` — ordered in-transaction RAG write, empty evidence, QA/RAG conflict, write-failure propagation (62 tests).
- `src/test/kotlin/.../mail/service/DelayedManualReplySnapshotTest.kt` (new) — 11-case matrix: LEGACY/DRAFT/REVIEW round-trip, QA/assembly, RAG, Unicode/links/variables, ICS+event, 0/10 attachments, no credentials/paths, unknown version, >4 MiB, legacy-conversation fail-closed.
- `src/test/kotlin/.../mail/RagSendBridgeTest.kt` — A1: three success cases assert the new seam.
- `PendingMailOperationServiceTrustWorkbenchTest.kt` — authorized, unchanged: 60 existing tests already green against the new seam.

## Deviations / skipped items

- **A1 amendment** (human/controller-approved, `ab3ad56`): one extra authorized test file beyond the original 7; scope respected (3 success cases + one now-unused import).
- `MailReplyDraftSendIntegrationTest.kt` untouched (run only), 27/27 green.
- No `pom.xml`, `application.yml`, `CLAUDE.md`, migration, frontend, or `docs/plans/fast/**` change was committed. No applied migration edited.
- No enqueue caller, worker, HTTP/DTO, or property switch was added in this child.

## Freshness

- Plan identity recomputed before report (child `2849fccb…`, master `9df55143…`): YES
- Worktree/branch/HEAD rechecked before commit and after: YES
- Commit `72906c0` reachable from `fast/manual-reply-delay-master` (`git merge-base --is-ancestor`): YES
- Required commands run in this invocation after final code state: YES
- Historical evidence used only as baseline: YES

## Remaining blocker

None. Note for 03/07: a delayed conversation reply without a saved draft is not representable by 01's `DelayedLegacyRestore` and currently fails closed in the snapshot factory.

## Next action

`verify-p` on `manual-reply-delay-02-preparation.md` (epoch `2849fccb…`).

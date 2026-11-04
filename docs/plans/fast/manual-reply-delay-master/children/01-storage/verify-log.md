## Light Verification: LIGHT_PASS
Child: 01-storage — docs/plans/2026-11-04/manual-reply-delay-01-storage.md
Boundary: 05342b4be63bbc7d9b30416ae73ada2d44b562df..d57026edc072b5469f08e8642c8c771acbd894e9
Verifier: Verify01Storage

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 05342b4..d57026e` yields exactly the 7 authorized paths: V174 SQL, `DelayedManualReplyModels.kt`, `DelayedManualReplyRepository.kt`, `ManualReplyDelayProperties.kt`, `FlywayMigrationIntegrationTest.kt`, `InboundPendingOwnerIT.kt`, `ExpertMailReadAccessIT.kt`. No pom.yml/application.yml/CLAUDE.md or other product/test change. |
| Plan and invariants | PASS | T1: `V174__create_delayed_manual_reply.sql:6-46` has all 19 planned columns, unique `send_attempt_id`, three indexes `(status,due_at_ms,id)`/`(expert_contact_id,account_code,created_at_ms,id)`/`(origin_kind,origin_id,status)`, attempt FK RESTRICT, no contact FK, five CHECKs incl. six status values, revision>=1, due>=created, origin id/version pairing, SENDING token+claimed_at. T2: `DelayedManualReplyModels.kt:33` six-state enum, `:79` internal `DelayedDispatchRef`, `:86` 13-field `DelayedManualReplyView`, `:104` `expectedRevision` request, strict private ObjectMapper with `FAIL_ON_UNKNOWN_PROPERTIES`/`StrictNullChecks`, explicit `schemaVersion==1` gate, 4 MiB check, restore union `DRAFT/REVIEW/LEGACY`, and no `MailSenderAccount`/password/file-byte fields. T3: `DelayedManualReplyRepository.kt:54` DB `CURRENT_TIMESTAMP(3)` clock, `:92` id-only due scan `LIMIT 20 ORDER BY due_at_ms,id`, explicit `FOR UPDATE` locks, snapshot-derived insert (`:127`) enforcing `dueAt == createdAt + 60000`, revision-CAS `cancelWaiting`/`claimDue`/`completeSending`, MANDATORY + live-transaction guard; `ManualReplyDelayProperties.kt` has `enabled=false`, `pollMs=1000`, `unknownAfterMs=600000`, positive validation, 60 s remains a constant. T4: fresh-chain expectation moved to 174; historical `InboundPendingOwnerIT.kt:424-426` and `ExpertMailReadAccessIT.kt:484-486` pinned with `.target("172")` while keeping historical counts; no unrelated expectation renumbered. New V173→V174 test asserts schema/index/FK/CHECK, Chinese subject, epoch-ms round-trip, unique-attempt conflict, invalid status rejection, and unchanged legacy attempt/draft rows. |
| Required commands | PASS | `mvn -q -DskipTests test-compile` (JAVA_HOME zulu-11) exit 0. `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` exit 0; fresh surefire: `tests=49, failures=0, errors=0, skipped=0` (569.172 s), BUILD SUCCESS. No SKIPPED fallback. |
| Downstream interfaces | PASS | Types match the later-child contracts: `DelayedManualReplyView` fields are exactly 04T3's 13-field public projection, `DelayedManualReplyStatus` is the six-state set (no attempt-enum change), `DelayedManualReplyActionRequest.expectedRevision` is present, and the repository exposes the reads/writes 03/04/05 consume (currentTimeMs, findById/ByIds/ByAttemptId/ByOrigin, scanDueIds, lockById/ByAttemptId, insert, cancel/claim/complete CAS). No enqueue/SMTP/HTTP/worker caller exists yet (`grep` over src/main+src/test finds only the three new files). |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

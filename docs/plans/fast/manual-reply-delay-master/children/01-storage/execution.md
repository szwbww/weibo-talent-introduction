# 01-storage Execution Report

> Append-only evidence log. Written by the implementation worker at the end of execution.

## Execution Result: READY_FOR_VERIFICATION

- Plan (child): `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-01-storage.md`
- Child plan SHA-256: `60d362e4c9c5155120f1f21c06008a434238bc83b4b8902d11ba441b929dd2a6` (12,317 bytes)
- Master plan SHA-256: `9df55143ff99d59e2a958e6703930a8ed69231b9e6b195ed749b7b2c8eb8ff5d` (33,108 bytes)
- Execution ID: `manual-reply-delay-01-storage.md@60d362e4c9c5155120f1f21c06008a434238bc83b4b8902d11ba441b929dd2a6`
- Execution epoch: NEW (no prior execution evidence; `execution.md`/`fix-log.md`/`verify-log.md` were empty)
- Executor: Impl01Storage
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`
- Target branch: `fast/manual-reply-delay-master`
- Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`
- Pre-execution code SHA: `05342b4be63bbc7d9b30416ae73ada2d44b562df`
- Post-execution code SHA: `d57026edc072b5469f08e8642c8c771acbd894e9` (`feat(fast-p): implement 01-storage`)
- Evidence HEAD: N/A (fast-p evidence stays uncommitted per brief)
- Implementation boundary: `05342b4..d57026e`, exactly the 7 authorized files

`worktree_identity.py` could not run: the shared git worktree list contains a prunable temp
worktree (`/private/var/.../multi-ai-kit-publish-...-lt1m864b`) and the helper calls
`Path.resolve(strict=True)` on every registered worktree, raising `FileNotFoundError`.
Worktree identity above was recorded with `git rev-parse` / `git worktree list` instead.
No repository state was changed by that failure.

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 table `delayed_manual_reply` (I-1/I-4/I-10) | IMPLEMENTED | `src/main/resources/db/migration/V174__create_delayed_manual_reply.sql` | Flyway IT: `V173 upgrades to V174 ...` + fresh-chain test green |
| T2 snapshot protocol + shared DTOs (I-4) | IMPLEMENTED | `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt` | compile + temporary round-trip diagnostics (5/5, removed before commit) |
| T3 repository + properties (I-1/I-10) | IMPLEMENTED | `.../mail/repository/DelayedManualReplyRepository.kt`, `.../config/ManualReplyDelayProperties.kt` | compile; repository exercised only at compile level per plan T4 (behavior IT owned by 02/03) |
| T4 migration test updates (K-flyway-latest-version-test-pin) | IMPLEMENTED | `FlywayMigrationIntegrationTest.kt`, `InboundPendingOwnerIT.kt`, `ExpertMailReadAccessIT.kt` | Flyway IT 49/49; MySQL ITs 24/24 against disposable MySQL |

## Key decisions

1. **Insert derives display columns from the snapshot.** `DelayedManualReplyInsert` carries the
   validated `DelayedManualReplySnapshot`; the repository encodes it (4 MiB check), derives
   `subject = payload.subject` / `text_body = payload.finalText`, writes `status='WAITING'`,
   `revision=1`, and requires `due_at_ms == created_at_ms + DELAYED_MANUAL_REPLY_DELAY_MS`.
   This enforces plan T1 (“subject/text_body 只在首次插入从快照派生，不可单独修改”) and I-1
   at the storage seam instead of trusting callers. Cross-field checks also bind
   `expertContactId`/`accountCode`/`actorUsername`/`originKind` to payload/evidence/restore.
2. **Codec is a private strict ObjectMapper**, not the Spring global mapper: Kotlin module with
   `StrictNullChecks`, `JavaTimeModule`, `FAIL_ON_UNKNOWN_PROPERTIES`,
   `FAIL_ON_NULL_FOR_PRIMITIVES`, `ACCEPT_FLOAT_AS_INT=false`. Kotlin constructor parameters have
   no defaults on persisted DTOs, so a missing key is a hard decode failure (I-4 fail closed).
3. **Restore is a Jackson discriminated union** (`kind` = `DRAFT`/`REVIEW`/`LEGACY`, unknown kind
   fails decode). DRAFT maps schema-validated fields of `ValidatedDraftSendRef` (context kept as
   `contextJson`); REVIEW maps the APPROVED frozen identity; LEGACY keeps the original input and
   archive inputs without forcing them into `MailReplyDraftContext`.
4. **Assembly archive reuses existing value types** (`TrustReplySourceRef`, `TrustReplyItemVersion`,
   `TrustReplyDiagnostics`, `CalendarAttachmentSnapshot`, `OutboundAttachmentSnapshot`). They are
   whitelisted value DTOs (no entities/credentials/paths/bytes); no SMTP account object is stored.
5. **Meeting event stored in epoch ms** (`DelayedMeetingEventSnapshot`) and validated
   same-present-or-same-absent with `calendarAttachment` (plan T2).
6. **Historical Flyway tests pinned explicitly.** `InboundPendingOwnerIT` (V170→V172) and
   `ExpertMailReadAccessIT` (V169→V172) now pass `.target("172")` while keeping their historical
   `targetSchemaVersion`/`migrationsExecuted` assertions; the two fresh-chain expectations moved
   to `174`. No unrelated expectation renumbered.

## Commands (all freshly run in this invocation, final code state)

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `mvn -q -DskipTests test-compile` | 0 | main + test sources compile (Java 11) |
| 2 | `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | 0 | `Tests run: 49, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS) |
| 3 | `mvn test -Dtest='InboundPendingOwnerIT,ExpertMailReadAccessIT' -DmysqlIt=true` (DB_URL→disposable `mysql:8.0.36` on 127.0.0.1:33306) | 0 | `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0` (16 ExpertMailReadAccessIT + 8 InboundPendingOwnerIT) |
| 4 | temporary `DelayedManualReplySnapshotCodecScratchTest` (5 tests: 3 restore variants round-trip + byte-stable re-encode, unknown schemaVersion, calendar/event pairing, unknown/missing JSON field, >4 MiB) | 0 | `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`; file deleted before commit (no unauthorized test file delivered) |

Command 3 used a disposable container (`delayed-it-mysql`, `MYSQL_DATABASE=talent_introduction`)
removed afterwards; no production/development database was touched. Docker was required and
available.

## Changed Files (committed in d57026e)

- `src/main/resources/db/migration/V174__create_delayed_manual_reply.sql` — new table, unique
  attempt key, three indexes, attempt FK RESTRICT, five CHECK constraints (status/revision/due/
  origin pairing/SENDING token); no contact FK by design; utf8mb4_unicode_ci.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt` — six-state
  enum, row/insert/view/action DTOs, `DelayedDispatchRef`, snapshot DTO tree, restore union, strict
  codec with 4 MiB limit and fail-closed validation.
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt` —
  DB clock, single/batch/attempt/origin reads, due-id scan (id-only, LIMIT 20, due/id order),
  explicit `FOR UPDATE` locks, snapshot-derived insert, cancel/claim/complete CAS, MANDATORY guards.
- `src/main/kotlin/com/weibo/talentintroduction/config/ManualReplyDelayProperties.kt` —
  `enabled=false`, `pollMs=1000`, `unknownAfterMs=600000`, positive-value validation, same-file
  `@EnableConfigurationProperties` registration.
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`
  — fresh expectation 174 + new V173→V174 test (schema/index/FK/CHECK, Chinese subject, epoch-ms
  round-trip, unique-attempt conflict, invalid status rejection, legacy attempt/draft unchanged).
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt` — explicit
  `.target("172")` on the historical upgrade step.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` — explicit
  `.target("172")` on the historical upgrade step; fresh-chain test renamed/updated to V174.

## Deviations / skipped items

- **Codec/repository behavior tests** are owned by 02/03 per plan T4. A temporary local diagnostic
  test was used to prove the sealed-union codec round-trips and the strict/fail-closed paths, then
  deleted; nothing unauthorized is committed. Repository runtime behavior is compile-checked here
  and will get real-IT coverage in 03.
- `worktree_identity.py` failure documented above; identity captured manually.
- No production DB, no SMTP, no HTTP, no worker, no enqueue caller was added; no existing public
  API/behavior changed; `pom.xml`, `application.yml`, `CLAUDE.md` untouched.
- Never edited an applied migration; V174 was confirmed free (highest was V173).

## Freshness

- Plan identity rechecked at execution start and before report: YES (same SHA-256)
- Worktree identity rechecked before commit (`git rev-parse`/`git status`): YES
- Reported commit reachable from `fast/manual-reply-delay-master`: YES (`git merge-base --is-ancestor`)
- Required commands run this invocation after final code state: YES
- Historical evidence used only as baseline: YES (no prior execution evidence existed)

## Remaining blocker

None.

## Next action

`verify-p` on `manual-reply-delay-01-storage.md` (epoch `60d362e4…`).

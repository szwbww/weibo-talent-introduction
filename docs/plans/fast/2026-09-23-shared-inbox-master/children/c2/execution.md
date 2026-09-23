# c2 execution.md

## Execution Result: PLAN_CONFLICT

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master/docs/plans/2026-09-23/02-shared-inbox-routing.md`
- Plan SHA-256: `ab9413ee5d9bca5e4c415a0b1a97c67b00c01c30aae3312d3bac27a1944b0288` (plan identity per brief: `commit:daabfdc900555f3c89a698cd85a0165ada20d1a9`)
- Execution ID: `<worktree>/docs/plans/2026-09-23/02-shared-inbox-routing.md@ab9413ee5d9bca5e4c415a0b1a97c67b00c01c30aae3312d3bac27a1944b0288`
- Execution epoch: NEW (no prior execution evidence for this identity; `children/c2/execution.md` was the empty template)
- Approval basis: current invocation (child brief `children/c2/brief.md`, approved plan above, upper constraint `docs/plans/2026-09-23/00-shared-inbox-main.md`)
- Executor: `C2Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Target branch: `fast/2026-09-23-shared-inbox-master`
- Worktree ID: `<worktree root>@fast/2026-09-23-shared-inbox-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Child base SHA: `a15cb52b599e81753bb9fa3bcbb6e04969f44813` (c1 code head; worktree HEAD before execution was `10264ac19690823d7078215b98b0e82fefc4e27f` = c1 evidence commit, no product difference)
- Pre-execution code SHA: `10264ac19690823d7078215b98b0e82fefc4e27f`
- Post-execution code SHA: `16efaae36c01c11412b457df3e4a3f088860ce9d` (`feat(fast-p): implement c2`, 10 authorized files only)
- Evidence HEAD: `16efaae36c01c11412b457df3e4a3f088860ce9d` (single product/test commit; no separate evidence commit requested)
- Implementation boundary: `10264ac..16efaae` (product base for the child remains `a15cb52`)

## Outcome summary

All **authorized** work is implemented, committed, and green on the brief's required commands. The child is **not** READY because the plan-mandated change to `listAutoReceiveAccounts()` (owner-only semantics — "reworking exactly that method into owner-only semantics is this child's job") necessarily retires a c1-authored contract test that is **outside this child's 10-file authorization**:

- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`
  `listAutoReceiveAccounts still returns disabled and shared inbox accounts` (lines 1059-1072):
  `expected: <[owner, alias]> but was: <[owner]>`

That file is not in the brief's authorized set and the master plan M-5 caps each child at its own file list ("每阶段最多改自己清单文件，超出先修计划"). Resolving it needs a plan amendment (authorize retiring that obsolete assertion), which this executor is not authorized to make or to act on. Everything else is complete — see the blocker section for the exact, minimal amendment.

## Files changed (exactly the 10 authorized files)

| # | File | Change | Reason |
|---|---|---|---|
| 1 | `src/main/kotlin/.../mail/service/MailReceiveService.kt` | +6 | `ReceivedMail.recipientAddresses: List<String> = emptyList()` — read-only parsed original top-level `To`/`Cc`, defaulted so every existing construction stays valid. |
| 2 | `src/main/kotlin/.../mail/service/ImapMailReceiveService.kt` | +29/−2 | `EnvelopeHeaders` gains `to`/`cc` (same top-level `getHeader` path as From/Subject/Message-ID, no full-MIME prefetch); new `parseTopLevelRecipients` (`InternetAddress.parse(header, false)`, To then Cc, order preserved, display names stripped, malformed/missing ⇒ empty, never blocks receiving); wired into `convertToReceivedMail`. |
| 3 | `src/main/kotlin/.../mail/service/MailSenderAccountService.kt` | +20/−2 | `listAutoReceiveAccounts()` now returns owners only (`inbound_mailbox_code IS NULL`, `enabled` still not a filter) so every receive entry (batch, queue fan-out, bounce scheduler) polls each physical mailbox once; new `resolveInboundOwner(account)`; `getAutoReceiveAccount`/`getAutoReceiveAccountOrNull` resolve alias→owner; `getReceiveAccount` keeps the raw logical read. |
| 4 | `src/main/kotlin/.../mail/repository/InboundMailProcessingRepository.kt` | +21/−6 | Replaced the two logical-account finders with the physical-key finder `findByMailboxOwnerCodeAndUidValidityAndImapUid` and the group-wide legacy finder `findLegacyOwnerlessByGroupAndImapUid` (`mailbox_owner_code IS NULL AND sender_account_code IN (group) AND imap_uid = ?`, list-returning). Clean cutover: the old finders had exactly one caller (the dedup path being replaced). |
| 5 | `src/main/kotlin/.../mail/repository/MailRecordRepository.kt` | +16 | New read-only `findOutboundCandidatesByMessageId(messageId): List<MailRecord>` restricted to `direction='OUTBOUND'`, list-returning so callers must reject ambiguity (c3's only read dependency). |
| 6 | `src/main/kotlin/.../mail/service/AutoMailReplyService.kt` | +167/−46 | Owner resolution + physical dedup before the business transaction (+ concurrent unique-key re-check that propagates unrelated exceptions); `processSingleCore` resolves the logical recipient by I-2 routing before any business write and writes `mailboxOwnerCode` on both processing-row writers; new `RECIPIENT_UNRESOLVED` manual path; `receiveAndAutoReply`/`processByUids` use the resolved owner for fetch/`markSeen`/cursor/probe/DMARC/bounce-monitor while business attribution stays logical. |
| 7 | `src/main/kotlin/.../mail/service/BatchAutoMailReplyService.kt` | +3/−1 | Contact-scoped check maps historical logical codes to owners and `distinctBy { accountCode }` so a shared group is polled once. |
| 8 | `src/test/kotlin/.../mail/service/ImapMailReceiveServiceTest.kt` | +108/−1 | I-2 header parsing: missing To/Cc, To+Cc order/display names, case preserved, nested `message/rfc822` To never leaks, malformed header never blocks receiving (`multipartMessage` helper gains `to`/`cc`). |
| 9 | `src/test/kotlin/.../mail/service/AutoMailReplyServiceTest.kt` | +521/−? | I-1…I-4 routing/dedup/cursor/attachment coverage (list below) + mechanical updates to the renamed finders and the owner-resolution stub. |
| 10 | `src/test/kotlin/.../campaign/OperatorStatusWriteSeamGuardTest.kt` | +4/−2 | Mechanical line-number shift only: `MailRecordRepository.kt` pins `612 → 628`, `669 → 685` (+16 lines inserted at `:148`); exclusion path/context/rules unchanged, shift reason recorded in the file's existing convention. |

Nothing outside these 10 files changed (`git status --short` before commit = exactly these 10 + untracked controller-owned `children/c3/brief.md`). `docs/plans/fast/**` was not touched by the commit (the c2 report is left uncommitted, as c1 did).

## Decisions taken

1. **Routing lives in `processSingle`.** `processSingle(account, received)` accepts either the physical owner (batch/scheduler loop) or an alias logical code (controller/queue/UID backfill); it resolves the owner internally and resolves the logical recipient from the headers, so *every* non-bounce/non-DMARC message — including unresolved ones — enters the single entry point before any business write (M-3 ordering for c3).
2. **Single-member group keeps today's behavior.** `groupMembers.size <= 1 ⇒ owner`, so no existing account's routing changes; only configured shared groups parse headers.
3. **Ambiguity is `RECIPIENT_UNRESOLVED`, never a guess.** To/Cc matched to exactly one group member wins; otherwise a `MessageIdNormalizer`-normalised `In-Reply-To` must match **exactly one** `OUTBOUND` record in the group (0 or >1 ⇒ unresolved); unresolved writes exactly one `MANUAL_REVIEW/RECIPIENT_UNRESOLVED` row under the owner with `expertContactId = null`, no SMTP, no expert-state write, no contact lookup.
4. **Precedence inside the pipeline**: physical-key duplicate (before the transaction) → group legacy fingerprint claim → routing → `BODY_TRUNCATED` → `LEGACY_UID_UNVERIFIABLE` → business path. Routing precedes the contact lookup so an unresolved recipient can never reach expert state.
5. **`mailboxOwnerCode` is derived at the write seam** (`resolveInboundOwner(account).accountCode` inside the two processing-row writers) instead of threading an extra parameter through ~17 call sites: it is a pure function of the logical account (single-level relation enforced by c1) and cannot drift from the routed attribution. Independent accounts therefore also carry a non-NULL physical key, which is what makes the V134 unique key effective for them.
6. **Physical dedup before the business transaction**, per I-3, with a `DataIntegrityViolationException` re-check that returns `DUPLICATE_IMAP_UID` **only** when the exact physical key now exists and rethrows everything else.
7. **Legacy rows are matched group-wide** (`sender_account_code IN (owner + aliases) AND mailbox_owner_code IS NULL AND imap_uid = ?`), each row only claimed as duplicate when `uidValidity == 0` **and** the strict three-field fingerprint (non-empty Message-ID, From, second-level receivedAt) matches; otherwise `LEGACY_UID_UNVERIFIABLE`. No backfill of the current generation.
8. **The stale c1 test was NOT edited** (see the blocker). Its two sibling `listAutoReceiveAccounts` mocks (`:452`, `:465`) still pass unchanged because they use accounts with a NULL owner.

## Commands (all run in the worktree, JDK 11, final implementation state)

| Command | Exit | Result |
|---|---|---|
| `mvn -B -Dtest=ImapMailReceiveServiceTest,AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,OperatorStatusWriteSeamGuardTest test` | 0 (`BUILD SUCCESS`) | `Tests run: 120, Failures: 0, Errors: 0, Skipped: 0` — ImapMailReceiveServiceTest 28, AutoMailReplyServiceTest 70, BatchAutoMailReplyServiceTest 21, OperatorStatusWriteSeamGuardTest 1 (baseline for these classes: 23/54/21/1 = 99; +21 new tests, 0 regressions) |
| `node --test src/test/js/*.test.js` | 0 | `tests 1121 / pass 1121 / fail 0 / suites 222` (identical to baseline) |
| `mvn -B -q -DskipTests test-compile` (intermediate, not a required command) | 0 | all main+test sources compile |
| `mvn -B -Dtest=MailSenderAccountServiceTest test` (scoped collateral proof, not a required command) | 1 | `Tests run: 62, Failures: 1` — `listAutoReceiveAccounts still returns disabled and shared inbox accounts` `expected: <[owner, alias]> but was: <[owner]>` (`MailSenderAccountServiceTest.kt:1070`). This is the only default-suite collateral found; it is the plan-mandated behavior change and the file is not authorized. |

Intermediate runs during the work (not evidence of the final state): the first 4-class run failed all 69 Auto tests on a Mockito matcher misuse in the new `setUp` stub (`Mockito.any(Class)` returns null for a non-null Kotlin parameter); the second failed 5 assertions (routing tests asserted `recorded`, which is `false` on the `CONTACT_NOT_FOUND` branch, and two `never()` verifications used `anyValue(...)` as if it were a literal). Both were fixed and the required command above was re-run on the final state.

## New test coverage (I-1…I-4)

- I-1: direct-to-alias routing keeps `fetchInboundSince`/`markSeen`/`cursor.get`/`cursor.advance` on the owner and never touches the alias cursor; contact-scoped check with `["owner","alias"]` polls the owner once; all-accounts check with `[owner, alias, independent]` polls 2 accounts (`["owner","independent"]`), not 3.
- I-2: direct-to-alias, direct-to-owner, case-insensitive match, unique `In-Reply-To` fallback, duplicate `OUTBOUND` Message-IDs ⇒ unresolved, `To`+`Cc` both group members ⇒ unresolved, BCC (no `To`/`Cc`, no `In-Reply-To`) ⇒ exactly one `MANUAL_REVIEW/RECIPIENT_UNRESOLVED` under the owner with zero SMTP, zero expert/contact/handoff interaction and attachments registered against the processing owner; matched path records the INBOUND `mail_record` and processing row under the logical account with `mailboxOwnerCode = owner`.
- I-3: replaying `(owner, uidValidity, uid)` adds no row and still `markSeen`s; a sibling group member's legacy NULL-owner row is claimed only on the strict fingerprint (and stays `LEGACY_UID_UNVERIFIABLE` when the fingerprint differs); a unique-key violation is a duplicate only when the physical key exists, otherwise it propagates and the UID stays unconfirmed.
- I-4: UID backfill for an alias logs in as the owner, `markSeen`s the owner and never reads/advances the alias cursor; cursor continuity assertions on the owner's advance.

## Deviations from the plan

- None in the authorized files. The plan's file list and required commands were followed exactly; the only divergence is the **unresolved** collateral in an unauthorized file (blocker below), which was deliberately not touched.

## Remaining blocker

- **Smallest missing authority**: permission to retire the now-obsolete assertion
  `MailSenderAccountServiceTest#listAutoReceiveAccounts still returns disabled and shared inbox accounts`
  (`src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt:1059-1072`).
  That test pins the pre-02 semantics that plan 02 explicitly supersedes (`listAutoReceiveAccounts()` 仅返回 owner); plan 01 scoped its assertion to "不因本阶段改变" (unchanged **by stage 01**), and its own audit says the receive list keeps the old semantics "直到计划 02 按归属筛选". The file is not in c2's authorized set and M-5 caps a child at its own list, so the executor must not retire it silently. Its two sibling tests (`:452`, `:465`) remain valid and must be kept.
- **Suggested amendment** (for the controller/human): add `MailSenderAccountServiceTest.kt` to c2's authorized files (record the M-5 cap exception in the `## Amendments` row, reason: "02 makes `listAutoReceiveAccounts()` owner-only; the stage-01 assertion pinning the old list must be retired"), then re-dispatch c2 in a new epoch to delete that one test method and re-run the two required commands. With that amendment the child is `READY_FOR_VERIFICATION` with no further product work.

## Invariants check (evidence)

- `I-1`: owner-only polling list + alias→owner resolution in `MailSenderAccountService`; `receiveAndAutoReply`/`processByUids`/`processSingle` use the owner for IMAP/`markSeen`/cursor/DMARC/bounce-monitor; logical account only for business attribution; `enabled` never filters receiving; attachment sources come from the owner fetch account (`ImapMailReceiveServiceTest` stamps `source.accountCode` from the fetch account).
- `I-2`: header-only parsing (top level, no full MIME), exact case-insensitive group matching, unique OUTBOUND `In-Reply-To` fallback with a list-returning OUTBOUND-only query, unresolved ⇒ one manual row / zero sends / zero expert-state writes.
- `I-3`: physical key check before the business transaction, V134 unique key as the concurrency backstop, strict group-wide legacy fingerprint, `uid_validity=0` never backfilled, unique-key violation treated as duplicate only on the exact physical key.
- `I-4`: `markSeen` only after the transaction succeeds (or for a verified duplicate); only confirmed/duplicate UIDs enter the owner cursor; alias cursors never read or advanced; failures leave the mail unseen and the cursor unmoved.
- Cross-path (for c3): every non-bounce/non-DMARC message, including unresolved recipients, passes through `AutoMailReplyService.processSingle` before any business write; `MailRecordRepository.findOutboundCandidatesByMessageId` is the read-only, OUTBOUND-only, list-returning Message-ID candidate query.

## Freshness

- Plan identity rechecked after execution: YES (`ab9413ee…`, unchanged)
- Worktree identity rechecked before staging/commit (`--expect-root/--expect-branch/--expect-git-dir`): YES
- Reported commit reachable from the target branch as `HEAD`: YES
- Required commands run this invocation on the final state: YES
- Historical evidence used only as baseline: YES

## Next Action

- PLAN_CONFLICT → obtain the human decision / plan amendment described in "Remaining blocker"; no product change is needed beyond retiring the obsolete assertion.

---

## Epoch 2

### Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master/docs/plans/2026-09-23/02-shared-inbox-routing.md`
- Plan SHA-256: `799ebb57a356234ebd5807d7d8c58cb192aa55106aa9fc1f7a128b39c4e9808e` (amended by A1/A2; previous epoch's identity was `ab9413ee…` — "same path, new content" epoch)
- Execution epoch: 2 (resume, `fix_round = 0`, product base `a15cb52`, resumed from epoch-1 commit `16efaae36c01c11412b457df3e4a3f088860ce9d`)
- Approval basis: human-approved amendment **A1** (authorize an 11th file for c2, `MailSenderAccountServiceTest.kt`, solely to retire the superseded stage-01 assertion) plus **A2** (M-5 file-cap exception), recorded by the controller in `327bbbf` / `8de18f6` / `4a726f9`; dispatched by `Main` in this invocation.
- Target worktree / branch / Worktree ID: unchanged (`fast/2026-09-23-shared-inbox-master`, same worktree; `--expect-root/--expect-branch/--expect-git-dir` re-checked before staging).
- Pre-execution code SHA (this epoch): `4a726f9` (controller amendment/evidence commit; product tree identical to `16efaae`)
- Post-execution code SHA: `e28da464bb4bf310698079340796382e32acd2d0` (`feat(fast-p): implement c2 epoch 2`, one file, 17 deletions)

### Authorized change (exactly one file, one deletion)

| File | Change | Reason |
|---|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt` | −17 lines: deleted **only** the `@Test` method `listAutoReceiveAccounts still returns disabled and shared inbox accounts` (and its blank separator line) | A1 authorization: it pinned the pre-shared-inbox receive list (`expected [owner, alias]`) and the `findAllByAccountCodeNot("SIMULATOR_NOOP")` interaction, which epoch 1's mandated owner-only `listAutoReceiveAccounts()` supersedes. Plan 01 had scoped that assertion to "不因阶段 01 改变"; plan 02 (amended) now owns its retirement. |

Everything else in that file is untouched (`assertFalse` remains used by 4 other assertions, so no dangling import). No product code changed in this epoch. `git diff --stat` for the epoch = `1 file changed, 17 deletions(-)`.

### Commands (final epoch-2 state, JDK 11)

| Command | Exit | Result |
|---|---|---|
| `mvn -B -Dtest=ImapMailReceiveServiceTest,AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,OperatorStatusWriteSeamGuardTest,MailSenderAccountServiceTest test` | 0 (`BUILD SUCCESS`) | `Tests run: 181, Failures: 0, Errors: 0, Skipped: 0` — ImapMailReceiveServiceTest 28, AutoMailReplyServiceTest 70, BatchAutoMailReplyServiceTest 21, MailSenderAccountServiceTest **61** (was 62 with the retired assertion), OperatorStatusWriteSeamGuardTest 1 |
| `node --test src/test/js/*.test.js` | 0 | `tests 1121 / pass 1121 / fail 0 / suites 222` |

No Docker command was required or run.

### Deviations

- None. Only the one authorized test method was deleted; no other file was touched; `docs/plans/fast/**` was kept out of the commit.

### Epoch-2 freshness

- Plan identity rechecked on the amended plan: YES (`799ebb57…`)
- Worktree identity rechecked before staging/commit: YES
- Commit reachable from the target branch as `HEAD`: YES
- Required commands run this invocation on the final state: YES
- Epoch-1 blocker resolved by A1/A2: YES (no known in-scope failure remains; the earlier `MailSenderAccountServiceTest` failure is gone)

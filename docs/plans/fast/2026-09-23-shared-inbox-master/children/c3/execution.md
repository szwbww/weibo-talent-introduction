# c3 execution.md

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master/docs/plans/2026-09-23/03-shared-inbox-bounce.md`
- Plan SHA-256: `7293abc79cfae2a0756fcf27308030c7300988d101ee3401213c37b52d879cd7` (brief plan identity `commit:daabfdc900555f3c89a698cd85a0165ada20d1a9`; `git log -1 -- <plan>` = `daabfdc`, path byte-identical at handoff — identity cross-checked with the same method that reproduces c2's recorded `799ebb57a356…` for `02-shared-inbox-routing.md`)
- Execution ID: `<worktree>/docs/plans/2026-09-23/03-shared-inbox-bounce.md@7293abc79cfae2a0756fcf27308030c7300988d101ee3401213c37b52d879cd7`
- Execution epoch: NEW (no prior execution evidence for this identity; the previous `execution.md` was the empty template and the previous dispatch of this role died before any work — verified by clean `git status` at `b2cc257`/`e1fbfac`)
- Approval basis: current invocation (child brief `children/c3/brief.md`, plan above, upper constraint `docs/plans/2026-09-23/00-shared-inbox-main.md`, SHA-256 `0b7066e0ef0273aa71c348368ce75248362962c3346f9e3287a04418d5615666`)
- Executor: `C3ImplementerRetry`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Target branch: `fast/2026-09-23-shared-inbox-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master@fast/2026-09-23-shared-inbox-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master`
- Child base SHA: `e28da464bb4bf310698079340796382e32acd2d0` (c2 terminal code head; worktree HEAD before execution was `e1fbfac` = base + controller docs commits only, no product difference)
- Pre-execution code SHA: `e1fbfac203010322949a8bf8f1be5579df4165bc`
- Post-execution code SHA: `e77cb065ba6261317adc7060b2a7729052086407` (`feat(fast-p): implement c3`, 6 authorized files only)
- Evidence HEAD: `e77cb065ba6261317adc7060b2a7729052086407` (single product/test commit; no separate evidence commit requested)
- Implementation boundary: `e1fbfac..e77cb06` (product base remains `e28da46`)

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 unique-OUTBOUND attribution in `ingest` | IMPLEMENTED | `BounceCollectionService.kt`, `BounceCollectionServiceTest.kt` | `findOutboundCandidates` (normalised candidates → `findOutboundCandidatesByMessageId`, deduped by id, deterministic order) + `resolveSenderAccountCode` (unique + in-group only, else passed-in + searchable `log.warn`); 6 new attribution tests |
| I-1 failedRecipient never the sender | IMPLEMENTED | `BounceCollectionService.kt`, `BounceCollectionServiceTest.kt` | `resolveOriginalContact` keeps the failedRecipient **expert** fallback only; `ingest never uses the failed recipient expert address as the sender account` |
| I-2 source stays the physical mailbox | IMPLEMENTED | `BounceCollectionService.kt`, `BounceCollectionServiceTest.kt` | `collectBounces` still `fetchUnseenMessages(account)`; `bounce_message_id` dedup before any lookup; `collectBounces logs in as the owner and attributes the alias bounce to the alias` |
| I-2 group-wide hard-bounce check | IMPLEMENTED | `AutoMailReplyService.kt`, `AutoMailReplyServiceTest.kt` | `groupMembersOf(account).map{distinct}.forEach { checkAndWarn(it) }`; `shared mailbox check runs the hard bounce monitor for every logical group member once` (owner ×1, alias ×1) |
| I-3 same-group probe recognition | IMPLEMENTED | `SelfCheckProbeDetector.kt`, `SelfCheckProbeDetectorTest.kt` | From (`sender_email`, case-insensitive) AND full generated subject for that account; 7 new boundary tests |
| Probes intercepted at the single entry | IMPLEMENTED | `AutoMailReplyService.kt`, `AutoMailReplyServiceTest.kt` | probe check at the top of `processSingle` before routing/business writes; batch current-account-only branch deleted |
| I-4 probe is not a business write | IMPLEMENTED | `AutoMailReplyService.kt`, `AutoMailReplyServiceTest.kt` | `SELF_CHECK_IGNORED` + `recorded=false`, absent from `MANUAL_REVIEW_OUTCOMES`; `verifyNoInteractions` on processing/mail_record/intent/tag/attachment/contact/delivery |
| I-4 ack + cursor semantics | IMPLEMENTED | `AutoMailReplyService.kt`, `AutoMailReplyServiceTest.kt` | `markSeen(owner, uid)` only when `skipImapAck=false`; batch `handledUids` add keeps the owner cursor continuous (`advance("owner", 1L, [302L], {302L}, 0L)`); non-positive UIDVALIDITY throws before the filter → no ack, no handled set |
| External same-subject mail not swallowed | IMPLEMENTED | `AutoMailReplyService.kt`, `AutoMailReplyServiceTest.kt` | `external mail with a self-check subject still takes the business path` → `MANUAL_REVIEW/CONTACT_NOT_FOUND` |

## Files changed (exactly the 6 authorized files)

| # | File | Change | Reason |
|---|---|---|---|
| 1 | `src/main/kotlin/.../mail/service/BounceCollectionService.kt` | +128/−17 | Shared I-1 attribution inside `ingest`: `findOutboundCandidates(originalMessageId)` (per-variant `MailRecordRepository.findOutboundCandidatesByMessageId`, deduped by id, order = candidate variants × id ASC) and `resolveSenderAccountCode(passedIn, candidates, messageId)` which only rewrites when there is **exactly one** OUTBOUND candidate whose `sender_account_code` is non-blank and inside `groupMemberCodes(passedIn)`, otherwise keeps the passed-in code and logs `Bounce attribution unresolved: reason={NO_OUTBOUND_RECORD\|AMBIGUOUS_OUTBOUND_RECORD\|OUTBOUND_WITHOUT_SENDER_ACCOUNT\|OUTBOUND_OUTSIDE_GROUP} …`; `groupMemberCodes` derives the physical group from `MailSenderAccountService.listAccounts()` + `inbound_mailbox_code`; `resolveOriginalContact(signal, outboundCandidates)` now reads the same OUTBOUND-only candidates and keeps the `failedRecipient` expert fallback. New tail-defaulted nullable ctor param `mailSenderAccountService` (existing test files construct the service directly). |
| 2 | `src/main/kotlin/.../mail/service/AutoMailReplyService.kt` | +37/−16 | `SELF_CHECK_IGNORED` outcome added (not in `MANUAL_REVIEW_OUTCOMES`); new private `groupMembersOf(owner)` (single definition of the physical group, reused by the probe filter, the core write chain and the bounce monitor); `processSingle` computes the group once after the positive-UIDVALIDITY gate and returns `SELF_CHECK_IGNORED` (with `markSeen(owner, uid)` iff `!skipImapAck`) **before** the physical-dedup query and the business transaction; `processSingleCore` takes the pre-computed `groupMembers`; the batch `receiveAndAutoReply` current-account-only probe branch is deleted; the post-collection hard-bounce check now runs for every logical group member. |
| 3 | `src/main/kotlin/.../mail/service/SelfCheckProbeDetector.kt` | +43/−7 | Group-wide strict recognition: `isSelfCheckProbe(from, subject, groupMembers: List<MailSenderAccount>)` requires `from` (trim+lowercase) to equal some member's `sender_email` **and** the subject to be that member's complete generated form — `^\s*\[\s*self\s*-\s*check\s*\]\s*(\S+)\s+(\d+)\s*$` (IGNORE_CASE) with the code token compared verbatim to `accountCode`. Prefix-only, From-only, cross-group, wrong code, `Re:` and non-decimal tails do not match. |
| 4 | `src/test/kotlin/.../mail/service/BounceCollectionServiceTest.kt` | +306/−10 | I-1/I-2: alias/owner attribution, no candidate / multiple candidates / other-group candidate keep the passed-in account, `failedRecipient` is never the sender while still resolving the expert, backfilled known logical account preserved, dedup happens before any lookup, and an end-to-end `collectBounces` DSN (physical owner login) attributed to the alias. Two pre-existing contact-resolution tests were mechanically re-pointed from `findByMessageId` to `findOutboundCandidatesByMessageId`. |
| 5 | `src/test/kotlin/.../mail/service/AutoMailReplyServiceTest.kt` | +146 | I-2/I-3/I-4: alias probe fetched through the owner mailbox leaves zero business writes and advances the owner cursor with the probe UID, UID backfill ignores the probe, `skipImapAck=true` never acknowledges, `uidValidity=0` probe is neither recorded nor acknowledged, external same-subject mail still reaches the manual path, and the shared group runs the hard-bounce monitor once per member. |
| 6 | `src/test/kotlin/.../mail/service/SelfCheckProbeDetectorTest.kt` | +147/−? | I-3 boundary set: owner probe, alias probe via the owner's group, spaced tag, case-insensitive From, other group, other member's code, `Re:`, incomplete subject, non-decimal/extra-token tails, missing from/subject, empty group. |

Nothing outside these 6 files changed (`git status --short` before commit = exactly these 6 modified). `docs/plans/fast/**` was **not** committed (this report is left untracked, as c1/c2 did).

## Decisions taken

1. **The attribution rule lives in `BounceCollectionService.ingest`** so all three callers (physical collection, the UID polling branch, `BounceBackfillService`) share it; `collectBounces`/`receiveAndAutoReply` pass the physical owner, backfill passes its known logical code — both are only the "keep" fallback, never a source of inference.
2. **Group membership is read from the account table**, not from a new field: `listAccounts()` + `(inboundMailboxCode ?: accountCode) == groupKey`, where `groupKey` is the passed-in account's own owner (alias-aware). `+ passedIn` is always included so an exact self-match stays legal when the account row is missing.
3. **Ambiguity is never resolved by guessing**: 0 candidates, >1 candidates, a blank `sender_account_code`, or a candidate outside the group all keep the passed-in code and emit one greppable warning naming the reason and counts. Variants of the *same* normalised Message-ID that hit the *same* `mail_record.id` are deduped, so a vendor-prefixed ID is not mistaken for ambiguity.
4. **The expert association switched to the same OUTBOUND-only candidates** (single query result reused) while keeping the pre-existing `failedRecipient`→expert fallback (including the "candidate exists but contact row missing → fall back" behaviour of the old code).
5. **`mailSenderAccountService` is a tail defaulted nullable ctor param** on `BounceCollectionService` (same convention as the existing `expertOperatorStatusService`): `BounceBackfillServiceTest.kt` is **not** in the authorized set and must keep compiling; production injects the real bean. Without injection the group degenerates to `{passedIn}`, i.e. the pre-03 attribution.
6. **Probe filtering is the first check inside `processSingle`, after the positive-UIDVALIDITY `require`** — a probe with a missing/zero UIDVALIDITY therefore never gets acknowledged or added to the handled set (fail-closed, same rule as any unknown-generation mail), which is what I-4 demands.
7. **`groupMembers` is computed once in `processSingle` and threaded into `processSingleCore`** instead of being recomputed per message in the core, and the same helper is reused for the post-collection bounce monitor — one definition of "physical group" in the service.
8. **The batch probe branch was deleted, not duplicated**; probes now inherit the batch loop's normal `handledUids.add` path, so the owner cursor advances continuously without a second filter.
9. **Probe subject matching is a strict anchored regex** on the raw subject (tag spacing tolerant, code token complete, decimal tail complete) rather than the old "delete all spaces + `startsWith`" prefix test: the generated form from `DefaultSelfCheckProbeSender` is fully described, and a pure-digit extension of another account code can no longer be re-attributed.
10. **`AutoMailReplyServiceTest` kept its existing `receiveAndAutoReply discards self-check probe without persisting` test unchanged** — it now exercises the new single-entry filter and still passes; no test was weakened or re-pinned to new text.

## Commands (all run in the worktree, JDK 11, on the final implementation state)

| Command | Exit | Result |
|---|---|---|
| `mvn -B -q -DskipTests test-compile` (intermediate) | 0 | all main + test sources compile |
| `mvn -B -Dtest=BounceCollectionServiceTest,AutoMailReplyServiceTest,SelfCheckProbeDetectorTest,BatchAutoMailReplyServiceTest,ImapMailReceiveServiceTest,OperatorStatusWriteSeamGuardTest test` | 0 (`BUILD SUCCESS`) | `Tests run: 152, Failures: 0, Errors: 0, Skipped: 0` — BounceCollectionServiceTest **15** (baseline 6, +9), AutoMailReplyServiceTest **76** (baseline 70 after c2, +6), SelfCheckProbeDetectorTest **11** (baseline 4, +7), BatchAutoMailReplyServiceTest **21** (unchanged), ImapMailReceiveServiceTest **28** (unchanged), OperatorStatusWriteSeamGuardTest **1** (unchanged) |
| `node --test src/test/js/*.test.js` | 0 | `tests 1121 / pass 1121 / fail 0 / suites 222` (identical to the 1121/1121 baseline) |

No Docker command was required or run; no formatter/linter/full-suite run was performed.

Intermediate failure during the work (not evidence of the final state): the first JVM run failed exactly one of my own new tests (`SelfCheckProbeDetectorTest.sender address match is case insensitive`) because its `from` fixture used the *account code* (`OWNER@…`) instead of the owner's actual `sender_email` (`SENDER@QFTechTalent.com`); the fixture was corrected and both required commands were re-run on the final state.

### Discriminating power of the new tests

Every new test depends on the new public surface (`SELF_CHECK_IGNORED`, the 3-arg group-aware `isSelfCheckProbe`, `findOutboundCandidatesByMessageId`), so none of them can compile — let alone pass — against the c2 base; the pre-existing c2 green run is the failure-side control (`BounceCollectionServiceTest` 6 → 15, `AutoMailReplyServiceTest` 70 → 76, `SelfCheckProbeDetectorTest` 4 → 11 with zero regressions).

## Deviations from the plan

1. **`BounceCollectionService` gained a ctor dependency on `MailSenderAccountService`** (plan's 阶段 2 says the group-membership check goes into `ingest`; 阶段 3 names `MailSenderAccountService.listAccounts`/`inbound_mailbox_code` as the group source). The file is authorized; no new file, no new field, no new interface was added.
2. **Stricter subject matching than the legacy `startsWith`-after-space-stripping** (decision 9). The generated form, the `[ self - check ]` spacing tolerance and every must-not-match case in I-3 are preserved; the only behaviour intentionally dropped is acceptance of a subject with **no** whitespace between the account code and the timestamp, which the probe sender never emits and the plan's "complete generated subject" wording excludes.
3. **`resolveOriginalContact` reads the same OUTBOUND-only candidate list** as the attribution (plan 阶段 2 explicitly asks for the read change) — the two reads now share one query result and one ordering.
4. No `docs/plans/fast/**` file was touched; this report is uncommitted by design (same as c1/c2).

## Invariants check (evidence)

- `I-1`: attribution only from a unique `direction='OUTBOUND'` `mail_record` (c2's read-only, list-returning query; the direction-unrestricted `findByMessageId` is no longer called from this path — asserted with `never()`), and only when that record's account is in the passed-in physical group; all negative shapes keep the passed-in account and warn; `failedRecipient` only ever resolves an **expert**, never a sender account.
- `I-2`: `collectBounces` still logs in as the passed account for unseen mail (asserted), `bounce_message_id` dedup still precedes everything (DUPLICATE without any write or lookup), original-expert matching and the `EMAIL_INVALID` side effect are unchanged (existing tests pass), and one shared-mailbox run now checks every logical group member exactly once.
- `I-3`: recognition requires both same-group `sender_email` (case-insensitive) and that account's full generated subject; cross-group, wrong code, From-only, prefix-only, `Re:`, non-decimal and extra-token subjects are rejected — and a real external mail carrying the probe subject still reaches the business path (asserted at the pipeline level).
- `I-4`: `SELF_CHECK_IGNORED`/`recorded=false` is returned before routing, the physical-dedup query and the transaction; zero writes to `inbound_mail_processing`/`mail_record`/`inbound_intent`/`inbound_mail_tag`/`mail_attachment` (and no SMTP/contact/expert interaction) are asserted for both the batch and the UID-backfill entry; only `markSeen(owner, uid)` under `!skipImapAck`, and the batch adds the UID to the handled set only when `processSingle` returned (so a failed ack or non-positive UIDVALIDITY cannot advance the cursor).
- Frozen c1/c2 decisions untouched: no change to `listAutoReceiveAccounts()` owner-only semantics, to `processSingle`'s duplicate→legacy→routing→`BODY_TRUNCATED`→`LEGACY_UID_UNVERIFIABLE`→business precedence, to `MailRecordRepository.findOutboundCandidatesByMessageId`, or to `BatchAutoMailReplyServiceTest`/`SelfCheckProbeSender`/`SenderAccountSelfCheckService`.

## Freshness

- Plan identity rechecked after execution: YES (`7293abc7…`, plan path unmodified; brief/main hashes recorded)
- Worktree identity rechecked before staging/commit (root, branch, git-dir): YES
- Reported commit reachable from the target branch as `HEAD`: YES (`e77cb06` = `HEAD` of `fast/2026-09-23-shared-inbox-master`)
- Required commands run this invocation on the final state: YES
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`

# Repair Plan: 00-shared-inbox-main

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-09-23/00-shared-inbox-main.md` (governing commit `8de18f69a63d1683515dd00c1b46fbddbd644094`, sha256 `0b7066e0ef0273aa71c348368ce75248362962c3346f9e3287a04418d5615666`)
Verification report: aggregate epoch 2 re-review, V-1 persistent
Implementation boundary: `9237d6f573335d1624217cbc5501f68a6f52b97b..1cff8f650cfa27ca506246380e0e56a77a20a4f9` (evidence head `421cf20d6b7a83557214966865ba477a3eb8c4ba`)

## Objective

Retain the known logical account on historical bounce backfill even when a unique same-group OUTBOUND record names a different account.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | 03 I-1 and its acceptance criterion: `BounceBackfillService` must not change a known logical account | `BounceBackfillService.run` supplies `row.senderAccountCode`, but `BounceCollectionService.ingest` treats every caller as physical-mailbox intake and replaces it with a unique same-group OUTBOUND account. No caller distinction or regression covers the known-backfill case. |

## Findings Excluded

| Finding | Reason |
|---|---|
| All non-V-1 review items and RECORD_ONLY observations | No confirmed mandatory violation in this repair scope. |

## Unchanged Contract

- Keep shared-mailbox intake attribution: a physical owner may switch only to one unique same-group OUTBOUND account; no/multiple/outside-group candidates retain owner and log the existing warning.
- Preserve bounce deduplication, `bounce_message_id`, original Message-ID, expert matching, HARD-bounce effects, detector/DSN parsing, and all non-backfill callers.
- Do not change migrations, production data, IMAP collection, self-check handling, monitoring, controller/API behavior, or any file outside the authorized list.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceCollectionService.kt` | Existing phase-03 file: expose a narrow backfill-only ingestion mode that preserves a supplied known logical account while retaining the normal physical-intake path. |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillService.kt` | A3-authorized caller: mark only historical processing-row backfill as known logical-account ingestion. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceBackfillServiceTest.kt` | A3-authorized regression: prove a same-group conflicting unique OUTBOUND record cannot overwrite the stored processing-row account. |

## Repair Tasks

### R-1: Preserve known backfill attribution

- Resolves: V-1.
- Root cause: the shared ingest API cannot distinguish a physical owner from a historical row whose `sender_account_code` is already authoritative.
- Files: exactly the Authorized Files above.
- Change: add a narrowly scoped signal through the existing ingest seam so `BounceBackfillService` preserves the row's known logical account. Normal `collectBounces` and `AutoMailReplyService` callers must retain unique same-group OUTBOUND attribution. Preserve the original message ID and existing contact/deduplication processing in both modes.
- Regression test: construct a historical bounce row for one account and a single same-group OUTBOUND candidate for another; run backfill and assert the saved `BounceRecord.senderAccountCode` is the row's account while `originalMessageId` and normal ingest count remain intact.
- Existing verification: rerun `BounceCollectionServiceTest` to retain owner/alias physical-intake behavior.
- Must not change: no-candidate, multi-candidate, INBOUND-only, outside-group, dedupe, HARD-bounce and expert-contact behavior.
- Prohibited: bypassing the common ingest/deduplication path, reassigning historical processing rows, modifying production records, or broadening attribution rules.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -B -Dtest=BounceBackfillServiceTest,BounceCollectionServiceTest test`
2. `node --check src/main/resources/static/app.js`
3. `node --test src/test/js/*.test.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -B -Dtest=MailSenderAccountServiceTest,MailSenderAccountControllerMvcTest,ImapMailReceiveServiceTest,AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,OperatorStatusWriteSeamGuardTest,BounceCollectionServiceTest,SelfCheckProbeDetectorTest test`
5. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -B -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test`

## Completion Criteria

- A historical backfill row retains its known `sender_account_code` despite a contradictory unique same-group OUTBOUND candidate.
- Physical shared-mailbox ingestion still attributes a unique same-group OUTBOUND candidate and preserves existing unresolved behavior otherwise.
- The backfilled bounce retains its original Message-ID, contact-resolution path, and dedupe behavior.
- Changed files remain inside the authorized list.
- All verification commands pass freshly.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-shared-inbox-master/docs/plans/fix/00-shared-inbox-main/repair.md` invocation authorizes:

1. Only the Authorized Files and verification commands in this plan.
2. After all repair tasks and commands pass, exactly one local product commit, staging only the Authorized Files, with subject `fix(shared-inbox): preserve backfill attribution`.
3. Appending `docs/plans/review/2026-09-23-shared-inbox-master/repair-execution.md` with approval source, repair identity, pre/post code SHAs, changed files, command evidence, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record repair execution`.
5. Returning to aggregate re-review only when the approving invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.

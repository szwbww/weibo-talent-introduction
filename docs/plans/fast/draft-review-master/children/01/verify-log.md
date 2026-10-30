# Child 01 — verify-log

## Epoch 1 — Verification Not Started

No implementation commit exists and no independent verifier was dispatched. The controller's pre-implementation baseline failed; see execution.md and baseline-reports.txt. This entry is not a light-verifier verdict or a product compliance claim. Required action: PAUSE for bounded scope amendment; no later child starts.

## Epoch 2 — Verification Not Started / Mandatory Gate Blocked

Implementer `DraftReview01Implementer` returned BLOCKED at product commit `6205aaf437a0e80642518b7d35f1b230635b7f35`, child base `b15bb069f43f89c45ee6859dba04a0baff5d774d`. No independent verifier was acquired because READY_FOR_VERIFICATION was not reached.

Executor evidence: final MailReviewDraftIT 12 tests/0 failures/0 errors/0 skipped; final AuthFlowIntegrationTest 9/0/0/0. Required combined gate remains failed: FlywayMigrationIntegrationTest 46/1/0/0, line 2328 expects an index explicitly dropped by applied V167. Amended authority permits latest-version literal removal, not this substantive schema expectation change. Actual HTTP/mail-transport smoke evidence is recorded in execution.md; controller did not repeat or independently validate that product work.

Required action: PAUSE for a bounded schema-test amendment. This is not a light-verifier verdict, and no later child may start.



## Epoch 3 — Attempt 1 — 2026-10-29T22:27:41+08:00

## Light Verification: LIGHT_PASS

Child: 01 and exact plan path `docs/plans/2026-10-29/draft-review-01-workflow.md` at `87a4ba94b070afebeb83d1c6b9fe885ed66a2aaa` (SHA-256 `5259533f437110da772b8a2035c84957e6b24a3dbb0b79b1a455f7e1dd2d63fd`).
Boundary: b15bb069f43f89c45ee6859dba04a0baff5d774d..c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26
Verifier: DraftReview01Verifier

Independent four-gate inspection only. Read the authoritative Epoch3 brief and exact committed plan, aggregate product diff, relevant implementation/test context, baseline-reports.txt, and execution epochs 2/3. No product, test, index, branch, commit, plan or other evidence edits; this append is the sole repository document change by this verifier. No full review-p/verify-p/repair-p/fix-v was invoked.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-only b15bb069f43f89c45ee6859dba04a0baff5d774d c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26` (artifact://930) lists exactly the amended 11 product/test files plus planning/controller evidence documents. `git diff ... -- pom.xml src` (artifact://927) confirms those 11 implementation files; `git diff c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26 -- src pom.xml` returned no differences. Dependency change is only jsoup 1.23.2. Applied migrations and existing shared-table columns remain unchanged. V120 changes are bounded to the authorized V167 folder contract; obsolete latest-version assertions were removed without repinning, with migration/schema/data checks retained (artifact://932). |
| Plan and invariants | PASS | Direct code/test mapping below covers I-1–I-7 and T1–T4. Fresh review IT 12/0/0/0 and real-login auth suite 9/0/0/0 pass. Fresh Flyway suite 46/0/0/0 passes, including the authorized remaining gate. Historical runtime smoke was inspected separately below, not represented as verifier-performed smoke. |
| Required commands | PASS | Both mandatory commands ran freshly once after the main inspection, sequentially in the retained worktree with zulu-11, OrbStack DOCKER_HOST and API 1.41. Both BUILD SUCCESS / exit 0. Target suites are enabled and non-skipped; receipts, timestamps and comparison below. |
| Downstream interfaces | PASS | Models preserve four state names, immutable row identity/target and approval/send-binding fields, exact Detail field names without expertEmail/recipientEmail, deleted `{id,deleted:true}`, page/event/context/confirmation DTO contracts. Controller exposes the named handlers at `/api/mail/mailbox/review-drafts`, strict integral expectedVersion and local error codes. Service exposes all planned lifecycle/history/context/translation methods; repository provides typed reads, CAS and strict event schema. `reviewContext(contactId, accountCodes)` returns the complete stable SHA-256 used by detail/approve/context/confirmContext. Reader producer handlers were traced through MailReadOnlyPolicy.isReaderAllowed and AuthInterceptor.preHandle; admin-only handlers are deliberately absent from reader dispatch. Events are decoded through validatePayload, with explicit COMMENT/CONTEXT_CONFIRMED/snapshot branches and unknown-type rejection. No later-child implementation is required or claimed at this API-only checkpoint. |

#### Direct invariant evidence

- I-1: `MailReviewDraftController.kt:103-105` obtains identity only from the session. `MailReviewDraftService.kt:174-196,262-280` enforces exact roles, owner/current grant, submitted visibility and grant-before-draft locking. Repository visibility predicates enforce authorized lists. `AuthFlowIntegrationTest.kt:667-739` uses actual login sessions for every handler, roles, malformed versions and revocation; existing send/attachment denial tests remain. Review IT authorization and both grant/revoke interleavings exercise real MySQL transactions.
- I-2: Service save/submit/withdraw/delete/approve/return/comment guards implement the four-state action rules; approval has no delivery dependency. Review IT complete state/action matrix, approval/comment lock interleavings and admin post-approval comment cover locking/frozen state. Auth test verifies no outbound row added by approval.
- I-3: Repository UPDATE predicates include id/owner/version/state, increment once and reject deleted/approved mutations; service change plus snapshot execute within the same transaction. CREATE starts at 1; unchanged save returns unchanged; comments append without version/updated_at mutation. Review IT tests same-version concurrent saves, event-trigger failure rollback, snapshot history, cursor pagination and approval/comment ordering.
- I-4: V169 creates only the two review tables, with event-to-draft RESTRICT and no contact/account FK. Review IT migrates both fresh DB and V168 history, compares prior administrator draft/contact data, checks soft-deleted history and retains review events after contact deletion. No old draft-store write is introduced.
- I-5: Service canonical applies jsoup Safelist, raw and canonical UTF-8 1 MiB bounds, manual newline normalization and server-derived text before persistence/events. Submission rejects blank content, known/unknown/malformed placeholders and final validation text over 20,000 characters. Formula was directly compared with `PendingMailOperationService.kt:1417-1429`, including ordered hrefs. Review IT checks malicious and preserved markup, exact byte/length boundaries and canonical response/event equality; auth test checks integral JSON and rejects extra writable fields.
- I-6: `MailboxConversationRepository.kt:33-72` feeds version/contact and nullable UTF-8 length-prefixed fields from the existing full-body UNION in one SQL ordered by source_rank/id. No time/page/chip filter or cap is passed. `rangeUnionSql:605-668` supplies OUTBOUND plus linked processing rows; service uses the same non-simulator account-repository method as MailboxConversationService.activeAccountCodes, including disabled accounts. Review IT mutates same-timestamp messages, old bodies, FAILED-to-SENT, accounts and cleaned bodies, excludes simulator/tag/note/process-state changes, exceeds 50 messages and rejects stale approval/confirmation tokens.
- I-7: Repository resolveTarget prefers latest real processing, then latest successful OUTBOUND; no source_inbound_id reinterpretation. Service identity fields remain unchanged through CAS; first submission is preserved, resubmission clears return reason, approval populates its complete field group and stable send request ID, confirmation only appends evidence. V169 approval CHECK enforces field grouping. Review IT covers same numeric processing/mail IDs, disabled real account selection, absent/failed/simulator-only anchors, create retry/content mismatch and grouped approval fields.

#### Fresh required commands and baseline comparison

Working directory for both commands: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`.

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true -DargLine=-Dapi.version=1.41`
   - Exit 0 / BUILD SUCCESS; finished `2026-10-29T22:04:42+08:00`; Maven total 03:55.
   - Fresh Surefire MailReviewDraftIT: tests 12, failures 0, errors 0, skipped 0; elapsed 51.222 s.
   - Full command artifact://934; fresh target/surefire-reports/com.weibo.talentintroduction.mail.service.MailReviewDraftIT.txt read afterward.
2. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true -DargLine=-Dapi.version=1.41`
   - Exit 0 / BUILD SUCCESS; finished `2026-10-29T22:26:12+08:00`; Maven total 21:25.
   - Fresh Surefire AuthFlowIntegrationTest: 9 tests, 0 failures, 0 errors, 0 skipped; elapsed 35.47 s.
   - Fresh Surefire FlywayMigrationIntegrationTest: 46 tests, 0 failures, 0 errors, 0 skipped; elapsed 1,072.379 s.
   - Command artifact://935 and job summary artifact://936; both exact Surefire text reports read afterward.

Both Maven invocations also ran their bound JavaScript phase (1912 passed, 0 failed/skipped) and bound syntax checks successfully. No separate package build, standalone lint or broader test invocation is claimed.

Baseline comparison: epoch1 baseline-reports.txt records AuthFlow 8/0/8/0 and Flyway 46/33/0/0 (tests/failures/errors/skipped). Those historical failures were read, not rerun to reproduce them. Epoch2 execution records review 12/0/0/0, auth 9/0/0/0 and the one remaining Flyway V120 assertion against an index removed by V167. The freshly verified state retains those review/auth successes and resolves the remaining migration assertion under the Epoch3 amendment, with Flyway now 46/0/0/0. Historical execution results are comparison inputs only, not evidence of this verifier run.

#### Inspected runtime smoke evidence — historical, not performed by verifier

Execution.md Epoch2 records the actual JDK11 production-class HTTP/MySQL/GreenMail/IMAP/ES smoke, including two drafts, authorization matrix, stale version, submit/withdraw/return/resubmit, both comments, soft-delete history, real new inbound ingestion, stale-token 409 then approval, frozen user writes, confirmation and revocation. It records unchanged outbound count across approval/confirmation and throwaway cleanup; startup artifact://872 was also inspected. Epoch3 records fresh changed-path production startup to v169, anonymous 401 and authenticated review-list 200, plus direct MySQL physical-owner/folder/UIDVALIDITY uniqueness and duplicate ERROR1062 checks, with explicit failed-start/cleanup disclosures. The runtime smoke remains executor evidence only. This verifier did not repeat live SMTP/IMAP, HTTP/browser smoke or deployment and makes no claim otherwise.

### AUTO_FIX

N/A — no proven violation of the four gates was identified; no fix was made or requested.

### RECORD_ONLY

N/A — no outside-scope issue was pursued. Ordinary compiler warnings in unchanged files were not expanded into a review.

### Required Action

- COMPLETE_CHILD

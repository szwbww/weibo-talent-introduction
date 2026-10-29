# Child 03 — verify-log



## Epoch 1 — Attempt 1 — 2026-10-29T23:44:20+08:00

## Light Verification: LIGHT_PASS

- Child exactid: `docs/plans/2026-10-29/draft-review-03-send.md@sha256:f960f013f569b5de074722c605b41d5fa382da28c1637666b9de773c860515f7`
- Child path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-03-send.md`
- Boundary: `1cc5e9f78518468be5fb60b042cf2e7be92ccd0b..f7b24c9c7364ea64adc4fa1013083343399756d7`
- Verifier: `DraftReview03Verifier` (independent of `DraftReview03Implementer`).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`; observed HEAD/branch: `f7b24c9c7364ea64adc4fa1013083343399756d7`, `fast/draft-review-master`.
- Exact plan read fully; fresh `shasum -a 256 docs/plans/2026-10-29/draft-review-03-send.md` matched the approved identity. Historical executor command results were not substituted for fresh results below.
- Scope of verification: only the four assigned lightweight gates. No full review-p/verify-p/repair-p/fix-v, product fix, plan amendment, deployment, index change, commit, or branch mutation performed. Only this report was appended; required Maven commands generated their ordinary target outputs.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Exact eight-file product/test scope | PASS | `git diff --name-only 1cc5e9f78518468be5fb60b042cf2e7be92ccd0b..f7b24c9c7364ea64adc4fa1013083343399756d7 -- src` exactly matches the eight authorized paths listed below. `git show --format=fuller --stat f7b24c9c7364ea64adc4fa1013083343399756d7` independently shows that implementation commit has eight files, 728 insertions, 19 deletions. The full exact boundary also contains predecessor child02 evidence and controller ledger updates; these are separately authorized evidence, not additional child03 product changes. No shared-table schema or migration delta. |
| 2. I-11–I-15 and planned send invariants | PASS | Traced strict controller request → coordinator → both original send entries → final rendered payload → completed lookup/prepareAndClaim → SMTP result dispatch → transactional finalizers → detail/event projection. Explicit checks and fresh enabled integration evidence are detailed below. All 18 new SendIT cases and 27 original DraftSend cases actually ran, with zero skipped. Recorded actual HTTP/SMTP/IMAP receipts were inspected separately, not rerun or claimed as verifier-performed. |
| 3. Required fresh commands | PASS | Fresh sequential C1 exit0: 45 JVM tests, failures0/errors0/skipped0; C2 exit0: 160 JVM tests, failures0/errors0/skipped0; C3 exit0: 65 JS tests, pass65/fail0/skipped0. Both Maven executions also completed their bound JS1912 pass/fail0/skipped0 and configured syntax hooks. Exact commands and receipts below. |
| 4. Exact downstream send/detail/safety interfaces | PASS | POST `/api/mail/mailbox/review-drafts/{id}/send` handler remains named `send` and absent from the reader whitelist. Request fields exactly `expectedVersion`, optional `confirmationEventId`, `safetyWarningConfirmed=false`, optional `strongConfirmationText`; no HTTP review reference/body/account/recipient/attachment fields. Returns original PendingMailSendResult. MailReviewDraftDetail exposes `sendStatus` and nullable `sentMailRecordId`; existing context/confirmation DTO names remain intact. Both optional internal reviewSendRef parameters default null. Local ResponseStatusException handler preserves downstream statuses; original ManualSendSafetyBlockedException still reaches GlobalExceptionHandler and its unchanged findings/strong-confirmation structure. SEND_RESULT is explicitly accepted by persisted event validation and read mapping. |

#### Exact authorized product/test paths

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReviewDraftController.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt`

#### Explicit invariant / integration evidence

- **I-11 / endpoint, payload, frozen body, fixed target:** MailReviewDraftController.send strictly parses allowed fields and integral identifiers. prepareSend requires exact admin, user1 ownership, current APPROVED version, undeleted row, current owner grant and valid original target/account/contact. Coordinator routes INBOUND to fixed processing/account and OUTBOUND to fixed anchor/account/send_request_id, without QA/RAG/assembly/meeting/attachments. PendingMailOperationService compares rendered subject/final HTML/final text byte-for-byte to the frozen snapshot before claim; ManualReplySendAttemptService validates the resulting internal payload again. Canonical saves trim subject after Safelist and original normalizers; submission uses the existing placeholder parser and the complete 20,000-character validation-text preflight. Fresh tests cover nonapproved states, reader/other actor, forged fields, invalid target, lost grant, simulator, removed target, frozen MIME, rendering differences, placeholders and length. Approval and context confirmation themselves have no delivery call.
- **I-12 / current context and exact confirmation:** prepareAndClaim locks review before attempt work and reads context inside that transaction with READ_COMMITTED. validateSendContext permits the approval token or an exact persisted CONTEXT_CONFIRMED event matching draft, version, actor and current token. Stale/missing/forged/wrong-draft/wrong-version/wrong-actor credentials fail409 before attempt/SMTP; a later inbound invalidates prior confirmation. Fresh enabled cases also exercise lock contention with a conversation update committed after the earlier plain read. No global message-write hooks or version lock were introduced.
- **I-13 / claim-bind, fingerprint, completed dedup and UNKNOWN:** only non-null reviewSendRef adds length-prefixed review-draft-v1/id/version after existing identity fields; null adds no bytes. OUTBOUND keeps its frozen send_request_id short key. Claim, binding and SEND_CLAIMED event commit together under review-first lock order. Review requests cannot use the ordinary request-id early success shortcut; exact completed-payload convergence validates persisted binding and SENT record. Bound SENT returns DEDUP before context revalidation, while bound UNKNOWN/IN_PROGRESS return blocking results without new attempt or binding replacement. Existing safe-retry state reclaims the same attempt. Fresh cases exercise concurrent requests/one SMTP, completed-payload lookup, direct DEDUP, fixed OUTBOUND request/anchor, ordinary-versus-two-review identity separation, unknown replay and safe retry.
- **I-14 / finalize events and actual detail:** success and failure acquire review row before attempt access; SEND_SUCCEEDED or SEND_RESULT is written with mail_record/attempt result in the existing REQUIRES_NEW transaction. Result-event dedup is checked under the review lock. Detail derives status from bound attempt and only returns a matching SENT record id; approval state/body/history remain APPROVED and intact. Fresh event-trigger failure cases prove claim/binding rollback, success rollback followed by unknown handling, and failure-result rollback without resend. The post-send HTTP failure test confirms durable SENT detail and repeat without another SMTP. Consumer dispatch was checked in the existing ClaimResult loop, repository event validator/mapper and detail projection; no newly emitted value is silently dropped.
- **I-15 / legacy null behavior:** review-only checks are conditional on reviewSendRef; original HTTP manual-send DTOs do not expose it. Conflicting draft/review references reject422, and review sends do not bind/close administrator drafts. Fresh original DraftSend27, attempt58, pending42, trust-workbench60, named RAG-adopt/meeting/attachment JS65 and Maven-bound frontend hooks pass. Existing suppression, identity/risk confirmation, attachments, meeting, QA/RAG and ordinary free manual-send routes remain on their original pipeline; no adopted-AI historical approval gate was added.

#### Fresh required commands (verifier-performed)

Commands ran sequentially from the assigned worktree after inspection, with no product edits in flight. JDK11 is `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`; C1 runtime receipt identifies Java11.0.15 and actual MySQL8.0.36 containers/Flyway through V169. OrbStack settings were the brief-authorized environment/flag, not test skips.

```sh
# C1
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true -DargLine=-Dapi.version=1.41

# C2
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest -DargLine=-Dapi.version=1.41

# C3
node --test src/test/js/ragAdoptSendBridge.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js
```

| Command | Fresh result | Receipt |
|---|---|---|
| C1 | Exit0, BUILD SUCCESS; 45 JVM tests, failures0/errors0/skipped0: SendIT18 + DraftSend27. Finished 2026-10-29T23:35:54+08:00; elapsed4:21. | `artifact://1034`, per-class totals882/1220 and aggregate1228; actual Java/MySQL startup517/903 and512–516/898–902. |
| C2 | Exit0, BUILD SUCCESS; 160 JVM tests, failures0/errors0/skipped0: attempt58 + pending42 + trust60. Finished 2026-10-29T23:39:01+08:00; elapsed2:59. | `artifact://1036`, per-class totals481/559/566 and aggregate570. |
| C3 | Exit0; tests65, suites18, pass65, fail0, cancelled0, skipped0, todo0; duration909.456917ms. | Verifier tool result “Running fresh named frontend gate”, completed after C2, wall1.04s. |
| C1/C2 bound frontend hooks | Each: JS1912, pass1912, fail0, skipped0; node-check-app and node-check-task-modal-runtime completed. | `artifact://1034:3849-3865`, `artifact://1036:3191-3207`. These are lifecycle hook results, not a claim of a full JVM suite run. |

#### Recorded actual smoke inspection (historical executor evidence only)

Read `children/03/execution.md` and actual receipts `artifact://1015`, `artifact://1020`, `artifact://1022`, `artifact://1023`. These are implementer-performed real authenticated HTTP/loopback GreenMail SMTP/IMAP/MySQL/ES smoke, not verifier-performed requests or deliveries. Receipt1015 shows approval with empty SMTP capture, reader403, forged body422 and the original safety422 with zero attempts. Receipt1020 shows actual inbound ingestion, send409, confirm-only with unchanged capture, another inbound invalidating event14, fresh event15 then one send and same-Message-ID replay. Receipt1022 shows SMTP acceptance followed by injected success-event failure, durable DELIVERY_UNKNOWN, replay409 without more delivery, and ordinary manual reply200. Receipt1023 shows APPROVED details/history retained, three SENT review bindings, one DELIVERY_UNKNOWN binding, five attempts, four SENT manual records,51 HTTP requests and final five SMTP messages (including the unknown delivery and ordinary reply). The execution report explicitly preserves the cursor-baseline send and earlier ES preparation error; neither was treated as successful context-block evidence. No production recipient or verifier smoke claim is made.

### AUTO_FIX

N/A — no proven in-scope defect requiring a uniquely plan-determined authorized correction. No repair applied.

### RECORD_ONLY

N/A — no additional out-of-scope finding raised. Full review remains deferred; later-child UI is not part of this gate.

### Required Action

- COMPLETE_CHILD

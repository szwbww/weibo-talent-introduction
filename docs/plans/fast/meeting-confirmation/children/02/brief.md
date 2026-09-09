# fast-p child 02 brief — MIME附件、防重和邮件存档

- Master: docs/plans/2026-09-09/00-meeting-confirmation-master.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/02-meeting-confirmation-delivery.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Shared audit (part of the contract): docs/plans/2026-09-09/meeting-confirmation-audit.md; code evidence: docs/plans/2026-09-09/meeting-confirmation-evidence/
- Dependencies: 01 (terminal LIGHT_PASS). Downstream consumer: child 03 send/download.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation (branch fast/meeting-confirmation)
- Child base SHA: 73c53fe6689f14ddbab48d9f8724b651c036a469 (child 01 terminal code head)
- Execution report: docs/plans/fast/meeting-confirmation/children/02/execution.md
- Fix log: docs/plans/fast/meeting-confirmation/children/02/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. This brief adds run-specific environment; the child plan file is the authority on requirements.

## Authorized files (exactly 8; modify ONLY these)

1. src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql (NEW)
2. src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt
8. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt

## Work to implement (from child plan T1..T4 + Invariants I-1..I-4)

- T1 (I-1): V123 `ALTER TABLE mail_record ADD COLUMN calendar_attachment_json LONGTEXT NULL;` — no backfill, no JSON constraint (application 01 codec validates strictly). MailRecord gains trailing `calendarAttachmentJson: String? = null`. Flyway latest-version assertions 122→123 (10 sites per plan); add nullable-column + historical-null checks. Do NOT touch 23/24/116 target assertions or old checksums.
- T2 (I-2/I-4): ManualReplySendAttemptService.SendPayload trailing `calendarAttachment: CalendarAttachmentSnapshot? = null`; computeFingerprint: original 11 fields byte-identical when null; when non-null append the two length-prefixed segments (marker `meeting-calendar-v1` + semanticSha256) after all original fields; shortKey stays MANUAL_RICH:+32 chars. Snapshot JSON always from child-01 CalendarAttachmentCodec.serialize. All 4 mail_record branches persist `payload.calendarAttachment?.let{...serialize...}`; null explicitly clears that branch's newly written snapshot (never inherit a safe-failure record's old attachment). Do not touch QA/RAG relation saves or sourceInboundId=null.
- T3 (I-3/I-4): ComposedMail trailing default `calendarAttachment`; SMTP keeps the no-attachment branch byte-for-byte as-is; attachment branch builds javax.mail MimeMultipart/MimeBodyPart + ByteArrayDataSource over the snapshot bytes (multipart/mixed outer; part 1 wraps original alternative(plain,html) or text/plain; part 2 = snapshot.icsText UTF-8 bytes, text/calendar; charset=UTF-8, attachment disposition, safe filename) — NEVER hand-concatenate MIME strings. Message-ID, From display name, In-Reply-To/References, List-Unsubscribe, error classification stay in the same places. Calendar has no METHOD param; not text/plain/visible-body substitute; no duplicated second html/plain. Snapshot hash/64KiB validation via 01 CalendarAttachmentCodec only, no network calls.
- T4 (I-1..I-4): extend the two existing tests + Flyway test; Mockito-capture the real MimeMessage and writeTo → reparse with MimeMessage; assert multipart structure / filename / UTF-8 bytes fully equal. Retry scenarios: safe-failure→success copy branch, fresh new, DEDUP/UNKNOWN; attachment hash difference and no-calendar original golden fingerprint preserved. No public send entry — service-level tests prove independent acceptance; never connect to real expert mailboxes.
- Keep all 4 invariants incl. state machine single-path (I-4: reservation/claim before SMTP; SENT success; failure sentAt=null; UNKNOWN no resend/no fake success; DEDUP_SENT returns old record without overwriting sent snapshot).

## Environment

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (JDK 11 mandatory).
- Migration IT needs Docker: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock plus `-Dapi.version=1.40` (docker-java client vs daemon min API), and `-DmigrationIt=true` to open the gate. Container `mailbox-refinement-mysql` (mysql:8.0.36 root/root) still listens on 127.0.0.1:3306 — not used by the Testcontainers migration IT; never run tests against a real business DB. Do not start/stop/modify the container.
- Child 01 already landed: 01 CalendarAttachmentSnapshot/CalendarAttachmentCodec/ICS generator (in MeetingConfirmationModels/Service). Reuse exactly — integration tests must feed real 01 generator output through real MIME/attempt finalize (plan IP-3/4/5: no hand-written twin strings).

## Required commands (run all freshly; exact command, exit code, counts in execution.md; unverifiable → NOT_RUN, never pass)

1. Targeted: JAVA_HOME=... mvn test -Dtest=SmtpMailDeliveryServiceTest,ManualReplySendAttemptServiceTest
2. Migration IT: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=... mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40
3. Full suite: JAVA_HOME=... mvn test (fresh; baseline at seed: 3234 run/0 fail/9 skipped + node 766/0; child-01 head: 3283 run/0 fail/9 skipped + node 766/0)
4. Save the SMTP-test-produced .eml fixture to target/meeting-confirmation.eml (child plan A-1) for later human download-SHA comparison.

## Downstream interfaces child 03 will consume (must match plan exactly)

- mail_record.calendar_attachment_json column + MailRecord.calendarAttachmentJson:String? (03 reads archive for historical download; null = no calendar).
- ManualReplySendAttemptService fingerprint: calendar semantics change fingerprint; no-calendar fingerprints byte-identical to pre-02 golden (03 relies on dedup semantics).
- ComposedMail.calendarAttachment (03 passes the previewed snapshot through the existing send path); SMTP multipart delivery exists but 03 adds no new MIME code.
- All archives parse via child-01 CalendarAttachmentCodec.parseOrNull → 03 returns snapshot.icsText/download with original bytes.

## Constraints

- Only the 8-file whitelist. A compile/test proof requiring another file → STOP and report PLAN_CONFLICT (do not extend scope).
- No SMTP to real mailboxes, no public send endpoint, no new tables/columns beyond V123, no cache framework, don't modify 01 files (they are child-02 read-only deps).
- Do not modify files outside the whitelist; already-dirty files on main stay untouched.
- Commit implementation locally as: feat(fast-p): implement 02
- Exclude fast-p evidence (docs/plans/fast/**) from the implementation commit; the controller commits evidence separately.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.

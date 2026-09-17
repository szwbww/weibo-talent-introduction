# Fast-P Execution — 05-attachment-delivery

## Epoch 1 — IMPLEMENTATION

- Executor: isolated implementer `Implementer05`.
- Product base: `2540a0665cd1eff406bec460e56b75fced929cbf` (child 04 terminal code head); child 04 evidence commit precedes this implementation in Git ancestry.
- Plan: `docs/plans/fast/meeting-mail-master/children/05-attachment-delivery/brief.md` (byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-05-attachment-delivery.md`).
- Upstream interfaces inherited: child 04's snapshot codec and `resolveForSend` bounded file set in `OutboundAttachmentModels.kt` / `OutboundAttachmentService.kt`; child 02's `SendPayload` + `finalizeSuccess` calendar seam in `ManualReplySendAttemptService.kt`.
- Required commands: `mvn test -Dtest=ManualReplySendAttemptServiceTest,SmtpMailDeliveryServiceTest`; `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` (Docker-blocked on this machine, must be recorded as 未验证).
- Dispatch: fresh implementer acquired through the Required Agent Gate.

### Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fast/meeting-mail-master/children/05-attachment-delivery/brief.md`
- Plan SHA-256: `e2a5c6e4c698398c9eee8aa643d2733eae75b1b24dc21b89c124baf9c8eb56ea` (unchanged before/after execution)
- Execution ID: `…/children/05-attachment-delivery/brief.md@e2a5c6e4c698398c9eee8aa643d2733eae75b1b24dc21b89c124baf9c8eb56ea`
- Execution epoch: NEW
- Executor: `Implementer05`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`
- Target branch: `fast/meeting-mail-master`
- Worktree ID: `/Users/…-fast-meeting-mail-master@fast/meeting-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`
- Pre-execution code SHA: `2540a0665cd1eff406bec460e56b75fced929cbf` (child 04 terminal code head)
- Pre-execution HEAD (evidence-only commit on top): `d6d735f325e564990eadb24577745fefe7c1c732`
- Post-execution code SHA / Evidence HEAD: `c75693a2e9dc6cc2f5b1a90b57eb84b072e43908` (`feat(fast-p): implement 05-attachment-delivery`, 8 files, no evidence files)
- Implementation boundary: `d6d735f..c75693a`
- Commit message: `feat(fast-p): implement 05-attachment-delivery` (matches brief; local only, no push/merge/rebase/amend)

### Changed Files (exactly the 8 authorized files)

| # | Path | Change |
|---|---|---|
| 1 | `src/main/resources/db/migration/V127__add_outbound_attachments_snapshot.sql` | new — `ALTER TABLE mail_record ADD COLUMN outbound_attachments_json LONGTEXT NULL`; no backfill, no default, no index |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailRecord.kt` | `outboundAttachmentsJson: String? = null` appended |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` | `SendPayload.outboundAttachments` (default empty), `outbound-attachments-v1` fingerprint segment, all four finalize branches write this attempt's snapshot (explicit null when empty) |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | `ComposedMail.outboundAttachments: List<OutboundMailFile> = emptyList()` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt` | generic attachments in `multipart/mixed` after the original body part and after the existing ICS; `originalBodyPart(...)` extracted from the old mixed branch (byte-identical construction) |
| 6 | `src/test/kotlin/…/mail/service/ManualReplySendAttemptServiceTest.kt` | 3 fingerprint tests + 8 four-branch/absence/no-schedule persistence tests |
| 7 | `src/test/kotlin/…/mail/service/SmtpMailDeliveryServiceTest.kt` | 3 real-MIME tests (generic only, ICS+generic, plain body+generic) with `writeTo` → reparse |
| 8 | `src/test/kotlin/…/campaign/repository/FlywayMigrationIntegrationTest.kt` | 13 latest-version assertions 126→127, `fresh database migrates through V127` renamed, new `V127 adds nullable outbound attachments json column leaving history null` test; historical targets (23/24/116/121) untouched |

No other file was created, edited, deleted, staged or committed. `MailDeliveryService.send()` signature unchanged; `ComposedMail` new field defaulted empty so every existing caller compiles unchanged.

### Commands (all run freshly in this invocation, after the final implementation state)

| Command | Exit | Evidence |
|---|---|---|
| `JAVA_HOME=<zulu-11> mvn test -Dtest=ManualReplySendAttemptServiceTest,SmtpMailDeliveryServiceTest` | `0` | `Tests run: 88, Failures: 0, Errors: 0, Skipped: 0`; per-class: `ManualReplySendAttemptServiceTest` 56/0/0/0, `SmtpMailDeliveryServiceTest` 32/0/0/0; `BUILD SUCCESS` (log `/tmp/fast05-test1.log`) |
| `JAVA_HOME=<zulu-11> mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | `1` | 未验证 — Docker unavailable, see below (log `/tmp/fast05-flyway-it.log`) |
| `JAVA_HOME=<zulu-11> mvn -q compile -DskipTests` (pre-check) | `0` | main sources compile |

### 未验证 (with the exact observed error)

`mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` cannot execute on this machine: OrbStack's docker socket is absent, `DockerClientFactory.instance().isDockerAvailable` is false and `startMysql()` fails before any migration runs.

```
2026-09-17 10:36:28.551 [main] ERROR o.t.d.DockerClientProviderStrategy - Could not find a valid Docker environment. Please check configuration. Attempted configurations were:
	UnixSocketClientProviderStrategy: failed with exception InvalidConfigurationException (Could not find unix domain socket). Root cause NoSuchFileException (/var/run/docker.sock)
	DockerDesktopClientProviderStrategy: failed with exception NullPointerException (null). Root cause NullPointerException (null)As no valid configuration was found, execution cannot continue.
[ERROR] com.weibo.talentintroduction.campaign.repository.FlywayMigrationIntegrationTest  Time elapsed: 0.342 s  <<< ERROR!
java.lang.IllegalStateException: Docker is required for Flyway migration tests
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
[ERROR] BUILD FAILURE
```

`/var/run/docker.sock` is a dangling symlink to `/Users/lukai/.orbstack/run/docker.sock` (OrbStack not running); `~/.testcontainers.properties` points at the same socket. The V127 migration was therefore **not executed against MySQL**, and the migration-IT assertions are not evidence — recorded as 未验证, never as passing. Compilation of the edited migration IT is proven (kotlin `test-compile` succeeded in both command runs and the class reached surefire).

### Invariant coverage

- **I-1 (unique no-attachment representation, four write branches)** — V127 adds a single `LONGTEXT NULL` column with no backfill/default/index; `MailRecord.outboundAttachmentsJson` defaults to `null` for every other producer (`MailRecord` constructor call sites unchanged). All four branches (`finalizeSuccess` new/copy, `finalizeFailure` new/copy) write `outboundAttachmentsJson = outboundSnapshotJson`, computed once per call from `payload.outboundAttachments` with `takeIf { it.isNotEmpty() } ?: null`, so a previous attempt's snapshot is always overwritten or explicitly cleared. Tests: `finalizeSuccess new branch persists ordered attachment snapshot json`, `finalizeSuccess copy branch overwrites previous attachment snapshot with this attempt`, `finalizeSuccess copy branch without attachments clears the failed record snapshot`, `finalizeFailure new branch persists ordered attachment snapshot json`, `finalizeFailure copy branch without attachments clears the previous snapshot`, `plain send persists explicit null and never an empty array or blank string` (asserts `null`, not `[]`/`""`).
- **I-2 (send identity carries complete ordered attachment semantics)** — the pre-existing 11-segment encoding and the child-02 `meeting-calendar-v1` segment order are untouched; a non-empty list appends `outbound-attachments-v1`, the count, then per item `filename`, `contentType`, `byteLength`, `sha256`, all through the existing big-endian length-prefix writer (no delimiter-joined strings). Upload UUID, disk path and upload time never enter. Tests: `fingerprint without calendar stays byte identical to pre-02 golden` and `inbound fingerprint stays byte identical to the pinned regression values` (both unchanged, still pass; the latter pins `fullHex = fa838dbc…becda`, `shortKey = MANUAL_RICH:fa838dbceec46871ec1eae26e9ba4666`), plus new `attachment fingerprint ignores upload id and keeps original no-attachment golden` (same bytes+filename with a new upload UUID → identical full/short hash; explicit empty list → the same frozen golden), `attachment fingerprint changes with bytes filename mime and order` (four single-factor changes each change the hash), `attachment segment is independent of the calendar segment` (4 distinct hashes; calendar semanticSha256 still drives identity when attachments are held constant). Claim/concurrency semantics unchanged: `prepareAndClaim` still reserves before SMTP and the existing DEDUP_SENT / SAFE_RETRY / IN_PROGRESS / UNKNOWN / PERMANENT_FAILED tests still pass.
- **I-3 (MIME original form and archive agree)** — `ComposedMail.outboundAttachments` defaults empty; when neither ICS nor generic attachments exist the original plain/`multipart/alternative` code path is byte-for-byte the pre-05 branch (existing tests `no-calendar html and plain sends keep pre-02 single multipart shapes`, `send uses plain string content for non-html mail`, `send uses multipart alternative for html mail` all pass unchanged). When either exists the outer part is `multipart/mixed` with the original body part first, then the existing ICS, then the generic attachments in selection order; every attachment has `Disposition: ATTACHMENT`, RFC 2231 encoded UTF-8 filenames, unchanged bytes, no archive expansion, and only the existing ICS is `text/calendar`. All new tests parse a real `writeTo` → `MimeMessage` round trip.
- **I-4 (original transactions and dedup states preserved)** — claim still commits (`REQUIRES_NEW`) before SMTP; no new attempt/SENT/FAILED/UNKNOWN state; the success `mail_record` and the child-02 calendar event remain in the same finalize transaction (`payload.meetingEvent?.let { … }` untouched); original bytes are resolved before SMTP (unchanged, 04 owns `resolveForSend`). New test `attachment only send never touches the scheduling service` proves a generic-attachment-only send never calls `meetingCalendarService.createFromSentMail`. Failed sends persist metadata with `sendStatus = "FAILED"`, `sentAt = null` (asserted in the failure tests).

### Four MIME combinations actually parsed from the serialized message

Produced by `writeTo` on the captured `MimeMessage` and re-parsed with `MimeMessage(Session, InputStream)` — one from the real test run and one from a throwaway `/tmp` JShell harness against the same compiled classes (no repository file added). ICS bytes in combination 2/4 are the real child-02 generator output taken from `target/meeting-confirmation.eml` (790 bytes, `sha256=765a9f89…a202c`).

1. **0 attachments** — `text/*` body only: `multipart/alternative` → part1 `text/plain; charset=UTF-8` (11 B), part2 `text/html; charset=UTF-8` (18 B). No `multipart/mixed`, no attachment part.
2. **ICS only** — `multipart/mixed` → part1 `multipart/alternative` (`text/plain; charset=UTF-8` 24 B + `text/html; charset=UTF-8` 31 B), part2 `text/calendar; charset=UTF-8; name=meeting-2026-09-11-Professor-Basdogan.ics`, `disposition=attachment`, 790 B equal to the fixture ICS bytes.
3. **Generic only (Chinese txt + zip)** — `multipart/mixed` → part1 `multipart/alternative` (plain 18 B + html 25 B), part2 `text/plain; charset=UTF-8; name*=UTF-8''%E8%AF%B4%E6%98%8E-%E4%B8%AD%E6%96%87.txt` `disposition=attachment` 34 B (bytes equal, `sha256=85b5c9dd…5a32b`), part3 `application/zip; name*=UTF-8''%E6%9D%90%E6%96%99.zip` `disposition=attachment` 552 B (bytes equal, `sha256=9ebfe1ac…75729`, zip not expanded); both filenames decode back to the original Chinese names; `In-Reply-To`/`References`/`Message-ID` preserved.
4. **Multiple generic + ICS** — `multipart/mixed` → part1 `multipart/alternative` (plain 12 B + html 19 B), part2 the ICS (`text/calendar`, attachment, 790 B byte-equal), part3 the Chinese txt (34 B byte-equal), part4 the zip (552 B byte-equal) — ICS before generic attachments, generic attachments in selection order; exactly one `text/calendar` part.

### Deviations

- None in implementation scope. (The required migration IT could not run — the brief lists it but `-DmigrationIt=true` is Docker-gated and this machine has no Docker; recorded as 未验证 above.)
- JShell evidence harness lives at `/tmp/combo05.jsh` / `/tmp/combo05.out`, outside the worktree; nothing to clean up in the repository.

### Freshness

- Plan identity rechecked: YES (identical SHA-256 before and after)
- Worktree identity rechecked: YES (with `--expect-root/--expect-branch/--expect-git-dir` before staging and committing)
- Reported commit reachable from target branch: YES (`c75693a` is `HEAD` of `fast/meeting-mail-master`)
- Required commands run this invocation: YES (both attempted; the migration IT is environment-blocked)
- Historical evidence used only as baseline: YES

### Remaining Blocker

- None for implementation. Independent verification of the DB-level V127 behaviour needs Docker (testcontainers) or an already-migrated local MySQL; `docs/plans/**` changes stay unstaged for the controller's evidence commit.

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`


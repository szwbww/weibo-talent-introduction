## Light Verification: LIGHT_PASS
Child: 01 — docs/plans/2026-09-09/01-meeting-confirmation-preview-api.md (专用模板、时区目录与日历预览), part of docs/plans/2026-09-09/00-meeting-confirmation-master.md
Boundary: 4e3613a3b59f287b3f9efa92d6aa673293d9a83e..73c53fe6689f14ddbab48d9f8724b651c036a469
Verifier: Child01Verifier

- Epoch: 1
- Attempt: 1
- Timestamp: 2026-09-09 15:39 CST (epoch 1 attempt 1: no prior verify-log.md existed in children/01, only execution.md)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation @ fast/meeting-confirmation, HEAD = 73c53fe (implementation commit; parent 6d05499 = docs seed; src tree identical to base 4e3613a at seed). No uncommitted src changes; only untracked docs/plans/fast/meeting-confirmation/ (run artifacts).
- Brief: children/01/brief.md was not materialized in this run (children/02..05 empty; only execution.md exists). Child brief content is carried by the master plan (00) + child plan 01, which are the approved contract and were read fully together with meeting-confirmation-audit.md (D1..D7) and the evidence directory.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff --name-status 4e3613a..73c53fe`: 44 files total; 9 non-docs files, exactly the plan's 变更文件清单 (#1..#9). The other 35 are docs/plans/2026-09-09/* seeded at 6d05499 (plan/audit/evidence material; not production implementation per master plan). Added: MeetingConfirmationModels.kt, MeetingConfirmationService.kt, MeetingConfirmationController.kt, V122__seed_manual_meeting_confirmation_template.sql, MeetingConfirmationServiceTest.kt, MeetingConfirmationControllerTest.kt. Modified: ManualExpertMailService.kt (+6 lines, two type gates), ManualExpertMailServiceTest.kt (+2 tests), FlywayMigrationIntegrationTest.kt (version assertions 121→122, +2 V122 cases). Worktree HEAD clean for src/. |
| Plan and invariants | PASS | I-1..I-6 all have direct code+test evidence (details below). |
| Required commands | PASS | All three run freshly at HEAD 73c53fe with zulu-11, compared to recorded baseline at seed 6d05499 (mvn test 3234 run/0 fail/9 skipped; node 766/0): (1) targeted unit tests 76/0/0 exit 0; (2) FlywayMigrationIntegrationTest with DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock -DmigrationIt=true -Dapi.version=1.40 → 18/0/0 exit 0 BUILD SUCCESS; (3) full `mvn test` → surefire Tests run: 3283, Failures: 0, Errors: 0, Skipped: 9, node tests 766 / suites 141 / pass 766 / fail 0, BUILD SUCCESS exit 0. Delta 3283−3234 = 49 = new unit tests (Service 35 + Controller 12 + Manual 2); Flyway IT and other opt-in gates stay among the 9 skipped in plain runs, same as baseline. |
| Downstream interfaces | PASS | Child 02 (02-meeting-confirmation-delivery.md) contracts verified against implementation, below. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

---

## Detail

### Gate 1 — Authorized scope
`git diff --name-status 4e3613a..73c53fe` non-docs = exactly 9 files, one-to-one with the plan 变更文件清单:
1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt` (new)
3. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt` (new)
4. `src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql` (new)
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` (modified, +6)
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` (modified, +74)
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt` (new)
8. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt` (new)
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` (modified)

### Gate 2 — Plan requirements and invariants (direct evidence)

- **I-1 (preview read-only, real target):** MeetingConfirmationService constructor injects only read collaborators (InboundMailProcessingRepository/ExpertContactRepository/MailSenderAccountService/MailComposeTemplateService + pure MailContentService); no SMTP/send_attempt/mail_record/meeting_schedule/status/count writer exists on the object graph (ServiceTest.kt:44-60 structural note). `preview is served purely from read collaborators and touches nothing else` (ServiceTest.kt:222) runs verifyNoMoreInteractions on all collaborators incl. an assert of exactly 2 findById (preview + validateAndBuild IP-2 re-check). Mismatched processing↔contact → IllegalArgumentException (ServiceTest.kt:244); missing processing/contact → NoSuchElementException (ServiceTest.kt:257,265). Account resolution = `requestedAccountCode?.takeIf{isNotBlank()} ?: inbound` via `getManualSendAccount`, same expression as Pending (Service.kt:186-189; test `preview uses requested account when provided else inbound account like Pending` ServiceTest.kt:274). ControllerTest (WebMvcTest + real AuthWebConfig, auth enabled) asserts 401 UNAUTHORIZED for all three endpoints (ControllerTest.kt:132,143,151). NoSuchElement→404, IllegalArgumentException→400 asserted end-to-end (ControllerTest.kt:299,315).
- **I-2 (dedicated template domain):** V122 inserts template_code=mail_type=MANUAL_MEETING_CONFIRMATION, enabled=1, subject "Meeting confirmation", description with the four {{...}} tokens, only when code absent, and creates the single CUSTOM_TEXT block only for the just-inserted header (LAST_INSERT_ID + NOT EXISTS guard); never writes mail_template. options() = listEnabled() filtered to that mailType, body = CUSTOM_TEXT blocks sorted by (blockOrder, id) joined with "\n\n" (Service.kt:195-215). Two ManualExpertMailService gates: listSendOptions filter (line 40) and direct-id rejection in composeComposeTemplate before enabled-check/render/SMTP (lines 204-210). Tests: `listSendOptions excludes meeting-confirmation-only template` and `sendManualMail rejects meeting-confirmation template id before SMTP` (never send/never save/never render; ManualExpertMailServiceTest.kt:1027-1091). Flyway IT: V122 seed assertions + `V122 does not overwrite a pre-existing manual meeting template configuration` (keeps 人工自定义 header and 2 blocks, adds none).
- **I-3 (same-source time):** both ends independently resolved via `ZoneRules.getValidOffsets` (0→DST gap message, 2→DST overlap message, 1→single offset; Service.kt:330-337). Duration 1..1440 minutes enforced with fixed message. Tests: Istanbul sample `2026-09-11T07:00:00Z/07:30:00Z`, meetingTime "Friday, September 11, 2026, from 10:00 AM to 10:30 AM Türkiye Time (UTC+3)", chinaTime "2026/09/11 周五 15:00 – 2026/09/11 周五 15:30" (assertIstanbulResponse ServiceTest.kt:212-240); Kolkata UTC+5:30; Sydney cross-day full dates on both ends; London `(UTC+0 → UTC+1)`; New_York 2026-03-08T02:30 gap and 2026-11-01T01:30 overlap rejected with the exact fixed messages (ServiceTest.kt:470,486); year 1900..2100, minute precision, past dates allowed (ServiceTest.kt:552). Timezone catalog offsets computed at date 12:00 UTC (Service.kt:76-82) with tests for UTC+3/+5:30/+1/−4/+8 and 短名/Etc exclusion (ServiceTest.kt:360).
- **I-4 (no default guessing):** options default salutation = contact.expertName raw (test "Professor Basdogan"), defaultZoneId=Asia/Shanghai constant, signature assembled only from non-empty account fields (senderName/title line 1, teamName/countryName line 2; empty fields produce no fabricated "Customer Care Officer" — ServiceTest.kt:334). Date/Zoom values come only from the request; no server-side example values.
- **I-5 (restricted content/stable snapshot):** `validateTemplateSnapshot` rejects unknown/`unclosed` tokens, missing required token, `${` residue and empty body with the fixed message; template id must be an enabled special template (Service.kt:255-295; ServiceTest.kt:671,701). Salutation/signature/Zoom reject `${`/`{{`/`}}` and control chars; signature CRLF→LF. HTML = single-arg `plainTextToHtml` (never the urls variant with hard-coded Unsubscribe text) then only the escaped full Zoom URL is replaced by a same-text safe anchor; escape order (&,<,>,",') identical to MailContentService's private escapeHtml so the escaped URL matches exactly (Service.kt:442-455; MailContentService.kt:33-41). Tests: `<img src=x onerror=alert(1)>` salutation stays text in HTML (&lt;img, no <img>); zoom.us.evil.example / zoom.us@evil.example / fragments / http / non-meeting path / braces all rejected (ServiceTest.kt:568-635); query `&tk=` preserved in HTML as `&amp;tk=` and raw in ICS URL/LOCATION (ServiceTest.kt:588). Zoom host allowlist zoom.us/zoom.com/zoom.com.cn with dot boundary, path `/j/[^/]+` or `/my/[^/]+` (Service.kt:300-329).
- **I-6 (recalculable calendar):** ICS property order fixed and asserted element-for-element (ServiceTest.kt:733); CRLF line endings with trailing CRLF; folding accumulates UTF-8 bytes per code point, continuation space counts 1 byte, never splits code points (Service.kt:489-521; test with 150-char Chinese + emoji, all physical lines ≤75 bytes and roundtrip restore ServiceTest.kt:787); TEXT escaping backslash→newline→;→, and unescape-restore test (ServiceTest.kt:769); 1 VEVENT only; no METHOD/ORGANIZER/ATTENDEE; UID = first 32 hex of semanticSha256 + "@qingfei-calendar"; DTSTAMP truncated to seconds; same config → byte-identical ICS/sha256; changing only generatedAt changes sha256 but NOT semanticSha256; semantic change (processing id) changes both (ServiceTest.kt:812); filename `meeting-{start-local-YYYY-MM-DD}-{token≤60}.ics` with "expert" fallback, matches `meeting-[0-9]{4}-[0-9]{2}-[0-9]{2}-[A-Za-z0-9-]{1,60}\.ics` (ServiceTest.kt:840); ≤64KiB enforced at generation and in codec.
- **Codec (T1/02 contract):** serialize validates then JSON-encodes with fixed-field Jackson Kotlin mapper (FAIL_ON_UNKNOWN_PROPERTIES) and throws IllegalArgumentException on invalid; parseOrNull returns null for null/blank, schemaVersion≠1, unknown content type, illegal filename, >64KiB, sha256≠icsText digest, semanticSha256 not 64 lowercase hex, missing schemaVersion, unknown JSON fields, malformed JSON — never throws to the timeline (Models.kt CalendarAttachmentCodec; ServiceTest.kt:872-1021).
- **Controller JSON (IP-1/IP-2):** routes exactly as plan table: GET options (contactId required → 400, senderAccountCode optional), GET time-zones (date ISO required → 400), POST preview; controller holds no service logic beyond routing.

### Gate 3 — Required commands (fresh runs at HEAD 73c53fe, zulu-11)
| Command | Fresh result | Baseline (seed 6d05499) |
|---|---|---|
| `mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,ManualExpertMailServiceTest` | Tests run: 76, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS, exit 0 | n/a (new classes; 35+12+29 as implemented) |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40` | Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS, exit 0 (160 s; single ERROR log line is an intentional negative-path migration-failure assertion inside a passing test) | opt-in gate (skipped in plain runs) |
| `mvn test` (full) | surefire Tests run: 3283, Failures: 0, Errors: 0, Skipped: 9; node tests 766 / suites 141 / pass 766 / fail 0; BUILD SUCCESS, exit 0 | 3234 run / 0 fail / 9 skipped; node 766/0 |

Delta 3283−3234 = 49, exactly the new unit tests (35 Service + 12 Controller + 2 Manual). No pre-existing test regressed; skip count and node totals unchanged (child 01 adds no JS).

### Gate 4 — Downstream interfaces for child 02
- **CalendarAttachmentSnapshot shape** (02 I-1): data class fields schemaVersion=1, filename, contentType, icsText, sha256, semanticSha256 exactly as T1; contentType constant "text/calendar; charset=UTF-8"; sha256 = SHA-256 of icsText UTF-8 bytes; semanticSha256 = 64 lowercase hex. Matches 02's "非null只能是01规范快照JSON，schemaVersion1，UTF8解析和hash必须一致".
- **CalendarAttachmentCodec semantics** (02 T2/T3): 02 persists via `CalendarAttachmentCodec.serialize` and re-validates hash/64KiB via the codec — implementation exposes exactly the documented `serialize(snapshot): String` (throws IllegalArgumentException on invalid, no silent output) and `parseOrNull(json: String?): CalendarAttachmentSnapshot?` (null on any corruption), usable from 02 without new dependencies; no shared functions leaked into controllers.
- **Byte-stable ICS / fingerprint semantics** (02 I-2): semanticSha256 field order fixed (processingId, contactId, accountCode, normalizedRecipient, expertSalutation, zoneId, startInstant, endInstant, zoomUrl, senderSignature) with domain tag `meeting-calendar-v1` and 4-byte big-endian UTF-8 length prefixes; excludes generatedAt/template text/randomness/filename — test-proven that generatedAt changes leave semanticSha256 identical and UID/DTSTAMP deterministic, so 02's "generatedAt不同指纹相同" and change-separation acceptance hold. ICS is regenerated byte-identically for the same frozen generatedAt (no random DTSTAMP), enabling 02/03's SHA-equality across preview → MIME → archive download.
- **V122 template identity** (03/04 options consumers + audit): code/type fixed MANUAL_MEETING_CONFIRMATION, name "专家会议确认 · 英文", subject "Meeting confirmation", enabled=1, single CUSTOM_TEXT block whose text is verbatim the plan's default template with the four {{...}} tokens once each and no `${` residue; not visible in ordinary single-send; Flyway IT asserts old MEETING_CONFIRMATION untouched and no overwrite of a same-code manual config.

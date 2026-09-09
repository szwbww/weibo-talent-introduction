# fast-p child 01 brief — 专用模板、时区目录与日历预览

- Master: docs/plans/2026-09-09/00-meeting-confirmation-master.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/01-meeting-confirmation-preview-api.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Shared audit (part of the contract): docs/plans/2026-09-09/meeting-confirmation-audit.md; code evidence: docs/plans/2026-09-09/meeting-confirmation-evidence/ (incl. frozen `timezone-aliases.target.js`, `source-sha256.json`, `meeting-2026-09-11-Professor-Basdogan.ics` example)
- Dependencies: none (first child). Downstream consumers: child 02 delivery (CalendarAttachmentCodec + snapshot), children 03/04 later.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation (branch fast/meeting-confirmation)
- Child base SHA: 4e3613a3b59f287b3f9efa92d6aa673293d9a83e
- Execution report: docs/plans/fast/meeting-confirmation/children/01/execution.md
- Fix log: docs/plans/fast/meeting-confirmation/children/01/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. This brief adds run-specific environment; the child plan file is the authority on requirements.

## Authorized files (exactly 9; modify ONLY these)

1. src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationModels.kt (NEW)
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationService.kt (NEW)
3. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationController.kt (NEW)
4. src/main/resources/db/migration/V122__seed_manual_meeting_confirmation_template.sql (NEW)
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt (NEW)
8. src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt (NEW)
9. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt

## Work to implement (from child plan T1..T4 + Invariants I-1..I-6)

- T1: `MeetingConfirmationModels.kt` — MeetingInput / MeetingPreviewRequest / MeetingPreviewResponse / CalendarAttachmentSnapshot + `object CalendarAttachmentCodec` (Jackson Kotlin mapper; serialize/parseOrNull per plan; reject empty, schemaVersion!=1, wrong contentType, illegal filename regex `meeting-[0-9]{4}-[0-9]{2}-[0-9]{2}-[A-Za-z0-9-]{1,60}\.ics`, UTF8>64KiB, sha256 mismatch vs icsText bytes, non-64-hex semanticSha256; parse damage → null never 500; serialize invalid → IllegalArgumentException).
- T2: `MeetingConfirmationService.kt` + `MeetingConfirmationController.kt` — exactly the three endpoints (options GET, time-zones GET, preview POST) and fixed service signatures; read-only; processing expertContactId == contactId; account = requested else inbound account; auth via existing /api/** interceptor, no exemption. Fixed error messages verbatim from the plan (template unavailable, DST gap/overlap, past-time UI hint strings). No default guessing (empty date/zoom, zoneId default Asia/Shanghai only). Timezone catalog from ZoneId.getAvailableZoneIds minus short names/Etc/* plus UTC; frozen 常用表 first then id lexicographic; zh alias map migrated verbatim from meeting-confirmation-evidence/timezone-aliases.target.js.
- T3: single renderer (default template verbatim from plan; four {{...}} single-pass replace, no double expansion); text/html both from one Instant pair; meeting_time English Locale.US format incl. same-day/cross-day/offset-change variants; Europe/Istanbul label `Türkiye Time`; china time fixed zh-CN `2026/09/11 周五 15:00 – ...`; HTML = MailContentService.plainTextToHtml escape then replace only the escaped full zoom URL text with the safe anchor; summary digest `meeting-calendar-v1` length-prefixed fixed field order (no generatedAt/template/random/filename); UID = first 32 hex of semanticSha256 + `@qingfei-calendar`; ICS fixed property order, TEXT/URI escaping, ≤75-byte folding by UTF-8 codepoints, CRLF, single VEVENT, UTC DTSTART/DTEND, no METHOD/ORGANIZER/ATTENDEE; filename rule with example `meeting-2026-09-11-Professor-Basdogan.ics`. I-6: same config → byte-identical ICS; generatedAt truncated to seconds, frozen per draft, excluded from semantic hash.
- T4: V122 seed (code/type MANUAL_MEETING_CONFIRMATION, subject `Meeting confirmation`, description verbatim incl. {{...}} literals, enabled=1, insert-if-absent, single CUSTOM_TEXT block only when inserted); ManualExpertMailService two type gates (list filter + direct-id reject); FlywayMigrationIntegrationTest latest-version assertion → 122 + old-template-unchanged/new-template-1-header-1-block checks.
- Keep all 5 invariants: read-only preview (I-1), template domain isolation (I-2), same-source time + DST rejection (I-3), no guessing (I-4), restricted content/stable snapshot + HTML anchor rule (I-5), recomputable calendar (I-6).

## Environment

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (JDK 11 mandatory).
- Docker (OrbStack) is up: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock. For the FlywayMigrationIntegrationTest (Testcontainers docker-java) also pass `-Dapi.version=1.40` (docker-java client 1.32 vs daemon min API 1.40); record the env note in execution.md.
- Container `mailbox-refinement-mysql` (mysql:8.0.36, root/root) from a prior run still listens on 127.0.0.1:3306 (database talent_introduction). It is NOT used by this child's migration IT (Testcontainers uses its own ephemeral MySQL); do not touch it, and never run the migration IT against a real business database.

## Required commands (run all freshly; exact command, exit code, and counts in execution.md; mark unverifiable as NOT_RUN, never as pass)

1. Unit/controller/service targets: JAVA_HOME=... mvn test -Dtest=MeetingConfirmationServiceTest,MeetingConfirmationControllerTest,ManualExpertMailServiceTest
2. Migration IT: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=... mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40
3. Full suite: JAVA_HOME=... mvn test (fresh; record totals; baseline at seed commit was 766 node pass / mvn result being recorded by controller)

## Downstream interfaces child 02 will consume (must match plan exactly)

- `CalendarAttachmentSnapshot` field names/types and `CalendarAttachmentCodec.serialize/parseOrNull` semantics (I-1 of child 02: mail_record.calendar_attachment_json LONGTEXT NULL; non-null must be exactly this 01 canonical snapshot JSON, schemaVersion 1, UTF8/hash consistent; null = no calendar). Byte-stable ICS (I-6) is the archiving contract.
- V122 template (code/type MANUAL_MEETING_CONFIRMATION) must exist enabled with its CUSTOM_TEXT block for child 03 send-time re-check; generic single-send list must keep excluding it.

## Constraints

- Only the 9-file whitelist. A compile/test proof requiring another file → STOP and report PLAN_CONFLICT (do not extend scope).
- Do not run SMTP, do not add DB columns/tables beyond the V122 template seed, do not touch the old meeting-confirmation business writes, no new Clock Spring bean, no cache framework.
- 先核对该阶段源码哈希：audit evidence `source-sha256.json` matches current tree at child base (commit 4e3613a); if a listed source hash differs, stop and report PLAN_CONFLICT instead of adapting silently.
- Do not modify files outside the whitelist; already-dirty files on main are out of scope and stay untouched.
- Commit implementation locally as: feat(fast-p): implement 01
- Exclude fast-p evidence (docs/plans/fast/**) from the implementation commit; the controller commits evidence separately.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.

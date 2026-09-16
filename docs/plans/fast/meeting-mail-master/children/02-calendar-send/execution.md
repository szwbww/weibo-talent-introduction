# Fast-P Execution — 02-calendar-send

## Epoch 1 — IMPLEMENTATION

- Executor: isolated implementer `Implementer02`.
- Product base: `906f241cf685145d70c7917bb8b950f69c711835` (child 01 terminal code head); prior child 01 evidence commit `27131e3a263324495b6e82aee4a689d1bcfcc7c9` precedes this implementation in Git ancestry.
- Plan: `docs/plans/fast/meeting-mail-master/children/02-calendar-send/brief.md` (byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-02-calendar-send.md`).
- Upstream interface inherited from child 01: `MeetingCalendarService.createFromSentMail(record: MailRecord, input: MeetingCalendarInput): EventRow` annotated `@Transactional(propagation = Propagation.MANDATORY)`; `MeetingCalendarInput(startUtc: Instant, endUtc: Instant, meetingLink: String?)` in `MeetingCalendarEvent.kt`; conflict signalling via `MeetingCalendarService.MeetingCalendarConflictException`; no public mark-as-sent endpoint.
- Required commands: `mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,MeetingCalendarSendIntegrationTest`; transaction cases additionally with `-DmysqlIt=true`.
- Dispatch: fresh implementer acquired through the Required Agent Gate.

### Execution Result: READY_FOR_VERIFICATION

- Plan (canonical): `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fast/meeting-mail-master/children/02-calendar-send/brief.md`
- Plan SHA-256: `41ac89014c49c3e0f22c470b27c3a187b95be33fc3055f69b6bb5e03f1531f15` (size 9243 B; unchanged before commit and after commit — rechecked)
- Execution ID: `…/children/02-calendar-send/brief.md@41ac89014c49c3e0f22c470b27c3a187b95be33fc3055f69b6bb5e03f1531f15`
- Execution epoch: `NEW` (no prior execution evidence names this EXECUTION_ID)
- Approval basis: current invocation (child brief = approved contract)
- Executor: `Implementer02`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`
- Target branch: `fast/meeting-mail-master`
- Worktree ID: `<root>@fast/meeting-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`
- Pre-execution code SHA: `27131e3a263324495b6e82aee4a689d1bcfcc7c9` (child 01 evidence head)
- Post-execution code SHA: `f83e29c397dd01ceafb98025e0d649a69c6eafae`
- Evidence HEAD: N/A (evidence commit is the controller's; `docs/plans/**` intentionally left uncommitted in this worktree)
- Implementation boundary: `906f241cf685145d70c7917bb8b950f69c711835` (child 01 code base) .. `f83e29c397dd01ceafb98025e0d649a69c6eafae`; one local commit, 5 files, +956/−8.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 单次校验产物贯穿全链（attachment + meetingEvent 同源） | IMPLEMENTED | `PendingMailOperationService.kt`, `ManualReplySendAttemptService.kt` | `executeManualRichSend` keeps the single `validateAndBuild` result (`rebuiltMeeting`) and derives both `calendarSnapshot` and `MeetingCalendarInput` from it; `SendPayload.meetingEvent` (default null) carries it. Unit: `meeting send carries the one real snapshot into payload and composed mail with thread headers` asserts `startUtc/endUtc/zoomUrl` equal the preview values; IT: calendar row `starts_at_utc/ends_at_utc/meeting_link` equal the preview UTC values. |
| I-2 成功落库原子生成（REQUIRES_NEW 内、SENT record 之后、attempt 标 SENT 之前） | IMPLEMENTED | `ManualReplySendAttemptService.kt` | `finalizeSuccess` calls `meetingCalendarService.createFromSentMail(savedRecord, event)` after `mailRecordRepository.save` + QA rows and before `updateStatusAndError(SENT)`; `finalizeFailure` never calls it. Unit: InOrder(save → createFromSentMail → updateStatusAndError), `finalizeFailure never creates a schedule…`, `finalizeSuccess propagates a schedule write failure…`; IT: rollback case (0 SENT record / 0 排期 / attempt `DELIVERY_UNKNOWN`). |
| I-3 未知发送不得补发；DEDUP/改期/取消不覆盖 | IMPLEMENTED | `PendingMailOperationService.kt`, `ManualReplySendAttemptService.kt` | Failure/unknown paths unchanged (409「发送状态未知，请勿重复发送」, no second SMTP, no fabricated schedule); DEDUP_SENT returns the existing result without touching the calendar. Unit: `meeting smtp safe failure never creates a schedule and stays a safe retry`, `meeting dedup sent and unknown claims never resend smtp or recreate the schedule`; IT: `resending a completed request never sends again and keeps later edits and cancel` (SMTP total 1; 改期 14:00 保持; CANCELLED 保持; source mail row bytes unchanged), `a schedule write failure … keeps delivery unknown` (UNKNOWN retry does not re-SMTP). |
| I-4 普通邮件兼容（指纹/线程/账号/QA/审计不变） | IMPLEMENTED | `ManualReplySendAttemptService.kt`, `PendingMailOperationService.kt` | `meetingEvent` never enters `computeFingerprint`; no-meeting paths pass `meetingEvent = null` (byte-identical identity). Golden assertion `fa838dbceec46871ec1eae26e9ba4666d6f1ac0fda1bd36f872110efd38becda` unchanged and passing; IT: ordinary rich reply creates 0 排期 and keeps `calendar_attachment_json` NULL. |
| 变更文件清单（5 files，无越界） | IMPLEMENTED | see Changed Files | `git show --stat f83e29c` = exactly the 5 authorized files. |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,MeetingCalendarSendIntegrationTest -DskipNodeTests=true` | PASS | exit 0; `Tests run: 75, Failures: 0, Errors: 0, Skipped: 1` (Pending 29, ManualReply 45, IT class reported skipped because it is `-DmysqlIt`-gated); BUILD SUCCESS |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,MeetingCalendarSendIntegrationTest -DskipNodeTests=true -DmysqlIt=true` | PASS | exit 0; `Tests run: 81, Failures: 0, Errors: 0, Skipped: 0` (Pending 29 / ManualReply 45 / MeetingCalendarSendIntegrationTest 7); BUILD SUCCESS; MySQL `127.0.0.1:3306/talent_introduction`, Flyway to V125 |

Both required commands were run freshly after the final implementation state and before the commit. `-DskipNodeTests=true` only disables the unrelated `exec-maven-plugin` node checks (frontend untouched); it does not affect the brief's Java/Kotlin commands.

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` — keep the single `validateAndBuild` result (`rebuiltMeeting`), derive `calendarSnapshot` + `MeetingCalendarInput(startUtc, endUtc, zoomUrl)` from it, pass `meetingEvent` in `SendPayload`.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` — inject required `MeetingCalendarService`; add `SendPayload.meetingEvent` (default null, excluded from the fingerprint); create the schedule inside `finalizeSuccess`'s `REQUIRES_NEW` transaction between the SENT `mail_record` save and the attempt `SENT` update.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` — assert `meetingEvent` equals the preview UTC/link, null on the old path, no schedule creation on safe-failure/dedup/unknown claims, SMTP totals.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` — constructor dependency updated; new tests for the success-transaction creation order, `finalizeFailure` never creating, no-calendar path untouched, and propagation of a schedule write failure; golden hashes preserved byte-identically.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt` (new) — real-MySQL (`mysqlIt`) integration: preview read-only, success creates exactly one row bound to the real SENT record, injected schedule-write failure rolls back the whole success transaction and keeps UNKNOWN without resend, SMTP failure creates nothing, resend/改期/取消 convergence, claim reuse.

### Deviations

- None in product scope. Test-infrastructure notes only: the injected failure toggle is a test-local subclass (`ToggleableMeetingCalendarService`) registered as the only `MeetingCalendarService` bean in the `@DataJdbcTest` slice; it delegates to the real implementation and injects `IllegalStateException` only when the toggle is on. All transaction boundaries (REQUIRES_NEW / MANDATORY) are the production Spring proxies — no transaction is mocked.
- Nothing outside the 5 authorized files was touched. `docs/plans/**` (execution/ledger) remains modified-but-uncommitted for the controller's evidence commit.

### Freshness

- Plan identity rechecked: YES (`41ac8901…` before and after commit)
- Worktree identity rechecked: YES (root/branch/git-dir match `--expect-*`)
- Reported commit reachable from target branch: YES (`f83e29c` is HEAD of `fast/meeting-mail-master`, parent `27131e3`)
- Required commands run this invocation: YES (both, on the final state)
- Historical evidence used only as baseline: YES

### Invariant / acceptance coverage

- I-1: one `validateAndBuild` per send produces both artifacts; no second ICS generation, no re-read of the current time, no browser-supplied schedule object (`MeetingCalendarInput` is built only from `rebuilt.startUtc/endUtc/meeting.zoomUrl`); forged/stale `previewAttachmentSha256` still fails 400 before any claim (pre-existing test `changed meeting config after preview is rejected 400 config changed`, unchanged and passing).
- I-2: preview only, save-draft, SMTP safe failure, SMTP unknown ⇒ 0 new schedules (unit + IT); SENT ⇒ exactly 1; injected calendar-write exception ⇒ whole success transaction rolls back (0 SENT record, 0 排期, attempt `DELIVERY_UNKNOWN`), and the upper layer keeps the existing UNKNOWN contract.
- I-3: resend of a completed request keeps SMTP total = 1; manual reschedule (14:00 Beijing) and cancellation are preserved; UNKNOWN retry never re-SMTPs and never fabricates a schedule.
- I-4: golden `fullHex` (and `MANUAL_RICH:` short key) assertions byte-identical; ordinary rich reply creates 0 schedules and keeps no calendar snapshot; thread headers/account/QA/RAG/audit code paths untouched.

### 未验证 / not executable here

- 人工验收清单 A-1/A-2/A-3 (sandbox SMTP + real calendar UI + A-3 requires child 03 UI): not executed — requires a human-described test environment (SMTP sandbox, UI). Automated equivalents above cover I-1…I-4; the ICS `DTSTART`/one-mail assertions are exercised at the `ComposedMail`/DB level, not through a real mail client.
- Docker/testcontainers: not required by this brief. The mandated MySQL integration path used the local `127.0.0.1:3306/talent_introduction` instance and succeeded (no Docker attempt was needed).

### Remaining Blocker

- None.

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`.

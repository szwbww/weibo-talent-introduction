# Machine Verification — docs/plans/2026-10-29/expert-access-master.md

## Epoch 1 — 2026-10-30T23:24:44.872Z

- Master plan: docs/plans/2026-10-29/expert-access-master.md (commit 04ba02e9f971cdebe26d8f37dd4de13a771eae93)
- Governing master identity: sha256 c1ca47e495a4f91959e3b467bd8f7b755b5003d7eb22d30308a7916df70e0638; recorded commit 04ba02e9f971cdebe26d8f37dd4de13a771eae93
- Invoked master identity: sha256 9143669bb109f81699ef7d4fa553ac4a19217347778c47c6b6da8625f1e0deb9
- Master identity state: AMENDMENT_RECORDED; A1–A44 carried forward in ledger and reviewer addendum, latest master amendment A43 (I-9; S-2; A-14; preserve literal/shared mobile CSS), human approval “授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p”
- Boundary: 59eb60cf00534a342c1019827a62f2b7b1db0f2b..39f38a6676c4d4d0fdb7cb679326834b4a17e97f
- Evidence parent: 39df449ac09e3f7385097f4507f2a0d9c78cb8f3
- Reviewer: ExpertAccessAggregateReviewer (fresh aggregate reviewer; no writer/light-verifier context)
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A; repair-p not invoked
- Manual status: PENDING; A-1–A-15 not accepted by machine evidence
- Complete returned report and follow-up addendum are preserved below. Fast-P RECORD_ONLY mapping is in the report; individual amended-file rows and exact enabled commands are in the addendum. Raw review receipts are in history://ExpertAccessAggregateReviewer; the addendum’s “controller transcript” refers to the reviewer tool transcript, not a new controller execution. Ignored target/surefire-reports files are retained local receipts, not committed immutable evidence.

# Aggregate / Master Review (review-fast-p → review-p → verify-p)

## Identity and boundary

- Repository: `/Users/lukai/IdeaProjects/weibo-talent-introduction`
- Retained worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`
- Branch: `fast/expert-access-master`
- Implementation boundary: `59eb60cf00534a342c1019827a62f2b7b1db0f2b..39f38a6676c4d4d0fdb7cb679326834b4a17e97f`
- Final code head: `39f38a6676c4d4d0fdb7cb679326834b4a17e97f`; current evidence HEAD: `39df449ac09e3f7385097f4507f2a0d9c78cb8f3`
- Governing master: `docs/plans/2026-10-29/expert-access-master.md` commit `04ba02e9f971cdebe26d8f37dd4de13a771eae93`, SHA256 `c1ca47e495a4f91959e3b467bd8f7b755b5003d7eb22d30308a7916df70e0638`
- Invoked master copy SHA256: `9143669bb109f81699ef7d4fa553ac4a19217347778c47c6b6da8625f1e0deb9`
- Master identity state: `AMENDMENT_RECORDED`; governing amendment A43 (`I-9; S-2; A-14; preserve literal/shared mobile CSS`; desktop 180×34 / mobile 308×44, unchanged styles blob, no product CSS/scope change; human approval recorded after the expert-access preflight blocker on 2026-10-29).
- Fast-p ledger SHA256 `70058ae9c5fb5966d2e0d4460f374d8035da7e8a19572c440d4c51c0779dd26b`; handoff SHA256 `2bebf66bbbdc010a26631431b1499383df777a164c22cbacaadb2eccee6a0830`.
- Worktree resolution: `DISCOVERED_FROM_GIT_WORKTREES`; discovery evidence `artifact://3693`.
- Product/test tree was clean before and after review; only controller-owned untracked `docs/plans/review/expert-access-master/` exists. No product, test, index, plan, ledger, brief, staging, or commit mutation by this reviewer.
- Combined diff: 168 paths — 97 product/test paths (32 `src/main`, 65 `src/test`) and 71 controller-owned plan/evidence paths. Per-child product/test scope: review05 11/13, 01 3/3, 02 10/10, 03 41/41, 04a 11/11, 04b 15/16, 05 12/12, 06 18/18, 07 15/15, 08 10/10; every changed file is in its child's authorized list and no unauthorized product/test path exists. Authorized-but-unchanged: review05 `app.js`/`batchEntryRelocation.test.js` (final-UI override/was not needed) and 04b `MailReviewDraftService.kt` (changed by review05); no feature reduction evidenced.
- Migrations: only new `V170__add_expert_mail_access_level.sql` and `V171__add_inbound_pending_owner.sql`; no edit to an applied migration; `application.yml`/deployment scripts unchanged.

## Verification Result: PASS

Plan: `docs/plans/2026-10-29/expert-access-master.md`
Implementation boundary: `59eb60cf00534a342c1019827a62f2b7b1db0f2b..39f38a6676c4d4d0fdb7cb679326834b4a17e97f`
Convergence: `INITIAL` (first aggregate epoch; no prior aggregate findings)
Manual acceptance: `PENDING` (all A-1–A-15 remain human items; none marked passed)

### Commands (fresh, exact boundary)

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/*.test.js` | PASS | exit0; 1985 tests / 353 suites; pass 1985, fail 0, cancelled 0, skipped 0, todo 0; duration 4.04 s |
| `node --check src/main/resources/static/{app,mailbox-chat,mailbox-readonly,contact-details}.js` | PASS | all four exit0 |
| `mvn test -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1` (JDK11.0.33, DOCKER_HOST OrbStack) | PASS | exit0/BUILD SUCCESS 21:26; JVM 5727 tests, 0 failures, 0 errors, 24 default conditional opt-in skips; integrated Node 1985, 0 fail/0 skip. The 24 default skips are not represented as enabled IT coverage. |
| Exact named matrix: 16 classes incl. AuthInterceptorTest, AuthFlowIntegrationTest, ExpertMailPermissionGuardTest, ExpertMailReadAccessIT, InboundPendingOwnerIT, MailboxConversationRepositoryIT, MailboxSuspensionServiceIT, MailReviewDraftIT, MailReviewQueryIT, MailReplyDraftRepositoryIT, MeetingCalendarControllerTest, MeetingCalendarServiceTest, ExpertManualProfileServiceTest, ExpertManualProfileControllerTest, MailReplyDraftSendIntegrationTest, FlywayMigrationIntegrationTest; `-DmysqlIt=true -DmigrationIt=true` | PASS | exit0/BUILD SUCCESS 13:46; 260 tests, 0 failures, 0 errors, 0 skips; FlywayMigrationIntegrationTest 46 tests; disposable MySQL 8.0.36, JDBC `serverTimezone=Asia/Shanghai` |
| Pristine calendar command: `MeetingCalendarControllerMysqlTest -DmysqlIt=true -DmigrationIt=true` on separate schema | PASS | exit0/BUILD SUCCESS 15.7 s; 3 tests, 0 failures/errors/skips; 170 migrations through V171 |
| Additional real-MVC scoped IT: `ExpertScopedOperationsIT -DmysqlIt=true` (own Testcontainers MySQL) | PASS | exit0/BUILD SUCCESS 30.6 s; 11 tests, 0 failures/errors/skips |
| Production runtime: app from `target/classes` + `mvn dependency:build-classpath -DincludeScope=test` output only; disposable MySQL, loopback ES/SMTP/translation; real Auth/JDBC/browser | PASS | App started after 170 migrations through V171; admin/user1 real logins; genuine Chromium 1440×1000 and 390×844; SMTP DATA received; cleanup completed |

Execution note: the first named-matrix launch was accidentally concurrent with the calendar surefire process and failed before running tests with `Error creating properties files for forking` (`No such file or directory`). The exact named matrix was rerun alone and passed; the calendar command had already passed separately. This is command orchestration, not a product result; no known product failure was rerun merely to confirm it.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 | PASS | V170 adds `access_level VARCHAR(8) NOT NULL DEFAULT 'READ'`; `ExpertMailAccessLevel`/`setAccessLevel`/`revoke`; bodyless PUT keeps FULL, explicit READ/FULL switches, invalid `ADMIN` 400; live responses 200/200/400; `ExpertMailReadAccessIT` 16 tests and `MailReviewDraftIT`/MVC matrix pass. |
| I-2 | PASS | `AuthInterceptor` exact HandlerMethod+verb gate + `ExpertMailPermissionGuard` real-resource FULL; `AuthInterceptorTest` 19 exact-handler tests; live user1 denial matrix: C messages 403, B READ write 403, global `/api/inbound-summary/mails`, `/api/expert-contacts`, `/api/mail/unmatched-inbound`, `/api/mail/mailbox`, `/api/experts/search` all 403; `ExpertScopedOperationsIT` 11 real-MVC tests. |
| I-3 | PASS | Reader list/header/mail tags rendered with `textContent`; live A tags `新发现` and literal `Long safe <tag>`; B empty array produced no placeholder; admin C null rendered no fake tags; READ write endpoints 403; `ExpertScopedOperationsIT`, `ExpertMailPermissionGuardTest`, and full Node suite. |
| I-4 | PASS | DB-resolved processing/mailRecord/attachment/document/schedule/draft ownership; live foreign C 403, B READ calendar/write 403, mixed calendar summaries 403, meeting-confirmation wrong contact 403; `ExpertMailPermissionGuardTest` 7 tests including three attachment owners and forged paths. |
| I-5 | PASS | V171 default admin; both actual AutoMailReply sinks allocate after contact lock; live owners 5001/5002 changed only through downgrade/reclaim, duplicate/bind behavior covered by `InboundPendingOwnerIT` 8 tests and full matrix. |
| I-6 | PASS | Downgrade/revoke lock contact and reclaim unfinished `MANUAL_REVIEW` user1 rows; live FUL→READ moved 5001/5002 to admin and admin badge 3→5; upgrade FULL did not steal them; re-open CASE falls back; `ExpertMailReadAccessIT` downgrade/reclaim test passes. |
| I-7 | PASS | Owner-scoped count/page/explain; live admin badge 3 (manualReviewTotal) and user1 2 mails/1 expert, pendingOnly total 1; admin `filterCounts` 1 FULL expert/2 submitted drafts; badge stayed 3 under seven original filters; `MailboxConversationRepositoryIT` 41 and `MailboxSuspensionServiceIT` 11 pass. |
| I-8 | PASS | Service-entry `requireFull` precedes send; live user1 FULL manual send produced exactly one SMTP DATA (678 bytes), `mail_record` 6003 OUTBOUND/MANUAL_RICH_REPLY/SENT, audit `SEND_MANUAL_RICH_REPLY` actor `user1`; READ send prerequisites denied; `MailReplyDraftSendIntegrationTest` 27 and `ExpertScopedOperationsIT` pass. |
| I-9 | PASS | Live admin popover 430 px with scope first, eight fields total, exactly one Apply/one Reset/one close; applied eight conditions (scope + seven originals) produced one SQL-backed A result and “8 项筛选已生效”; Reset returned ALL; access select 180×34 desktop, failure 503 restored confirmed FULL with inline error and zero send. |
| I-10 | PASS | Live user1 FULL delegated shell: one editor, no email/account identity, no admin access select, send present; 390 px scrollWidth 390; only mailbox/logout nav visible; switching to READ B produced no delegated shell/editor/send. Lifecycle/late-response and minimal-host tests are in the full Node suite and `ExpertScopedOperationsIT`; no product code changed. |
| I-11 | PASS | Live READ old APPROVED draft contentEditable false, no send; FULL upgrade showed old APPROVED in delegated history read-only, no send/approve; separate manual editor remained sendable; `MailReviewDraftIT` 12, `ExpertScopedOperationsIT` four-state/immutability test, `mailReviewAdmin.test.js` pass. |
| I-12 | PASS | Four copy-saves replaced by explicit narrow UPDATEs; `InboundPendingOwnerIT` 8 real-JDBC concurrency tests; `OperatorStatusWriteSeamGuardTest` and full matrix pass; no stale full-entity save remains. |
| I-13 | PASS | Admin All shows all three conversations; user1 only A/B grants; global routes 403; automatic/background flows unaffected (scheduling/mail-queue disabled in runtime); `ExpertScopedOperationsIT` and `MailboxSuspensionServiceIT` pass. |
| I-14 | PASS | Per-child scope counts within authorized limits (above); V170/V171 are the only new columns; no application.yml/deployment edits; all mandatory commands fresh; 24 default skips explicitly not counted as enabled evidence. |
| S-1 | PASS | Reader DOM uses declared classes and `textContent`; live malicious label escaped; empty array no placeholder; null profile admin marker observed; JS/style and scoped IT pass. |
| S-2 | PASS | CSS block matches plan verbatim; live desktop 180×34/max180, mobile 308×44/max100%, no overflow; failure rollback; list FULL badge; no large permission card. |
| S-3 | PASS | CSS matches plan verbatim; popover 430 px, badge 18 px, eight fields, one Apply, no obsolete filter-count badge/top review button; counts from server; 0/unavailable/99+ semantics covered by Node tests; live badge unaffected by filters. |
| S-4 | PASS | Delegated shell CSS/DOM reused; one editor; profile/tag/calendar/material host paths; no email/account line, video nav/panel, or global task nav; mobile containment; Node + scoped IT pass. |
| S-5 | PASS | Reader chips `reader-all`/`reader-pending`; server pending expert count 1, list page reset; card mail count 2; failure shows “不可用”; Node/full browser evidence. |
| Acceptance I-1–I-14 | PASS (machine) | Commands and live receipts above; no confirmed mandatory failure. |
| Acceptance S-1–S-5 | PASS (machine) | Verbatim CSS/DOM, browser 1440/390, disabled/failure states; no new inline style or undeclared class. |
| Manual A-1–A-15 | PENDING | Human acceptance checklist remains unrun by this reviewer; no item marked passed. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| None prior (initial aggregate epoch) | N/A | No previous aggregate report or repair lineage. |
| No new P1/P2 findings | N/A | All mandatory commands and runtime paths passed; observations below are pre-existing or evidence limits, not patch-introduced mandatory defects. |

### Findings

#### P1
- None confirmed.

#### P2
- None confirmed. The wrong-verb/unmapped-method 500 and foreign-anchor 500 observations are pre-existing global error mappings with zero side effects; they are recorded as observations, not new patch defects. Raising `MIN_SAMPLE_SIZE`/error-status style preferences was not treated as a requirement.

#### Observations (non-blocking, evidenced)
- Historical broad all-opt-in run 5963/3 failures/24 errors/11 skips remains recorded in child 07/08 baseline evidence and was not rerun wholesale. Its causes include unstubbed fixture cursors, a global thread-name assertion, Flyway placeholder fixture config, and duplicate bean definitions. These are outside this master’s mandatory default/required-named gates and are not evidence that the expert-access contract failed.
- Headless Chromium cannot prove a native OS background/foreground transition; real page focus/visibility handlers and production requests were exercised, but no OS transition is claimed (also O-07-1/O-08V5-1).
- Default `mvn test` has 24 opt-in skips; they are not counted as enabled integration coverage. The required enabled named matrix and calendar command had zero skips.
- Review05’s `batchEntryRelocation.test.js` and `app.js` were authorized but unchanged because the final UI override removed the obsolete top review button; 04b’s `MailReviewDraftService.kt` was changed by review05. No feature removal follows.
- The first named-matrix invocation was inadvertently concurrent with the calendar surefire process and failed before tests due surefire temp-file creation; the isolated rerun passed. This is execution orchestration, not a product defect.

## RECORD_ONLY Re-evaluation

| Record | Master mapping | Disposition |
|---|---|---|
| review05/O-1 | I-14/evidence | Fixture-only timezone correction; not product, not a mandatory violation. |
| 02/O-1 | I-2 | Wrong-verb unmapped request returned 500; actual mapped PUT was 403 and no side effect. Global method-error mapping is pre-existing and approved as closure; no authorization bypass. Observation. |
| 03/O-03-001 | I-4/A-5 | Foreign anchor mismatch returned 500/`CONVERSATION_SENT_ANCHOR_NOT_FOUND` instead of intended 422; mismatch denied with zero SMTP/DB/file effects. Pre-existing error-status behavior; not a patch-introduced mandatory violation. Observation. |
| 07/O-07-1 | I-10/S-4 | Native OS background unavailable headless; real handlers/disposal exercised. Evidence limitation, not a product defect. |
| 08/O-08V5-1 | I-10/S-4 | Same native-OS limitation; handlers exercised; no OS transition claimed. Evidence limitation. |
| 08/O-08V5-2 | I-14/evidence | Historical broad all-opt-in failures retained; exact unrestricted defaults and required named matrix passed independently. Not a mandatory in-scope product finding; not rerun wholesale. |

## Evidence Boundaries

- Manual acceptance A-1–A-15 is `PENDING`; machine PASS is not human acceptance.
- Native OS backgrounding cannot be established in headless Chromium; real DOM/page lifecycle handlers were exercised instead.
- Historical broad all-opt-in failures remain baseline evidence and were not reconfirmed or repaired; they are outside the exact required gates.
- Internal review-draft translation/approval live browser path was not re-run end-to-end in this aggregate epoch; real-MVC `ExpertScopedOperationsIT`, `MailReviewDraftIT`, full Node suite, and child 08 direct browser evidence cover those paths. No mandatory evidence was waived.

## review-p orchestration

Verification result: `PASS`; convergence `INITIAL`.
Repair planning: `N/A` (no eligible confirmed failure; `repair-p` not invoked).
Repair artifact: `N/A`.

## Final routing

Machine result: `PASS` → `AWAITING_HUMAN_ACCEPTANCE`.
One allowed next action: controller persists this report and generates `docs/plans/review/expert-access-master/manual-acceptance.md`; a human must execute A-1–A-15 and sign off the exact reviewed boundary `59eb60cf00534a342c1019827a62f2b7b1db0f2b..39f38a6676c4d4d0fdb7cb679326834b4a17e97f` and governing master identity `04ba02e9f971cdebe26d8f37dd4de13a771eae93` / SHA256 `c1ca47e495a4f91959e3b467bd8f7b755b5003d7eb22d30308a7916df70e0638`. Do not treat machine PASS as acceptance, merge, push, or deploy authorization.

No product code was modified. No review evidence, ledger, plan, product, tests, index, staging, or commit was written by this reviewer. All owned app/browser/ES/SMTP/translation services and the disposable MySQL container were stopped and removed; temporary fixtures were cleaned.

# Addendum to aggregate report (controller completeness check; verdict unchanged PASS / INITIAL)

Original full report is preserved. This addendum adds only the requested receipts, authority carry-forward, and boundary classification.

## 1. Literal enabled commands, environment, and raw receipts

Environment actually used: JDK Zulu `11.0.33` at `/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home`; Maven `3.9.11`; Node `v25.7.0`; Docker `29.8.2` via `unix:///Users/lukai/.orbstack/run/docker.sock`; disposable MySQL `8.0.36`; JDBC `serverTimezone=Asia/Shanghai`; named matrix schema `talent_introduction`; pristine calendar schema `expert_access_agg_calendar`; `-DreuseForks=false -DforkCount=1 -DargLine=-Dapi.version=1.41`; loopback ES `http://127.0.0.1:33950`; runtime app `33848`, SMTP `33951`, translation `33952`.

### Named matrix (exact)

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home \
  DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
  SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33450/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=verifyAgg \
  ES_BASE_URL=http://127.0.0.1:33950 \
  mvn surefire:test \
  -Dtest=AuthInterceptorTest,AuthFlowIntegrationTest,ExpertMailPermissionGuardTest,ExpertMailReadAccessIT,InboundPendingOwnerIT,MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,MailReviewDraftIT,MailReviewQueryIT,MailReplyDraftRepositoryIT,MeetingCalendarControllerTest,MeetingCalendarServiceTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest,MailReplyDraftSendIntegrationTest,FlywayMigrationIntegrationTest \
  -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Observed: exit0, BUILD SUCCESS 13:46, `Tests run: 260, Failures: 0, Errors: 0, Skipped: 0`; FlywayMigrationIntegrationTest 46 tests, 0/0/0.

### Pristine calendar (exact)

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home \
  DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
  SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33450/expert_access_agg_calendar?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=verifyAgg \
  ES_BASE_URL=http://127.0.0.1:33950 \
  mvn surefire:test -Dtest=MeetingCalendarControllerMysqlTest \
  -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Observed: exit0, BUILD SUCCESS 15.7 s, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`, 170 migrations through V171.

### Additional scoped IT

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home \
  DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
  ES_BASE_URL=http://127.0.0.1:33950 \
  mvn surefire:test -Dtest=ExpertScopedOperationsIT -DmysqlIt=true \
  -DargLine=-Dapi.version=1.41 -DreuseForks=false -DforkCount=1
```

Observed: exit0, BUILD SUCCESS 30.6 s, `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.

### Raw-output references

The command stdout was captured in the controller transcript (the redirection files were transient and cleaned after review). Durable raw surefire receipts still exist in the retained worktree:

- `target/surefire-reports/TEST-com.weibo.talentintroduction.campaign.controller.MeetingCalendarControllerMysqlTest.xml` — `tests="3" errors="0" skipped="0" failures="0"`.
- `target/surefire-reports/TEST-com.weibo.talentintroduction.mail.service.ExpertScopedOperationsIT.xml` — `tests="11" errors="0" skipped="0" failures="0"`.
- `target/surefire-reports/TEST-*.xml` — 382 raw class reports from the fresh full/named runs; the 260-test aggregate and 5727-test aggregate were reported on stdout from the exact commands above. No `artifact://` URI was emitted for those stdout streams; the controller-captured output plus these surefire XML files are the raw receipts.

## 2. A1–A44 carry-forward and retroactively authorized files

All A1–A44 are recorded in the fast-p ledger and are governing; no amendment is unrecorded. Latest governing amendment is A43 for the master; A44 applies the same geometry clarification to child 08. A1/A2/A3 establish the review05 prerequisite and final-UI override; A4–A44 carry the bounded fixture/activation exceptions. No master requirement was removed or weakened. A21–A24 only renamed `pendingBadge.total` to the actual wire `manualReviewTotal`; A41–A44 only clarify existing geometry and add no product/test file.

The amendments did retroactively authorize existing product/test files. Individual matrix rows follow; each is `PASS` from the same per-child scope check plus the fresh full/named/scoped runs already reported.

### review05 — authority A2/A3; inherited 13-file I-14 exception (not an addition beyond its plan)

```text
PASS src/main/resources/static/mailbox-chat.js
PASS src/main/resources/static/app.js                         (unchanged by review05; final UI needs no top button)
PASS src/main/resources/static/index.html
PASS src/main/resources/static/styles.css
PASS src/test/js/mailReviewAdmin.test.js
PASS src/test/js/mailboxServerDrafts.test.js
PASS src/test/js/mailReviewReader.test.js
PASS src/test/js/batchEntryRelocation.test.js                 (unchanged; obsolete top-button compatibility not needed)
PASS src/test/js/discoveryRecheck.test.js
PASS src/test/js/mailboxMailReadAccess.test.js
PASS src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt
PASS src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt
```

### 02 — authority A4/A5/A6/A7 (3 retroactive files)

```text
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt
PASS src/test/js/mailboxReadOnly.test.js
```

### 03 — authority A8–A12 (33 retroactive files)

```text
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt
PASS src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialArchiveFlowTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/EmailSuppressionControllerAuthTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactNoteControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailOpenTrackingControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt
PASS src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt
PASS src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplyIdentityDefaultsIntegrationTest.kt
```

### 04a — authority A13/A14 (6 retroactive files)

```text
PASS src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
```

### 04b — authority A15/A16 (9 retroactive files)

```text
PASS src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt
PASS src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
```

### 05 — authority A17/A18 (3 retroactive files)

```text
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt
```

### 06 — authority A10/A19/A20/A25/A26/A27/A28 (8 retroactive files)

```text
PASS src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt
PASS src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt
PASS src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt
PASS src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt
```

### 07 — authority A29–A36 (5 retroactive files)

```text
PASS src/test/js/mailboxChatStyle.test.js
PASS src/test/js/mailReviewReader.test.js
PASS src/test/js/mailboxCalendarIntegration.test.js
PASS src/test/js/mailboxReplyIdentity.test.js
PASS src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt
```

### 08 — authority A37–A40 (3 retroactive files)

```text
PASS src/test/js/mailReviewAdmin.test.js
PASS src/test/js/authFlow.test.js
PASS src/test/js/mailboxServerDrafts.test.js
```

No retroactive file was omitted; no unlisted product/test file changed. A1/A2/A3 add no file beyond review05’s inherited plan; A21–A24 rename no file; A41–A44 authorize no product/test file.

## 3. Resolution of the earlier evidence-boundary wording

The governing master separates machine acceptance (“验收标准” I-11: review four states, comments/history/return/withdraw; FULL upgrade must not alter APPROVED; new manual draft can send; approval/context confirmation never auto-SMTP) from the manual checklist item A-10 (“待审核筛选与同框审核”: admin popover, translate, approve, new inbound, confirmation, explicit send, personal-draft preservation). The former is machine-verifiable; the latter is human acceptance.

Fresh machine coverage actually collected: `MailReviewDraftIT` 12/12 and `MailReviewQueryIT` 5/5 in the named matrix; `ExpertScopedOperationsIT` 11/11 (including “READ review four states remain independently locked after FULL upgrade without automatic delivery”); full Node suite including `mailReviewAdmin.test.js` (17 tests) and the other review/reader suites, 1985/1985. Fresh actual browser additionally confirmed READ old APPROVED read-only and FULL old APPROVED read-only/no send, and a real user1 manual send with one SMTP DATA, `mail_record` 6003 SENT and `SEND_MANUAL_RICH_REPLY` actor `user1`.

What was not re-executed fresh in the browser this epoch is the admin A-10 sequence of translate → approve → new inbound → confirm → explicit send and the personal-draft-preservation UI. That is manual A-10 scope, not a missing mandatory machine command; the machine I-11 behavior is covered by the fresh ITs above and is not being waived by a child verdict. A-10 therefore remains `PENDING` for human acceptance. There is no mandatory fresh machine evidence gap in the evidence-boundary statement; the accurate classification is: machine I-11 PASS; A-10 manual PENDING.

Final result remains `PASS`, convergence `INITIAL`, repair `N/A`; `productModified: false`; no product/tests/index/plan/review evidence edited; no rerun of completed checks.

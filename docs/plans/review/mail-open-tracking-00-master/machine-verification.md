# Aggregate Machine Verification — mail-open-tracking-00-master

## Epoch 1 — 2026-09-25T14:20:25Z

- Master plan: docs/plans/2026-09-25/mail-open-tracking-00-master.md (sha256 14ff85b2875caf62e2adf936973c3b30f8f4a66b375c86a16c51f4f7c11f86a1; recorded commit ab2dda0f86c52d8bd9570d994fa895f087931df3)
- Governing master identity: worktree sha256 14ff85b2875caf62e2adf936973c3b30f8f4a66b375c86a16c51f4f7c11f86a1; recorded commit ab2dda0f86c52d8bd9570d994fa895f087931df3
- Master identity state: CONSISTENT; recorded A1 master amendment under G-6, approved 2026-09-25T11:20:27Z; recorded A2 child 03 amendment under G-2, same approval
- Boundary: f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5..2fd810b50ebded265cb0cf5eeaca2a2b6550521a
- Evidence HEAD at dispatch: c26d65a4e515da41afe6b591352a5041f96e7f03
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/mail-open-tracking-00-master/repair.md (sha256 107fb3416f73b14b646f8790d832e49025ca0d6f43b539497d56f721cdaab292), DRAFT_READY
- Manual acceptance: PENDING

Master, ledger, and handoff SHA-256 values match the supplied identities. Approved amendments A1/A2 are recorded. The aggregate diff changes exactly the 33 product/test files in the child-plan manifest.

### Fresh Commands

| Command | Result | Evidence |
|---|---|---|
| JDK 11 `mvn test -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest -DskipNodeTests=true` | PASS | Exit 0; 7 tests, 0 failures/errors/skips. Initial sandbox attempt exited 1 before tests because `target/classes/application.yml` could not be written; escalated rerun passed. |
| JDK 11 `mvn test -DmysqlIt=true -DmigrationIt=true -Dtest=MailOpenTrackingRepositoryIT,FlywayMigrationIntegrationTest -DskipNodeTests=true` | FAIL | Initial escalated run exited 1 before test bodies: Testcontainers requested Docker API 1.32; daemon requires >=1.40. Rerun with `JAVA_TOOL_OPTIONS=-Dapi.version=1.40` reached MySQL: exit 1; 37 tests, 1 failure, 0 errors/skips. All 32 Flyway tests passed; 1 of 5 repository tests failed. |
| JDK 11 `mvn test -Dtest=AutoMailReplyServiceTest,PendingMailOperationServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true` | PASS | Exit 0; 153 tests, 0 failures/errors/skips. |
| JDK 11 `mvn test -Dtest=SmtpMailDeliveryServiceTest,MailOpenTrackingPersistenceTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true` | PASS | Exit 0; 238 tests, 0 failures/errors/skips. |
| `node --test src/test/js/mailOpenTracking.test.js` | PASS | Exit 0; 8/8. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 1,193/1,193. |
| `node --check src/main/resources/static/app.js` | PASS | Exit 0. |
| JDK 11 `mvn test` | PASS | Exit 0; 4,034 Kotlin tests, 0 failures/errors, 13 skips, from fresh Surefire XML; integrated Node suite 1,193/1,193 and syntax checks passed. |
| `git diff --check f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5..2fd810b50ebded265cb0cf5eeaca2a2b6550521a -- src` | PASS | Exit 0. |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Master G-1, ordered delivery/default off | PASS | Ledger records terminal children 01→04; repository missing/invalid setting is false (`MailOpenTrackingRepository.kt:47-50`); SMTP reserves only after eligibility (`SmtpMailDeliveryService.kt:22-47`). |
| Master G-2, existing send behavior | PASS | Suppression precedes reservation; tracking preparation failure falls back to one send (`SmtpMailDeliveryService.kt:22-49,134-149`); 153 reply and 238 SMTP/persistence tests pass. |
| Master G-3, storage scope | PASS | V141 creates one tracking table and one nullable unique FK column (`V141__create_mail_open_tracking.sql:1-15`); one setting key (`MailOpenTrackingRepository.kt:154-160`); 32 Flyway tests pass. |
| Master G-4, opaque ID and success counts | PASS for implementation; fixture-dependent aggregate assertion pending | SecureRandom 32-byte token and ID reservation (`MailOpenTrackingService.kt:49-63`); success ID carried into records (`SmtpMailDeliveryService.kt:134-140`, `ManualOutreachTxHelper.kt:32-76`); query counts `t.id` over successful outbound rows (`MailOpenTrackingRepository.kt:88-103,111-160`). |
| Master G-5, honest signals and closure | PASS | Atomic conditional GET update (`MailOpenTrackingRepository.kt:78-86`); HEAD bypasses signal (`MailOpenTrackingController.kt:44-56`); UI labels state as signals (`app.js:14271-14390`); 8 focused JS tests pass. |
| Master G-6, mandatory gates | FAIL | Required MySQL repository test fails at `MailOpenTrackingRepositoryIT.kt:173`. |
| Child 01 I-1–I-4, I-6 | PASS | Settings, token/FK, independent reservation, atomic signal, and authenticated API paths inspected; focused tests 7/7, repository IT 4/5, Flyway 32/32. |
| Child 01 I-5 | BLOCKED | Repository SQL and fixture inspected, but failing boundary assertion stops the real-MySQL test before its list count, summary, and status assertions. |
| Child 01 I-7 | FAIL | Required real-MySQL boundary test fails; finding V-1. Query has planned half-open predicates (`MailOpenTrackingRepository.kt:114-115`). |
| Child 02 I-1–I-4 | PASS | Explicit reply bit at audited producers; image removal before final manual-send identity (`PendingMailOperationService.kt:567-568`); 153/153 focused tests. |
| Child 03 I-1–I-5 | PASS | Eligibility, wire-only pixel, failure isolation, success-only ID, and both MIME branches inspected (`SmtpMailDeliveryService.kt:22-149`); 238/238 focused tests. |
| Child 04 I-1–I-5, S-1–S-3 | PASS for machine checks | Settings state, signal labels, pagination, request sequence, escaping, DOM, and CSS inspected (`app.js:14193-14406`, `index.html:254-299`, `styles.css:11816-11861`); focused JS 8/8 and full JS 1,193/1,193. Browser acceptance remains pending. |
| Authorized scope | PASS | Approved manifest union: 33 files; changed `src` paths: 33; outside/missing: none. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | No prior aggregate finding; first whole-plan review. |

### Findings

#### P1

N/A.

#### P2

- **V-1 — mandatory MySQL boundary fixture.** `MailOpenTrackingRepositoryIT.kt:170` inserts `at.minusNanos(1000)` immediately before midnight into `mail_record.sent_at DATETIME`, which has zero fractional precision (`V1__create_business_tables.sql:109`). MySQL stores row 9206 on the next day; the test expects it excluded and fails at line 173. The remaining count and summary assertions do not execute. The implicated repair scope is the one test file. This P2 blocks PASS because child 01 I-7 and master G-6 explicitly require the MySQL gate.

#### Observations

- The Docker API mismatch was resolved for testing with `JAVA_TOOL_OPTIONS=-Dapi.version=1.40`; no source repair is indicated.
- No authenticated deployment, live HTTPS pixel route, test-mailbox delivery, or browser A-check was performed. Manual acceptance remains PENDING.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 01 O-01: Docker/MySQL NOT_RUN | G-3/G-4/G-6; child 01 I-5/I-7 | Executed, FAIL | Real MySQL ran 37 tests with API override; one fixture assertion fails; all 32 Flyway tests pass. |
| Child 03 O-01: dependency MySQL NOT_RUN | G-2/G-4/G-6 | Executed, FAIL inherited | Child 03 focused 238/238 passes; 01 database gate fails V-1. |
| Child 04 O-01: live A-1–A-5 NOT_RUN | Human acceptance checklist | PENDING | No authenticated deployment, real mailbox, or browser A-check was performed. |

### Evidence Boundaries and Next Action

The failing repository assertion prevents later assertions in that test from executing on real MySQL. The full Maven suite passes because these integration classes are opt-in, so its result does not override the explicit MySQL gate failure. No live human acceptance was performed. Repair planning returned DRAFT_READY at the exact path above. Execute only after explicit human approval, then rerun the complete aggregate matrix and all required commands. No implementation was performed. No product code was modified.

## Epoch 2 — 2026-09-25T15:52:55Z

- Master plan: docs/plans/2026-09-25/mail-open-tracking-00-master.md (sha256 14ff85b2875caf62e2adf936973c3b30f8f4a66b375c86a16c51f4f7c11f86a1; recorded commit ab2dda0f86c52d8bd9570d994fa895f087931df3)
- Governing master identity: worktree sha256 14ff85b2875caf62e2adf936973c3b30f8f4a66b375c86a16c51f4f7c11f86a1; state CONSISTENT; A1 master G-6 eleven-file exception and A2 child 03 G-2 clean cutover approved HUMAN:2026-09-25T11:20:27Z
- Boundary: f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5..e5932fe2f7d7d8851da2eaa81ee6f758c2907e74
- Evidence HEAD at dispatch: 4b8ed86b71f54602fb05db4d1e365eb5bb4f955a
- Reviewer: /root/aggregate_rereviewer (fresh agent, no inherited implementation context)
- Result: PASS
- Convergence: PROGRESSING; V-1 RESOLVED
- Repair artifact/result: docs/plans/fix/mail-open-tracking-00-master/repair.md (sha256 107fb3416f73b14b646f8790d832e49025ca0d6f43b539497d56f721cdaab292); no new repair planning
- Repair evidence mode: DURABLE_HANDOFF; approval and execution recorded in docs/plans/review/mail-open-tracking-00-master/repair-execution.md
- Manual acceptance: PENDING

Plan, ledger, handoff, and repair identities match. The aggregate implementation changes exactly the 33 source/test paths in the approved manifest. The repair delta changes only the Authorized File `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt`.

### Fresh Commands

| Command | Result | Evidence |
|---|---|---|
| JDK 11 `mvn test -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest -DskipNodeTests=true` | PASS | Exit 0; 7 tests, 0 failures/errors/skips. First sandbox attempt failed before test bodies because `target/classes/application.yml` was unwritable; rerun with filesystem access passed. |
| JDK 11 `JAVA_TOOL_OPTIONS=-Dapi.version=1.40 mvn test -DmysqlIt=true -DmigrationIt=true -Dtest=MailOpenTrackingRepositoryIT,FlywayMigrationIntegrationTest -DskipNodeTests=true` | PASS | Exit 0; 37/37, RepositoryIT 5/5 including repaired boundary and Flyway 32/32; 0 failures/errors/skips. |
| JDK 11 `mvn test -Dtest=AutoMailReplyServiceTest,PendingMailOperationServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true` | PASS | Exit 0; 153 tests, 0 failures/errors/skips. |
| JDK 11 `mvn test -Dtest=SmtpMailDeliveryServiceTest,MailOpenTrackingPersistenceTest,InitialOutreachServiceTest,ManualInitialOutreachServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true` | PASS | Exit 0; 238 tests, 0 failures/errors/skips. |
| `node --test src/test/js/mailOpenTracking.test.js` | PASS | Exit 0; 8/8. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 1,193/1,193. |
| `node --check src/main/resources/static/app.js` | PASS | Exit 0. |
| JDK 11 `mvn test` | PASS | Exit 0; 4,034 Kotlin tests, 0 failures/errors, 13 skips; integrated Node 1,193/1,193. |
| `git diff --check f9c8dce2d1f2efe09d9ccb0c439498d2e91f22f5..e5932fe2f7d7d8851da2eaa81ee6f758c2907e74 -- src` | PASS | Exit 0. |

For Maven, `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. The MySQL command also sets `JAVA_TOOL_OPTIONS=-Dapi.version=1.40` for the Docker daemon API. The initial sandbox write failure is environment evidence, not a test failure; the mandatory command was rerun and passed.

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Master G-1 | PASS | Children 01→04 terminal in order; setting defaults off; SMTP checks eligibility. |
| Master G-2 | PASS | Suppression precedes reservation; preparation failure falls back to one send; reply and SMTP suites pass. |
| Master G-3 | PASS | V141 adds one tracking table and one nullable unique FK column; repository uses one setting key. |
| Master G-4 | PASS | SecureRandom token, success-only ID propagation and ID-based successful-mail aggregation; MySQL gate passes. |
| Master G-5 | PASS | Conditional signal update, HEAD without recording, UI signal labels; focused JS passes. |
| Master G-6 | PASS | All mandatory machine gates pass; live human acceptance remains pending. |
| Child 01 I-1 | PASS | Missing/invalid setting false; HTTPS configuration required to enable; MySQL test passes. |
| Child 01 I-2 | PASS | 32-byte token, unique token and nullable unique FK; MySQL test passes. |
| Child 01 I-3 | PASS | Cross-bean `REQUIRES_NEW` reservation; MySQL test passes. |
| Child 01 I-4 | PASS | Atomic first/last update gated by setting; GET/HEAD distinction; MySQL test passes. |
| Child 01 I-5 | PASS | Successful outbound base set, status/count/summary and orphan assertions execute on MySQL. |
| Child 01 I-6 | PASS | `/api/**` interceptor; GIF-only public route; DTO excludes token/body. |
| Child 01 I-7 | PASS | Half-open Shanghai date range and one read-only snapshot transaction; repaired boundary test passes. |
| Child 02 I-1 | PASS | Audited reply producers set `isReply`; 153 focused tests pass. |
| Child 02 I-2 | PASS | Reply bit remains in send DTO; existing thread fields persist. |
| Child 02 I-3 | PASS | Narrow tracking-image stripping in `MailContentService`; focused tests pass. |
| Child 02 I-4 | PASS | Pending-mail HTML stripping precedes final validation and send identity. |
| Child 03 I-1 | PASS | SMTP excludes reply bit, headers and reply subject prefixes. |
| Child 03 I-2 | PASS | Pixel enters wire copy; plain text and stored body remain separate. |
| Child 03 I-3 | PASS | Suppression before reservation; preparation errors do not add a send attempt. |
| Child 03 I-4 | PASS | Only successful delivery carries tracking ID into business saves. |
| Child 03 I-5 | PASS | Both MIME branches use wire copy; 238 focused tests pass. |
| Child 04 I-1 | PASS | UI reads server settings and restores server-owned state after failures. |
| Child 04 I-2 | PASS | Three signal labels; no token or image preview. |
| Child 04 I-3 | PASS | Shared date/account range, 20-row pagination, zero-denominator dash. |
| Child 04 I-4 | PASS | Request and detail sequences reject stale responses. |
| Child 04 I-5 | PASS | Dynamic fields escaped; detail ID validated. |
| Child 04 S-1–S-3 | PASS, machine checks | New DOM/CSS scoped to tracking UI; focused and full JS tests pass; browser check pending. |
| Authorized scope | PASS | Manifest 33; changed `src` paths 33; outside/missing 0; repair only RepositoryIT. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | Fixture uses `at.minusSeconds(1)` and asserts persisted previous-day date. Real MySQL RepositoryIT passes 5/5, including count, status, summary and orphan assertions. |

P1: N/A. P2: N/A. Observations: N/A.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 01 O-01: Docker/MySQL NOT_RUN | G-3/G-4/G-6; child 01 I-5/I-7 | RESOLVED | Fresh 37/37 MySQL/Flyway pass. |
| Child 03 O-01: inherited MySQL dependency | G-2/G-4/G-6 | RESOLVED | Same MySQL gate passes; SMTP suite 238/238 passes. |
| Child 04 O-01: live A-1–A-5 NOT_RUN | Human acceptance checklist | PENDING | No authenticated deployment, HTTPS callback, real mailbox or browser acceptance claim. |

### Evidence Boundary

Authenticated deployment, live HTTPS callback, real mailbox and browser acceptance were not run. No product code was modified during review. Human acceptance remains pending.

# Aggregate Machine Verification — mailbox-suspension

## Epoch 1 — 2026-10-03T22:31:16+0800

- Master plan: `docs/plans/2026-10-03/mailbox-suspension.md` (sha256 `a507377f29cdda0f1f77b80767f2c924f99bfb1c6f84301d258fb880d632b5b7`)
- Governing master identity: worktree sha256 `a507377f29cdda0f1f77b80767f2c924f99bfb1c6f84301d258fb880d632b5b7`; recorded commit `c486c5c44806b5b4c4db654606c358efb94fec5a`
- Master identity state: `CONSISTENT`; amendments: `N/A`
- Boundary: `9d7e389f00521582e45beba213d32508485cb536..0837c372f45d69374084113d8258d8c3320d748c`
- Evidence head: `43afec58b8310786184051d5b00bcdabf43a74b4`
- Reviewer: `/root/aggregate_reviewer` (fresh; no fast-p implementation or light-verification conversation)
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/mailbox-suspension/repair.md` — `DRAFT_READY`
- Product modification by reviewer: none.

### Fresh Command Evidence

| Command | Exit | Result/evidence |
|---|---:|---|
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DskipTests test-compile` | 0 | `BUILD SUCCESS` |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=MailboxConversationRepositoryIT,MailboxConversationRepositorySqlCompatTest,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailboxSuspensionServiceIT test` | 1 | 71 run / 0 failure / 6 error. Exact baseline-only errors: repository `dismissed_at` data error (25/1E) and Calendar context errors (5/5E). New/relevant suites: SQL compatibility 2/2, controller 32/32, suspension service 7/7. |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | 1 | Testcontainers Docker client API 1.32 rejected by engine minimum API 1.40. |
| Flyway retry with `DOCKER_API_VERSION=1.44` | 1 | Same client API 1.32 error. |
| Documented OrbStack-compatible Flyway retry: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock ... -Dapi.version=1.40` | 0 | Flyway 35/35; `BUILD SUCCESS`; report `target/surefire-reports/TEST-com.weibo.talentintroduction.campaign.repository.FlywayMigrationIntegrationTest.xml`. |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn -Dtest=UnmatchedInboundMarkResolvedIdentityTest,PendingMailOperationServiceTest,UnmatchedInboundTrustWorkbenchTest test` | 0 | 55/55: identity 6, pending-operation 37, trust-workbench 12. |
| `node --check src/main/resources/static/mailbox-chat.js` | 0 | Parse pass. |
| `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxChatStyle.test.js` | 0 | 207/207. |
| `node --test src/test/js/*.test.js` | 1 then 0 | Initial sandbox run: `EPERM` opening `target/discovery-plan-acceptance/08.html`, no assertion failure. Retry with target write access: 1421/1421. |

### Master Contract Matrix

| Contract | Result | Evidence |
|---|---|---|
| Master I-1: explicit confirmation is sole suspension end | PASS | Service deletes only on explicit resume/end; processing paths read only. |
| Master I-2: preserve categorization/write paths | PASS | Follow, replied-dismissal, and watermark predicates retain prior behavior. |
| Master I-3: ordered isolated slices | PASS | `01 → 01b → 02`; code/test boundary is within authorized slice files. |
| 01 I-1: durable per-user/contact storage | PASS | V147 PK/FK and service lock/read/write path. |
| 01 I-2: no automatic deletion | PASS | Pending-count logic is read-only; no processing-triggered delete. |
| 01 I-3: real cross-account pending count | PASS | Batch query excludes simulator and aggregates real sender accounts. |
| 01 I-4: list/category/pagination predicates | PASS | Suspended/pending/replied predicates retain old guards. |
| 01 I-5: Session API, validation, idempotency | PASS | Session-scoped controller/service and idempotent state operations. |
| 01 I-6: additive compatibility | PASS | Migration/API additions leave unrelated protocol unchanged. |
| 01b I-1: resolved actor from Session | PASS | Controller ignores body actor identity. |
| 01b I-2: existing processing service retained | PASS | Shared processing path retained; no suspension write. |
| 01b I-3: compatible response | PASS | Adds resolved actor only on success. |
| 02 I-1: six tabs/default selection | PASS | Fixed tab order/default decision paths present. |
| 02 I-2: real suspension state/count rendering | PASS | GET/PUT/DELETE integration and targeted tests pass. |
| 02 I-3: explicit continue/end behavior | PASS | No automatic cancellation; inline completion behavior present. |
| 02 I-4: cancel routing preserves semantics | PASS | UI consumes backend state; no client-side category mutation. |
| 02 I-5: async/session/mobile isolation | FAIL — V-1 | Stale suspension callbacks can mutate a later active context. |
| 02 I-6: inline reason/safe rendering/styles | PASS | Focused style/behavior suites pass. |
| 02 I-7: inline processing Session identity | PASS | Identity regression suite passes. |
| S-1..S-4: visual/CSS/cache contracts | PASS | Focused and full Node suites pass. |
| Master A-1/A-2; 02 A-1..A-8 | PENDING | Human/browser acceptance not performed. |

### Finding Lineage

| ID | Severity | Status | Requirement | Evidence |
|---|---:|---|---|---|
| V-1 | P1 | NEW / REPAIRABLE | 02 I-5 and T3 | `mailbox-chat.js:1184-1187` mobile return only saves draft/switches pane; `:1842-1861` state load ignores pane/user/current-detail checks; `:2119-2143`, `:2169-2200`, and `:2235-2267` completions check only disposal; `:2203-2233` reason GET has only partial sequence guard. Late A callbacks can alter B/mobile-list DOM, route/tab, refresh, or form state. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1: six backend errors | Master scope/baseline distinction | PERSISTS; unrelated | Exact baseline `dismissed_at` data error and Calendar context errors; no new assertion failure. |
| 01 O-2: cache-key failures | 02 S-1..S-4 | RESOLVED | Full Node retry 1421/1421. |
| 01b O-1: cache-key failures | 02 S-1..S-4 | RESOLVED | Full Node retry 1421/1421. |
| 02 O-1: auth failure feedback | 02 I-7 | OBSERVATION | Disabled controls cannot receive the described browser click. |
| 02 O-2: browser end/delete/tab/retry coverage | Master manual acceptance | PENDING | Requires human browser acceptance; not repair authority. |

### Review-P Result

`FAIL` / `INITIAL` / `DRAFT_READY`. One bounded repair plan covers V-1 only. No product code, tests, review evidence, staging, commits, or deployment was modified by the reviewer.

## Epoch 2 — 2026-10-03T23:44:05+0800

- Master plan: `docs/plans/2026-10-03/mailbox-suspension.md` (sha256 `a507377f29cdda0f1f77b80767f2c924f99bfb1c6f84301d258fb880d632b5b7`)
- Governing master identity: worktree sha256 `a507377f29cdda0f1f77b80767f2c924f99bfb1c6f84301d258fb880d632b5b7`; recorded commit `c486c5c44806b5b4c4db654606c358efb94fec5a`
- Master identity state: `CONSISTENT`; amendments: `N/A`
- Boundary: `9d7e389f00521582e45beba213d32508485cb536..1c65a2c47f25c3783d8e158120496a133596e951`
- Evidence head: `2b628ff536476aedf67568c0be9579e6c042ec24`
- Post-repair evidence: `DURABLE_HANDOFF`; repair `docs/plans/fix/mailbox-suspension/repair.md` sha256 `cd2ab0dd24c91a3d8f4e93bcfdea12deccbef6f96eef8dc048261aadbc3fc592`; approved by human `$execute-p` invocation at `2026-10-03T23:08+0800`; prior/repaired code `0837c372f45d69374084113d8258d8c3320d748c..1c65a2c47f25c3783d8e158120496a133596e951`; executor `UNAVAILABLE`.
- Reviewer: `/root/aggregate_review` (fresh after repair commit; no inherited implementation or lightweight-verification conversation)
- Result: `PASS`
- Convergence: `PROGRESSING`
- Repair artifact/result: `docs/plans/fix/mailbox-suspension/repair.md` — `NO_ACTION` (already executed and verified)
- Product modification by reviewer: none.

### Fresh Command Evidence

| Command | Exit | Result/evidence |
|---|---:|---|
| `mvn -DskipTests test-compile` | 0 | `BUILD SUCCESS` |
| B2 MySQL targeted tests | 1 | 71 run / 0 failure / 6 error; exact baseline-only errors: `MailboxConversationRepositoryIT` `dismissed_at` data error (1) and Calendar context missing `PendingMailOperationService` (5). Relevant suites: SQL compatibility 2/2, controller 32/32, suspension service 7/7. |
| B3 Flyway with documented OrbStack environment (`DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`, `-Dapi.version=1.40`) | 0 | 35 / 0 failure / 0 error. |
| 01b targeted Maven identity tests | 0 | 55 / 0 failure / 0 error: identity 6, pending-operation 37, trust-workbench 12. |
| `node --check src/main/resources/static/mailbox-chat.js` | 0 | Parse pass. |
| `node --test src/test/js/mailboxSuspension.test.js` | 0 | 30 / 30 / 0. |
| 02 Node subset | 0 | 212 / 212 / 0. |
| Full Node suite, sandbox first run | 1 | 1425 / 1426; sole `EPERM` writing ignored `target/discovery-plan-acceptance/08.html`, not an assertion failure. |
| Full Node suite, target-output retry | 0 | 1426 / 1426 / 0. |
| CSS baseline comparison | 0 | `mailbox-chat.css` byte-identical to the required baseline. |

### Master Contract Matrix

| Contract | Result | Evidence |
|---|---|---|
| Master I-1: explicit action is sole suspension end | PASS | Server uses explicit PUT/DELETE only; processing/read paths do not delete suspension rows. |
| Master I-2: preserve categorization/write paths | PASS | Follow, replied-dismissal, processing service, and original category predicates remain. |
| Master I-3: ordered isolated slices | PASS | 01 → 01b → 02 API/DTO/frontend chain closes; scope is authorized. |
| 01 I-1: durable session storage | PASS | V147 PK/FK and nullable reason. |
| 01 I-2: no automatic end | PASS | Pending count is read-only; only new table is written. |
| 01 I-3: cross-real-account pending count | PASS | Real sender-account join excludes simulator/ghost accounts and includes disabled accounts. |
| 01 I-4: category/pagination parity | PASS | Suspended `EXISTS`, pending/replied `NOT EXISTS`; count/page predicates agree. |
| 01 I-5: Session API, validation, idempotency | PASS | Session username with 401/404/409/400 and idempotent PUT/DELETE. |
| 01 I-6: additive compatibility | PASS | No old status table, IMAP/SMTP, or ES write; DTO defaults remain compatible. |
| 01b I-1: Session actor | PASS | Body actor is ignored; Session identity reaches both parameters. |
| 01b I-2: original processing chain | PASS | Only original `markResolved` success returns. |
| 01b I-3: compatible response | PASS | `{id, processStatus:"PROCESSED", resolvedBy}`; old body is accepted. |
| 02 I-1: six tabs/default | PASS | Exact six tabs, pending probe, and focus preservation. |
| 02 I-2: server-state rendering | PASS | UI consumes GET/PUT/DELETE state and cross-account count. |
| 02 I-3: explicit continue/end | PASS | Continue makes zero DELETE; end makes one DELETE; no automatic end. |
| 02 I-4: post-end routing | PASS | Server response decides pending/followed/replied. |
| 02 I-5: async/session/mobile isolation | PASS | State GET, reason GET, PUT, cancel DELETE, and end DELETE bind `convEpoch`, `paneEpoch`, user, `workEpoch`, and disposal; expert/user/reset/mobile-return invalidates stale work. |
| 02 I-6: inline reason/safe rendering/styles | PASS | Inline form, escaping, S-4 contract, CSS baseline, and tests pass. |
| 02 I-7: inline processing Session identity | PASS | No body actor; confirmation precedes POST; then suspension state reload. |
| S-1..S-4: visual/CSS/cache contracts | PASS | 212/212 subset, 1426/1426 full Node retry, and CSS comparison pass. |
| Master A-1/A-2 | PENDING | Human/browser acceptance has not occurred. |

### Finding Lineage

| ID | Epoch 1 | Epoch 2 |
|---|---|---|
| V-1 / P1 | NEW — stale callbacks could mutate a later/mobile-list context | RESOLVED — five discriminating state/reason/PUT/cancel/end callback tests pass; stale A cannot affect B's form, navigation, tab, or hidden detail. |

The `listSeq` omission is an intentional correct guard design: routine refresh changes it, which would strand current PUT/DELETE controls busy; context identity is fully covered by reset-aware `workEpoch` plus conversation, pane, user, and disposal state.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1: six backend errors | Baseline/scope distinction | PERSISTS; baseline-only | Same 6 errors; no relevant new assertion failure. |
| 01 O-2 and 01b O-1: cache-key failures | 02 S-1..S-4 | RESOLVED | Full Node retry 1426/1426. |
| 02 O-1: auth failure feedback | 02 I-7 | OBSERVATION | Disabled control cannot be clicked in a real browser. |
| 02 O-2: browser end/delete/tab/retry evidence | Master manual acceptance | PENDING | Human browser check remains required; not repair authority. |

### Review-P Result

`PASS` / `PROGRESSING` / `NO_ACTION`. No product code, tests, plans, review evidence, staging, commits, or deployment was modified by the reviewer.

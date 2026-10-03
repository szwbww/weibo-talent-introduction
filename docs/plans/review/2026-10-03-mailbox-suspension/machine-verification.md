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

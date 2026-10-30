# Child 02 — independent light verification

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 02 — docs/plans/2026-10-29/expert-access-02-access-level.md; epoch 3, attempt 1
Boundary: 30c8af9b98f675f628d3ee98bd737f1716ecf54c..6c0981eb4e148e9789c3af49e9d2dca9739c2508
Verifier: ExpertAccess02Verifier

Identity: approved master/child commit 4961c395096eb4bda13e3014319aad01390d44f3; child SHA256 dc6abdf9c35bac88f360cb7639faaec9a581b4a46b03562c07e5942836dc610c independently matched. Root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master; branch fast/expert-access-master. Baseline ledger A4–A7 and execution epochs 1–3 read; retained failures were not relabelled as passes.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary `git diff --stat` (artifact://2208) shows the ten authorized product/test paths, plus seven approved controller planning/evidence paths excluded from product scope. No application configuration, frontend product, guard, or applied migration change. Bounded fixture/cache-test diff inspected (artifact://2225). Existing dirty brief/execution/ledger belonged to controller and were left untouched. |
| Plan and invariants | PASS | ExpertMailReadAccessService.kt:12–15,43–68,76–106 gives NONE/READ/FULL, derived canRead, real uncached bulk reads, strict persisted values and atomic non-downgrading legacy upsert. ExpertMailReadAccessController.kt:30–75 keeps admin/session authority, optional legacy body, explicit READ/FULL validation and compatible response. V170 adds only default READ column. ExpertMailReadAccessIT.kt:352–505 directly covers transitions, invalid values, concurrency, current AuthWebConfig route denial, V169 upgrade and fresh prefix. ProjectionTest.kt:102–306 covers current-level denial/re-read, cursor isolation, zero READ attachment interactions, original FULL projection and single bulk lookup. Fresh actual runtime smoke below confirms changed HTTP paths, not just MockMvc. |
| Required commands | PASS | Fresh sequential commands in artifact://2210, all exit 0: `mvn test -Dtest=ExpertMailReadAccessIT -DmysqlIt=true -DargLine=-Dapi.version=1.41` =15/0/0/0; `mvn test -Dtest=MailboxReadAccessProjectionTest` =8/0/0/0; `mvn test -Dtest=OutboundAttachmentFlowTest,MailReviewQueryIT -DmysqlIt=true -DargLine=-Dapi.version=1.41` =14/0/0/0 (9+5); `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -DargLine=-Dapi.version=1.41` =46/0/0/0. Total83 JVM, zero skips. Each Maven command also executed bound full Node1961/1961, zero skipped, and bound syntax gates successfully. Explicit `node --test src/test/js/mailboxReadOnly.test.js src/test/js/taskActivityCenter.test.js` =81/81, zero skipped. Fresh counts extracted by grep in artifact://2210. |
| Downstream interfaces | PASS | MailboxConversationService.kt:173,229 uses one real batch level map; :254–266,361 exposes live wrapper level including empty account scope; :260,287–346 suppresses READ attachment-only reads and preserves FULL original source/snapshot/feature checks. Exact enum, optional legacy PUT and DTO accessLevel/canRead contracts are available to03/07. Runtime confirmed FULL is not global admin or permission to later write routes. No positive future-child send/download/UI claim is made. |

Fresh command environment: JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock. External ExpertMailReadAccessIT fixture: SPRING_DATASOURCE_URL=`jdbc:mysql://127.0.0.1:23391/expert_access02?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`, username root, password fixture. New owned `--rm` mysql:8.0.36 container expert-access02-verify-mysql; other integration fixtures were Testcontainers-isolated. No installed11.0.15 tzdb workaround or production resource used.

Actual runtime: dependency:build-classpath exit0; Java11.0.33 launched real application/embedded Tomcat on127.0.0.1:23831, auth enabled with current AuthWebConfig, isolated fresh expert_access02_verify_smoke schema, migration startup throughV170 (artifact://2214), disabled queue/scheduling/discovery/LLM and loopback-only ES/SMTP/IMAP. Admin/user1 real session login succeeded. 29 actual HTTP responses were exercised in the persistent verifier kernel: initial NONE403; legacy PUT→READ; READ list and live wrapper READ; explicit FULL plus legacy PUT remained FULL; FULL list/live wrapper FULL; real processing-owned verify.txt attachment appeared at FULL and admin projection matched exactly; downgrade READ suppressed attachments immediately; revoke restored403. Invalid NONE/ADMIN/numeric bodies400; unauthorized expert B403; FULL user1 grant PUT/DELETE, account/task APIs, actual PUT follow and POST manual-rich-reply all403. Final DB counters: send_attempts0, mail_records2 seeded, follows0, grants0 (artifact://2224). No actual send or file-stream read claim.

Smoke fixture corrections retained as verification history, not product failures: first attachment seed used mail_record-only INBOUND, which is not a standalone timeline source; corrected to real INBOUND_PROCESSING ownership after reading the migration (initial guessed column failed1054, then used inbound_processing_id). First follow request used unsupported POST; corrected to actual PUT and observed403. No product/test mutation was performed.

Cleanup: owned API service expert-access02-verify-api stopped; owned --rm MySQL container stopped/removed after all gates completed; Testcontainers completed their own cleanup. Only this report was edited; no commits, index changes, plan edits or delegation.

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: Smoke's unsupported `POST /api/mail/mailbox/conversations/22001/follow` returned500 with “Request method POST not supported” (artifact://2221). Actual mapped PUT was subsequently403. Unsupported-method exception mapping is outside child02's authorized files/requirements; no further investigation or correction.

### Required Action
- COMPLETE_CHILD

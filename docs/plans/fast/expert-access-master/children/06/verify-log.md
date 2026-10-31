# Child 06 — independent light verification

## Epoch 5 — fresh attempt 1 (2026-10-30)

- Approved child/master identity: `commit:8284ae04acaed6f7222774c8a4c6ac522f6690f0`; child SHA256 independently read as `cb29ebd9ff569ba251108c38b36de95a38dec35c118cafd2c16e8719dd56631d`.
- Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`. Verifier is distinct from the epoch1–5 writers. Read current brief, complete child plan, governing master invariants and ledger amendments A10/A19–A28; executor receipts were baseline guidance, not independent proof.
- Exact boundary: `e4c5d83a580228a92f1dd2c427538829651a40a6..b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc`. Interleaved controller documentation excluded from product scope. No product/test/index/commit/plan/ledger/brief mutation.

Fresh sequential commands used this literal environment prefix from the target root:
```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:33426/talent_introduction?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=child06 ES_BASE_URL=http://127.0.0.1:33926
```
1. Prefix + `mvn test -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT,InboundPendingOwnerIT -DmysqlIt=true -DargLine=-Dapi.version=1.41`: exit0, **60/0fail/0error/0skip**, suites41/11/8; `artifact://2638`. Node1961/0fail/0skip and both Maven-bound syntax checks passed.
2. Prefix + `mvn test -Dtest=MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,UnmatchedInboundMailServiceTest,OperatorStatusWriteSeamGuardTest,AuthInterceptorTest,InboundMailProcessingRepositoryTest,MailReviewQueryIT -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41`: exit0, **101/0fail/0error/0skip**, suites46/6/15/1/19/9/5; `artifact://2641`. Node1961/0fail/0skip and both syntax checks passed.
3. Prefix + `mvn test -Dtest=MailRecordRepositoryMonitoringIT,MailMonitoringServiceTest,MailMonitoringControllerPostmasterTest,InboundMailSummaryControllerTest,AutoMailReplyServiceTest,BatchAutoMailReplyServiceTest,InboundMailTagServiceTest,AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41`: exit0, **220/0fail/0error/0skip**, suites15/10/5/2/102/21/9/10/46; `artifact://2642` and fresh individual Surefire XML independently parsed in verifier Eval `Reading fresh final suite counts` (large command output is partially truncated). Node1961/0fail/0skip and both syntax checks passed.
4. `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert-access-06-verify-runtime/classpath.txt`: exit0; fresh runtime classpath, dependency jars plus `target/classes`, no testclasses or expanded WAR.

Fresh actual runtime, after all required test commands:
- Owned disposable `expert-access-06-verify-mysql` container `7d1500830745d3ac3ae61754329376d6ceec27405d1b9aec226f662811b4f670`, loopback33426; separate `expert_access_06_runtime` database. Monitoring IT independently used its own MySQL fixture. No production DB.
- Real `TalentIntroductionApplicationKt`, Java11.0.33, service `expert-access-06-verify-app`, PID6256, loopback33826; startup receipt `artifact://2646`. AUTH_ENABLED=true; queue/scheduling/discovery disabled; actual AuthWebConfig/controllers/services/JDBC/transactions. Real cookie logins for admin/user1 and actual admin password change.
- Independently re-executed the inspectable prior acceptance procedure against this new process/database, with unique verifier ports/root/container; no prior HTTP results reused. Verifier Eval `Exercising fresh owner API lifecycle` records all **67 actual HTTP requests/statuses**, SQL outcomes and assertions.
- Historical admin3 retained after FULL grant; disabled-real-account user1 pending3 gives admin/user badges3/3, pending experts2/1, Alpha All timeline5. Admin scopes ALL/USER1_FULL/PENDING_REVIEW totals2/1/2; empty filtered page retains `filterCounts={user1FullExpertCount:1,pendingReviewDraftCount:5}`; user1 has no filterCounts. Invalid enum400; forged queue actor/username still admin3.
- Twelve initial mapped forbidden requests (READ/NONE suspension verbs, restricted expert scopes, unmatched queue and grant endpoint) returned403 with identical before/after DB snapshot, no incremental ES/SMTP observations and empty attachment files. No unmapped-method probe used as default-deny evidence.
- FULL PUT suspension200 hides user pending3→0 without changing admin3; suspendedOnly1; PATCH reason and DELETE resume200 restore3. FULL target with only admin pending returns409.
- Completion ignores forged body identity, persists resolvedBy=user1, reduces personal pending3→2 and retains expert-wide attention. Downgrade READ reclaims unfinished2 (admin5/user0); READ timeline remains5; READ reopen403, admin reopen200 yields admin6; FULL regrant does not migrate historical admin ownership. Global inbound summary6 before/after and submitted drafts5 remain.
- Barrier-synchronized real downgrade/suspend requests observed200/409; final pending owners admin7/user0; subsequent READ suspension GET403. Final SQL: pending admin7, submitted5, mail_record0. ES observer20 total calls, SMTPevents=[], attachmentfiles=[]; denied matrix had zero incremental observer activity. This demonstrates exercised connection/response/file-creation boundaries, not uninstrumented OS syscall reads or actual SMTP delivery.
- Owned application stopped; observers shut down/closed; attachment directory/classpath/runtime directory removed; `docker ... rm --force expert-access-06-verify-mysql` exit0. No owned runtime services/scaffolds remain.
- Browser1440/390 NOT_RUN: no frontend change in this boundary; child plan:149 explicitly permits same-session API equivalents until07/08 final UI. Existing inherited foreign-anchor/unmapped-method500 decisions were not reopened or reconfirmed.

## Light Verification: LIGHT_PASS
Child: 06 — docs/plans/2026-10-29/expert-access-06-pending-query.md
Boundary: e4c5d83a580228a92f1dd2c427538829651a40a6..b1bcd534c51f8cfe31b616dcf8f758b653d9e0fc
Verifier: ExpertAccess06Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git diff <base> <head> -- src/main src/test` (`artifact://2630`) shows18 paths,+388/-68. Independent plan-table/path set comparison: authorized18/changed18/unauthorized∅/missing∅. Monitoring fixture delta is exactly two paired cleaned_body ALTERs, preserving unicode_ci vs general_ci; lineguard delta is only two existing noise-site line numbers. Interleaved controller docs excluded as instructed. |
| Plan and invariants | PASS | Repository:252–267 unfiltered SQL toolbar counts,571–590 personal suspension aggregation,733–745 owner pending flag without deleting All history,836–950 shared scope/ACL/AND/HAVING predicates. Service:112–174 strict enum/admin restriction and empty-page counts. Policy:59 pendingBadge and guard:39–42 four exact FULL suspension keys; ownerIT:83–191 real AuthWebConfig NONE/READ/FULL/cross/admin matrix,214–425 sink/de-dup/races/narrow-update/reopen/rollback. Fresh owner EXPLAIN (`artifact://2638`:757–759) selects idx_inbound_pending_owner,type=ref,rows1,Using index. MailReviewQueryIT:221–242 preserves constant page-size query count/no-body/reader-zero SQL, with no replacement exact-count pin. All fresh gates and actual67-request lifecycle above passed; global summary/automation/monitoring unchanged. |
| Required commands | PASS | All four commands above freshly executed sequentially on final committed product source: exits0, JVM60+101+220=381 tests,0fail/0error/0skip; Node1961/0fail/0skip on each Maven test gate plus both syntax checks. mysqlIt/migrationIt explicitly enabled where required, including AuthFlowIntegrationTest. Fresh final XML confirms220, including15monitoring; prior epoch4 SQL1267 failure preserved in execution history, not silently waived. Actual production runtime/auth/JDBC/API exercised after gates, owned fixtures cleaned. |
| Downstream interfaces | PASS | Controller:140–153 optional admin filterCounts with exact user1FullExpertCount/pendingReviewDraftCount names; service returns them on empty pages and omits for user1. expertScope=ALL/USER1_FULL/PENDING_REVIEW is strict and admin-only for restricted scopes. Existing pendingBadge.manualReviewTotal remains mail-unit wire; list total is expert-unit, toolbar review count draft-unit. Actual runtime independently verifies these units, owner-only suspension/old queue and unchanged All/disabled-account visibility. No future07/08 UI/routes demanded. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

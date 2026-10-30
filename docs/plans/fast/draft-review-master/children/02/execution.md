# Child 02 — execution

## Discovery / Epoch 1 (superseded identity)

- Original plan identity: `b9bbae9442d052c1ae1a122b25e42ac2020bf1973f0aff76d3bcd823adf79d91`; execution began at evidence HEAD `86f809eb9f1f6ae42eb676e426da2993ead7ae8c`, product base `c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26`.
- First required combined IT invocation used `serverTimezone=UTC`, unlike the repository's default `Asia/Shanghai`. It finished at 22:37:34 +08:00 with 45 tests / 5 failures / 1 error / 0 skipped, exit 1 (`artifact://950`; recovered command snapshot `artifact://951`).
- One new query-instrumentation test incorrectly predicted 9 statements; actual production JDBC execution measured 10. Removed the incidental fixed count assertion, retaining comparison of size=1 and size=100 query growth, explicit reader zero-review-query assertions, and printed measured evidence.
- Four existing repository failures were caused by the UTC override against this JVM's timezone. Restored the repository-default timezone for subsequent fresh commands; no changes to those existing tests or production date/time semantics.
- The remaining pre-existing fixture error was exact SQL at `MailboxConversationRepositoryIT.kt:242`: INSERT into `expert_replied_dismissal` omitted required `dismissed_at`, producing `Field 'dismissed_at' doesn't have a default value`. Reported exact evidence/file to Main before touching that file.
- Controller recorded bounded amendment `2af9b9ca1025c5741086558365f92793357a307e` before the fixture edit, under the user's continuous authorization. It adds that test file only for `dismissed_at` / `NOW()`, preserves every original semantic assertion, and prohibits permanent fixed statement-count assertions. No other baseline repairs were made.

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-02-query.md`
Plan SHA-256: `55fc2fcc6a63d069019d14c570f579e7468f33cf1e1d0f28a5be5082e45f3752`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-02-query.md@55fc2fcc6a63d069019d14c570f579e7468f33cf1e1d0f28a5be5082e45f3752`
Execution epoch: NEW (Epoch 2; reconciled already-written, uncommitted work against the complete amended plan, without replaying it)
Approval basis: current assignment and brief Epoch 2, controller-recorded amendment `2af9b9ca1025c5741086558365f92793357a307e`, user's recorded continuous authorization.
Executor: `DraftReview02Implementer`
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`
Target branch: `fast/draft-review-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master@fast/draft-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-draft-review-master`
Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Pre-execution code SHA / child_base_sha: `c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26`
Pre-execution evidence HEAD: `86f809eb9f1f6ae42eb676e426da2993ead7ae8c`
Epoch 2 governing plan HEAD: `2af9b9ca1025c5741086558365f92793357a307e`
Post-execution code SHA: `1cc5e9f78518468be5fb60b042cf2e7be92ccd0b`
Evidence HEAD at handoff: `1cc5e9f78518468be5fb60b042cf2e7be92ccd0b` (this report remains uncommitted for controller-owned evidence persistence)
Implementation boundary: `c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26..1cc5e9f78518468be5fb60b042cf2e7be92ccd0b`, product delta committed as `feat(fast-p): implement 02`; intervening plan/evidence commits are not product changes.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 / I-8, I-9 | IMPLEMENTED | Conversation controller/service | Exact optional `reviewPendingOnly=false`, item `reviewPendingCount:Long=0`, list `reviewPendingTotal:Long=0`; reader true rejected before database queries, ordinary reader counts remain zero. Real authenticated HTTP and enabled controller gate below. |
| T2 / I-8 | IMPLEMENTED | Conversation repository/service | One shared submitted/nondeleted/real-account predicate feeds expert membership EXISTS, batch counts and complete-scope total. Count/page/explain use the existing common expert predicates. Expert pagination/order and ordinary mail counts remain unchanged. |
| T2 / I-9 | IMPLEMENTED | Conversation repository/service | Account intersection, account existence/non-simulator filtering, disabled-real-account inclusion, non-chip filters preserved; toolbar total clears all mutually exclusive chips. Empty chip result or out-of-range page still returns full-scope badge. Reader never executes review-summary queries; original authorization EXISTS retained. |
| T2 / I-10 | IMPLEMENTED | Conversation repository | Reads current new review table directly, no cache/index/timer or child01 write-path changes. Committed production submit/withdraw/delete/approve/return transitions immediately reflected; revoked grants preserve admin work; deleted contact/account excluded. |
| T3 / I-8–I-10 | IMPLEMENTED | MailReviewQueryIT / controller test | Fresh enabled tests: 5 new query IT cases, 40 repository compatibility cases, 46 controller cases; all successful and non-skipped. Actual HTTP pages, approval/return/revocation/orphan/history smoke below. |
| Authorized baseline fixture repair | IMPLEMENTED | MailboxConversationRepositoryIT | Only the existing INSERT gained `dismissed_at` / `NOW()`; all original assertions retained. Required compatibility gate passed with repository-default timezone. |
| Preserve predecessor/master boundary | IMPLEMENTED | Six authorized product/test files | Child01 streaming review-context reader and shared UNION/membership logic retained; no schema/migration, reader whitelist, old admin draft storage, frontend, ledger, handoff, verifier-report or plan edits by executor. |

### Commands

Every final mandatory command ran freshly under the current amended identity, after final implementation and actual API/EXPLAIN/size100 smoke. Commands ran sequentially, not concurrently.

```sh
# C1
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
  DB_URL='jdbc:mysql://127.0.0.1:33370/talent_introduction?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  DB_USERNAME=root DB_PASSWORD=root \
  mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true -DargLine=-Dapi.version=1.41

# C2 — explicitly enabled; the plan's bare invocation would skip this mysqlIt-gated class
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
  DB_URL='jdbc:mysql://127.0.0.1:33370/talent_introduction?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  DB_USERNAME=root DB_PASSWORD=root \
  mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true -DargLine=-Dapi.version=1.41

# Actual HTTP runtime preparation, not a test substitute
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn dependency:build-classpath -Dmdep.outputFile=/tmp/fast-draft02-classpath -DincludeScope=test
```

| Command | Result | Evidence |
|---|---|---|
| C1 | Exit 0; 45 tests / 0 failures / 0 errors / 0 skipped | Finished 22:44:45 +08:00; `artifact://973`. MailReviewQueryIT 5; MailboxConversationRepositoryIT 40. |
| C2 | Exit 0; 46 tests / 0 failures / 0 errors / 0 skipped | Finished 22:47:58 +08:00; `artifact://975`; Surefire text reports the same counts. |
| Maven-bound frontend hooks in C1/C2 | Exit 0 both invocations | Each ran 1912 JS tests, 1912 pass, 0 fail, 0 skipped; node-check-app and node-check-task-modal-runtime also completed. Not a full JVM suite claim. |
| dependency:build-classpath | Exit 0 | Background command bg_101 completed successfully at 22:34:31 +08:00; generated third-party dependency classpath for production server, excluding target/test-classes. |
| Authorized product `git diff --check -- <six files>` | Exit 0 | No whitespace errors in authorized product delta. An earlier unrestricted diff check exited 2 solely for controller-owned brief trailing blank line; that unrelated document was not edited by executor. |
| Plan/worktree identity helpers, explicit-whitelist staging and commit, HEAD/branch ancestry check | Exit 0 | `artifact://977`; expected root/branch/git-dir enforced before staging and before commit; final HEAD `1cc5e9f78518468be5fb60b042cf2e7be92ccd0b`. |
| Smoke service/container cleanup | Exit 0 | Stopped `fast-draft02-api`; stopped the `--rm` container `fast-draft02-mysql`, removing both isolated databases; removed temporary classpath file. |

No full verification skill, deployment, package/WAR build, push, merge, reset, history rewrite or extra worktree was used. These are executor-run checks, not independent PASS.

### Actual changed API smoke (not MockMvc)

Production `target/classes` ran on JDK11 with embedded Tomcat port 18092, real authentication/cookies, real production services/repositories and a fresh MySQL 8.0.36 schema migrated by Flyway to V169. Third-party dependency classpath did not include target/test-classes.

Runtime:

```sh
env DB_URL='jdbc:mysql://127.0.0.1:33370/fast_review02_smoke?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
  DB_USERNAME=root DB_PASSWORD='<throwaway>' ES_BASE_URL=http://127.0.0.1:19298 \
  AUTH_ENABLED=true MAIL_SCHEDULING_ENABLED=false MAIL_QUEUE_ENABLED=false \
  /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java \
  -cp 'target/classes:<generated-third-party-classpath>' \
  com.weibo.talentintroduction.TalentIntroductionApplicationKt \
  --server.port=18092 --spring.main.lazy-initialization=true \
  --spring.rabbitmq.listener.simple.auto-startup=false
```

Server startup: `artifact://952`. ES was intentionally configured only to a loopback address; external ES/profile behavior was not an acceptance target or claimed successful. Mail endpoints used local-only test fixture settings; no delivery endpoint was called and no production recipient/service was configured.

Initial contacts: Alice=90201, Bob=90202, Carol=90203. Reader created actual drafts through child01 HTTP endpoints after actual admin grants. Alice's first submitted draft targeted real enabled account `review-a`; then a newer real OUTBOUND target on disabled account `review-b` was inserted and her second draft selected that disabled target. Bob had one submitted draft; Carol had one DRAFT.

| Scenario | Observed HTTP/DB result |
|---|---|
| Admin login and initial password change; reader login | 200 / 204 / 200, real sessions. |
| Admin grants; reader create/submit | All successful; drafts 1/2/4 submitted, draft 3 unsubmitted; Alice's second submitted account was exactly `review-b` with enabled=false. |
| GET conversations?reviewPendingOnly=true&page=0&size=1 | total=2, reviewPendingTotal=3, Bob once with count1. |
| GET page=1&size=1 | total=2, reviewPendingTotal=3, Alice once with count2. Existing conversation sort preserved; no duplicate expert cards. |
| GET ordinary list | total=3; Carol retained, original counts unchanged. |
| Filter accountCode=review-a / review-b | Pending totals2 / 1; disabled real account remains visible. |
| followed=true with no marked experts | total=0, toolbar reviewPendingTotal=3. |
| Reader ordinary list | reviewPendingTotal=0, all reviewPendingCount=0. |
| Reader reviewPendingOnly=true | 403 MAIL_READ_FORBIDDEN. |
| Approve Alice draft1 with real observed context token | 200 APPROVED; next pending total2 and Alice count1. |
| Return Alice draft4 | 200 RETURNED; next pending total1, expert total1, Bob only. |
| Revoke Bob's reader authorization | Revocation 200; reader review detail403; admin pending total remains1. |
| Ordinary mail count comparison before/after review actions | Alice [received0,sent2,pending0], Bob [0,1,0], Carol [0,1,0], exactly unchanged. |
| Real GET size=100 after adding97 throwaway conversations | 100 items / total100 / pending draft total1; measured database queries below. |
| Delete Bob's mail/contact fixture | Next pending list total0 / reviewPendingTotal0 / items[]; submitted review row remained in DB. |
| Admin GET known Bob review events after contact deletion | 200, retained SUBMIT and CREATE events. |

34 actual HTTP requests were recorded in executor eval state; decisive values are in “Exercising changed conversation API contract”, “Measuring actual size100 HTTP database queries” and “Confirming orphan removal retains review history” tool results. All throwaway data was removed with the isolated `--rm` container after C2.

### Real query measurements and EXPLAIN

- Fresh C1 instrumentation (`artifact://973`) printed `REVIEW_QUERY_SIZE100 rows=100 sqlCount=10 reviewSqlCount=2`. This counts the actual JDBC conversation repository statements with peripheral repositories mocked in that focused IT; size1 and size100 use the same number of queries. It is not a production latency claim or permanent fixed-count assertion.
- Actual real-server size100 request used MySQL general log on the throwaway server. Recorded **13 SELECTs**, including real session-user lookup, real account lookup and real contact metadata lookup; **2** SELECTs read `mailbox_review_draft`. All request statements in the captured receipt used thread19. Concurrent pre-existing attachment worker lock/queue polling was explicitly excluded from request attribution; it was not included in the13 or hidden as request work. Receipt: executor tool result “Summarizing actual request SQL evidence”.
- Actual SQL categories were auth user; sender accounts; review total; conversation count; conversation page; latest message; latest inbound; account codes; materials; real expert metadata; suspension state; suspension pending count; review page counts. No per-expert review query/body read was issued.
- Actual default-page EXPLAIN used the real captured HTTP SQL, not a manually approximated query (executor tool result “Explaining actual changed API query”): derived conversation rows103, contact PRIMARY eq_ref, OUTBOUND scan101 rows, inbound contact index range, follow PRIMARY eq_ref; temporary/filesort present.
- Actual **reviewPendingOnly=true** page SQL was separately captured from HTTP with general logging, then explained through `PREPARE CONCAT('EXPLAIN ', captured_sql)` after logging was disabled. Real receipt `artifact://966`:

| id | select_type | table | access | key | estimated rows | Extra |
|---|---|---|---|---|---:|---|
| 1 | PRIMARY | subquery6 | ALL | — | NULL | Using temporary; Using filesort |
| 1 | PRIMARY | ec | eq_ref | PRIMARY | 1 | — |
| 1 | PRIMARY | derived3 | ref | auto_key0 | 10 | — |
| 6 | MATERIALIZED | d | range | idx_review_draft_pending | 2 | Using where; Using index |
| 6 | MATERIALIZED | review_account | index | account_code | 3 | Using where; Using index; Using join buffer (hash join) |
| 3 | DERIVED | mr | ALL | — | 101 | Using where |
| 4 | UNION | imp | range | fk_inbound_mail_processing_contact | 1 | Using index condition; Using where |
| 2 | DEPENDENT SUBQUERY | ef | eq_ref | PRIMARY | 1 | — |

The integration gate also executes production `explainConversationsPage` with the same pending/non-chip membership predicates. No claim of scan elimination, index-only global execution or latency/throughput guarantee is made.

### Changed Files

1. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` — exact default-compatible parameter and DTO tail fields.
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` — reader rejection/zero counts, full-scope toolbar total, batched item counts, empty-page handling.
3. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` — common real-account pending predicate, expert EXISTS, total/batch queries; predecessor context reader untouched.
4. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt` — real MySQL scope/state/permission/paging/EXPLAIN/query-growth coverage.
5. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt` — default behavior, real SQL pending projection/pages/chip and reader403/zero regression; review fixture cleanup.
6. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt` — bounded existing fixture INSERT correction only.

### Deviations

- Controller-approved A5/A6 amendment added the single existing fixture correction before that edit; current plan identity above incorporates it.
- Explicit `-DmysqlIt=true` added to controller command so its gate is enabled, not skipped.
- Used isolated local MySQL ports/databases and the repository-default timezone; no runtime configuration or schema file was changed.
- Product commit excludes all fast-p documents. Existing controller-owned dirty brief and ledger were preserved. This separately-authorized report is the only executor-edited evidence document.

### Freshness

- Plan identity rechecked: YES; unchanged at commit/handoff, `55fc2fcc6a63d069019d14c570f579e7468f33cf1e1d0f28a5be5082e45f3752`.
- Worktree identity rechecked: YES; expected canonical root, branch and worktree Git directory enforced.
- Reported commits reachable from target branch: YES; implementation commit is target HEAD and branch ancestry check exited0.
- Required commands run this invocation: YES; both enabled and non-skipped after final amended implementation and actual API/EXPLAIN/size100 smoke.
- Historical evidence used only as baseline: YES; initial failing command is not final gate evidence.
- Product working tree/index clean at commit: YES; only controller evidence documents remained dirty before this report append.

### Remaining Blocker

None known within the approved child02 scope. Independent verification has not been performed or represented as PASS.

### Next Action

READY_FOR_VERIFICATION → controller dispatches the isolated deferred four-gate verifier; executor does not invoke full verification skills or modify ledger/handoff/verifier reports.

## Epoch 2 — Controller Terminal Evidence Record

Independent DraftReview02Verifier returned LIGHT_PASS / COMPLETE_CHILD for `c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26..1cc5e9f78518468be5fb60b042cf2e7be92ccd0b`, fresh required suites45 and46 passed without skips; full report in verify-log.md. Automatic repair rounds0. No whole-system verification or human acceptance claimed.


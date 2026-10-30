# Child 02 — verify-log



## Epoch 2 — Attempt 1 — 2026-10-29T22:59:11+08:00

## Light Verification: LIGHT_PASS

Child: `02` — `docs/plans/2026-10-29/draft-review-02-query.md`

Exactid: `commit:2af9b9ca1025c5741086558365f92793357a307e:docs/plans/2026-10-29/draft-review-02-query.md` (SHA-256 `55fc2fcc6a63d069019d14c570f579e7468f33cf1e1d0f28a5be5082e45f3752`). Read the complete exact committed plan and brief including authoritative Epoch 2; the earlier embedded identity is superseded.

Boundary: `c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26..1cc5e9f78518468be5fb60b042cf2e7be92ccd0b`

Verifier: `DraftReview02Verifier` (fresh independent context, distinct from `DraftReview02Implementer`).

Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`; branch `fast/draft-review-master`.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1 — Authorized files | PASS | Boundary product diff contains exactly the six amended authorized paths listed below. Existing RepositoryIT delta is solely the fixture INSERT addition of `dismissed_at` / `NOW()`; no assertions changed there. Intervening plan/master/ledger/child01 evidence commits are separately authorized historical documentation, not extra child02 product changes. `git diff 1cc5e9f78518468be5fb60b042cf2e7be92ccd0b -- src pom.xml` returned no output before fresh commands. No product/test/index/Git/plan edits by this verifier. |
| 2 — Explicit requirements | PASS | Repository lines 195–341 and 739–933: common `expertPredicates` contains pending EXISTS; count/page/explain share it, existing membership and HAVING. The pending predicate requires SUBMITTED, nondeleted, intersected account scope and existing non-simulator target account, without enabled/grant restriction. Full-scope draft total reuses candidate membership and clears all six mutually exclusive chips; page counts are batched and empty contacts short-circuit. Service lines 114–229 preserves normal counts, original reader authorization, zero reader review queries/fields, and reader true=403. Fresh real-MySQL tests exercise 3 drafts/2 experts, size1 pages, account/q/message filters, chips, disabled/simulator/missing accounts, submit/withdraw/delete/approve/return, grant revocation, missing contact/account, out-of-range/empty pages and size100. EXPLAIN and query measurements below are observed, not performance promises. |
| 3 — Every required command freshly | PASS | C1 exit0: 45 tests, 0 failures/errors/skips (MailReviewQueryIT 5; RepositoryIT 40), artifact://987. C2 exit0: 46 tests, 0 failures/errors/skips, artifact://989. Both ran sequentially after final inspected implementation with explicit mysqlIt=true, JDK11, OrbStack socket, API1.41 and repository-default Asia/Shanghai timezone. Each also ran Maven-bound 1912 JS tests with 1912 pass/0 fail/0 skipped plus both node syntax hooks. Fresh Surefire text reports independently match class counts. No historical command is counted as a fresh gate. |
| 4 — Exact downstream interfaces | PASS | Controller GET list accepts and forwards `reviewPendingOnly: Boolean=false`; service forwards it to `ConversationFilter.reviewPendingOnly: Boolean=false`, whose actual consuming dispatch is the shared SQL expert predicate. DTO tails are exactly `ConversationItemResponse.reviewPendingCount: Long=0` and `ConversationListResponse.reviewPendingTotal: Long=0`; service materializes both, including empty-page totals. Repository `reviewPendingCountsByContacts(contactIds, accountCodes, accountCode): Map<Long, Long>` and `reviewPendingTotal(username, filter): Long` are wired to the service. Existing list fields/defaults, query names, sort, pendingOnly/pendingCount semantics and child01 streaming context reader are retained. Restricted reader handler routing explicitly still allows list; service blocks review selection and skips both review projections. Fresh controller HTTP serialization/binding assertions pass. |

### Authorized product paths and scope receipts

Command: `git diff --name-only c9454b6d5dd193c73b78dfbc99dbe58c8c70ae26 1cc5e9f78518468be5fb60b042cf2e7be92ccd0b -- src` (exit0; artifact://983):

1. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`

The separate fixture-only diff in artifact://983 shows one INSERT replacement, adding dismissed_at and NOW(), with the original behavioral assertions untouched. No schema/migration/static-UI changes in the product delta. No incidental fixed total-statement-count assertion was added to RepositoryIT; the new focused IT records measured statement count and compares page-size query growth.

### Fresh command receipts

Prerequisites freshly observed at `2026-10-29T22:51:55+08:00`: Zulu OpenJDK `11.0.15`, Docker server `29.8.2` through `unix:///Users/lukai/.orbstack/run/docker.sock`. A verifier-owned disposable MySQL `8.0.36` container `fast-draft02-verifier-mysql` published only `127.0.0.1:33371`; application ITs used that empty isolated database, and MailReviewQueryIT created its own real Testcontainers MySQL. No user database/container was modified. No TZ/user.timezone/UTC override was applied.

Both commands below ran from the specified worktree with this exact environment prefix:

`env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock DB_URL=jdbc:mysql://127.0.0.1:33371/talent_introduction?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai DB_USERNAME=root DB_PASSWORD=root`

(The DB_URL value was shell-quoted as a single argument.)

| ID | Required Maven command with enabled gates | Exit / counts | Fresh receipt |
|---|---|---|---|
| C1 | `mvn test -Dtest=MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true -DargLine=-Dapi.version=1.41` | 0; 45/0/0/0 tests/failures/errors/skips | artifact://987; completed `2026-10-29T22:55:50+08:00`, elapsed3:45. ReviewQuery5, Repository40. |
| C2 | `mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true -DargLine=-Dapi.version=1.41` | 0; 46/0/0/0 tests/failures/errors/skips | artifact://989; completed `2026-10-29T22:59:02+08:00`, elapsed3:05. Explicit mysqlIt=true prevents the bare planned command silently skipping this gated class. |

Fresh Surefire summaries read: `target/surefire-reports/com.weibo.talentintroduction.mail.repository.MailReviewQueryIT.txt` (5/0/0/0), `...MailboxConversationRepositoryIT.txt` (40/0/0/0), and `target/surefire-reports/com.weibo.talentintroduction.mail.controller.MailboxConversationControllerTest.txt` (46/0/0/0). These are targeted gates, not a claim of a full JVM suite or WAR build.

Fresh C1 printed `REVIEW_QUERY_SIZE100 rows=100 sqlCount=10 reviewSqlCount=2` (artifact://987 lines749–751). This is focused JDBC instrumentation with peripheral repositories mocked, not the real-server total. Production repository EXPLAIN was freshly executed: pending `d` uses `idx_review_draft_pending`, review_account uses account_code index, normalized conversation derived table participates in the same shared pending membership, and temporary/filesort remains visible. Non-chip filter EXPLAIN paths also executed successfully. No latency or throughput guarantee is inferred.

Cleanup command `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker stop fast-draft02-verifier-mysql` exited0 at `2026-10-29T22:59:11+08:00`; --rm removed the verifier disposable database/container. Product files remained read-only throughout verification. No full verification skill, fix, commit, staging, branch manipulation, deployment, build/package command or new runtime smoke was performed.

### Historical executor smoke inspected — not performed by verifier

Read complete child02 execution.md including superseded discovery/UTC baseline and current Epoch2 evidence. Historical baseline failures are preserved and are not final gate results. The bounded fixture amendment predates the product implementation; old UTC failures did not lead to changed date assertions or production timezone semantics.

Execution.md records actual production Tomcat/JDK11/real-session HTTP on port18092, MySQL8.0.36/FlywayV169, real draft create/submit/approve/return and reader grants. Recorded initial pending pages are expert total2/draft total3, Bob count1 and Alice count2; ordinary list retains Carol, account totals2/1 include disabled account, empty followed-chip result retains badge3, reader counts0 and pending selection403. Approval/return yield3→2→1; revocation preserves admin review while denying reader; deleting the contact removes its ordinary card while known-id admin event history survives; mail count snapshots are unchanged. This verifier inspected that historical evidence, did not repeat those runtime actions and does not represent them as verifier-run smoke.

Read original historical startup receipt artifact://952 (Tomcat18092, Java11.0.15, isolated fast_review02_smoke migratedV169; loopback ES failures explicitly visible and outside these query requirements) and exact pending EXPLAIN receipt artifact://966 (actual captured pending HTTP SQL, idx_review_draft_pending, temporary/filesort). Execution.md records real size100 HTTP100 items and13 request SELECTs, two reading review drafts, excluding unrelated worker queries; this13-query statement remains executor evidence, distinct from freshly observed focused IT10. Historical successful mandatory receipts artifact://973 and artifact://975 were inspected only as history, never substituted for C1/C2 above.

### AUTO_FIX

N/A — no proven in-scope defect and no repairs applied.

### RECORD_ONLY

N/A — no additional issue established in this bounded four-gate inspection; no broad audit was performed.

### Required Action

- COMPLETE_CHILD

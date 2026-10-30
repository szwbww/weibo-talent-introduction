# Child 02 — execution

## Epoch 1 pre-mutation scope evidence

- Plan SHA-256: `ff1565438734fd5ef9a20f7ca275354ae8b9b38f6fa7ea4d1153f39e9761ef99`.
- Target: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`, branch `fast/expert-access-master`, HEAD `0cca6c60ae9908501ffdf79372296e24db48ba01`; product base `30c8af9b98f675f628d3ee98bd737f1716ecf54c`.
- No product mutation or command gate has occurred. Existing dirty brief/ledger are controller-owned.
- Required production single/bulk `access_level` lookup needs a mandatory `ExpertMailReadAccessService` dependency in `MailboxConversationService`. Its existing constructor takes repository, senderAccountRepository, expertContactRepository, expertMaterialService, inboundMailTagService, expertSearchService, mailRecordRepository; none exposes access-level reads. The repository is outside child02 scope. A nullable/default dependency with NONE/READ fallback would introduce a compatibility shim and fail to project actual FULL rights, so it is not proposed.

### Complete constructor grep receipt

Tool: `functions.grep`; pattern: `MailboxConversationService\(`; path: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test`; case=true, gitignore=true, skip=null. Complete returned matching output:

```text
# /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/kotlin/com/weibo/talentintroduction/mail/

## controller/
### OutboundAttachmentFlowTest.kt#BE26
 238:        )
*239:        conversationService = MailboxConversationService(
 240:            mailboxConversationRepository,
 241:            mailSenderAccountRepository,
 242:            contactRepository,

## repository/
### MailReviewQueryIT.kt#993C
 70:        Mockito.`when`(contacts.findAllById(Mockito.anyCollection())).thenReturn(emptyList())
*71:        conversations = MailboxConversationService(repository, senders, contacts,
 72:            Mockito.mock(ExpertMaterialService::class.java), Mockito.mock(InboundMailTagService::class.java),
 73:            Mockito.mock(ExpertSearchService::class.java), Mockito.mock(MailRecordRepository::class.java))
 74:    }

## service/
### MailboxReadAccessProjectionTest.kt#E1B3
 74:        mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
*75:        service = MailboxConversationService(
 76:            repository,
 77:            senderAccountRepository,
 78:            expertContactRepository,
```

### Minimal existing unlisted constructor sites

`src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt:239-247`:

```kotlin
        conversationService = MailboxConversationService(
            mailboxConversationRepository,
            mailSenderAccountRepository,
            contactRepository,
            expertMaterialService,
            inboundMailTagService,
            expertSearchService,
            mailRecordRepository
        )
```

This direct constructor must receive the mandatory access-level dependency; its reader projection fixture must supply actual levels rather than rely on the old existence-only repository stub.

`src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt:71-73`:

```kotlin
        conversations = MailboxConversationService(repository, senders, contacts,
            Mockito.mock(ExpertMaterialService::class.java), Mockito.mock(InboundMailTagService::class.java),
            Mockito.mock(ExpertSearchService::class.java), Mockito.mock(MailRecordRepository::class.java))
```

This direct constructor can reuse its existing `NamedParameterJdbcTemplate(counted)` (`named`, line 61) to instantiate the real mandatory access service, retaining genuine MySQL/query-count evidence. No repository implementation change is needed.

### Pending controller decision

Controller owns any bounded amendment, approved plan/brief/ledger changes and plan-only commit. Executor preserves the no-product-mutation state until notified to re-read the amended exact identity and start a new epoch.

## Epoch 2 inherited frontend gate blocker evidence

Plan identity: `e2d2f68a6f39dbc1c4421d22b6e52bd200ab8fd73a60b11260b57c3b3732ca51`; plan-only authority commit `1fd83001f99533b787b7dc6099678a910648fca2`; nine product/test files authorized.

Exact final-source command, cwd the target worktree:

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:23381/expert_access02?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=fixture mvn test -Dtest=ExpertMailReadAccessIT -DmysqlIt=true -DargLine=-Dapi.version=1.41
```

- Target JVM integration suite: 15 run, 0 failure/error/skip (`artifact://2181:1029-1033`).
- Maven exit 1 after its bound Node phase: 1961 tests, 1960 pass, 1 fail, 0 skip/cancel (`artifact://2181:3709-3716`).
- Full failing assertion (`artifact://2181:3718-3743`):

```text
test at src/test/js/taskActivityCenter.test.js:676:5
I-8: all eleven versioned assets share the same cache key and the old one is gone
AssertionError [ERR_ASSERTION]: a cache key literal must live only in index.html — found in: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/js/mailboxReadOnly.test.js
actual: [ '/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/test/js/mailboxReadOnly.test.js' ]
expected: []
operator: 'deepStrictEqual'
at TestContext.<anonymous> (.../src/test/js/taskActivityCenter.test.js:700:16)
```

Source receipt: `functions.grep` pattern `20261030-expert-access-01|cache key|cacheKey`, absolute target `src/test/js/mailboxReadOnly.test.js`:

```text
 540:        assert.ok(keys.length > 0);
*541:        assert.deepStrictEqual(Array.from(new Set(keys)), ["20261030-expert-access-01"]);
*542:        assert.ok(index.includes('src="mailbox-readonly.js?v=20261030-expert-access-01"'));
 543:    });
 544:});
```

Existing unchanged guard (`src/test/js/taskActivityCenter.test.js:695-700`):

```javascript
        // 键字面量只允许出现在 index.html：脚本与测试都不得再固化它。
        const stragglers = [
            ...fs.readdirSync(staticDir).filter((name) => name.endsWith(".js")).map((name) => path.join(staticDir, name)),
            ...fs.readdirSync(__dirname).filter((name) => name.endsWith(".js")).map((name) => path.join(__dirname, name))
        ].filter((file) => fs.readFileSync(file, "utf-8").includes(CACHE_KEY));
        assert.deepStrictEqual(stragglers, [],
```

Smallest retirement: in mailboxReadOnly.test.js only, retain keys extraction/nonempty check, assert the set of keys has size 1 rather than equalling a pinned release literal; assert the real mailbox-readonly script reference uses `keys[0]`. No new literal pin, no index/resource key change, no taskActivityCenter guard relaxation or change.

The inherited offending literal was introduced before child02. Neither JS file was mutated by this executor. Other distinct required gates and actual isolated API smoke continue under the existing nine-file authority; controller owns any tenth-file amendment and exact new identity before that mutation.

## Epoch 3 — Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-02-access-level.md`

Plan SHA-256: `dc6abdf9c35bac88f360cb7639faaec9a581b4a46b03562c07e5942836dc610c`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-02-access-level.md@dc6abdf9c35bac88f360cb7639faaec9a581b4a46b03562c07e5942836dc610c`

Execution epoch: NEW, epoch 3 / fix_round 0. Epoch 1 and 2 evidence above is retained, not relabelled as current-identity passing evidence. Implementation already authorized in epoch 2 was reconciled with the complete amended epoch 3 plan and retained.

Approval basis: current invocation, continuous recorded human authority, controller plan-only amendments `1fd83001f99533b787b7dc6099678a910648fca2` and `4961c395096eb4bda13e3014319aad01390d44f3`, and epoch 3 brief. The plan's historical DRAFT header does not supersede that explicit execution authority. Executor did not edit either approved plan or ledger.

Executor: `ExpertAccess02Impl`; skill: execute-p. fix-v constraints were consulted for required local checks; this is implementation execution evidence, not an independent verification verdict.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`

Target branch: `fast/expert-access-master`

Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`

Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`

Pre-execution code SHA / child base: `30c8af9b98f675f628d3ee98bd737f1716ecf54c`

Initial invocation HEAD: `0cca6c60ae9908501ffdf79372296e24db48ba01`; epoch 2 HEAD: `1fd83001f99533b787b7dc6099678a910648fca2`; epoch 3 HEAD before product commit: `4961c395096eb4bda13e3014319aad01390d44f3`. Interleaved controller evidence/plan commits are not reported as product changes.

Post-execution code SHA / Evidence HEAD at executor handoff: `6c0981eb4e148e9789c3af49e9d2dca9739c2508`

Product commit: `feat(fast-p): implement 02`, 10 files, 402 insertions, 85 deletions. Only authorized product/test/migration files were staged. This report, controller brief/ledger and approved plans are excluded.

Implementation boundary: product base `30c8af9b98f675f628d3ee98bd737f1716ecf54c` through product commit `6c0981eb4e148e9789c3af49e9d2dca9739c2508`, with controller-owned plan/evidence commits retained in ancestry.

### Task Status

| Requirement | Status | Evidence |
|---|---|---|
| I-1 single level fact | IMPLEMENTED | V170 adds only `access_level VARCHAR(8) NOT NULL DEFAULT 'READ'`; absent row is NONE; service accepts only persisted READ/FULL and rejects unknown persisted values. Boolean get/grant/revoke APIs remain. |
| I-1 explicit switch / legacy compatibility | IMPLEMENTED | Optional body chooses READ/FULL; explicit NONE/unknown/lowercase/numeric/null/missing level yields 400 without a write; no-body upsert never changes existing FULL or first-grant metadata. DELETE remains idempotent NONE. Fresh MySQL tests and real HTTP exercise these paths. |
| I-1 bulk/live projection | IMPLEMENTED | Mandatory non-null access service; page-level batched query, one live lookup per messages request including empty account scope. Admin sees user1's actual grant, not an admin-derived FULL. Constant-query real MySQL review fixture remains passing, size100 sqlCount=11/reviewSqlCount=2. |
| I-2 current auth boundary | IMPLEMENTED/PRESERVED | Admin-only grant controller and service; real AuthWebConfig/MVC NONE/READ/FULL/cross-contact/admin tests; current exact route policy unchanged, FULL does not open any later-child write or download handler. Denied mocks have zero material/search/record/pending/follow interactions. |
| I-4 attachment projection | IMPLEMENTED | READ hides all four attachment fields and makes no attachment-only calls; FULL follows unchanged source/snapshot/account checks. Admin/null internal identity preserves prior attachment behavior even when projected user1 level is READ/NONE. Existing outbound flow assertions unchanged and passing. |
| Inherited review/reader behavior | PRESERVED | Review query fixture retains actual counted JDBC; genuine HTTP create→submit→approve works; FULL still cannot edit/delete APPROVED, and downgrade/revoke is observed live. JS reader functional tests remain unchanged; only fixed-key literals retired under amendment. |
| I-14 scope/migration/evidence | IMPLEMENTED | Exact final ten-file whitelist, only new V170 after V169, no applied migration/config/deployment/resource/index/policy edits. All required final commands ran after final source changes, targeted IT skips=0. |

Future resource guards, user direct SMTP send, owner assignment, profile/calendar/tag writes and final UI controls remain intentionally unimplemented here, as required by the child's no-new-user-write boundary and master staged-release contract. Their eventual A-5/I-4 positive-write acceptance is not claimed by this child.

### Changed Files

1. `src/main/resources/db/migration/V170__add_expert_mail_access_level.sql` — forward-only default READ column.
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt` — enum, strict single/bulk reads, requireRead/requireFull, explicit level upsert and legacy Boolean behavior.
3. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt` — optional level request, admin-first semantic validation and compatible canRead/accessLevel response.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt` — summary/message-wrapper level fields.
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt` — mandatory level dependency, batch/live projection and READ-only attachment suppression.
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` — genuine MySQL V169 upgrade/fresh migration, legacy/explicit/concurrent grants and real MVC auth/projection.
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt` — current level admission, READ zero-attachment interaction, FULL/admin/internal original projection and bulk-level fixture.
8. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt` — only mandatory constructor dependency/level fixture; original assertions unchanged.
9. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt` — only mandatory constructor dependency using existing counted real JDBC; original assertions unchanged.
10. `src/test/js/mailboxReadOnly.test.js` — only two fixed-version assertions converted to dynamic uniform-key/module-reference assertions; no taskActivityCenter guard or index/static source edit.

### Final Commands and Results

All repository commands used cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`. Final command chain completed with exit 0 (`artifact://2197`). Every Maven command below exited 0 independently, as proven by its BUILD SUCCESS and the sequential `&&` chain.

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:23381/expert_access02?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=fixture mvn test -Dtest=ExpertMailReadAccessIT -DmysqlIt=true -DargLine=-Dapi.version=1.41

env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn test -Dtest=MailboxReadAccessProjectionTest

env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=OutboundAttachmentFlowTest,MailReviewQueryIT -DmysqlIt=true -DargLine=-Dapi.version=1.41

env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -DargLine=-Dapi.version=1.41

node --test src/test/js/mailboxReadOnly.test.js src/test/js/taskActivityCenter.test.js
git diff --check
```

| Final command | Result | Fresh evidence |
|---|---|---|
| ExpertMailReadAccessIT | PASS, 15 run / 0 failure/error/skip | `artifact://2197:1029-1033`; BUILD SUCCESS at 3722; finished 12:43:47 +08:00. V169→V170 exactly one migration at 773; empty→V170 169 migrations at 1028. Both pre-existing grants remain READ and first timestamps survive. |
| MailboxReadAccessProjectionTest | PASS, 8 run / 0 failure/error/skip | `artifact://2197:4209-4213`; BUILD SUCCESS at 6902; finished 12:48:47 +08:00. |
| OutboundAttachmentFlowTest + MailReviewQueryIT | PASS, 9 + 5 = 14 run / 0 failure/error/skip | `artifact://2197:7658-7694`; constant-size100 query receipt preserved; BUILD SUCCESS at 10383, finished 12:52:41 +08:00. |
| FlywayMigrationIntegrationTest | PASS, 46 run / 0 failure/error/skip | `artifact://2197:22695-22699`; real Testcontainers MySQL8.0.36, BUILD SUCCESS at 25388; finished 13:05:41 +08:00. |
| Maven-bound full Node suite | PASS in all four final Maven invocations, each 1961/1961, 0 fail/skip/cancel | `artifact://2197:3709-3716,6889-6896,10370-10377,25375-25382`; bound Node syntax phase also completed before each BUILD SUCCESS. |
| Explicit reader/task-activity Node gate | PASS 81/81, 0 fail/skip/cancel | `artifact://2197:25496-25503`. |
| git diff --check | PASS, exit 0 | Last command of same final exit-0 chain, no output. |

Targeted JVM total across final commands is 83 tests, all passing with zero skips. This is not a claim that the entire unrelated JVM suite or WAR packaging was run.

### Earlier Failed Commands (Not Passing Evidence)

- First epoch2 access command with the same access environment/flags above: exit1 at test compilation, missing required `ExpertContact.expertName` in the new fixture (`artifact://2173`). Added the required existing-domain constructor argument within scope.
- Next access command: exit1, 15 tests / 1 failure / 2 errors / 0 skip (`artifact://2177`). New fixture records prevented the existing contact-cascade test from deleting its contact; V16 seeded campaign id1 before upgrade fixture insertion; missing accessLevel in an op1 forged body bound to 400 before the old admin403 contract. Records were moved to the new auth test only, V169 seeded data cleaned before the upgrade-specific fixture, and semantic level validation moved after admin checking via a nullable request field. Existing admin403/cascade assertions were retained, not relaxed.
- Final epoch2 access command: JVM15/15 but entire Maven exit1 due inherited fixed-cache literal / Node1960/1961 (`artifact://2181`). Exact receipt and controller-owned amendment are recorded above. It is not relabelled as PASS.
- Distinct in-flight projection command before epoch3 source correction: exit1, 8 tests / 0 failures / 2 errors / 0 skip (`artifact://2188`). New Mockito String `eq` returned null into a Kotlin non-null parameter, leaving matcher state for the next setup. Used the established non-null matcher fallback for those new fixture calls. No production behavior or assertion was weakened.
- `&&` tails following failed commands did not run; only the final chain above satisfies all final gates.

### Actual Isolated Changed-Path API Smoke

Fresh disposable database command:

```bash
docker --host unix:///Users/lukai/.orbstack/run/docker.sock run --rm -d --name expert-access02-mysql -e MYSQL_ROOT_PASSWORD=fixture -e MYSQL_DATABASE=expert_access02 -p 127.0.0.1:23381:3306 mysql:8.0.36
```

Readiness: `docker --host unix:///Users/lukai/.orbstack/run/docker.sock exec expert-access02-mysql mysqladmin ping -uroot -pfixture` exited0, `mysqld is alive`.

A separate fresh `expert_access02_smoke` schema on the same disposable container was created for the real application, not shared with destructively reset integration fixtures. Runtime classpath command:

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/expert-access02-runtime.classpath -Dmdep.includeScope=test
/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home/bin/java @/tmp/expert-access02-java.args
```

Dependency command exit0; API service readiness on `127.0.0.1:23821`, PID91364. Real Spring Boot application/embedded Tomcat, real session login/AuthInterceptor/controllers/services/JDBC/Flyway; not MockMvc or browser-routed success mocks. Startup proof `artifact://2185`: empty smoke schema applied169 migrations endingV170 and application started on Java11.0.33.

Arguments selected main class `com.weibo.talentintroduction.TalentIntroductionApplicationKt`, classpath `target/classes` plus resolved dependencies, and:

```text
--server.port=23821
--spring.datasource.url=jdbc:mysql://127.0.0.1:23381/expert_access02_smoke?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
--spring.datasource.username=root
--spring.datasource.password=fixture
--talent-introduction.auth.enabled=true
--talent-introduction.elasticsearch.base-url=http://127.0.0.1:23382
--talent-introduction.mail-queue.enabled=false
--talent-introduction.scheduling.enabled=false
--talent-introduction.expert-discovery.enabled=false
--talent-introduction.expert-discovery.cron=-
--talent-introduction.llm.enabled=false
--talent-introduction.llm.auto-reply-enabled=false
--talent-introduction.mail-attachment-storage.base-path=/tmp/expert-access02-attachments
--spring.web.resources.static-locations=file:/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/main/resources/static/
```

ES was a controlled loopback Python HTTP fixture at23382, returning deterministic search profiles/tags and recording POST calls. Initial mapping HEAD/PUT requests received fixture501 warnings; no production ES was contacted. No production DB/SMTP/IMAP was configured or used. Sender fixture was disabled, with loopback unreachable SMTP/IMAP; no email send was requested successfully.

Retained runtime evidence: `/tmp/expert-access02-smoke.json`, 64 actual HTTP responses, request methods/paths/statuses/bodies, provider calls and assertion summaries. Observed:

1. Real login returned admin `mailReadOnly=false` and user1 `mailReadOnly=true`; FULL never changed that global identity contract. Anonymous grant GET401; NONE expert messages403 and reader list total0.
2. Legacy no-body admin PUT created READ. Reader messages had actual body/mail tags and all four attachment projections suppressed; list contained just its authorized expert with accessLevel READ and the controlled expert tag.
3. Explicit FULL then legacy no-body PUT remained FULL. Live reader messages exposed the real inbound-owned attachment; admin returned identical message projection. Admin list showed A=FULL and B=NONE. Non-authorized B messages403.
4. Original source check remained intact: an attachment on an OUTBOUND legacy record was not projected, while processing-owned attachment was projected. The first smoke assertion incorrectly expected both counts to be1; it failed in the external smoke harness only. Reading the existing source contract showed OUTBOUND legacy records intentionally return no incoming-material attachments. Corrected the fixture expectation to MAIL_RECORD0 / INBOUND_PROCESSING1; did not alter product logic or tests to bypass the check.
5. Invalid NONE/ADMIN/numeric level bodies returned400, with FULL unchanged.
6. Existing review workflow created a user-owned draft, submitted it, and admin approved it without a send. FULL user1 PUT/DELETE of that APPROVED draft returned409; detail remained APPROVED. mail_send_attempt remained0.
7. At FULL, 13 denied real requests exercised admin grant/delete, follow, direct rich reply with cross-expert anchor/attachment, incoming attachment list, record/processing attachment download, cross-expert calendar/outbound-snapshot links, meeting calendar, task/account global APIs and review approval. All returned403. Controlled ES delta0; before/after counts identical: attempts0 / mail_records2 (seeded) / mail_tags1 / calendar_events0 / follows0. No SMTP success or new send record occurred.
8. Sixteen concurrent actual HTTP legacy PUT requests all returned FULL and left exactly one grant row.
9. Explicit READ downgrade immediately produced accessLevel READ and zero attachment counts on the next detail request. The APPROVED review stayed readable and locked. DELETE returned NONE; reader messages/review detail became403 and list total0, while admin still read messages with projected level NONE. Missing expert under admin returned404.

No independent OS-level file-stream/SMTP-call instrumentation is claimed for this runtime smoke; actual403 JSON responses, unchanged DB send/side-effect counts, controlled ES call delta and the test-level zero mocked service interactions are the exercised evidence. Three-owner positive downloads and successful user1 SMTP are later-child behavior and were not enabled here.

### Scope / Manual Acceptance / Cleanup

- Both amendments were recorded and committed by the controller before the newly authorized file mutations. Exact plan hashes were reread per epoch; no approval transferred silently across changed bytes.
- No front-end product code, CSS/DOM/index asset key, application.yml, deployment script, authentication policy, unlisted file or later-child feature changed. Existing reader functional/browser contracts are retained. The JS-only tenth file is the explicitly authorized inherited test retirement.
- A-2 final select UI/S-2 and A-5 positive FULL sends remain PENDING for their later implementation/human acceptance; this child supplied real same-session API level-switch equivalents, not a fabricated UI or successful FULL send. No new browser UI surface was implemented by child02; no browser-size/screenshot or final human-acceptance claim is made.
- Required gates ran sequentially after final source writes; none was run while product files were being written. No parallel builds/tests/formatters were used.
- Owned API service stopped normally; controlled ES fixture shut down; `docker ... stop expert-access02-mysql` exited0 and its `--rm` container was removed. Testcontainers cleaned their own disposable containers.
- Temporary Java argument/classpath files were removed; no throwaway source/scripts were added to the repository. Runtime JSON evidence remains outside the repo in `/tmp`.
- No push, merge, reset, rebase, amend, deployment, other worktree mutation or subagent delegation occurred.

### Identity / Commit Evidence and Freshness

- Plan identity command at each epoch: `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-02-access-level.md`; epoch3 hash above rechecked unchanged before stage/handoff.
- Worktree helper used explicit target path and, immediately before both `git add` and `git commit`, `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`.
- Scope/identity receipt: `artifact://2201`; staging receipt: `artifact://2202`; commit receipt: `artifact://2203`.
- `git add --` listed only the ten authorized product/test/migration paths above. `git commit -m 'feat(fast-p): implement 02'` exited0, produced `6c0981eb4e148e9789c3af49e9d2dca9739c2508`.
- Post-commit `git rev-parse HEAD` matched that SHA; `git merge-base --is-ancestor HEAD refs/heads/fast/expert-access-master` exited0.
- Required commands run freshly in final epoch: YES. Historical evidence used only as baseline: YES. Final migration/mysql opt-in gates had no target skips.
- Product/index clean after commit; only controller-owned brief/ledger and this requested uncommitted execution report remain for evidence handling.

### Remaining Blocker

None for this bounded child. READY_FOR_VERIFICATION is not PASS and is not human acceptance.

### Next Action

Controller runs independent verify-p against epoch3 plan identity and product commit; keeps its evidence separate.

## Controller terminal checkpoint — 2026-10-30T05:36:47.375Z
- Terminal independent verdict: LIGHT_PASS_WITH_NOTES; exact report retained in verify-log.md.
- Final child code head: 6c0981eb4e148e9789c3af49e9d2dca9739c2508
- Actual repair rounds: 0; fix commits: —.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.

# Child 01 — execution

## Epoch 1 — Controller Baseline / Paused Before Implementation

- Timestamp: 2026-10-29T18:12:07+08:00 (baseline command completion).
- Actor: fast-p controller; no implementer acquired or dispatched.
- Outcome: PAUSED_FOR_HUMAN; not an execute-p completion report.
- Product base/head: b15bb069f43f89c45ee6859dba04a0baff5d774d.
- Plan SHA256: 7df4d59d9cb3430f5a3523bf4a11216351987a991cda6a4ef1184b603b00e185.
- Seed evidence HEAD: f3417e2 (docs-only; not a product implementation).

### Baseline Command
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true -DargLine=-Dapi.version=1.41
```
Exit 1. Tests 54; failures 33; errors 8; skipped 0. Required runtime JDK11 and Docker available. Original Surefire reports: [baseline-reports.txt](baseline-reports.txt). Full command output was returned as artifact://831.

### Evidence and Scope Conflict
- FlywayMigrationIntegrationTest: 46 tests, 33 failures, 0 errors, 0 skipped. The failing latest-version assertions expect 157 while migrations successfully reached 168. This is pre-existing at the exact approved product base.
- `grep pattern="157|latestVersion|latest version" path="src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt"` locates fixed latest-version assertions including lines 89, 117, 159, 199, 211 and 2957. Original report records all failed assertion locations. This file is absent from child 01 Authorized Files, so the controller cannot repair it or authorize a writer to do so.
- AuthFlowIntegrationTest: 8 tests, 8 errors, 0 skipped. ApplicationContext fails with BeanDefinitionOverrideException: WorkerLifecycleTestConfig tries to register attachmentTransferWorker when the production bean already exists. Surefire XML line 73 records the root cause; original text report preserves the complete exception.
- Governing authority: child 01 T4 and acceptance require real authentication/migration gates; master I-23 and lines 78–80 prohibit out-of-whitelist edits and require plan revision. Continuing cannot produce the mandated successful gate under current authority. No checks rerun to reconfirm these failures.

### Resume Prerequisite
Human-approved bounded plan amendment authorizing obsolete latest-version assertion cleanup in FlywayMigrationIntegrationTest.kt, preserving substantive schema/data migration assertions; no repinning incidental latest-version literals. Allow test-local bean isolation inside already-authorized AuthFlowIntegrationTest.kt, or explicitly authorize an additional test configuration file if needed. Do not enable global bean overriding, skip tests, or edit applied migrations. Record amendment identities and master-rule authority before dispatching a writer. Begin child 01 in a new epoch after approval; product repair rounds used: 0.


## Epoch 2 — Execute-p Result: BLOCKED

This is an explicitly **incomplete / BLOCKED** implementation checkpoint, not READY_FOR_VERIFICATION and not an independent verification PASS. The required migration gate still fails at an existing substantive schema assertion outside the approved baseline-repair boundary. Epoch 1 above is unchanged.

### Identities and authority

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-01-workflow.md`
- Plan SHA-256: `ce0cf1d937dcf3f8f75f4dbb04506ad2b7e4c7218020b217480b482777d9af1a`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master/docs/plans/2026-10-29/draft-review-01-workflow.md@ce0cf1d937dcf3f8f75f4dbb04506ad2b7e4c7218020b217480b482777d9af1a`
- Execution epoch: NEW identity relative to Epoch 1; resumed continuously under this Epoch 2 identity.
- Approval basis: current child assignment, brief Epoch 2, amended plan at `c21b3aa9059d453811f65bc2c3229e1c51fbd997`, and controller's subsequent instruction to preserve the substantive migration boundary while finishing reachable authentication work.
- Executor: `DraftReview01Implementer`; delegated store and test slices, integrated by this executor. No delegated builds/tests were run.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master`
- Target branch: `fast/draft-review-master`
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-draft-review-master`
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-draft-review-master@fast/draft-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-draft-review-master`
- Pre-execution code SHA / child base SHA: `b15bb069f43f89c45ee6859dba04a0baff5d774d`
- Pre-execution evidence HEAD: `02762dd7b53f8b3eabbe6907d5a7b4903b7c424a`
- Post-execution code SHA / local implementation commit: `6205aaf437a0e80642518b7d35f1b230635b7f35`
- Commit subject: `feat(fast-p): implement 01`
- Evidence HEAD at handoff: same code commit; this append-only report remains uncommitted for controller-owned evidence persistence.
- Implementation boundary: child-base product state to the above local implementation commit. The commit contains exactly the amended 11 authorized implementation/test files; fast-p reports and the pre-existing dirty ledger were excluded.
- Identity helpers ran before implementation, before staging, before commit and at handoff. The plan bytes were compared against the approved commit without differences. No extra worktree, deployment, push, merge, reset, amend or history rewriting occurred.

### Task status and affected files

| Requirement | Status | Files / evidence |
|---|---|---|
| T1 / I-3, I-4, I-7 | IMPLEMENTED | V169 creates only the two review tables; typed models and transactional repository preserve immutable identity, CAS updates, snapshots and insert-only events. New-empty and V168-upgrade scenarios passed in MailReviewDraftIT. |
| T2 / I-1, I-2, I-3, I-5, I-7 | IMPLEMENTED | Session-only controller, exact handler whitelist, role/owner/current-grant checks, grant-before-draft write locks, four-state transitions, canonical HTML/text, placeholder and size limits, comments/history/deletion/translation/context confirmation. Final authentication gate passed, actual HTTP lifecycle and revocation smoke completed. |
| T3 / I-6 | IMPLEMENTED | Conversation repository adds a streaming SHA-256 over the existing complete real-mail UNION, stable source/id order and nullable UTF-8 length prefixes. IT context mutations passed; actual SMTP/IMAP new message caused stale approval rejection. |
| T4 / review transactions and authentication | IMPLEMENTED | MailReviewDraftIT: 12 tests passed, no skips. Final AuthFlowIntegrationTest: 9 tests passed, no skips. |
| T4 / complete migration gate | BLOCKED | FlywayMigrationIntegrationTest: 46 tests, 1 remaining substantive schema-assertion failure. Exact evidence and authority boundary below. |
| Authorized baseline repairs | IMPLEMENTED within boundary | Removed obsolete latest-version literals without replacement and retained migrate calls/schema/data checks. AuthFlow uses local scan isolation, preserves application-root repository discovery, and does not enable global bean overriding. No applied migration changed. |
| Master I-23 / I-24 | PRESERVED | Only child 01's amended 11 files changed in the product commit; shared existing tables gain no columns. No later child implementation or ledger/plan/verifier/handoff edits. Evidence is not represented as independent approval. |

Changed files:

1. `src/main/resources/db/migration/V169__create_mailbox_review_draft.sql` — new isolated draft/event store.
2. `pom.xml` — jsoup 1.23.2 dependency.
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` — states, row/DTO/event/context types and local exception.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt` — transactional review persistence, visibility, target resolution and strict event payload decoding.
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` — permissions, canonicalization, lifecycle, history and context operations; no SMTP dependency.
6. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReviewDraftController.kt` — explicit API handlers, strict integral parsing and local error mapping.
7. `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt` — precise reader handler additions only.
8. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt` — additive complete review-context digest query.
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftIT.kt` — real MySQL migration/transaction/concurrency/state/content/context scenarios.
10. `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt` — local test isolation and real-login permission/API scenarios.
11. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` — obsolete latest-version assertions removed; substantive assertions retained.

### Fresh commands, exits and counts

All Maven commands below ran in the target worktree with JDK 11 and OrbStack. These are executor-run results, not historical baseline results.

```sh
# C1 — required review transaction gate
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=MailReviewDraftIT -DmysqlIt=true -DargLine=-Dapi.version=1.41

# C2 — required combined authentication/migration gate
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true -DargLine=-Dapi.version=1.41

# C3 — final reachable authentication-only gate after removing an incidental nonexistent-route assertion
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=AuthFlowIntegrationTest -DmigrationIt=true -DargLine=-Dapi.version=1.41
```

| Run | Command | Exit | Tests / failures / errors / skipped | Evidence / interpretation |
|---|---|---:|---|---|
| 1 | C1 | 1 | 12 / 0 / 12 / 0 | `artifact://856`; explicit test configuration was still supplemented with the main application scan, leaking WorkerLifecycleTestConfig. Fixed local primary test configuration; no global override. |
| 2 | C1 | 1 | 12 / 0 / 4 / 0 | `artifact://859`; test fixture lacked required process_reason, trigger creation required test-server trust setting, and MySQL's 1 MiB packet ceiling could not hold both boundary-size HTML and derived text. Corrected fixture and isolated container startup flags. |
| 3 | C1 | 1 | 12 / 1 / 0 / 0 | `artifact://863`; malformed unterminated placeholder was not rejected by existing helper regexes. Added explicit remaining placeholder-opener rejection after calling the planned helpers. |
| 4 | C1 | 0 | 12 / 0 / 0 / 0 | `artifact://867`; first successful review gate. |
| 5 | C2 | 1 | 55 / 13 / 9 / 0 | `artifact://889`; remaining same-line obsolete version literals and missing root repository discovery in isolated AuthFlow config. Completed authorized literal removal and local root auto-configuration package registration. |
| 6 | C1 | 0 | 12 / 0 / 0 / 0 | `artifact://892`; final required review gate after those authorized test changes, finished 20:05:21 +08:00. |
| 7 | C2 | 1 | 55 / 2 / 0 / 0 | `artifact://893`; Flyway's substantive V120 index assertion and the new test's nonexistent `/action` route expectation. AuthFlow 9 / 1 / 0 / 0; Flyway 46 / 1 / 0 / 0. Finished 20:18:00 +08:00. |
| 8 | C3 | 0 | 9 / 0 / 0 / 0 | `artifact://896`; final auth result after controller instructed removal of the incidental nonexistent-route assertion. Existing real denied write/send/attachment/follow/password endpoint coverage remains. Finished 20:22:57 +08:00. |

The final change after run 7 was deletion of that incidental test assertion only; no production code or migration changed. C1's final run and C2's failure evidence remain applicable to the unchanged product/migration state. C3 proves the final AuthFlow file. C2 is deliberately not described as passing or silently replaced by C3; its migration gate remains incomplete and blocked.

Additional commands:

- `git diff --check` — exit 0, including the final pre-staging check.
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/fast-draft01-smoke-classpath -DincludeScope=runtime` — exit 0.
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/fast-draft01-smoke-classpath-all -DincludeScope=test` — exit 0; added provided servlet runtime dependencies for direct Java startup, without including target/test-classes.
- `plan_identity.py`, `worktree_identity.py`, explicit-whitelist `git add`, exact-subject `git commit`, `git rev-parse HEAD`, and branch ancestry check — exit 0.
- No WAR/package build or independent verification skill ran; neither is claimed.

### Actual API and mail-transport smoke

This was a real HTTP server using production `target/classes`, embedded Tomcat, authentication sessions and a fresh MySQL database, not MockMvc or service mocks. Java runtime was the required zulu-11. Third-party classpath was generated above; test source/classes were not on the server classpath. Runtime arguments included:

```sh
env DB_URL='jdbc:mysql://127.0.0.1:33369/talent_introduction?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' DB_USERNAME=root DB_PASSWORD='<throwaway>' ES_BASE_URL=http://127.0.0.1:19299 AUTH_ENABLED=true MAIL_SCHEDULING_ENABLED=false MAIL_QUEUE_ENABLED=false \
  /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp 'target/classes:<generated third-party dependency classpath>' \
  com.weibo.talentintroduction.TalentIntroductionApplicationKt --server.port=18091 --spring.main.lazy-initialization=true --spring.rabbitmq.listener.simple.auto-startup=false
```

The server started successfully (`artifact://872`). Lazy initialization avoided eagerly touching unrelated timezone-catalog functionality; that functionality was not tested or changed. Only localhost test infrastructure was configured:

- MySQL `mysql:8.0.36`, container `fast-draft01-smoke-mysql`, localhost 33369; application Flyway reached 169.
- GreenMail `greenmail/standalone:2.1.3`, container `fast-draft01-smoke-mail`, SMTP 33025 / IMAP 33143, throwaway smoke mailbox.
- Real Elasticsearch `docker.elastic.co/elasticsearch/elasticsearch:7.17.26`, container `fast-draft01-smoke-es`, localhost 19299, single-node, security disabled, 256 MiB heap; cluster health GREEN.

Throwaway account `smoke-review` was disabled for automatic outbound, but remained a valid real receiving/context account. Expert A=90101 was authorized through the actual admin grant endpoint; B=90102 was not. Initial processing and outbound rows intentionally shared numeric id 90101. The created draft selected `INBOUND` target 90101, not a mail_record-derived processing identity.

Observed HTTP/DB evidence, with cookies/passwords omitted:

| Scenario | Observed result |
|---|---|
| Anonymous review list | 401 UNAUTHORIZED. |
| Admin login/change initial password; reader login | 200 / 204 / 200. |
| Admin grants A; reader creates two drafts | Grant 200; draft IDs 1 and 2, both 200 and distinct. |
| Reader creates for unauthorized B | 403 MAIL_READ_FORBIDDEN. |
| Admin reads draft 1 before first submission | 404 REVIEW_DRAFT_NOT_FOUND. |
| Reader calls real initial-outreach POST and attachment download path | Both 403 READ_ONLY_FORBIDDEN. |
| Unsafe HTML create | `<img onerror>` removed; `<b>version</b>` retained; response did not contain expertEmail. |
| Save second version then save using stale version 1 | 200 then 409 REVIEW_VERSION_CONFLICT. |
| Submit → withdraw → save → submit → admin return → resubmit | All successful, submitted version 8; original and second content snapshots present in events. |
| Both actors comment on draft 2, then reader deletes it | Both comments 200; delete 200; detail exactly `{id:2,deleted:true}`; default list excludes it. Events: DELETE, COMMENT, COMMENT, SUBMIT, CREATE. |
| Admin observes draft 1 context | Token `158bd6c62be3ed1dd34edee81892e248b8d0972b4ddb5063fdd23e918e61de55`. |
| Real inbound SMTP → IMAP → existing receive endpoint | GreenMail accepted the test email; final receive response fetched=1, recorded=1, replied=0, manualReview=1, meetingInvitations=0. Timeline contained `<smoke-context-new-2@example.test>`. |
| Approve using old observed token | 409 REVIEW_CONTEXT_CHANGED. |
| Refresh context then approve | Token `b4a8790b589165bcca24ec7cb4343c45a95b149230b0e686dcd1951d0a7a6508`; 200 APPROVED, version 9. |
| Reader save/withdraw/delete/comment after approval | Each 409 REVIEW_STATE_CONFLICT. |
| Admin comment and confirm-context after approval | Both 200; confirmation event id 16. |
| Admin translate current approved text | 200 `{ok:false,translatedText:null,reason:"TRANSLATION_DISABLED"}` under the intentionally disabled provider configuration; no successful provider translation is claimed. |
| Approval and confirmation outbound side effects | OUTBOUND mail_record count was 1 before and remained 1 after; no review operation sent mail. |
| Admin revokes A, reader reads deleted events or comments | Revocation 200; both reader operations 403 MAIL_READ_FORBIDDEN, without body disclosure. |

Smoke request recorder observed 46 real HTTP requests, including the initially failed receive attempt described next. Approved history in chronological order: CREATE, SAVE, SUBMIT, WITHDRAW, SAVE, SUBMIT, RETURN, SUBMIT, APPROVE, COMMENT, CONTEXT_CONFIRMED. Evidence is in the executor's actual eval/tool transcript under “Completing real mailbox approval smoke” and “Completing frozen-state and revocation smoke”; the decisive response values are reproduced above.

Smoke setup issues were not concealed: first fixture insert used nonexistent `index_level`; corrected to the existing `current_index_level` before inserting contacts. The first receive initialized the existing inbox cursor and intentionally imported no history. A subsequent real fresh email initially hit an absent localhost ES backend and returned 400; logs identified the required ES HEAD lookup. Starting the real isolated ES backend resolved that infrastructure prerequisite; a premature ES HTTP request closed before startup completed, after which startup logs and GREEN health established readiness. The retained inbound was then processed successfully through the unchanged receive pipeline. No fake ES or SMTP implementation was substituted.

Cleanup: stopped the smoke Java service, stopped/removed all three explicitly named throwaway containers (`docker stop` exit 0; containers were launched with `--rm`), and removed the two temporary classpath files. No production recipient, mailbox, database or ES instance was used. No smoke scaffolding was committed.

### Exact remaining blocker and preserved evidence

Final Flyway Surefire text:

```text
Test set: com.weibo.talentintroduction.campaign.repository.FlywayMigrationIntegrationTest
Tests run: 46, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 543.79 s <<< FAILURE!
V120 scopes inbound uid uniqueness by uid validity without backfilling history
org.opentest4j.AssertionFailedError: expected: <true> but was: <false>
  at ...FlywayMigrationIntegrationTest.V120 scopes inbound uid uniqueness by uid validity without backfilling history(FlywayMigrationIntegrationTest.kt:2328)
```

The unchanged assertion at line 2328 is:

```kotlin
assertTrue(connection.indexExists("inbound_mail_processing", "uk_inbound_mail_processing_uid_validity"))
```

The already-applied V167 migration explicitly drops that index:

```sql
-- src/main/resources/db/migration/V167__scope_inbound_by_folder.sql:7-12
ALTER TABLE inbound_mail_processing
    ADD COLUMN folder VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'INBOX',
    DROP INDEX uk_inbound_mail_processing_owner_uid,
    DROP INDEX uk_inbound_mail_processing_uid_validity,
    ADD UNIQUE KEY uk_inbound_mail_processing_source (mailbox_owner_code, folder, uid_validity, imap_uid),
    ADD KEY idx_inbound_logical_uid (sender_account_code, uid_validity, imap_uid),
```

This is not a stale latest-version literal. The approved amendment authorizes only that literal cleanup while retaining meaningful schema/data assertions. Changing this schema expectation, skipping the test, targeting an earlier migration instead, or editing V167 would exceed that authority. The assertion and applied migration were preserved. The first failed assertion prevents claiming the rest of that test's later assertions succeeded.

The unrelated newly-added `/action` assertion was classified as incidental: no such consumer-visible API handler exists, and the approved API explicitly prohibits a generic action endpoint. It exercised existing unsupported-route dispatch (500), not an allowed handler's permission contract. Per controller instruction it was removed rather than pinning 500 or adding a forbidden endpoint. The final real-login authentication suite passes and real denied write/send/attachment coverage remains.

### Deviations, freshness and next action

- No authorized invariant or acceptance requirement was silently reduced. The full required migration gate remains failed and prevents READY_FOR_VERIFICATION.
- Existing dirty `docs/plans/fast/draft-review-master/ledger.md` was preserved, not edited/staged by this executor. This report is the only executor evidence-document edit.
- Plan identity rechecked: YES, unchanged SHA above.
- Worktree identity rechecked: YES; local implementation commit is HEAD of the exact target branch and passed ancestry check.
- Required commands run freshly in this invocation: YES, mandatory IT properties enabled and target tests non-skipped. Final test-only incidental-route removal additionally verified by C3; C2 remains a recorded failed gate, not a PASS.
- Historical evidence used only as baseline: YES. No epoch-1 baseline command was rerun merely to reconfirm its old failure.
- Reported product commit reachable from target branch: YES.
- Working-tree/index product state after commit: implementation committed; only pre-existing controller ledger dirtiness and this designated report remain.
- Remaining blocker: human-approved bounded amendment for the substantive V120/Flyway schema expectation after V167. No further product or authentication implementation blocker is known from the exercised evidence.
- Next action: controller persists BLOCKED checkpoint and obtains the bounded schema-test decision. Then execute only the newly authorized repair and rerun all mandatory final gates before independent verification. Do not dispatch child 02 from this incomplete checkpoint.


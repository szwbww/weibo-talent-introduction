# Child 04a — independent light verification

## Light Verification: LIGHT_PASS
Child: 04a — docs/plans/2026-10-29/expert-access-04a-profile-tags.md
Boundary: d5093a2509427526784254aba7dbe98ff2a9b1d9..020bda37127f92187589e9d8f10e3bb3cbccb187
Verifier: ExpertAccess04aVerifier

Epoch: 2; fresh attempt: 1; date: 2026-10-30.
Plan identity: commit:c3afa57cfe2b448470b8968ac7cc47e24963bbc9. Independent `git show <identity>:docs/plans/2026-10-29/expert-access-04a-profile-tags.md | shasum -a 256` and current-file checksum both returned `2f2c40c18aa1e625fda490de27f82224603e41b25b5a246c37dca34fcb6cbdbf`. Branch `fast/expert-access-master`, HEAD exactly review head; boundary ancestry command exit0.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary `git diff --stat` / product diff (`artifact://2349`) show exactly the 11 authorized product/test files. Seven interleaved controller-owned plan/evidence files excluded from product scope as instructed. Four product controllers/policy only; no frontend, persistence schema, calendar, sending or later-child implementation change. Fixture amendment and noise-line-only changes independently inspected (`artifact://2358`); existing scanner path/context remain unchanged. |
| Plan and invariants | PASS | ExpertIndexController.kt:367–399 guards normalized identity plus current layer/FULL before ES; ExpertManualProfileController.kt:57–91 guards normalized identity/current layer before service and explicitly maps permission failure403, preserving CAS409. InboundMailSummaryController.kt:152–216 resolves actual processing/tag ownership before writes and uses session actor. ExpertScopedOperationsIT.kt:125–228 supplies direct actual JDBC/MVC evidence for NONE/READ/FULL, layer changes, shared-identity sibling isolation, QA/CUSTOM, admin/background, zero-effect denial and CAS. Independently exercised real runtime below: 48 HTTP requests, statuses 17×200 / 29×403 / 1×409 / 1×401; final JDBC profile version1/user1, two correctly owned tags, sibling grants0, outbound0; downgrade rejects all eight handlers without ES calls. |
| Required commands | PASS | Fresh sequential exact scoped command exit0: 5 JVM tests, failures0/errors0/skips0 (`artifact://2355`, completed16:33:20+0800). Fresh sequential exact original command exit0: 116 JVM tests, failures0/errors0/skips0 (`artifact://2359`, completed16:37:43+0800). Both used Java11.0.33, actual MySQL Testcontainers, `-DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41`, OrbStack Docker socket. Each Maven invocation also ran 1961 JS tests/352 suites, pass1961/fail0/skipped0/cancelled0/todo0, and both bound JS syntax checks. Prior writer seed failures are baseline history, not fresh failures; no target was skipped. |
| Downstream interfaces | PASS | MailReadOnlyPolicy.kt activates exactly the eight guarded handler/verb additions; exhaustive AuthInterceptorTest regression included in116. Existing tagOptions remains available. Current normalized profile identity, true current layer, CAS, strict403, READ projection and contact-specific mail ownership verified directly; shared profile access does not grant sibling mailbox access. No future04b/calendar/direct-send/finalUI positive authority claimed or required here. |

Fresh commands (worktree above; executed sequentially, no concurrent Maven):

```sh
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=ExpertScopedOperationsIT -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=ExpertManualProfileControllerTest,ExpertManualProfileServiceTest,MeetingCalendarControllerTest,MeetingCalendarServiceTest,MeetingCalendarSendIntegrationTest,MailReviewDraftIT,ExpertIndexControllerTest,ExpertIndexControllerMvcTest,InboundMailSummaryControllerTest,AuthInterceptorTest,OperatorStatusWriteSeamGuardTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert04a-verifier.classpath
```

Independent actual changed-path runtime (beyond tests):

- Actual Java11.0.33 application main `com.weibo.talentintroduction.TalentIntroductionApplicationKt`, classpath strictly `target/classes` plus freshly built test-scope dependency classpath, not WAR or target/test-classes; PID42776, HTTP loopback18484. Disposable `mysql:8.0.36` container `expert04a-verifier-mysql`, loopback33484, database `scoped_runtime`; real Flyway/JDBC. URL explicitly used `serverTimezone=Asia/Shanghai`. Controlled in-memory ES HTTP provider on19484, not represented as a real ES cluster. Scheduling, discovery and queue disabled; temporary attachment path; no production resources or SMTP delivery.
- JDBC executed through Java11 jshell/DriverManager, not mocked repositories: seeded FULL8401, READ8402, NONE8403, same-normalized-identity/no-grant sibling8404 in separate campaign. Real bootstrap credentials/cookie sessions authenticated user1/admin; real `/api/auth/me` confirmed reader session.
- Initial19 denial requests (READ/NONE expert tag add/remove/get/profile get/patch; READ/NONE/sibling mail add/auto; wrong RAW layer; global expert/summary routes) all403, controlled ES delta0; independent JDBC snapshot profile0/tag0/mail-record0.
- FULL expert add/get/remove/get all200; controlled provider recorded six actual HTTP requests to candidate `_search` / `_update/verify-doc-a`, preserving original `新发现` / `瑞典` tags. Normalized `" VERIFY-A "` profile get200/version0; patch200/version1; stale repeat409; admin RAW read200/same version.
- FULL custom tag body forged `operatorName=admin`; actual persisted actor user1. Admin foreign custom tag persisted admin. Foreign delete403, own delete200; own auto-tag200, options200; retained own tag supports post-downgrade check. READ conversation messages200 preserved foreign CUSTOM label/accessLevel READ, attachmentCount0, firstAttachmentNames[], calendarAttachment null, outboundAttachments[].
- Live JDBC grant downgrade8401 to READ, same cookie session: expert add/remove/get, manual-profile get/patch, mail add/auto/delete all403, controlled ES delta0. Anonymous profile401 and reader grant-upgrade PUT403.
- Final independent JDBC: `VERIFY-A | version1 | updated_by=user1 | talent_code=verify0042`; tags `8402/foreign-label/admin`, `8401/audit-retained/user1`; sibling grant count0,8401 READ,outbound count0.
- Instrumentation correction disclosed: runtime harness initially read tag response key `id`; actual wire key is `tagId`, causing a local KeyError after successful first tag write. Independently read persisted identifier through JDBC and continued without repeating that mutation or changing product. This is not a product failure.
- Cleanup observed: actual app terminated/waited exit143; ES server closed/thread stopped; owned `--rm` MySQL container stopped successfully; own classpath and attachment directory removed. No fixture source files created, no product/test/index/commit changes. Browser N/A: no UI change in authorized boundary. Known inherited foreign-anchor500 observation was not rerun or reopened; it is outside this child's gates.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

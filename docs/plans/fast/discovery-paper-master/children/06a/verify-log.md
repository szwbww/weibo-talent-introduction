# Child06a light verification log

## Epoch 1 — Verification not dispatched
- Execution PLAN_CONFLICT: mandatory Maven finalstate exited1; JVM12passed/0fail/error/skip, boundNode1816/1787passed/29failed across28 unauthorized JS testfiles.
- Exact inventory and unchanged-byte provenance at child06a base are in execution.md:139–196. Prior05 module introduced17th asset, not06a. Separate actualSQL/TCPHTTP settings smoke exited0. No independent light verdict or waiver of failing gate.


## Epoch 2 — Attempt 1

## Light Verification: LIGHT_PASS
Child: 06a — docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md (amended child/master authority 6112a58b4bc94b510a6b1cff88cfd9ec159c83d6; A9/A10)
Boundary: 1b8c4981f0116e17648f064482e505d436c02edd..16a138852bea292d40b47dd9cb2d7fc0c8732578
Verifier: ApolloPersistenceVerifierE2

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary `git diff --name-only ... -- src` contains the five authorized implementation/IT paths plus exactly the 28 amended JS paths (33 total); complete receipt artifact://405. `git diff 16a138852bea292d40b47dd9cb2d7fc0c8732578 -- src` was empty; HEAD names fast/discovery-paper-master at the review head. Governance/evidence changes in the full boundary are not additional product changes. Full JS diff artifact://403 has 35 insertions/35 deletions: count/description/unique-cardinality 16→17 and actual ordered discovery-papers.js insertion in mailboxChatBehavior and mailboxGroupPush; no other assertions removed or weakened. index.html:11–17,2456–2466 confirms seven CSS/ten versioned JS, module before app.js and unversioned task-modal-runtime.js. No frontend product/runtime wiring change. |
| Plan and invariants | PASS | V160:1–12 matches exact singleton/check/no-seed DDL. Repository:32–85 implements validated find/save, transaction/SELECT FOR UPDATE, revision0 insert1, stale-version conflict, no-op preserving time/version, CAS increment/readback, UTC millisecond conversion and invalid stored-row rejection. Service:24–60,86–112 implements strict frozen types/fields/source revisions, independent flags, deployment defaults without persistence, separate service/key/worker availability and sanitized fail-closed storage errors. Controller:14–42 implements exact GET/PUT, required integral nonnegative revision/two booleans, unknown-field rejection, 400/409/503 dispatch. AuthWebConfig:24–27 covers the route without exemption. SettingsIT:64–236 exercises combinations, recreation, no-op, both races, malformed HTTP/snapshot inputs, unavailable/invalid storage and real schema enforcement. Fresh runtime output below proves actual SQL/TCP HTTP and no non-setting changes/provider requests/key leakage. |
| Required commands | PASS | Fresh JDK11 Maven command below exited 0 / BUILD SUCCESS at 2026-10-24T09:10:33+08:00 (artifact://407). Existing controller 4/4 and SettingsIT 8/8, total JVM12 failures0/errors0/skipped0; full bound Node1816/1816, suites334, failures0/cancelled0/skipped0/todo0; bound node-check-app and node-check-task-modal-runtime succeeded. Separate actual SmokeLauncher exited0 at 09:11:08+08, after Maven, using all127 entries of the freshly generated surefire.test.class.path. Results match epoch2 baseline; epoch1 inherited Node29 failures across28 paths are resolved without exclusions. |
| Downstream interfaces | PASS | Service:14–60,64–93 exposes resolve/save/freeze and FrozenApolloAccess.fromSettings/toJson/parse with exactly schemaVersion=1, settingRevision, searchEnabled, detailEnabled, source. New snapshots permit SAVED with positive revision or DEPLOYMENT_DEFAULT/revision0; parse explicitly accepts LEGACY for old-run interpretation, rejects unknown source/schema/missing/extra/coerced/overflow fields. Consumer routing was traced through controller response/error handlers and the shared parser; usage grep finds no current worker/registration consumer, deliberately deferred to06b by this child. Interface-only comparison with06b:22,64,72–84 matches its apolloAccess envelope and same-parser handoff; no later-child implementation review or runtime adoption claim. |

Fresh gate-3 command, cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryApolloSettingsIT,DiscoveryVerificationControllerTest -DmigrationIt=true
```

Exit 0. Fresh actual SQL first-write race in Maven: `APOLLO_FIRST_WRITE_RACE emptyLockedReads=2 statuses=[200, 409] revision=1`. Full Maven result receipt: artifact://407:462,737,916–923,3415–3432. No check was substituted with a historical execution result.

Separate smoke exact executed argument vector: `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java`, `-cp`, the complete property `surefire.test.class.path` parsed from the newly generated `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsIT.xml`, `com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsSmokeLauncher`. Launched by Python `subprocess.run(command, cwd=w, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)`; process return code was propagated. No shortened/manual dependency classpath. XML tests8/errors0/failures0/skipped0/time27.952s; classpath127 entries, SHA256 `f3b59342667efda4decb557407c65553243f487e1361d23b82ba322cd836228e`.

Observed separate smoke output:

```text
Successfully applied 159 migrations to schema apollo_settings, now at version v160
APOLLO_RUNTIME_FLOW defaultGET=200/revision0 firstPUT=200/revision1 recreatedGET=200/SAVED independentPUT=200/false/true/effectiveDetails=false stale=409 unknown=400 anonymousGET=401 anonymousPUT=401 nonSettingTables=93 nonSettingChanges=0 providerObserverRequests=0 snapshotRoundtrip=true
APOLLO_FIRST_WRITE_RACE emptyLockedReads=2 statuses=[200, 409] revision=1
APOLLO_RUNTIME_MYSQL_RESTART previousPort=32873 currentPort=32874
APOLLO_RUNTIME_OUTAGE stoppedMysqlGET=503 stoppedMysqlPUT=503 restoredSavedRow=true nonSettingChanges=0 providerObserverRequests=0 keyLeak=false
APOLLO_RUNTIME_EXIT 0
```

Disposable MySQL8.0.36 plus real loopback embedded Tomcat/DispatcherServlet and existing API login interceptor; race barrier follows two actual empty locked reads. MySQL was actually stopped/restarted. Only setting saves and isolated fixture setup/cleanup occurred; all93 non-setting table counts/checksums, including seeded immutable run snapshot, remained unchanged across the tested flows. No outside provider requests or production/business database writes. Auth identity lookup is fixture-stubbed. Recreated service/controller is not a full production application restart. No full Kotlin suite, WAR build, browser/human/production acceptance, provider behavior, or later-child verification claimed. Review is limited to the four light gates. Only this report was appended; epoch1 preserved, no product/test/plan/index/commit/branch edits or repair performed.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

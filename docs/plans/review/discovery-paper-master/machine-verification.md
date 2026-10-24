## Epoch 1 — 2026-10-24 — Verification Result: FAIL

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-master.md`  
Phase: aggregate/master  
Reviewer: `/root/aggregate_master_reviewer`  
Implementation boundary: `c6e9709f349f32c5feb1d067b47c697b0dff05b6..37da0edbdb9737ed75c27ea8e6e525e35ef78354`  
Evidence head: `ef84aabfc0e509a8178c626f22c430dcdffb78bb`  
Convergence: INITIAL  
Manual acceptance: PENDING  
Prior aggregate review/repair lineage: N/A  
Post-repair evidence mode/executor metadata: N/A

### Identity and boundary

All commands used cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`, abbreviated **W** below. Source paths in tables are relative to W unless absolute.

| Identity | Verified value |
|---|---|
| Branch | `fast/discovery-paper-master` |
| Governing master commit | `3c5a68ad9917fdee22e0548b5afc57a6df04d580` |
| Governing master SHA-256 | `fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3` |
| Invoked-copy SHA-256 | `ae1f7d432643853aba3071975167441a2b9607ca04151021617a90f6ae2f215f` |
| Master identity state | AMENDMENT_RECORDED; A1–A16 govern |
| Fast ledger SHA-256 | `de7e31a72c3f931aacbd8fe285f34c398ab54ec344c308c72031b83ab6c2f87d` |
| Handoff SHA-256 | `ee4944f00f684918b75d23e7d569212ad93333d02e0f77c1a2e362250e5b7956` |
| Resolution | DISCOVERED_FROM_GIT_WORKTREES; supplied discovery selected exactly this retained worktree and eight children |
| Later commits | `476b6ae`, `33c2ab4`, `ef84aab`; evidence only, no `src/` or `scripts/` delta |
| Final tracked state | No tracked modifications; index empty; HEAD unchanged |
| Reviewer-created repository artifact | Only `docs/plans/fix/discovery-paper-master/repair.md` |
| Pre-existing controller artifact | Untracked `docs/plans/review/discovery-paper-master/ledger.md`; untouched |

Fresh byte comparisons against each recorded child commit and ancestry checks passed:

| Child | Recorded commit | Current SHA-256 |
|---|---|---|
| 01 | `c18dbc060dcf4c8440f853ede4d1d3aaf737994f` | `6e1d78f48e71e9a518ce6cb7ea8de1cbd6ae2af76a0d837351aacd693d9ee0d3` |
| 02 | `87c5268976807c19c165fe50c91ba3372ed0075d` | `53381edc616c15ae87db31c1abacea4a4652702a60432dec0aa713fb57af11c4` |
| 03 | `8d2313da868251b68dcaee02b744dfa777b41674` | `c46019e05d5315854277057d2550541093b5f4649a7961c2f4a30f9ccf999abe` |
| 04 | `bcf5d64fb9534759eb1cad08db3089d36e4a1fb0` | `d506cc357820388ec3ef7a094314c28e58196db9800c84df52f0757459ecbf49` |
| 05 | `541411d540019428c72b31775c325087b33da303` | `4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00` |
| 06a | `6112a58b4bc94b510a6b1cff88cfd9ec159c83d6` | `9802f4f4b4009a8dfbf57eb158efb83e32a32e5badc7f8f27f5fddc4b6aa3fc7` |
| 06b | `0199430a0b4f30f89b8fb240d58880167471b512` | `41c6fd43b5cd37153a524d251cc9283e15e8d73581a423851b053db91324b170` |
| 07 | `3c5a68ad9917fdee22e0548b5afc57a6df04d580` | `b1fc292788236a5620340395e393a97216cac516df449a2ad3f1ccfdd703fa69` |

The exact invoked/governing master diff was freshly read using:

```sh
diff -u /Users/lukai/IdeaProjects/weibo-talent-introduction/docs/plans/2026-10-23/discovery-paper-master.md /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-master.md
```

It contains the recorded scope/count/order exceptions and amendment records, including A16’s exact three-test synchronization. No unapproved amendment was substituted.

### Combined scope inspection

Fresh per-child Git deltas were compared with each amended plan’s exact file table.

| Child | Changed product/test files | Authorized | Unexpected |
|---|---:|---:|---|
| 01 | 9 | 9 | None |
| 02 | 10 | 10 | None |
| 03 | 7 | 7 | None |
| 04 | 10 | 10 | None |
| 05 | 7 | 7 | None |
| 06a | 33 | 33 | None |
| 06b | 11 | 11 | None |
| 07 | 8 | 8 | None |
| Cumulative unique union | 78 | 78 | None |

Migration edits are new V159/V160 only. Product frontend changes occur in 05/07; 06a’s additional files contain resource-test synchronization. Shared-file history follows the ordered children. Product/script `git diff --check` passed. Whole-boundary whitespace checking returned exit 2 solely for trailing whitespace in the seeded documentation snapshot `discovery-paper-code-receipts.md`; no behavioral finding.

### Commands

JDK: `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`, Zulu 11.0.15. Maven executable: `/opt/homebrew/bin/mvn`.

**C-1 — Required JVM union, freshly executed:**

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home /opt/homebrew/bin/mvn test -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,SourceAuthorEmailResolverTest,DiscoverySampleReplayTest,DiscoveryPaperLayoutRegressionTest,PaperQualificationEvidenceExtractorTest,DiscoveryPaperAnalysisServiceTest,DiscoveryApolloSettingsIT,DiscoveryVerificationControllerTest,DiscoveryVerificationWorkerTest,DiscoveryCountrySupplementFlowIT,DiscoveryCountryRegistrationTest,DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRepositoryIT -DmigrationIt=true -DmysqlIt=true
```

Exit 0; BUILD SUCCESS; 10:29 elapsed. Log: `/private/tmp/aggregate-discovery-maven.log`.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryPaperArchiveRepositoryIT | 4 | 0 | 0 | 0 |
| DiscoveryPaperArchiveServiceTest | 15 | 0 | 0 | 0 |
| DiscoveryPaperArchiveControllerTest | 7 | 0 | 0 | 0 |
| DiscoverySampleStoreTest | 7 | 0 | 0 | 0 |
| DiscoverySampleCaptureTest | 4 | 0 | 0 | 0 |
| DiscoveryPaperArchiveCaptureTest | 9 | 0 | 0 | 0 |
| DiscoveryPaperArchiveFlowIT | 8 | 0 | 0 | 0 |
| PdfEmailExtractorTest | 43 | 0 | 0 | 0 |
| ExpertDiscoveryServiceTest | 194 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 52 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 26 | 0 | 0 | 0 |
| DiscoverySampleReplayTest | 4 | 0 | 0 | 0 |
| DiscoveryPaperLayoutRegressionTest | 2 | 0 | 0 | 1 |
| PaperQualificationEvidenceExtractorTest | 9 | 0 | 0 | 0 |
| DiscoveryPaperAnalysisServiceTest | 7 | 0 | 0 | 0 |
| DiscoveryApolloSettingsIT | 8 | 0 | 0 | 0 |
| DiscoveryVerificationControllerTest | 4 | 0 | 0 | 0 |
| DiscoveryVerificationWorkerTest | 16 | 0 | 0 | 0 |
| DiscoveryCountrySupplementFlowIT | 8 | 0 | 0 | 0 |
| DiscoveryCountryRegistrationTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationIntegrationTest | 14 | 0 | 0 | 0 |
| DiscoveryVerificationFlowIT | 32 | 0 | 0 | 0 |
| DiscoveryVerificationQueryServiceTest | 18 | 0 | 0 | 0 |
| ApolloPeopleSearchClientTest | 13 | 0 | 0 | 0 |
| DiscoveryVerificationRulesTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationAdmissionGateTest | 8 | 0 | 0 | 0 |
| DiscoveryVerificationRepositoryIT | 17 | 0 | 0 | 0 |
| **Total: 27 classes** | **567** | **0** | **0** | **1** |

The skipped test is the explicit private-original-corpus gate at `DiscoveryPaperLayoutRegressionTest.kt:20–22`; 566 tests executed. It is not a real-effect PASS.

C-1 also actually executed Maven-bound full Node: **1823/1823**, 334 suites, zero failed/cancelled/skipped/todo, followed by both bound syntax goals.

| ID | Exact command | Result |
|---|---|---|
| C-2 | `node --check src/main/resources/static/app.js` | Exit 0 |
| C-3 | `node --check src/main/resources/static/discovery-papers.js` | Exit 0 |
| C-4 | `node --test src/test/js/discoveryPapers.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryVerification.test.js` | Exit 0; 65/65, 4 suites, zero failures/skips |
| C-5 | `node --test src/test/js/discoveryApolloSettings.test.js src/test/js/discoveryVerification.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryPapers.test.js` | Exit 0; 118/118, 17 suites, zero failures/skips |
| C-6 | `node --test src/test/js/*.test.js` | Exit 0; 1823/1823, 334 suites, zero failures/skips |
| C-7 | `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home /opt/homebrew/bin/mvn package -DskipTests` | Exit 0; BUILD SUCCESS; 2:50; product/test compilation and WAR packaging completed; JVM execution explicitly skipped; bound Node 1823/1823 and syntax goals passed |
| C-8 | `python3 /private/tmp/aggregate_layout_comparator.py` | Exit 0; seven controlled CLI cases produced their expected exits/counters |
| C-9 | `python3 /private/tmp/aggregate_discovery_runtime.py` | Exit 0; six standalone JVM runtime processes, all exit 0 |
| C-10 | Dedup probe compile/run below | Process exit 0; discriminating output proves V-1 |
| C-11 | `python3 /private/tmp/aggregate_discovery_ui.py` plus CUA Chromium interactions | Fresh real-assets UI checks completed; fixture terminated intentionally with Ctrl-C/exit 130; browser tabs closed and viewport reset |

Logs C-4/C-5/C-6: `/private/tmp/aggregate-discovery-{05-node,focused-node,full-node}.log`. C-7: `/private/tmp/aggregate-discovery-package.log`.

A preliminary default-sandbox Node invocation exited 1 because writes under W’s `target/discovery-plan-acceptance/` received EPERM. Counts unavailable for that preliminary run. Repeating with the authorized sandbox escalation produced C-4/C-5/C-6 above. No test/product edit or exclusion resolved it.

WAR:

`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`

SHA-256: `fed81740960e47072b2d725af184be956bfa2dd6368ffeddf0ebe444f8c7b117`. No reproducible-build or deployment claim.

### Separate runtime evidence

C-9 parsed the freshly generated C-1 XML:

`target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.archive.DiscoveryPaperArchiveServiceTest.xml`

It selected property `surefire.test.class.path` as **CP**. CP SHA-256:

`f3b59342667efda4decb557407c65553243f487e1361d23b82ba322cd836228e`.

Each process used environment:

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib
```

Exact effective invocation for each row:

```text
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp CP FULL_CLASS [ARGUMENT]
```

CP was passed as one complete subprocess argument, not a partial manually assembled dependency list.

| Full class / argument | Exit | Fresh observations |
|---|---:|---|
| `com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher` | 0 | Real temporary files, SQL and FileStore; settings reread 25/15 GiB; 3072 bytes/2 files; protected/shared deletion behavior; unprotection retained bytes; controller Range 206/100 bytes; delete retained metadata/outcome/analysis; content 410 |
| `com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher` | 0 | 1024 exact bytes; SHA `a9c40cf3c9314d60ef74257cbf948ffd117523ba94ffbde20874973db5114c02`; first frozen/latest updated; savedAttempts=2; sharedFiles=1; blockedHTTP=0; queue PENDING, attempts=2 unchanged, delay=60s, cursor unchanged |
| `com.weibo.talentintroduction.discovery.service.LayoutPdfFixtures` | 0 | Actual generated double-column PDF; resolved=2, crossColumnRejected=1, conflictingOwnerRejected=1, network=0, businessWrites=0 |
| `com.weibo.talentintroduction.discovery.archive.PaperAnalysisSmokeLauncher` | 0 | Three historical facts: assistant/completed/student; year/hash/paragraph verified; PUBLICATION_TIME; first unchanged; cached repeat no write; missing-baseline delta null; deleted analysis retained; reanalysis 410; network=0 |
| `com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsSmokeLauncher` | 0 | Actual SQL/TCP HTTP; default revision0; save/recreate; false/true retained with ineffective details; stale409, unknown400, anonymous401; first-write race; stopped MySQL GET/PUT503; recovery retained row; 93 other tables unchanged; provider observer0; no Key leakage |
| `com.weibo.talentintroduction.discovery.service.DiscoveryVerificationWorkerTest --apollo-access-smoke` | 0 | Existing A true/true unchanged, new B true/false, LEGACY preserved; free-required-detail PENDING/PAID_DETAILS_DISABLED with DETAIL attempts/permits/cache lookups/HTTP all0; needless-detail APOLLO_CLEAR remains eligible; malformed CONFIG_INVALID/providerHTTP0; channels3/2/1/pending1; actual persisted queue v1 null requested=false versus v2 null requested=true, both same-run reentry unchanged |

Logs: `/private/tmp/aggregate-CLASS_SHORT_NAME.log`.

Disposable MySQL and strict fake/intercepted HTTP were used. Worker/Gate projection was exercised only against isolated synthetic fixtures. No production context/DB or real provider/paid call was used.

### Comparator controls

Each case freshly invoked:

```text
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before BEFORE --after AFTER --labels LABELS --output OUTPUT
```

Synthetic fixture directory: `/private/tmp/aggregate-layout-rw7g22fk`.

| Case | Exit | Required observed counter |
|---|---:|---|
| unchanged | 0 | All difference counters0 |
| lost | 2 | lostAssociations1 |
| new | 2 | newEmails1, addedAssociations1, incorrectAssociations1 |
| conflict | 2 | conflicts1, addedAssociations1, incorrectAssociations1 |
| hash | 2 | bodyHashMismatches2, representing two differing identities |
| config | 2 | configMismatches1 |
| labelled gain, existing mailbox | 0 | addedAssociations1, newEmails0, incorrectAssociations0 |

These controls establish comparator behavior; they establish no original294 effect result.

### Real Chromium evidence

C-11 served W’s real static assets on `http://127.0.0.1:63239/`. Only disposable fixture responses and fixture launch controls were added; product assets were unchanged. CUA operated actual controls and inspected rendered DOM/computed styles/screenshots.

| Gate | Fresh result |
|---|---|
| Desktop dimensions | 1440×1080; modal1180×864; Apollo grid546px/546px |
| Responsive dimensions | 643×900: modal595×720, grid253.5px/253.5px; 640×900: one column552px |
| Input/disabled/focus styles | Checkbox14×14; disabled save opacity .45; keyboard save focus-visible true, outline `rgb(59,130,246)` |
| Saved/draft behavior | Edit produced dirty with no automatic PUT; actual launch while dirty blocked; save read server revision; four combinations observed; false/true retained independent paid flag and search-off warning |
| Duplicate save | Actual double-click under delayed PUT produced one revision6 PUT; inputs/reload disabled during save |
| Apollo failure/retry | 409 and503 retained draft and correct guidance; GET404 disabled settings/save and displayed backend-not-ready; restore GET recovered |
| Lifecycle | Slow settings GET and slow PUT followed by switching to CHECK_REPLIES did not restore the discovery configuration into the new host; close hid modal and cleared reader iframe source |
| Run/current separation | Current free-only form remained separate from historical paid-on revision2; summary showed3/2/1/pending1 and non-billing wording; LEGACY explicit; absent fields “未记录”; summary refresh retained dirty draft |
| Actual launch transport | Default POST body `{"sources":["OPENALEX"]}`; keyword POST `.../run/by-keyword?keywords=aggregate+fixture&sources=OPENALEX`; continuous-mode keyword POST with `keywords=continuous+fixture`; all omitted `apolloAssist` |
| Paper selection/protection | Protected row selectable; unprotect sent false, cleared selection, retained content |
| Reader safety/lifecycle | Script-like title/body displayed as text; preview caused no analysis POST; Esc closed reader and restored focus to “查看记录” |
| Paper evidence | Rendered assistant/completed/student separately, year2023, PARTIAL/BASELINE_MISSING and historical-qualification warning; no invented zero delta/current qualification |
| Delete | Preview distinct from commit; first commit409 refreshed preview and required another click; only second click committed; deleted row retained record view and disabled download |
| Paper settings/error/empty | Empty state and list503 recovery text rendered; settings503 retained25/15 inputs and open dialog |
| Table/other pages | Header position static/background `rgb(248,249,251)`; narrow table inner width553/scrollWidth1010; review/history tabs rendered; CHECK_REPLIES retained700px modal and original columns with Apollo hidden |

Screenshots were visually inspected through CUA. Fixture setup initially used a non-root route that changed the application’s calculated contextPath; corrected to root query routes. Generic unrelated account-page fixture shapes produced an account-page diagnostic; no production defect was inferred. Remote fonts were blocked by fixture policy. Controlled UI transport is not authenticated real-backend persistence, real original PDF/corpus, or billing evidence.

### Contract matrix

All machine rows are mandatory unless explicitly described as effect-only/manual. Passing suites do not override V-1.

| ID | Requirement / source | Required evidence | Verdict / evidence |
|---|---|---|---|
| M-I1 | Claims distinguish experiment/defaults/implementation/effects; master I-1 | Reports, versions, original corpus boundaries | PASS for claim discipline; effect gates remain BLOCKED below; no79/52 gain or qualification-effect PASS asserted |
| M-I2 | Storage, identity, historical qualification and admission separate; master I-2 | Write/read traces and runtime | PASS; archive repository writes only archive tables; analysis service22–120; historical DTO/facts; C-9 analysis and settings/Worker isolation |
| M-I3 | Exact scopes, ordered shared files, literal UI contracts; master I-3/A1–A16 | Combined diff and per-file authorization | PASS;78-file exact union; child counts9/10/7/10/7/33/11/8; amendment rows below; C-4/C-5 literal DOM/CSS |
| M-I4 | Live archive protection; saved Apollo affects new runs only; master I-4 | Capacity, snapshots, free DETAIL0 | **FAIL V-1** for scoped reuse; remaining snapshot/free paths PASS under C-1/C-9 |
| M-ENV | JDK11/existing dependencies/new migrations/isolated fixtures; master preflight | Source/build/runtime | PASS; V159/V160 only; no dependency/config/provider endpoint changes |
| M-NONGOALS | No eligibility loosening/history refill/OCR/LLM/new provider/Key management | Complete product diff | PASS; original Rules/Gate/client behavior preserved; A13 governs test reconciliation |
| 01-I1/T1 | Independent archive/schema, dedup, metadata retained | SQL/files/source | PASS except capacity interaction separately V-1; V159; repository54–55/90–99; C-9 storage |
| 01-I2/T2 | Verified SAVED; protection/CAS; crash/recovery | Service/tests/runtime | PASS; service214–275/386–402/438–500; C-1 storage15 and controller7; C-9 storage |
| 01-I3 | Shared/protected deletion recheck; snapshot409; partial deletion | Locks/reference traces/tests | PASS; service404–454; repository references/finishDelete; C-1/C-9; browser409 |
| 01-I4 | Singleton defaults/CAS/503, actual bytes/FileStore, exact capacity | SQL/settings/disk tests | **FAIL V-1** in scoped reuse; other defaults/settings/unknown/concurrency cases PASS |
| 01-I5/T3 | Auth, positive IDs, no paths/URL secrets, symlink safety, safe MIME/Range/410 | Controller/service/tests/runtime | PASS; service87–156/364–380; controller content/parseRange; fresh controller7 and storage smoke; prior scalar-array sanitization fix retained |
| 02-I1/T1 | Six sources, worker-thread scopes, raw bytes, CORE embedded | Registered integration fixture/source/runtime | PASS; Capture81–128; ExpertDiscovery1613–1640/2124–2150; XML/PDF hooks; C-1 FlowIT8/Capture9 and C-9 capture |
| 02-I2 | Cache/download/consumption statistics distinct | Queue/source/stat traces | PASS; cached extraction path unchanged; CORE embedded hook; C-1/C-9 |
| 02-I3/T2 | No SYNC consumption/checkpoint on archive error; await futures; QUEUE CAS+60s/no attempt increase | Exception/lease traces/runtime | PASS for failure handling; ExpertDiscovery1699–1714; Pipeline1033–1042; C-9 blockedHTTP0/cursor unchanged/PENDING+60s |
| 02-I4/T1a | Disabled independent samples; accepted scope survives toggle; frozen first/latest | Service/Repository/Capture tests | **FAIL V-1** for accepted-scope capacity accounting; enablement/first/latest behavior PASS; repository66–78 and Capture105 |
| 03-I1/I2/T1 | Exact name/geometry/section/first-mailbox boundaries | Actual PDFs/resolver tests | PASS; PdfAuthorContactLayout117–170; Pdf43/Resolver26; C-9 actual PDF |
| 03-I3 | Merge old/new claims, conflict downgrade, no new mailboxes | Resolver integration/comparator | PASS for code/synthetic evidence; resolver unchanged; C-1/C-8/C-9 |
| 03-I4/T2 | PDF layout20261023, identity/proof/cache unchanged, first preserved | Constants/diff/cache regression | PASS; Layout12/Capture105; identity/proof files unchanged; C-1 |
| 03-EFFECT | Exact294 dual-version79/52/0/page34 evidence; child03 I-3, master effect boundary | Complete frozen originals/config/labels and replay | **BLOCKED, effect-only**; fresh private test skipped; historical incomplete corpus cannot prove effects. Master permits honest code completion, not effect acceptance |
| 04-I1/T2 | Unique named biography assertion; no other-author/reference/pronoun qualification | Extractor traces/tests | PASS; extractor19–105;9 fresh extractor tests; analysis smoke three facts |
| 04-I2 | Historical categories/NOT_FOUND/PARTIAL/ERROR; candidate≠completed | Extractor/DTO/UI/tests | PASS; extractor degree lookahead and separate classes; prior candidate-status fix retained; C-1/C-9/browser |
| 04-I3/T1 | Analysis-only writes; leases/BUSY/CAS; no downloads/business writes; deleted analysis retained | Service/read handles/repository/runtime | PASS; ArchiveService56–81; Analysis22–120; repository81–83; C-1/C-9 |
| 04-I4/T3 | Anchored baseline/hashes/cache, partial delta null, complete hasGain | Analysis/query tests | PASS; Analysis28–52/96–120; repository112–115; Analysis7/Repository4; browser missing-baseline display |
| 04-EFFECT | Original23/432/person-label evidence and original-quote gaps | Private bodies/labels/pure audit | **BLOCKED, effect-only**; no fresh aggregate private-label audit; historical0facts and missing person-level labels establish no successful effect claim |
| 05-I1 | API-sourced counts/null/units/snapshot/new-library notice | Actual module/tests/browser | PASS; discovery-papers.js39–55/76–100; C-4 and Chromium |
| 05-I2 | Protected selectable; page selection reset; unprotect no delete; explicit preview/commit409 | Mutation tests/browser | PASS; module58–72/153–197; C-4 and Chromium wire observations |
| 05-I3/T1 | Lazy list, abort/generation/contextPath/text safety/cleanup | Host/module/tests/browser | PASS; module24–43/128–152/203–243; app2469/27077; C-4/Chromium |
| 05-I4/T2 | Preview read-only; analysis click-only; historical warning/deleted analysis | Renderer/backend/runtime/browser | PASS; module249–284; C-9 analysis; Chromium reader/evidence/delete |
| 05-S1–S4/T3 | Literal DOM/CSS,1180px/80vh, scoped styles/states/table/checkbox/cache | Literal assertions + real Chromium | PASS; CSS13364–13435; C-4/C-5 and observed viewports/header/focus/empty/error/deleted states |
| 06a-I1/I2/T1 | Independent singleton flags/defaults/CAS/no-op/invalid storage503 | Actual SQL/HTTP/race/recreate | PASS; SettingsRepository34–85, SettingsService86–111, V160; SettingsIT8; C-9 |
| 06a-I3 | Save only setting, auth/no Key/provider0 | Controller/table comparison/runtime | PASS; SettingsController16–43;93 other tables unchanged/provider0/401/400/409/503 |
| 06a-I4/T2 | One strict frozen schema; no second defaults/quota | Shared parser/roundtrip/downstream | PASS; FrozenApolloAccess15–61; Registration32–40/77–112; C-1/C-9 |
| 06a-T3 | Exact28 resource synchronization,33-file exception; no exclusions | Per-file diff/full Node | PASS; amendment table; bound1823/1823; no frontend product delta in06a |
| 06b-I2/T1/T2 | Existing run first; one new freeze; null/false/true; LEGACY/invalid; queuev1/v2 | Registration/queue/SQL runtime | PASS; Registration58–112; Pipeline50/327/647/1266; integration14, Worker16, C-9 |
| 06b-I3/T3 | Free no DETAIL reserve/cache/HTTP; PENDING preserves rules; needless detail remains eligible | Worker/Gate/runtime | PASS; Worker208/385–391; C-9 exact zero counters and APOLLO_CLEAR; original Rules/Gate regressions |
| 06b-I4 | Search/detail/other/pending counts are registered attempts | SQL/DTO/browser | PASS; VerificationRepository218–236; Query121–136; actual3/2/1/pending1 |
| 06b-MNC/A13 | Original budgets/429/credits/SELF/manual/country/cache preserved | Production diff + amended seven scenarios | PASS; Rules/Gate/client production unchanged; fresh FlowIT32, Repository17, Rules19, AdmissionGate8, country27; A13 scope audited |
| 07-I1/T1 | Draft/save/readiness/dirty gate; page omits override | Source/tests/actual transport | PASS; app10205/27189/27231/27252/27287; C-5; three actual launch captures |
| 07-I2 | Four independent combinations and capability distinction | Settings/tests/browser | PASS; app27199; C-1/C-5/C-9/browser |
| 07-I3/T2 | Run snapshot from summary; LEGACY/unknown; attempts≠billing | Backend/frontend/tests/browser | PASS; app27427/27434; Query130; C-5/C-9/Chromium |
| 07-I4 | Generation/request sequence, one PUT, no draft overwrite, disposal | Source/transition tests/browser | PASS; app27077/27178/27231–27284; C-5 and delayed/repeated actual interactions |
| 07-S1–S3/T3 | Literal DOM/CSS/cache, responsive/focus, papers/review/other-task retention | Literal tests/full Node/browser | PASS; CSS13437–13459; C-5/C-6/C-7; Chromium |
| M-BUILD | Final WAR and complete affected JS, no hidden skips | Fresh package/count/hash | PASS; C-7; JVM skip explicitly recorded |
| M-MANUAL | All authoritative A-n require human results | Human-originated acceptance/sign-off | PENDING; masterA1–A3;01A1–A3;02A1–A3;03A1–A2;04A1–A3;05A1–A4;06aA1–A2;06bA1–A3;07A1–A3 |

### Amendment authority

The following authority applies to each associated file row below; approval is recorded human authority, not inferred from passing tests.

| IDs | Master rule / reason / recorded approval |
|---|---|
| A1/A2 | I-3, sequencing and preflight5; two storage seams required accepted-scope saving and atomic first/latest;02 cap8→10/order01→02→04; `HUMAN:批准 继续`,2026-10-23 |
| A3/A4 | I-3, sequencing/preflight5; fresh capture version assertion requires seventh file; preserve first/cache/effect boundary; `HUMAN:批准 继续`,2026-10-24 |
| A5/A6 | I-3, sequencing/preflight5; tenth file excludes incomplete-baseline hasGain without weakening rollback/first/CAS; `HUMAN:批准 继续` |
| A7/A8 | I-3, sequencing/preflight5; seventh file synchronizes16→17 resource contract, preserving equal-key/runtime assertions; `HUMAN:批准 继续`,2026-10-24 |
| A9/A10 | I-3, file-table/preflight5; exact28 resource tests, original5+28=33 one-time test-only exception; no frontend product authorization; `HUMAN:批准 继续`,2026-10-24 |
| A11/A12 | I-3/I-4, file-table/preflight5; only PipelineService added,10→11 exception; persistentqueuev2 saved defaults versus legacyv1 nullfalse, existingrun unchanged; `HUMAN:批准 继续` |
| A13/A14 | I-3/I-4/preflight5; exact seven existing FlowIT scenarios reconciled to preserved production behavior, no file addition/waiver; `HUMAN:继续 修复 直到可以人工复验；说错了 是可以执行 review-fast-p` |
| A15/A16 | I-3/I-4, sequencing/preflight5; exact ContinuousRun/ScheduleSetting/Papers additions,07 five→eight; omission and unchanged bounded CSS; `HUMAN:批准 继续 — approving preceding07 exactthree-test scopeamendment` |

### Per-file amendment matrix

Every added authorized path is listed individually. PASS here judges authorization/scope; it does not waive V-1’s behavior.

| Amendment | Exact file | Verdict / governing-rule assessment |
|---|---|---|
| A1/A2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` | PASS scope; accepted-scope seam follows authorization; capacity defect V-1 remains |
| A1/A2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt` | PASS; transactionally preserves first, updates latest; no new business writer |
| A3/A4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt` | PASS; fresh pdfLayout expectation20261023; first/latest assertions retained |
| A5/A6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepositoryIT.kt` | PASS; incomplete baseline excluded; rollback/first/CAS retained |
| A7/A8 | `src/test/js/discoveryVerification.test.js` | PASS;05 resource count/description synchronization |
| A9/A10 | `src/test/js/batchDemandProgress.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/batchEmailVerification.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/batchSenderFilter.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/checkRepliesRelocation.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/contactTimingStyle.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/discoveryScheduleSetting.test.js` | PASS;06a resource synchronization only |
| A9/A10 | `src/test/js/expertContactSharedNote.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/expertManualProfile.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/expertMeetingVideo.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/mailOpenTracking.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/mailboxChatBehavior.test.js` | PASS; actual ordered list adds discovery-papers.js before app.js |
| A9/A10 | `src/test/js/mailboxChatStyle.test.js` | PASS; resource synchronization only |
| A9/A10 | `src/test/js/mailboxGroupPush.test.js` | PASS; count and actual ordered module list synchronized; ordering/key assertions retained |
| A9/A10 | `src/test/js/mailboxReplyIdentity.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/mailboxSuspensionStyle.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/mailboxTemplateReferenceStyle.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/manualReplySubjectPrefill.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/meetingCalendar.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/meetingConfirmationAssets.test.js` | PASS; resource count/uniqueness assertions synchronized |
| A9/A10 | `src/test/js/mobileCoreNavigation.test.js` | PASS; count16→17; key assertions retained |
| A9/A10 | `src/test/js/overlayAndDialogContrast.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/ragKnowledgeBasePage.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/ragWorkbenchRender.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/recipientPreviewDiagnostics.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/senderBindingDisplay.test.js` | PASS; count/description16→17 |
| A9/A10 | `src/test/js/taskActivityCenter.test.js` | PASS; count16→17; runtime registration assertions retained |
| A9/A10 | `src/test/js/taskRecordsSemantics.test.js` | PASS; count16→17; behavior assertions retained |
| A9/A10 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS; count/description16→17 |
| A11/A12 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` | PASS; newcriteriaVersion2, accepts1/2; registration historical flag derives persisted version; no additional schema |
| A13/A14 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` | PASS; three explicitrule0/LEGACY fixtures; Apollo/nonApollo budget split; capped broad/narrow continuation; two original alias cursors and A4 missing-completion conservative PENDING;32 fresh tests pass |
| A15/A16 | `src/test/js/discoveryContinuousRun.test.js` | PASS; only direct launch override assertion changed to absence; keyword/source/includeRawScan/timeout/no-completion retained |
| A15/A16 | `src/test/js/discoveryScheduleSetting.test.js` | PASS; only direct launch override assertion changed to absence; schedule/source/status assertions retained |
| A15/A16 | `src/test/js/discoveryPapers.test.js` | PASS; exact paper CSS comparison bounded by existing start/end inclusive; entire contract still compared |

### Fast-P RECORD_ONLY re-evaluation

| Source item | Master requirement | Fresh mapping/result | Evidence |
|---|---|---|---|
| 02 O-1 earlier synthetic ARXIV outside-test requests | Isolated fixtures/no real provider requests | Historical deviation retained; no current product defect proven; not a waiver | Current fixture loopback rewrite/guard inspected; C-1/C-9 capture fresh guarded runs pass. Historical exact wire count remains unavailable |
| 03 O-1 original corpus/page34 incomplete | M-I1/03 effect gate | **BLOCKED effect evidence**; no synthetic substitute | Fresh private gate skips; historical294 metadata/295 references/216 files/79 missing/215 complete is historical evidence only, not fresh aggregate availability or79/52 PASS |
| 04 O-1 real qualification effect incomplete | M-I1/M-I2/04 effect gate | **BLOCKED effect evidence** | Historical23 bodies/432 pages/0facts and unavailable person labels do not prove coverage of known1+2 or remaining49; no fresh aggregate private-label audit |
| 04 O-2 helper classpath invocation | Mandatory independent runtime | Resolved invocation concern | C-9 uses entire fresh Surefire CP/hash and standalone analysis exits0 |
| 05 O-1 isolated fixture boundary | API/UI/persistence separation | Limitation retained; backend and UI evidence separately supplied | C-1/C-9 real SQL/files and C-11 real-assets browser; no claim of authenticated integrated deployment/restart/originalPDF/billing |
| 05 O-2 attempt timestamp absent | M-I1/05 factual display | No mandatory violation proven | UI explicitly says record capture time/file time unavailable; fresh reader displayed that wording |
| 05 O-3 preliminary browser harness failures | Mandatory actual UI evidence | Historical harness failures not promoted | Fresh C-11 actual DOM/style/mutation observations; own contextPath fixture correction disclosed |
| 07 fixture/font/harness limitations | M-I1/07 browser boundary | Limitation retained, no historical proof substituted | Fresh C-11; remote fonts blocked; generic unrelated fixtures not product evidence |

### Finding lineage

| Finding | State | Regression boundary |
|---|---|---|
| V-1 | NEW | Introduced in child02’s accepted-scope storage path within the reviewed cumulative boundary; no prior aggregate finding/repair |

### Findings

#### P1

**V-1 — Existing scoped blobs are charged again before deduplication.**

Requirement: child01 T-2 explicitly says verified existing-blob reuse adds no bytes. Child02 T-1a requires the accepted scope to use that shared storage seam with current capacity protection. Master I-4 retains live storage semantics.

Evidence: `DiscoveryPaperArchiveService.kt:228` executes:

```kotlin
if (scope != null) { permit(scope); preflight(bytes.size.toLong()) }
```

The verified existing-blob decision occurs afterward at lines229–233. `preflight` lines180–182 subtracts the full incoming body from free/archive headroom. Consequently a zero-growth reuse fails whenever that body exceeds remaining headroom, even though actual thresholds permit retaining the existing file.

Fresh discriminating probe:

- Source: `/private/tmp/AggregateDedupProbe.java`.
- CP: the complete fresh C-1 Surefire property defined above.
- Exact compile: `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/javac -cp CP -d /private/tmp /private/tmp/AggregateDedupProbe.java`.
- Exact execution: `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp /private/tmp:CP AggregateDedupProbe`.
- Both invoked through Python subprocess from W; process exit0.
- Actual disposable MySQL and temporary1024-byte stored blob.
- Set usableSpace=`minFreeBytes+1023`; accepted scope and attempt creation succeeded.
- Same-byte scoped save failed; same-condition direct reuse succeeded.

Observed output:

```text
SCOPED_REUSE=ARCHIVE_STORAGE_BLOCKED freeAboveMinimum=1023 existingBytes=1024 physicalGrowthRequired=0
DIRECT_REUSE=SUCCESS reused=true archiveBytes=1024
```

Log: `/private/tmp/aggregate-discovery-dedup.log`.

Impact: a downloaded duplicate unnecessarily becomes an archive infrastructure failure; SYNC may abort its page and QUEUE may defer the job despite zero required storage growth. This is a required-behavior defect, not optional hardening. Trigger frequency depends on duplicate-body size versus remaining positive capacity headroom.

Smallest implicated scope: `DiscoveryPaperArchiveService.kt` plus a discriminating regression in existing `DiscoveryPaperArchiveCaptureTest.kt`. No schema/API/rule/UI amendment is needed.

#### P2

N/A.

#### Observations

- Documentation snapshot whitespace is non-behavioral; excluded from repair.
- Original-corpus effect evidence and private person-label verification remain incomplete; no real-effect success inferred.
- Human acceptance remains pending for all26 master/child A-n items.
- CLAUDE’s fix-v instruction was read; AUDIT_ONLY constraints applied. The explicit review-p/review-fast-p invocation governs the single repair.md/report routing. No fix-N, continuation, knowledge write-back or mechanical repair was performed.

### Evidence boundaries

- Original294 dual-version/page34 effect evidence: BLOCKED; explicitly permitted code-completion limitation, not effect acceptance.
- Original23/private person-label effect evidence: BLOCKED; synthetic facts establish behavior only.
- Browser fixture responses do not establish production authentication, SQL-backed UI restart, vendor billing or original-corpus rendering.
- No production deployment/enablement or human sign-off.
- Mandatory violation V-1 determines FAIL before the unavailable effect-evidence state.

### Repair Planning Result: DRAFT_READY

Baseline plan: W’s exact governing `discovery-paper-master.md`.  
Verification result: FAIL / INITIAL.  
Repair artifact:

`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md`

Final repair SHA-256:

`4c9cc7637968e0691ba61bc362acbd3324c97d1a8d50c0d1ecdb0ec2bd991584`

Included findings: V-1 only, one root cause.  
Excluded: unavailable effect evidence, historical RECORD_ONLY limitations, documentation whitespace.

Exactly two Authorized Files:

1. `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt`
2. `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt`

The draft contains the discriminating zero-growth reuse/new-growth blocking regression, preserved permission/capacity/first/latest/queue contracts, required executor checks, and subsequent complete independent aggregate review.

Resolved one-approval handoff:

- Exactly one local product commit: `fix(discovery-papers): avoid charging reused scope blobs twice`.
- Execution evidence only: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/review/discovery-paper-master/repair-execution.md`.
- Exactly one handoff-only evidence commit: `docs(review-fast-p): record discovery paper scope reuse repair`.
- No extra product/test files, history rewrite, push, merge or deployment.

Repair self-review completed. No implementation was performed. The aggregate report destination was not written by this reviewer; controller persists this complete returned report.

### Next action

To approve and execute this repair, send:

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md
```

No product code was modified.


## Epoch 2 — 2026-10-24 — Verification Result: PASS

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-master.md`
Phase: aggregate/master
Reviewer: `/root/aggregate_master_reviewer_e2`
Implementation boundary: `c6e9709f349f32c5feb1d067b47c697b0dff05b6..1ebe3534be212c26eb1e490d747ae4a10daa702f`
Evidence HEAD: `410c7d95fc010ade43f5412d9c9a9193d17d29cd`
Convergence: **PROGRESSING**
Manual acceptance: **PENDING**

**PASS applies to the master’s explicitly permitted code-completion boundary. Original294/page34 and original23/person-label effect evidence remain BLOCKED, effect-only. No real-effect acceptance, human PASS, deployment readiness, or integration approval is asserted.**

### Identity and authority

All repository commands used exactly:

`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`

Abbreviated **W** below; repository-relative source paths resolve within W.

| Item | Verified value |
|---|---|
| Branch | `fast/discovery-paper-master` |
| Worktree resolution | EXPLICIT |
| Common repository | `/Users/lukai/IdeaProjects/weibo-talent-introduction` |
| Governing master commit | `3c5a68ad9917fdee22e0548b5afc57a6df04d580` |
| Governing master SHA-256 | `fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3` |
| Master identity state | AMENDMENT_RECORDED; A1–A16 govern |
| Invoked identity | Current invocation supplied the handoff; no new invoked master copy. Prior invoked-copy SHA-256: `ae1f7d432643853aba3071975167441a2b9607ca04151021617a90f6ae2f215f` |
| Fast ledger SHA-256 | `de7e31a72c3f931aacbd8fe285f34c398ab54ec344c308c72031b83ab6c2f87d` |
| Fast handoff SHA-256 | `ee4944f00f684918b75d23e7d569212ad93333d02e0f77c1a2e362250e5b7956` |
| Prior code | `37da0edbdb9737ed75c27ea8e6e525e35ef78354` |
| Repair candidate | `1ebe3534be212c26eb1e490d747ae4a10daa702f` |
| Repair evidence mode | DURABLE_HANDOFF |
| Repair SHA-256 | `4c9cc7637968e0691ba61bc362acbd3324c97d1a8d50c0d1ecdb0ec2bd991584` |
| Repair execution SHA-256 | `d8d0b84be484d42b4fb4ce99285b30725a80823e38e24b221b8878de775f567f` |
| Repair approval | Exact human-originated `$execute-p` invocation persisted in `repair-execution.md`; current repair bytes and resolved local commits named |
| Executor | Stable identity UNAVAILABLE; durable report names top-level assistant `openai-codex/gpt-6.1-sol:max` |
| Separation | Fresh reviewer created after repair commit, without inherited execution context |
| Final state | HEAD unchanged; index empty; no product/script dirty files; only controller-owned review ledger modified |

Fresh ancestry checks returned exit 0 for base→candidate and candidate→HEAD. Candidate→HEAD changes only `docs/plans/review/discovery-paper-master/repair-execution.md`.

All eight current child-plan bytes match their recorded commits; all referenced child evidence commits belong to the retained lineage.

| Child | Recorded identity commit | Current SHA-256 |
|---|---|---|
| 01 | `c18dbc060dcf4c8440f853ede4d1d3aaf737994f` | `6e1d78f48e71e9a518ce6cb7ea8de1cbd6ae2af76a0d837351aacd693d9ee0d3` |
| 02 | `87c5268976807c19c165fe50c91ba3372ed0075d` | `53381edc616c15ae87db31c1abacea4a4652702a60432dec0aa713fb57af11c4` |
| 03 | `8d2313da868251b68dcaee02b744dfa777b41674` | `c46019e05d5315854277057d2550541093b5f4649a7961c2f4a30f9ccf999abe` |
| 04 | `bcf5d64fb9534759eb1cad08db3089d36e4a1fb0` | `d506cc357820388ec3ef7a094314c28e58196db9800c84df52f0757459ecbf49` |
| 05 | `541411d540019428c72b31775c325087b33da303` | `4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00` |
| 06a | `6112a58b4bc94b510a6b1cff88cfd9ec159c83d6` | `9802f4f4b4009a8dfbf57eb158efb83e32a32e5badc7f8f27f5fddc4b6aa3fc7` |
| 06b | `0199430a0b4f30f89b8fb240d58880167471b512` | `41c6fd43b5cd37153a524d251cc9283e15e8d73581a423851b053db91324b170` |
| 07 | `3c5a68ad9917fdee22e0548b5afc57a6df04d580` | `b1fc292788236a5620340395e393a97216cac516df449a2ad3f1ccfdd703fa69` |

### Scope and repair boundary

Fresh per-child Git deltas matched each amended file table.

| Child | Changed product/test/script files | Authorized | Unexpected |
|---|---:|---:|---|
| 01 | 9 | 9 | None |
| 02 | 10 | 10 | None |
| 03 | 7 | 7 | None |
| 04 | 10 | 10 | None |
| 05 | 7 | 7 | None |
| 06a | 33 | 33 | None |
| 06b | 11 | 11 | None |
| 07 | 8 | 8 | None |
| Cumulative unique union | 78 | 78 | None |

The repair delta contains exactly:

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt`
2. `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt`

No third product/test file changed. Existing assertions remain; two discriminating tests were added. No test exclusions or bypass additions were found.

Shared-file history follows the approved sequence. Frontend product changes occur in 05/07. Only new migrations V159/V160 changed; applied migrations, dependencies, application configuration, authentication configuration, old sample production files, identity constants, Rules, AdmissionGate and Apollo client remain unchanged. Product/script `git diff --check` returned exit 0.

### Commands

Fresh environment: JDK11 Zulu 11.0.15, Maven 3.9.11, Node v25.7.0. Runtime fixture logs confirm Docker 29.8.2. Required Maven runs explicitly used JDK11.

**C-1 — Full required JVM union**

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,SourceAuthorEmailResolverTest,DiscoverySampleReplayTest,DiscoveryPaperLayoutRegressionTest,PaperQualificationEvidenceExtractorTest,DiscoveryPaperAnalysisServiceTest,DiscoveryApolloSettingsIT,DiscoveryVerificationControllerTest,DiscoveryVerificationWorkerTest,DiscoveryCountrySupplementFlowIT,DiscoveryCountryRegistrationTest,DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRepositoryIT -DmigrationIt=true -DmysqlIt=true
```

Exit **0**, BUILD SUCCESS, elapsed **10:36**. Log: `/private/tmp/discovery-e2-maven.log`.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryPaperArchiveRepositoryIT | 4 | 0 | 0 | 0 |
| DiscoveryPaperArchiveServiceTest | 15 | 0 | 0 | 0 |
| DiscoveryPaperArchiveControllerTest | 7 | 0 | 0 | 0 |
| DiscoverySampleStoreTest | 7 | 0 | 0 | 0 |
| DiscoverySampleCaptureTest | 4 | 0 | 0 | 0 |
| DiscoveryPaperArchiveCaptureTest | 11 | 0 | 0 | 0 |
| DiscoveryPaperArchiveFlowIT | 8 | 0 | 0 | 0 |
| PdfEmailExtractorTest | 43 | 0 | 0 | 0 |
| ExpertDiscoveryServiceTest | 194 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 52 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 26 | 0 | 0 | 0 |
| DiscoverySampleReplayTest | 4 | 0 | 0 | 0 |
| DiscoveryPaperLayoutRegressionTest | 2 | 0 | 0 | 1 |
| PaperQualificationEvidenceExtractorTest | 9 | 0 | 0 | 0 |
| DiscoveryPaperAnalysisServiceTest | 7 | 0 | 0 | 0 |
| DiscoveryApolloSettingsIT | 8 | 0 | 0 | 0 |
| DiscoveryVerificationControllerTest | 4 | 0 | 0 | 0 |
| DiscoveryVerificationWorkerTest | 16 | 0 | 0 | 0 |
| DiscoveryCountrySupplementFlowIT | 8 | 0 | 0 | 0 |
| DiscoveryCountryRegistrationTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationIntegrationTest | 14 | 0 | 0 | 0 |
| DiscoveryVerificationFlowIT | 32 | 0 | 0 | 0 |
| DiscoveryVerificationQueryServiceTest | 18 | 0 | 0 | 0 |
| ApolloPeopleSearchClientTest | 13 | 0 | 0 | 0 |
| DiscoveryVerificationRulesTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationAdmissionGateTest | 8 | 0 | 0 | 0 |
| DiscoveryVerificationRepositoryIT | 17 | 0 | 0 | 0 |
| **Total: 27 classes** | **569** | **0** | **0** | **1** |

**568 tests executed successfully.** The sole skip is `DiscoveryPaperLayoutRegressionTest.kt:20–22`, explicitly reporting unavailable exact original294 input. It is not effect evidence.

C-1 also ran bound Node **1823/1823**, 334 suites, zero failures/cancelled/skipped/todo, followed by both bound syntax goals.

| ID | Exact command | Fresh result |
|---|---|---|
| C-2 | `node --check src/main/resources/static/app.js` | Exit 0 |
| C-3 | `node --check src/main/resources/static/discovery-papers.js` | Exit 0 |
| C-4 | `node --test src/test/js/discoveryPapers.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryVerification.test.js` | Exit 0; 65/65, 4 suites, zero failures/skips |
| C-5 | `node --test src/test/js/discoveryApolloSettings.test.js src/test/js/discoveryVerification.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryPapers.test.js` | Exit 0; 118/118, 17 suites, zero failures/skips |
| C-6 | `node --test src/test/js/*.test.js` | Exit 0; 1823/1823, 334 suites, zero failures/skips |
| C-7 | `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn package -DskipTests` | Exit 0; BUILD SUCCESS; 02:38; bound Node1823/1823 and syntax passed; JVM execution explicitly skipped |
| C-8 | `python3 /private/tmp/discovery_e2_comparator.py` | Exit 0; all seven controlled outcomes matched |
| C-9 | `python3 /private/tmp/discovery_e2_runtime.py` | Exit 0; all six standalone JVM processes exit 0 |
| C-10 | `python3 /private/tmp/discovery_e2_ui.py` plus CUA Chromium interactions | Required rendered UI gates completed; both disposable server sessions intentionally ended with Ctrl-C/exit130 |

Node logs: `/private/tmp/discovery-e2-{05-node,focused-node,full-node}.log`.
Package log: `/private/tmp/discovery-e2-package.log`.

The initial UI server invocation in the default sandbox failed with socket `PermissionError`; authorized elevated execution succeeded. No product/test change resolved it.

WAR:

`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`

Size: **51,617,556 bytes**
SHA-256: **`0004a2bcb9637b1d24876df75c23b8a75f6f477cdf25929bf8261b253bc7d014`**

No reproducible-build or deployment claim.

### Separate runtime evidence

The prior runtime/comparator/UI harnesses were inspected before adaptation. New harnesses, controls and logs use epoch2-specific paths under `/private/tmp`.

C-9 freshly parsed property `surefire.test.class.path` from:

`W/target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.archive.DiscoveryPaperArchiveServiceTest.xml`

That XML was generated this epoch at `2026-10-24T06:01:51.625421+00:00`, reports Java11.0.15, and supplies **127 classpath entries**.

CP SHA-256:

`f3b59342667efda4decb557407c65553243f487e1361d23b82ba322cd836228e`

Each separate process used the complete CP as one argument:

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib

/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -cp CP FULL_CLASS [ARGUMENT]
```

| Full class / argument | Exit | Fresh observations |
|---|---:|---|
| `com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher` | 0 | Actual SQL/files/FileStore; settings reread25/15GiB;3072bytes/2files; protected/shared deletion; unprotection retained bytes; Range206/100bytes; delete retained metadata/outcome/analysis; content410 |
| `com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher` | 0 |1024bytes; SHA `a9c40cf3c9314d60ef74257cbf948ffd117523ba94ffbde20874973db5114c02`; first frozen/latest updated; savedAttempts2/sharedFiles1; blockedHTTP0; queuePENDING/attempts2 unchanged/+60s/cursor unchanged |
| `com.weibo.talentintroduction.discovery.service.LayoutPdfFixtures` | 0 | Actual double-column PDFs; resolved2/crossColumnRejected1/conflictingOwnerRejected1/network0/businessWrites0 |
| `com.weibo.talentintroduction.discovery.archive.PaperAnalysisSmokeLauncher` | 0 | Three separate historical facts; year2023/hash/paragraph/PUBLICATION_TIME; first unchanged; cached repeat no write; missing-baseline delta null; deleted analysis retained; reanalysis410/network0/businessWrites0 |
| `com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsSmokeLauncher` | 0 | Actual SQL/TCPHTTP; defaultrevision0; save/recreate; false/true retained/effectiveDetailsfalse; stale409/unknown400/anonymous401; first-write race[200,409]; actual MySQL restart/outageGET-PUT503/recovered saved row;93 other tables unchanged/providerObserver0/keyLeakfalse |
| `com.weibo.talentintroduction.discovery.service.DiscoveryVerificationWorkerTest --apollo-access-smoke` | 0 | Atrue/true unchanged; Btrue/false; LEGACYtrue/true; free-required-detailPENDING/PAID_DETAILS_DISABLED with DETAILattempts/permits/cacheLookups/HTTP all0; needless-detailAPOLLO_CLEAR remains eligible; malformedCONFIG_INVALID/providerHTTP0; channels3/2/1/pending1; persistedqueuev1 nullrequestedfalse versusv2 true, same-run reentry unchanged |

Logs: `/private/tmp/discovery-e2-CLASS_SHORT_NAME.log`.

Disposable MySQL and strict fake/intercepted provider HTTP were used. No production application context/DB, real provider or paid call was used.

### Comparator controls

Fixture directory:

`/private/tmp/discovery-e2-layout-kyt_mjx_`

Each case freshly executed:

```text
python3 scripts/discovery_paper_archive/verify_layout_gain.py --before F/BEFORE --after F/AFTER --labels F/labels.json --output F/OUTPUT
```

Here F is the exact fixture directory above; filenames resolve as follows.

| Case | BEFORE | AFTER | OUTPUT | Exit / observed counter |
|---|---|---|---|---|
| unchanged | before.json | unchanged.json | unchanged-result.json |0; all difference counters0 |
| lost | before.json | lost.json | lost-result.json |2; lostAssociations1 |
| new | before.json | new.json | new-result.json |2; newEmails1/addedAssociations1/incorrectAssociations1 |
| conflict | before.json | conflict.json | conflict-result.json |2; conflicts1/addedAssociations1/incorrectAssociations1 |
| hash | before.json | hash.json | hash-result.json |2; bodyHashMismatches2 |
| config | before.json | config.json | config-result.json |2; configMismatches1 |
| labelled gain | before_gain.json | gain.json | gain-result.json |0; addedAssociations1/newEmails0/incorrectAssociations0 |

Log: `/private/tmp/discovery-e2-comparator.log`.

These controls prove comparator behavior, not original294 effects.

### Real Chromium evidence

The fixture served W’s unchanged real static assets at `http://127.0.0.1:63240/`. CUA operated actual controls and inspected rendered DOM, computed styles and screenshots.

| Gate | Fresh result |
|---|---|
| Desktop |1440×1080; modal1180×864; Apollo grid546px/546px |
| Responsive |643×900 modal595×720/grid253.5px253.5px;640×900 single grid552px |
| Styles |checkbox14×14; disabled opacity.45; enabled save keyboard focus-visible true/outline`rgb(59,130,246)` |
| Draft/save |Edit produced dirty without PUT; actual launch blocked; successful save used server revision; all four independent combinations observed |
| Search-off/detail-on |Paid flag remained true; explicit “搜索关闭期间不调用” wording |
| Duplicate save |Delayed actual double-click produced exactly one revision5 PUT; search/save/reload disabled while saving; server returned revision6 |
| Failures |409/503 retained draft and correct guidance; GET404 disabled inputs/save and showed backend-not-ready; successful reload recovered |
| Lifecycle |Slow GET and slow PUT followed by CHECK_REPLIES did not restore Apollo controls; ordinary modal width700; closing hid task modal and cleared reader iframe src |
| Run/current separation |Current free-only state separate from historical paid-on revision2; channels3/2/1/pending1 and non-billing wording; LEGACY explicit; absent metadata “未记录”; summary refresh retained dirty draft |
| Default launch |Actual POST `/api/expert-discovery/run`, body `{"sources":["OPENALEX"]}` |
| Keyword launch |Actual POST `/api/expert-discovery/run/by-keyword?keywords=epoch2+fixture&sources=OPENALEX` |
| Continuous launch |Actual POST `/api/expert-discovery/run/by-keyword?keywords=epoch2+continuous&sources=OPENALEX` |
| Launch override |All three omitted `apolloAssist` |
| Protection |Protected row selectable; unprotect sent false, cleared selection and retained content |
| Reader |Script-like title/body rendered as text; preview generated zero analysis POSTs; Esc restored focus to “查看记录” |
| Historical evidence |Assistant/completed/student separate; year2023; PARTIAL/BASELINE_MISSING and historical warning; missing delta remained unknown |
| Delete |Preview separate from commit; first commit409 refreshed preview and waited; only second explicit click committed |
| Deleted record |Record view retained; download/reanalysis disabled; prior analysis visible |
| Empty/error/settings |Empty state rendered; list503 guidance and recovery worked; settings503 retained25/15 inputs and open dialog |
| Table/old pages |Headerstatic/background`rgb(248,249,251)`; narrow table inner551/scrollWidth1010; review/history tabs rendered; other task retained original columns and hid Apollo |

Wire log: `/private/tmp/discovery-e2-discovery-ui-wire.log`.

Saved screenshots:

- `/private/tmp/discovery-e2-desktop-saved.png`
- `/private/tmp/discovery-e2-desktop-protected-selected.png`
- `/private/tmp/discovery-e2-desktop-reader.png`
- `/private/tmp/discovery-e2-desktop-delete409.png`
- `/private/tmp/discovery-e2-desktop-deleted.png`
- `/private/tmp/discovery-e2-settings503.png`

Additional narrow/empty rendered screenshots were inspected through CUA. All temporary tabs were closed, viewport overrides reset, and fixture servers terminated.

Generic unrelated account metadata fixtures produced an initial account-page shape diagnostic; no product defect was inferred. Remote fonts were blocked by fixture policy. This proves real-assets UI behavior with controlled transport, not authenticated backend deployment, original-PDF fidelity, SQL-backed UI restart or vendor billing.

### Contract matrix

Machine rows are mandatory within the code-completion boundary. Effect-only and human rows retain their explicit separate status.

| ID | Requirement / source | Verdict | Fresh evidence |
|---|---|---|---|
| M-I1 | Separate facts/defaults/proposals/effects | PASS | Versioned evidence/source; no79/52 or qualification-effect success asserted; effect limitations below |
| M-I2 | Storage/association/historical qualification/admission separate | PASS | Archive-only SQL; AnalysisService22–120; C-9 historical facts/network0/businessWrites0 |
| M-I3 | Exact scope/order/literal UI contracts | PASS |78-file union; amended child counts; per-file matrix; C-4/C-5 literal DOM/CSS; source history |
| M-I4 | Live archive capacity; new-run Apollo snapshots | PASS |Service174/217–245; repaired capacity regressions; Registration77–112; C-9 freeDETAIL0/old-run unchanged |
| M-ENV | Existing dependencies/JDK11/new migrations/isolated fixtures | PASS |No pom/application/auth change; V159/V160 only; C-1/C-7/C-9 |
| M-NONGOALS | No eligibility loosening/history refill/OCR/LLM/new provider/Key management | PASS |Complete product boundary; Rules/Gate/client unchanged; A13 test contract respected |
| 01-I1/T1 | Independent archive, raw-byte dedup, audit retention | PASS |V159; Repository54–55/87–100; Service217–280; actual storage smoke |
| 01-I2/T2 | VerifiedSAVED/protection/CAS/crash recovery | PASS |Service217–280/391–410/472–505; ServiceTest15; controller7; storage runtime |
| 01-I3 | Shared/protected deletion recheck and snapshot409 | PASS |Service410–462; references/finishDelete; SQL/file tests; Chromium explicit second confirmation |
| 01-I4 | Settings/defaults/CAS/503/FileStore/exact capacity | PASS |Repository18–32; Service174–183/300–337; tests and fresh zero-growth/new-growth counters |
| 01-I5/T3 | Auth/positiveID/path-secret safety/symlink/MIME/Range/410 | PASS |Service87–156/369–385; controller39–92; fresh controller7/storage runtime |
| 02-I1/T1 | Six sources/worker scopes/rawbytes/COREembedded | PASS |Capture81–128; ExpertDiscovery1613–1640/2124–2150; XML/PDF hooks; FlowIT8/Capture11 |
| 02-I2 | Cache/download/consumption counts distinct | PASS |Cached extraction branch unchanged; embedded hook; Repositorycounts; C-9 |
| 02-I3/T2 | SYNCawait-all/no consume-checkpoint; QUEUECAS+60s/no attempts | PASS |ExpertDiscovery1699–1714; Pipeline1033–1042; FlowIT; C-9 blockedHTTP0/cursor unchanged |
| 02-I4/T1a | Disabled/sample independence/accepted-scope permission/frozenfirst | PASS |Capture; Service162–194/217–245; Repository66–79; new reuse regression and existing toggle tests |
| 03-I1/I2/T1 | Exact unique-name/geometry/section/firstmailbox boundaries | PASS |Layout117–170; actual PDF43/resolver26; standalone actual double-column runtime |
| 03-I3 | Unified conflicts/no generated mailboxes | PASS |Unchanged resolver merge; source/actual PDF tests; seven comparator controls |
| 03-I4/T2 | Layout20261023; identity20261004/proof20260925; oldfirst/cache preserved | PASS |Layout12/Capture105; unchanged DiscoveryIdentity24/26 and cache gate; C-1 |
| 03-EFFECT | Exact294 dual-version79/52/0/page34 proof | **BLOCKED, effect-only** |Fresh genuine corpus skip; incomplete historical originals cannot establish effects; explicit master code-completion exception applies |
| 04-I1/T2 | Unique named biography facts; reject others/references/pronouns | PASS |Extractor19–105/155–178; extractor9; C-9 actual historical facts |
| 04-I2 | Historical categories/NOT_FOUND/PARTIAL/ERROR; student≠completed | PASS |Extractor assertions/lookahead; distinct DTO kinds; tests/runtime/UI |
| 04-I3/T1 | Analysis-only writes/leaseBUSY/CAS/no network/deletion retention | PASS |Service56–81; Analysis22–120; Repository81–83; Analysis7 and C-9 |
| 04-I4/T3 | Anchored bodies/hashes/cache/partialdeltanull/completehasGain | PASS |Analysis28–52/96–120; Repository112–115; SQL/tests/runtime/Chromium |
| 04-EFFECT | Original23/432/person-label/quote-gap audit | **BLOCKED, effect-only** |No fresh private-label audit; historical0facts and absent person-level labels cannot establish known1+2 coverage or any conclusion on49 |
| 05-I1 | APIcounts/null/units/snapshot/new-library notice | PASS |Module39–55/76–100; C-4; rendered Chromium states |
| 05-I2 | Protected selection/reset/unprotect/preview/explicit409 retry | PASS |Module58–72/153–197; C-4; actual wire and screenshots |
| 05-I3/T1 | Lazy lifecycle/abort/generation/contextPath/textsafety/cleanup | PASS |Module24–43/128–152/203–243; app2452/27077; transition tests/Chromium |
| 05-I4/T2 | Previewread-only/clickanalysis/historical warning/deleted analysis | PASS |Module249–284; analysis backend/runtime; zero preview analysisPOST; deleted reader |
| 05-S1–S4/T3 | LiteralDOM/CSS/1180px80vh/scopedstates/table/checkbox/cache | PASS |Styles13364–13435; literal tests; actual three viewports/styles/screenshots |
| 06a-I1/I2/T1 | Independent flags/defaults/CAS/no-op/strict503 | PASS |SettingRepository34–85/SettingsService86–111; SettingsIT8; actual race/restart/outage runtime |
| 06a-I3 | Setting-onlywrites/auth/noKey/provider0 | PASS |SettingsController16–43;93 nonsetting tables unchanged/provider0/401/400/409/503 |
| 06a-I4/T2 | One strict frozen schema/no duplicate defaults or quota | PASS |FrozenApolloAccess15–61; Registration32–40/77–112; C-1/C-9 |
| 06a-T3 | Exact28 resource synchronizations/33-file exception/no exclusions | PASS |Individual scope inspection below; only resource assertions/descriptions/lists; full Node1823 |
| 06b-I2/T1/T2 | Existingrunfirst/singlefreeze/null-false-true/LEGACY/invalid/queue1-2 | PASS |Registration58–112; Pipeline50/647/1266; Integration14/Worker16; real SQL queue runtime |
| 06b-I3/T3 | FreeDETAILreserve/cache/HTTP0; preservePENDING/originalrules | PASS |Worker208/385–391 before reuse/reserve; C-9 zero counters; needless-detailAPOLLO_CLEAR eligible |
| 06b-I4 | Registered search/detail/other/pending channels | PASS |Repository218–236; Query121–136; actual3/2/1/pending1; Chromiumnonbilling |
| 06b-MNC/A13 | Originalbudgets/429/credits/SELF/manual/country/cache | PASS |Rules/Gate/client production unchanged; approved seven scenarios inspected; FlowIT32/Repository17/Rules19/Gate8/country27 pass |
| 07-I1/T1 | Draft/save/readiness/dirtygate/omit pageoverride | PASS |App10205/27189/27231/27252/27287; C-5; actual three launch captures |
| 07-I2 | Four independent combinations/capability distinction | PASS |Settings/runtime/focusedtests; actual four combinations and search-off warning |
| 07-I3/T2 | Historicalsnapshot/LEGACY/unknown/attempts≠billing | PASS |App27427/27434; Query130; backendruntime and Chromium |
| 07-I4 | Generation/sequence/onePUT/draftretention/disposal | PASS |App27077/27178/27231–27284; C-5; delayedGET/PUT/doubleclick/summaryrefresh |
| 07-S1–S3/T3 | LiteralDOM/CSS/cache/responsive/focus/papers-oldtaskretention | PASS |Styles13437–13459; C-5/C-6/C-7; actualChromium |
| M-BUILD | Final WAR and complete affectedJS | PASS |C-7/buildhash; JVMskip explicitly separated from C-1 |
| M-MANUAL | Authoritative26 A-n and human boundarysign-off | **PENDING** |Master3;013;023;032;043;054;06a2;06b3;073; no human results manufactured |

Accumulation, persistence/state-machine, cross-plan and scope checks: PASS within this code boundary. No extra product behavior or deleted required path identified.

### Amendment authority

The exact governing diff and fast-ledger amendment rows were read. Each approval below is recorded human authority.

| ID | Master rule | Reason | Recorded approval |
|---|---|---|---|
| A1 |I-3/preflight5 |02 storageService/Repository seams;8→10; accepted-scope and immutablefirst/latest |HUMAN:批准 继续,2026-10-23 |
| A2 |I-3/sequencing/preflight5 |Master02count/order synchronized withA1 |Same preceding human approval |
| A3 |I-3/preflight5 |CaptureTest seventh file; fresh layoutversion expectation |HUMAN:批准 继续,2026-10-24 |
| A4 |I-3/sequencing/preflight5 |Master03count6→7; effect boundary unchanged |Same preceding human approval |
| A5 |I-3/preflight5 |RepositoryIT tenth file; incompletebaseline excluded fromhasGain |HUMAN:批准 继续 |
| A6 |I-3/sequencing/preflight5 |Master04count9→10; completebaseline retained |Same preceding human approval |
| A7 |I-3/preflight5 |VerificationJS seventh file;16→17 resource synchronization |HUMAN:批准 继续,2026-10-24 |
| A8 |I-3/sequencing/preflight5 |Master05count6→7 |Same preceding human approval |
| A9 |I-3/preflight5 |Exact28 resource tests; original5+28=33 |HUMAN:批准 继续,2026-10-24 |
| A10 |I-3/filetable/preflight5 |One-time33-file test-only exception; no frontend product permission |Same preceding human approval |
| A11 |I-3/I-4/preflight5 |PipelineService eleventh file; persistentqueuev2/v1 compatibility |HUMAN:批准 继续 |
| A12 |I-3/I-4/filetable/preflight5 |Master10→11 exception; no regressionwaiver |Same preceding human approval |
| A13 |I-3/I-4/preflight5 |Exactseven historicalFlowIT contracts; preserved production semantics |HUMAN:继续 修复 直到可以人工复验；说错了 是可以执行 review-fast-p |
| A14 |I-3/I-4/preflight5 |Master recordsA13; unchanged filecount/productionlimits |Same preceding human approval |
| A15 |I-3/I-4/preflight5 |ExactContinuousRun/ScheduleSetting/Papers additions;5→8 |HUMAN:批准 继续 — approving preceding07 exactthree-test scopeamendment |
| A16 |I-3/I-4/sequencing/preflight5 |Master07count synchronized; no newproductbehavior |Same preceding human approval |

### Per-file amendment matrix

Every amendment-added or behavior-reconciled path is listed separately. Paths are relative to W.

| Amendment | Exact file | Fresh assessment |
|---|---|---|
| A1/A2 |`src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` |PASS; authorized seam; V-1 now resolved; currentcapacity/permission retained |
| A1/A2 |`src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepository.kt` |PASS; transactional frozenfirst/latest merge; archive-only writes |
| A3/A4 |`src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt` |PASS;20261023 expectation and originalfirst assertions retained; repair adds two authorized regressions |
| A5/A6 |`src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveRepositoryIT.kt` |PASS; incompletebaseline excluded; rollback/first/CAS retained |
| A7/A8 |`src/test/js/discoveryVerification.test.js` |PASS;05resourcecount/description synchronization |
| A9/A10 |`src/test/js/batchDemandProgress.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/batchEmailVerification.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/batchSenderFilter.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/checkRepliesRelocation.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/contactTimingStyle.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/discoveryScheduleSetting.test.js` |PASS;06aresource synchronization only |
| A9/A10 |`src/test/js/expertContactSharedNote.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/expertManualProfile.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/expertMeetingVideo.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/mailOpenTracking.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/mailboxChatBehavior.test.js` |PASS; actual ordered list addsdiscovery-papers.js beforeapp.js |
| A9/A10 |`src/test/js/mailboxChatStyle.test.js` |PASS; resourcecount/description only |
| A9/A10 |`src/test/js/mailboxGroupPush.test.js` |PASS; count/orderedlist; ordering/key assertions retained |
| A9/A10 |`src/test/js/mailboxReplyIdentity.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/mailboxSuspensionStyle.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/mailboxTemplateReferenceStyle.test.js` |PASS; resourcecount/description only |
| A9/A10 |`src/test/js/manualReplySubjectPrefill.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/meetingCalendar.test.js` |PASS; resourcecount/description only |
| A9/A10 |`src/test/js/meetingConfirmationAssets.test.js` |PASS; count/uniqueness synchronization; other assertions retained |
| A9/A10 |`src/test/js/mobileCoreNavigation.test.js` |PASS; count17/key assertions retained |
| A9/A10 |`src/test/js/overlayAndDialogContrast.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/ragKnowledgeBasePage.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/ragWorkbenchRender.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/recipientPreviewDiagnostics.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/senderBindingDisplay.test.js` |PASS; count/description17 |
| A9/A10 |`src/test/js/taskActivityCenter.test.js` |PASS; count17/runtime assertions retained |
| A9/A10 |`src/test/js/taskRecordsSemantics.test.js` |PASS; count17/behavior assertions retained |
| A9/A10 |`src/test/js/trustReplyWorkbenchSharedMount.test.js` |PASS; count/description17 |
| A11/A12 |`src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` |PASS; newcriteria2/read1-2; persistedversion determineshistorical; existingrun unchanged |
| A13/A14 |`src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` |PASS; exactrule0fixtures/budgetsplit/cappedcontinuation/conservativecachedPENDING;32 fresh tests pass |
| A15/A16 |`src/test/js/discoveryContinuousRun.test.js` |PASS; overrideabsence; keyword/source/includeRawScan/timeout/no-completion retained |
| A15/A16 |`src/test/js/discoveryScheduleSetting.test.js` |PASS; overrideabsence; other schedule/source/status assertions retained |
| A15/A16 |`src/test/js/discoveryPapers.test.js` |PASS; complete unchanged CSS compared within existing inclusive markers |

### Fast-P RECORD_ONLY re-evaluation

| Source item | Master requirement | Result | Fresh evidence / retained limitation |
|---|---|---|---|
|02O-1 earlier syntheticARXIV external requests |Isolated fixtures/no real provider |Historical deviation retained; no current mandatory defect proven |Final loopback rewrite and rejecting interceptors inspected atFlowIT79–100; fresh C-1/C-9 guarded execution; historical exactwirecount unavailable |
|03O-1 originalcorpus/page34 |M-I1/03effect |**BLOCKED effect-only** |Fresh private test genuinely skipped; historical294metadata/295references/216files/79missing/215complete not promoted to fresh availability or79/52PASS |
|04O-1 qualificationeffects |M-I1/M-I2/04effect |**BLOCKED effect-only** |Historical23bodies/432pages/0facts and missingpersonlabels do not establish known1+2 coverage or conclusions on49; no fresh private-labelaudit |
|04O-2 helperclasspath |Independent runtime |Resolved invocation concern |C-9 uses all127 fresh SurefireCPentries; standaloneanalysis exit0 |
|05O-1 isolatedfixturelimit |API/UI/persistence separation |Limitation retained |ActualSQL/files C-1/C-9 plus real-assetsChromium; no authenticated integrateddeployment/restart/originalPDF/billing claim |
|05O-2 absentattempttimestamp |Factual UI display |No mandatory violation proven |Reader labels actual recordcapturetime and explicitly says filetime unavailable |
|05O-3 preliminaryharnessfailures |ActualUI evidence |Historical failures retained; no inferred productdefect |Fresh actual controls/wire/styles/screenshots passed; fixtureboundary disclosed |
|07fixture/font/harness limitations |M-I1/07browser |Limitation retained |FreshChromium; blockedremotefonts/fallback; unrelated genericfixtures not treated as productproof |

### Finding lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | **RESOLVED** |Service231 verifies AVAILABLE/hash/length before choosing reuse; scoped capacity uses zero incremental bytes for valid reuse and actualbytes for new content. Fresh two-case capture regressions demonstrate no growth and continued positive-growth blocking. |

Prior V-1 root cause no longer exists. Blocking findings shrink from one to zero; no equal-or-higher new regression found. Convergence is **PROGRESSING**.

Fresh C-1 output:

```text
SCOPE_REUSE limit=free-space bodyBytes=1024 headroom=1023 saved=2 reused=1 failed=1 usedBytes=1024 files=1 growth=0 first=frozen newBody=ARCHIVE_STORAGE_BLOCKED
SCOPE_REUSE limit=archive-cap bodyBytes=1024 headroom=1023 saved=2 reused=1 failed=1 usedBytes=1073740801 files=2 growth=0 first=frozen newBody=ARCHIVE_STORAGE_BLOCKED
```

The archive-cap fixture uses a real sparse filler file counted by actual directory-size accounting. Both cases preserve first, update latest on reuse, retain identical physical logical bytes/filecount, and reject distinct new content without increasing SAVED.

Pre-request headroom gating remains in `beginScope`/`beginScopeAttempt`. Current deployment permission, scope membership/attempt binding, path/writability checks, DELETING rejection, verified reuse, currentcap/minfree and new-write postchecks remain. Direct-save behavior remains.

The executor’s historical failing-before B0 evidence is preserved as lineage, not represented as a new independent old-code run. Current candidate behavior was independently tested freshly.

### Findings

#### P1

N/A — V-1 resolved.

#### P2

N/A.

#### Observations

- Original-corpus and person-label effects remain incomplete.
- Seeded code-receipts documentation whitespace remains unrelated/nonbehavioral; excluded from repair.
- CLAUDE-required `fix-v` was read and applied in AUDIT_ONLY mode. Explicit review-p/review-fast-p governs reporting and repair routing. No fix-N, continuation, knowledge update, product repair or review-evidence write occurred.
- Knowledge probes for source-layout receipts, page-commit completeness, reentry accounting and cache-key synchronization were read; no inferred requirement was promoted to P1.
- No configured LSP is recorded; the repair changes a private helper and tests, with no exported API change.

### Evidence boundaries

- **Original294/page34:** BLOCKED effect-only. No complete same-original dual-version79/52/0 verification; genuine skip retained.
- **Original23/person labels:** BLOCKED effect-only. No fresh original-quote/person-label audit or qualification-effect success.
- The master explicitly allows code completion when originals are missing while prohibiting real-effect success claims. This PASS uses that exception; it does not satisfy the effect/manual gates.
- Synthetic parser/fact tests establish bounded behavior, never real-original accuracy or human acceptance.
- Browser fixture evidence does not establish production authentication, integrated SQL-backed UI restart, originalPDF fidelity or vendor billing.
- All26 authoritative manual items and explicit sign-off of candidate/master identity remain pending.

### Repair planning

**N/A.** PASS does not route to repair-p. Existing exact `repair.md` remains unchanged; no new repair round or artifact was created.

### Next action

Controller: append this complete Epoch2 report, create the plan-derived pending26-item manual-acceptance epoch, and set **AWAITING_HUMAN_ACCEPTANCE** for code head `1ebe3534be212c26eb1e490d747ae4a10daa702f` and governing master SHA-256 `fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3`.

No product code was modified.

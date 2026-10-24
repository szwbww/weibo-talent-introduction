# Child06b execution log

## Execution Result: PLAN_CONFLICT

Paused, uncommitted implementation; **not READY_FOR_VERIFICATION**, not an independent verification result. Authorized changes and logs remain in the retained worktree. No incomplete feature commit was created.

### Identity and authority

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md`
- Plan SHA-256: `53611d3ba33bee9e2d20fb9c14f1adc5f4ecd22070fec1de8d9a5ac14a00b57f`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md@53611d3ba33bee9e2d20fb9c14f1adc5f4ecd22070fec1de8d9a5ac14a00b57f`
- Epoch: NEW, child06b epoch1/fix_round0; approval: current invocation and exact brief.
- Exact child commit: `c18dbc060dcf4c8440f853ede4d1d3aaf737994f`; `git show c18dbc060dcf4c8440f853ede4d1d3aaf737994f:docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md | shasum -a 256` returned the same SHA-256.
- Master: `6112a58b4bc94b510a6b1cff88cfd9ec159c83d6`; full current master/brief read. No amendment made.
- Executor: ApolloRuntimeImplementer, with disjoint reporting and fixture implementation slices.
- Target root: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`
- Branch: `fast/discovery-paper-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`
- Pre-execution code SHA / child base: `16a138852bea292d40b47dd9cb2d7fc0c8732578`
- Initial/final retained HEAD: `7371ef5e50af0694967350a205e9fcf98d3a948b`
- Post-execution code commit: N/A; new evidence commit: N/A. No staging/commit.
- Boundary: authorized working-tree changes on retained HEAD. Existing dirty `docs/plans/fast/discovery-paper-master/ledger.md` and child evidence scaffolding preserved, not included as product changes.

Observed retained history:

```text
7371ef5e50af0694967350a205e9fcf98d3a948b docs(fast-p): checkpoint 06a
27acda3ca258d86bc1c1073520264f407100f502 docs(fast-p): record 06a light verification
16a138852bea292d40b47dd9cb2d7fc0c8732578 feat(fast-p): implement 06a
```

### Task status

| Requirement | Status | Evidence / remaining boundary |
|---|---|---|
| I-1 / T-1 mandatory saved settings | IMPLEMENTED in authorized code | Required constructor dependency; real SQL fixture service. Existing06a SettingsIT freshly ran8 tests, zero failures/errors/skips. No06a/controller/schema changes. |
| T-2 null/false and explicit-true validation | IMPLEMENTED | Null/false retained without settings reads; explicit true validates readiness and saved search, preserving503 and409 APOLLO_SEARCH_DISABLED; settings-unavailable translated to stable503. |
| I-2 / T-2 freeze/reentry | PARTIAL / CONFLICT C-1 | Existing run wins before service/settings checks; each new begin freezes once and rechecks explicit true from that snapshot. Historical nullfalse retained. Manual/scheduled behavior passed; new queue/null acceptance fails. |
| I-2 legacy/invalid snapshots | IMPLEMENTED | Shared06a parser/serializer; absent member → LEGACY booleans from run.apolloRequested; invalid → Worker CONFIG_INVALID/no provider request, Query unknown/null. |
| I-3 / T-3 paid permissions | IMPLEMENTED | Guard before DETAIL cache lookup and permit/reservation; original decision preserved with only added PAID_DETAILS_DISABLED. Actual SQL/Worker/Gate smoke passed. |
| I-4 / T-3 readonly channels/summary | IMPLEMENTED | ActualSQL3/2/1/pending1, total6; known empty zero, missing unknown. Constructor/capabilities/admission counts retained. |
| Required regression success | BLOCKED | Final fully enabled171 tests:8 failures,0 errors,0 skipped. One queue contract failure; seven unchanged FlowIT assertions. No test exclusions/changed expectations. |
| A-1/A-3 settings coverage | EXERCISED via fresh SettingsIT | Four combinations, persistence/CAS/default/failure protocol; not production deployment/human acceptance. |
| A-2 all launch parity | CONFLICT C-1 | Existing real controller/scheduler/pipeline integration test proves default queue mismatch. |
| Commit/readiness | NOT DONE | `feat(fast-p): implement 06b` intentionally not used for an incomplete feature. |

### C-1 — New queue null and historical null lack an authoritative discriminator

Proven unavoidable unauthorized-file conflict. Null preservation and historical-nullfalse both remain binding; neither was weakened.

Actual source chain:

```text
DiscoveryPipelineService.kt:50       PIPELINE_CRITERIA_VERSION = 1
DiscoveryPipelineService.kt:320      normalized = verificationRegistration?.resolveOptions(requested)
DiscoveryPipelineService.kt:324      criteriaJson = objectMapper.writeValueAsString(normalized)
DiscoveryPipelineService.kt:325-328  launch persists JSON with criteriaVersion = PIPELINE_CRITERIA_VERSION
DiscoveryPipelineService.kt:632      criteria = criteriaOf(initial)
DiscoveryPipelineService.kt:646      begin(executionId, requireNotNull(state.criteria), historical = true)
DiscoveryPipelineService.kt:1263-1270 criteriaOf accepts only version1 and deserializes PaperSearchCriteria
PaperSearchCriteria.kt:14            apolloAssist: Boolean? = null
DiscoveryPaperQueueRepository.kt:401-408 absent/null apolloAssist is false in launch option comparison
```

Actual queue metadata was inspected: version1 is also written for new launches; no authoritative new/legacy discriminator reaches begin. Generation/timestamp heuristics or in-memory object identity are not an approved persistent compatibility protocol. Registration receives identical nullable criteria plus historical=true from both cases.

Observed original acceptance with mysqlIt=true:

```text
DiscoveryVerificationIntegrationTest
manual keyword scheduled and continuous freeze options and bind actual positive execution IDs
expected: <[true, false, true, true]>
 but was: <[true, false, true, false]>
at DiscoveryVerificationIntegrationTest.kt:231
```

Its new continuous launch is `pipeline.launch(PaperSearchCriteria(sources=listOf("CROSSREF"), keywords=listOf("continuous")), "MANUAL", false)` followed by an actual dispatched tick. Its expectation remains unchanged.

Minimum additional production authority: `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt`, with an approved persistent criteria provenance/version interpretation distinguishing newly saved nullable criteria from legacy criteria. Existing authorized `DiscoveryVerificationIntegrationTest.kt` can retain its default-queue assertion and add legacy compatibility coverage. A dedicated `DiscoveryPipelineServiceTest.kt` change would need separate authorization if selected; it is not proven mandatory. A new domain field/migration is not proven necessary. Even the additional production file exceeds the ten-file cap: human revision/split/explicit cap authorization is required. No workaround/amendment applied.

Previously raised C-2 is **WITHDRAWN**. Exact T-2 permits explicit-true validation reads in resolveOptions; the single-read constraint is the fresh begin freeze, not all validation plus freeze globally. Authorized code now validates explicit true before task launch, preserves null, and begin uses existing-first/readiness-only/one fresh freeze, without calling resolveOptions again. New unit cases cover saved-off409, settings changing between validation/freeze, and read-failure503. No task/controller changes were needed for that interpretation.

### Authorized changed files

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — required settings dependency, explicit validation, immutable fresh access and shared existing-run interpretation.
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — CONFIG_INVALID boundary and pre-cache/pre-permit paid-disabled finish.
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — optional snapshot/attempt DTOs, reason translation.
4. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — readonly aggregation/value DTO only.
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — constructor/160 fixture, explicit saved true/true existing registration case; actual SQL/Worker/summary regression and standalone launcher.
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt` — real settings dependency, explicit true/true and160 migration.
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt` — explicit stub, constructor/container bindings, freeze/reentry/validation/503 tests.
8. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` — settings/160, frozen A/B/legacy regression; original launch expectation retained.
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` — shared real SQL settings fixture, optional observed repository worker,160; legacy seed JSON `{}` and original assertions retained.
10. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` — snapshots/legacy/unknown, bounds, retained attempts/read failures.

No rules/Gate/client/controller/frontend/schema/06a/other product-test edits. Historical seeded runs retain missing-member JSON and apolloRequested semantics. New paid registration fixtures explicitly save true/true instead of relying on new detail=false defaults.

### Caller/selector receipts

`functions.grep(pattern="DiscoveryVerificationRegistrationService\\(", path="<target>/src")` returned constructor files confined to authorized tests:

```text
main/.../DiscoveryVerificationRegistrationService.kt  class declaration
test/.../DiscoveryCountryRegistrationTest.kt          fixture, interrupt, cancellation
test/.../DiscoveryCountrySupplementFlowIT.kt          normal, supplement-off, concurrent, budget
test/.../DiscoveryVerificationIntegrationTest.kt      fixture, disabled
test/.../DiscoveryVerificationWorkerTest.kt           standalone scenario, existing runtime registration
```

Production search was `functions.grep(pattern="DiscoveryVerificationRegistrationService|resolveOptions\\(|\\.begin\\(", path="<target>/src/main/.../{ExpertDiscoveryController.kt,DiscoveryPipelineService.kt,ExpertDiscoveryScheduler.kt,ExpertDiscoveryService.kt}")`. Observed:

```text
ExpertDiscoveryController.kt:400  verificationRegistration?.resolveOptions(normalized)
DiscoveryPipelineService.kt:320  verificationRegistration?.resolveOptions(requested)
DiscoveryPipelineService.kt:646  verificationRegistration?.begin(..., historical=true)
ExpertDiscoveryScheduler.kt:401  verificationRegistration?.resolveOptions(requested)
ExpertDiscoveryService.kt:441    verificationRegistration?.begin(execId, criteria)
```

Regression class names resolved from source declarations, not guessed aliases: ApolloPeopleSearchClientTest, DiscoveryVerificationRulesTest, DiscoveryVerificationAdmissionGateTest, DiscoveryVerificationRepositoryIT, DiscoveryApolloSettingsIT, plus authorized tests. `DiscoveryVerificationIntegrationTest.kt:41` requires **mysqlIt=true**; SettingsIT requires migrationIt=true. Final command enables both.

### Exact commands / results

Command A:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryCountrySupplementFlowIT,DiscoveryCountryRegistrationTest,DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRepositoryIT,DiscoveryApolloSettingsIT -DmigrationIt=true
```

Command B, complete resolved opt-ins:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryCountrySupplementFlowIT,DiscoveryCountryRegistrationTest,DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRepositoryIT,DiscoveryApolloSettingsIT -DmigrationIt=true -DmysqlIt=true
```

| Invocation | Result | Evidence / next invocation reason |
|---|---|---|
| A, finished09:22 | exit1, test compilation failed | artifact://428; missed interrupt/cancel settings arguments and duplicate companion launcher; repaired only authorized files/reused original launcher. |
| A, finished09:28:54 | exit1;156 tests/7 failures/3 errors/1 skipped | artifact://430; new enum Mockito.eq null/unfinished-stubbing cascade corrected; no old assertion edits. |
| A, finished09:36:34 | exit1;156/7/0/1 | artifact://436; discovered integration mysqlIt opt-in; skip not accepted as coverage. |
| B, finished09:45:32 | exit1;168/8/0/0 | artifact://440; newly exercised queue proves C-1; then completed exact T-2 explicit prelaunch validation/unit contracts. |
| **B, final state, finished09:54:38** | **exit1;171 tests/8 failures/0 errors/0 skipped** | **artifact://444**, total08:29; all selected suites ran after final edits, original failed expectations retained. |
| Final separate SQL/Worker/summary Java launcher | exit0 | `target/06b-apollo-access-smoke-final.log`, observed stdout below. Earlier separate successful smoke preserved in `target/06b-apollo-access-smoke.log`. |
| `git diff --check` | exit0/no output | artifact://448. |
| Final identity helpers/status | exit0 | artifact://448; unchanged plan/root/branch/git-dir/HEAD; staged0, authorized dirty products preserved. |

No checks ran during delegated edits. Subsequent invocations had a constructor/matcher/opt-in/explicit-validation change, not a baseline rerun to confirm reported errors or an exclusion. No baseline checkout/test rerun, production Spring context, deployment/push/merge/Git rewrite or real provider requests. Surefire failure stops Maven before bound Node test/syntax executions; those and a WAR build are **not claimed**.

Final fresh Surefire:

| Class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryVerificationWorkerTest | 16 | 0 | 0 | 0 |
| DiscoveryCountrySupplementFlowIT | 8 | 0 | 0 | 0 |
| DiscoveryCountryRegistrationTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationIntegrationTest | 13 | 1 | 0 | 0 |
| DiscoveryVerificationFlowIT | 32 | 7 | 0 | 0 |
| DiscoveryVerificationQueryServiceTest | 18 | 0 | 0 | 0 |
| ApolloPeopleSearchClientTest | 13 | 0 | 0 | 0 |
| DiscoveryVerificationRulesTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationAdmissionGateTest | 8 | 0 | 0 | 0 |
| DiscoveryVerificationRepositoryIT | 17 | 0 | 0 | 0 |
| DiscoveryApolloSettingsIT | 8 | 0 | 0 | 0 |

### Unchanged FlowIT failures / provenance

| Existing case / line | Observed final mismatch |
|---|---|
| lowercase HTTP self evidence… :780 | expected APOLLO_UNCLEAR, actual DIRECT_CLEAR |
| 403 invalid response page caps item budget… :636 | expected autoCount0, actual1 |
| new public alias requests… :909 | expected requests3, actual0 |
| self identity negatives… :851 | ambiguous expected autoCount0, actual1 |
| A3 distinct campuses… :1034 | incomplete-search expected narrow count0, actual1 |
| inflight Apollo query index… :887 | expected APOLLO_UNCLEAR, actual PENDING |
| A4 legacy in flight… :1110 | expected APOLLO_NO_MATCH; actual SEARCH_INCOMPLETE/SEARCH_PAGE_LIMIT |

Bounded source investigation, not baseline tests:

```sh
git diff -- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt
git blame -L 771,782 HEAD -- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt
git blame -L 86,105 HEAD -- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt
git blame -L 204,217 HEAD -- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
```

artifact://434 shows Worker only adds parsing/detail=false gating; FlowIT only settings/worker fixture/target160, not failed assertions or legacy JSON. Blame: SELF expectation from41e95f05(Oct19); ruleVersion>=1 SELF qualification fromb0d2f278(Oct20); missing-run ruleVersion default1 from043c8437(Oct21). These predate06b and remain unchanged. Current SELF Professor fixture can take DIRECT_CLEAR before Apollo. Historical requested=true runs synthesize detail=true, so the paid-disabled branch is not entered.

Other unchanged-source observations: repository caps ORCID, not Apollo item requests; pagination retains incomplete search without prior finishedQueryKeys and continues saved queries after a capped query. **[INFERENCE]** These failures are pre-existing fixture/current-code mismatches from unchanged-path/blame evidence; no claim a baseline checkout was executed or every root cause was repaired. Required-suite failure remains a blocker. No rule change, old-payload repinning or expectation rewrite to make the suite green.

### Separate actual SQL runtime

Outside JUnit via Python Eval subprocess, using the complete **fresh final Surefire** classpath from `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryVerificationWorkerTest.xml`, not a stale helper or hand-selected dependencies:

```python
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess, hashlib
wt = Path('/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master')
report = wt / 'target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryVerificationWorkerTest.xml'
root = ET.parse(report).getroot()
props = {p.attrib['name']: p.attrib['value'] for p in root.findall('./properties/property')}
cp = props['surefire.test.class.path']
assert str(wt / 'target/test-classes') in cp and str(wt / 'target/classes') in cp
assert 'mysql-connector-j' in cp
command = ['/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java', '-cp', cp,
           'com.weibo.talentintroduction.discovery.service.DiscoveryVerificationWorkerTest',
           '--apollo-access-smoke']
result = subprocess.run(command, cwd=wt, stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT, text=True, timeout=240)
```

Complete classpath SHA-256: `f3b59342667efda4decb557407c65553243f487e1361d23b82ba322cd836228e`. Final separate exit: **0**.

Observed stdout:

```text
APOLLO_ACCESS_SMOKE frozen A=true/true revision=1 unchanged=true B=true/false revision=2 legacy=LEGACY/true/true oldDetailHttp=2
APOLLO_ACCESS_SMOKE free-required-detail outcome=PENDING reason=PAID_DETAILS_DISABLED detailsComplete=false attempts=0 permits=0 cacheLookups=0 detailHttp=0 gate=not-admitted
APOLLO_ACCESS_SMOKE live-free-search requests=1 detailHttp=0 needless-detail=APOLLO_CLEAR gate=eligible projection=PROJECTED
APOLLO_ACCESS_SMOKE malformed=CONFIG_INVALID providerHttp=0 attempts=0
APOLLO_ACCESS_SMOKE channels search=3 detail=2 other=1 pendingDispatch=1 totalRegistered=6 billing=not-inferred emptyRun=zero missingRun=unknown
APOLLO_ACCESS_SMOKE PASS isolatedMySQL=true strictMockHTTP=true productionContext=false oldSettingsImmutable=true
```

Launcher creates/disposes isolated Docker MySQL, migrates160 and exercises real registration/settings/repository/Worker/rules/Gate/commit/query with strictly mocked HTTP. No production app/services/data.

Same-run paid cache source is intentionally synthetic successful SQL accounting through existing repository methods: it proves free **target** cannot even look it up. It is not a free-mode provider call or target activity. Target has0 attempts/permits/cache lookups/reuse/HTTP; original recomputed outcome/qualification/route/personId and reasons plus added reason are asserted. A separate live target performs mocked free search/no detail. Strong existing search evidence still admits/projects through real Gate. A/legacy settings stay byte-identical. Malformed access fails before ORCID/Apollo. Six channel attempts include completed429 and unfinished ORCID dispatch, not billing evidence.

Smoke does **not** claim queue provenance solved: actual queue test fails above.

### Downstream DTO contracts for07

- `VerificationSummary.apolloAccess: FrozenApolloAccess?` reads immutable run JSON only; fields schemaVersion/settingRevision/searchEnabled/detailEnabled/source.
- SAVED uses positive revision, DEPLOYMENT_DEFAULT revision0, missing member on existing run LEGACY with both flags=run.apolloRequested. Malformed/missing run is null/unknown, not false/free.
- `VerificationSummary.apolloAttempts: VerificationAttemptChannels?`: Long search/detail/other/pendingDispatch. Search=APOLLO BROAD/NARROW/VARIANT; detail=APOLLO DETAIL; remaining registered attempts=other. Pending DISPATCH_RESERVED overlaps a channel, not extra to sum. Known empty run zeros; missing run/aggregate unknown/null.
- Registered attempts include failures/reservations; never infer successful paid calls or consumed credits.
- Reason: PAID_DETAILS_DISABLED → `付费详情未启用，证据仍不完整`.
- Existing capabilities ready/auth and apolloDefault compatibility unchanged. New controls still use06a settings API; no claim all deployment budgets/timeouts now freeze-read.

### Freshness / remaining boundary

- Plan identity: rechecked unchanged.
- Root/branch/git-dir/retained HEAD: rechecked unchanged.
- Required resolved suites after final edits: YES, exit1/171/8/0/0 recorded; no selected skip.
- Separate runtime after final edits/fresh final classpath: YES, exit0.
- Historical06a evidence: baseline only; SettingsIT freshly rerun.
- New product/evidence commits/reachability: N/A, none created/staged.
- Working tree remains dirty with exact authorized partial changes and preserved controller evidence; no reset/rewrite.
- Remaining: human C-1 queue provenance/file-cap decision plus bounded treatment of unchanged FlowIT failures without altering admission/rule contract. No automatic plan/repair amendment generated.
- Next: resolve/amend authority, complete implementation/commit/checks, then controller-owned independent verification. Do not advance07 or treat as PASS.

## Epoch2 Execution Result: PLAN_CONFLICT

Fresh executor `ApolloRuntimeImplementerE2`, execute-p; implementation retained, **not READY_FOR_VERIFICATION**. Epoch1 history above is unchanged. C-1 queue provenance is now implemented and freshly exercised. The required regression still fails in seven existing FlowIT cases; no assertions, production rules, or test exclusions were changed to conceal this.

### Current execution identity

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md`
- Plan SHA-256: `ff878a348b79b0a9454af6decd9ae8115eca3d3cb8c685a4d7b396b6d91ff7f1`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md@ff878a348b79b0a9454af6decd9ae8115eca3d3cb8c685a4d7b396b6d91ff7f1`
- Execution epoch: NEW; current amended plan bytes, A11/A12 and current invocation authorize eleven product/test paths. This is not a continuation claim against the previous plan hash.
- Approval basis: user-approved Epoch2 brief and amended child; ten prior partial files preserved, no completed-child replay.
- Target root: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`
- Target branch: `fast/discovery-paper-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`
- Child product base: `16a138852bea292d40b47dd9cb2d7fc0c8732578`
- Pre-execution code SHA: `16a138852bea292d40b47dd9cb2d7fc0c8732578`
- Pre-execution HEAD / final HEAD: `c5eca7007eed3de47a9f33363a28c6ae8aa01690`
- Post-execution product SHA: N/A. Evidence commit: N/A. No files staged or committed; the instruction permits the product commit only after correct final checks.
- Boundary: retained authorized working-tree implementation on that HEAD. Controller-owned dirty ledger is preserved and untouched.

### Task status and implementation boundary

| Requirement | Status | Current evidence |
|---|---|---|
| T-1 / I-1 shared persisted settings | IMPLEMENTED | Mandatory SettingsService dependency retained, shared freeze/parser; SettingsIT freshly passes eight cases. |
| T-2 / I-2 freeze and reentry | IMPLEMENTED | New begin freezes once after existing-run branch; null/false retained; explicit true validation retained; fresh registration/integration suites pass. |
| Epoch2 queue provenance | IMPLEMENTED | Pipeline writes criteriaVersion=2, accepts 1/2, passes historical=true only for 1. Added SQL integration scenario checks null serialization, real execution binding, old1 nullfalse/new2 nullsaveddefault, persisted criteria unchanged, same-run reentry after saved settings change. |
| I-2 old/malformed run interpretation | IMPLEMENTED | LEGACY derives from run.apolloRequested; malformed access CONFIG_INVALID before provider requests. Fresh Worker/Query tests and standalone runtime. |
| I-3 paid-disabled boundary | IMPLEMENTED | Pre-cache/pre-permit gate retained; standalone real SQL/Worker/Gate proves PENDING plus reason and zero attempts/permits/cache lookups/HTTP for target. |
| I-4 readonly channels | IMPLEMENTED | Fresh standalone SQL summary proves search3/detail2/other1/pending1, total6; missing unknown, known-empty zero. |
| Required regression success | CONFLICT | Fresh 172 tests, seven failures, zero errors/skips. Integration now fourteen passing cases including queue boundary; all failures are FlowIT assertions below. |
| Commit / readiness | BLOCKED by conflict | No misleading completed-feature commit. |

Authorized product/test files retained (no additions outside this list):

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — required settings, validation/freeze/reentry and shared historical interpretation.
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — malformed snapshot boundary and paid-disabled pre-cache/pre-permit finish.
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — immutable access/attempt DTOs and reason.
4. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — readonly channel aggregation.
5. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` — Epoch2 versioned queue provenance.
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — retained runtime/access coverage; standalone entry now also invokes real queue-version smoke.
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountrySupplementFlowIT.kt` — retained required settings and160 fixture.
8. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCountryRegistrationTest.kt` — retained required settings/stubs and registration contracts.
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` — retained launch/freeze tests; new version1/version2 SQL scenario plus standalone launcher.
10. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` — retained shared settings fixture/160 migration; original legacy JSON, cached payloads and assertions unchanged.
11. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` — retained immutable access/channel/missing-data coverage.

### Fresh commands and observed results

Initial `python ...` identity invocation exited127 because this environment exposes `python3`, not `python`. Retried the failed prerequisite with python3; no setup or repository edits were repeated.

```sh
python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md
python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master
```

Both exit0; current identities above. Full amended child, complete brief and prior execution report were read before implementation. Final helper commands below reconfirm unchanged identities.

Required regression after final product/test edits:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryCountrySupplementFlowIT,DiscoveryCountryRegistrationTest,DiscoveryVerificationIntegrationTest,DiscoveryVerificationFlowIT,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRepositoryIT,DiscoveryApolloSettingsIT -DmigrationIt=true -DmysqlIt=true
```

Exit1; finished 2026-10-24T10:47:16+08:00, total07:17. **172 tests / seven failures / zero errors / zero skipped**. Full Maven output `artifact://462` (failure summary `artifact://465`). No repeated unchanged baseline run; this invocation exercises the amended queue implementation and added tests.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryVerificationWorkerTest | 16 | 0 | 0 | 0 |
| DiscoveryCountrySupplementFlowIT | 8 | 0 | 0 | 0 |
| DiscoveryCountryRegistrationTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationIntegrationTest | 14 | 0 | 0 | 0 |
| DiscoveryVerificationFlowIT | 32 | 7 | 0 | 0 |
| DiscoveryVerificationQueryServiceTest | 18 | 0 | 0 | 0 |
| ApolloPeopleSearchClientTest | 13 | 0 | 0 | 0 |
| DiscoveryVerificationRulesTest | 19 | 0 | 0 | 0 |
| DiscoveryVerificationAdmissionGateTest | 8 | 0 | 0 | 0 |
| DiscoveryVerificationRepositoryIT | 17 | 0 | 0 | 0 |
| DiscoveryApolloSettingsIT | 8 | 0 | 0 | 0 |

Surefire failure prevents lifecycle execution of the bound frontend goals. They were actually run separately using their existing Maven execution IDs, not skipped or suppressed:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:exec@node-test exec:exec@node-check-app exec:exec@node-check-task-modal-runtime
```

Exit0, BUILD SUCCESS; Node1816 tests/334 suites,1816 pass, zero fail/cancelled/skipped/todo, then both syntax executions succeeded (`app.js`, `task-modal-runtime.js`). Output `artifact://469`.

Final diff/identity/boundary command:

```sh
git diff --check && python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md && python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-23/discovery-paper-06b-apollo-runtime.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-branch fast/discovery-paper-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master && git status --short
```

Exit0 (`artifact://472`); plan hash/root/branch/git-dir/HEAD unchanged; staged0, eleven dirty authorized product/test files plus preexisting dirty controller ledger, before this append-only report addition.

### Fresh separate actual runtime

Ran outside JUnit through Python Eval subprocess, using the complete newly generated Surefire `surefire.test.class.path` property. Exact launcher construction:

```python
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess, hashlib
wt = Path('/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master')
report = wt / 'target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryVerificationWorkerTest.xml'
root = ET.parse(report).getroot()
props = {p.attrib['name']: p.attrib['value'] for p in root.findall('./properties/property')}
cp = props['surefire.test.class.path']
assert str(wt/'target/test-classes') in cp and str(wt/'target/classes') in cp and 'mysql-connector-j' in cp
command = ['/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java', '-cp', cp,
           'com.weibo.talentintroduction.discovery.service.DiscoveryVerificationWorkerTest',
           '--apollo-access-smoke']
result = subprocess.run(command, cwd=wt, stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT, text=True, timeout=300)
(wt/'target/06b-epoch2-apollo-access-smoke.log').write_text(result.stdout)
```

Exit0; classpath SHA-256 `f3b59342667efda4decb557407c65553243f487e1361d23b82ba322cd836228e` (the dependency paths are unchanged, but test/classes were freshly compiled by the required invocation). Observed stdout:

```text
APOLLO_ACCESS_SMOKE frozen A=true/true revision=1 unchanged=true B=true/false revision=2 legacy=LEGACY/true/true oldDetailHttp=2
APOLLO_ACCESS_SMOKE free-required-detail outcome=PENDING reason=PAID_DETAILS_DISABLED detailsComplete=false attempts=0 permits=0 cacheLookups=0 detailHttp=0 gate=not-admitted
APOLLO_ACCESS_SMOKE live-free-search requests=1 detailHttp=0 needless-detail=APOLLO_CLEAR gate=eligible projection=PROJECTED
APOLLO_ACCESS_SMOKE malformed=CONFIG_INVALID providerHttp=0 attempts=0
APOLLO_ACCESS_SMOKE channels search=3 detail=2 other=1 pendingDispatch=1 totalRegistered=6 billing=not-inferred emptyRun=zero missingRun=unknown
APOLLO_ACCESS_SMOKE PASS isolatedMySQL=true strictMockHTTP=true productionContext=false oldSettingsImmutable=true
APOLLO_QUEUE_ACCESS_SMOKE version=1 nullPersisted=true executionId=4001 requested=false frozen=true/false reentry=unchanged providerHttp=0
APOLLO_QUEUE_ACCESS_SMOKE version=2 nullPersisted=true executionId=4001 requested=true frozen=true/false reentry=unchanged providerHttp=0
```

Isolated disposable MySQL, actual SQL settings/registration/attempts/Worker/Rules/Gate/query and actual queue/task execution binding; provider/ES HTTP strictly intercepted with MockRestServiceServer, not live Apollo sockets. The two version scenarios clear the isolated tables between cases, so id4001 is reused across independent cases, not an overwrite of one run. Paid-cache source is synthetic SQL evidence as documented in epoch1; free target does not read it. No production Spring context, deployment or business data writes. Runtime is not a substitute for the failing required suite.

### Bounded investigation and exact remaining authority

The final seven failures are:

| Existing FlowIT assertion | Fresh observation | Source-established boundary |
|---|---|---|
| lowercase HTTP SELF :780 | APOLLO_UNCLEAR expected, DIRECT_CLEAR actual | Missing run ruleVersion becomes1; rule1 includes SELF Professor in qualification and short-circuits before Apollo. |
| multi-scenario provider/page/item-budget :636 | autoCount0 expected,1 actual | Fixture budget branch sets maxRequestsPerItem3; repository explicitly applies item cap only to non-Apollo, allowing the subsequent Apollo evidence path. No source permission to restore the old Apollo cap. |
| new public alias :909 | three requests expected, zero actual | Same SELF Professor direct-clear short circuit. |
| SELF identity negatives :851 | ambiguous autoCount0 expected,1 actual | Same SELF Professor direct-clear short circuit before Apollo ambiguity is requested. |
| A3 incomplete-search :1034 | zero narrow expected, one actual | Worker durably marks a capped broad query incomplete but continues the remaining saved queries, including narrow. |
| cached inflight cursor :887 | APOLLO_UNCLEAR expected, PENDING actual | Legacy payload already has ruleVersion0; cursor queryIndex1/page2, no finishedQueryKeys for prior query. allComplete requires every query index in finishedQueryKeys. |
| A4 legacy inflight :1110 | APOLLO_NO_MATCH expected, SEARCH_INCOMPLETE/SEARCH_PAGE_LIMIT actual | Same old cursor/finishedQueryKeys representation, independent of Apollo access. |

Receipts (read-only, no baseline test checkout):

```sh
git blame -L 774,798 HEAD -- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt
git blame -L 834,918 HEAD -- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt
git blame -L 210,224 HEAD -- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
git blame -L 88,109 HEAD -- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt
git blame -L 481,507 HEAD -- src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
git blame -L 280,337 HEAD -- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt
git blame -L 617,650 HEAD -- src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt
```

Observed blame: SELF expectations originate41e95f05 Oct19; SELF rule1 and Apollo item-cap exemption b0d2f278 Oct20; missing run ruleVersion default1 is043c8437 Oct21; per-query finishedQueryKeys completion and continue-after-cap are8024e30c Oct21. The budget expectation predates these at a0103c2e Oct18. Current retained FlowIT diff changes only shared settings/worker fixture/migration target, not these inputs/assertions. Worker access additions do not alter the legacy true/true path.

This establishes old assertions and later implementation semantics, **not a claimed baseline test result**. [INFERENCE] They are pre-existing compatibility/test drift; no unobserved baseline PASS/FAIL is asserted.

Three SELF test inputs could plausibly be made explicitly ruleVersion0, supported by the unchanged Rules API and existing WorkerTest version0 coverage: the original assertions and their default `rules.evaluate(...)` recomputation target the pre-rule1 semantics. This is distinct from changing expected outcomes. However, that does not solve the independent item-budget/capped-query/cached-cursor assertions; a blanket fixture version pin is not an authorized resolution and was not applied.

**Minimum decision required:** explicitly reconcile the retained FlowIT item-budget/capped-query expectations with the must-not-change current Apollo budget/query behavior, and decide whether old cached payloads without finishedQueryKeys require a targeted production compatibility restoration or explicitly versioned fixture coverage. If selecting test-contract corrections, authorize exact FlowIT scenario/expectation revisions; current invocation expressly forbids expectation repinning/weakening. If selecting production restoration, explicitly amend the preserved budget/query compatibility requirements before editing. No extra product file is proven necessary for these decisions: Worker/Repository/FlowIT are already authorized, but the behavioral authority is missing. Rules/Gate/client changes are neither needed nor requested.

A narrower fixture-only ruleVersion0 correction for the three old SELF scenarios is supported by provenance, but cannot justify a successful whole-child result or override the remaining contradictory assertions. No new repair plan, waiver, rules change, expectation change, unrelated behavior repair, or unauthorized file edit was made.

### Caller receipts and downstream contracts

Fresh `functions.grep` over target `src`:

```text
pattern: DiscoveryVerificationRegistrationService\(|verificationRegistration\?\.(begin|resolveOptions)
ExpertDiscoveryController.kt:400   resolveOptions(normalized)
ExpertDiscoveryScheduler.kt:401    resolveOptions(requested)
ExpertDiscoveryService.kt:441      begin(execId, criteria)
DiscoveryPipelineService.kt:320    resolveOptions(requested)
DiscoveryPipelineService.kt:646    begin(executionId, criteria, historical=initial.criteriaVersion==1)
Direct test constructor files: DiscoveryCountryRegistrationTest,
 DiscoveryCountrySupplementFlowIT, DiscoveryVerificationIntegrationTest, DiscoveryVerificationWorkerTest.
```

Fresh regression class selectors were resolved via `functions.grep` pattern `class (DiscoveryVerificationWorkerTest|DiscoveryCountrySupplementFlowIT|DiscoveryCountryRegistrationTest|DiscoveryVerificationIntegrationTest|DiscoveryVerificationFlowIT|DiscoveryVerificationQueryServiceTest|ApolloPeopleSearchClientTest|DiscoveryVerificationRulesTest|DiscoveryVerificationAdmissionGateTest|DiscoveryVerificationRepositoryIT|DiscoveryApolloSettingsIT)` against target `src/test/kotlin`; each name exists in its matching source file. No guessed selectors or omitted opt-ins.

Downstream DTOs unchanged from epoch1 implementation: nullable `VerificationSummary.apolloAccess: FrozenApolloAccess?` and `apolloAttempts: VerificationAttemptChannels?`; schema1 access fields revision/search/detail/source, SAVED positive revision, DEPLOYMENT_DEFAULT revision0, absent member on an existing run explicit LEGACY from apolloRequested; malformed/missing run null. Channel Long fields search/detail/other/pendingDispatch are registered attempts, not billed credits; pending overlaps channels. Known empty run zero, missing unknown. Reason `PAID_DETAILS_DISABLED` maps to `付费详情未启用，证据仍不完整`. Query constructor and capabilities readiness/apolloDefault compatibility preserved. No claim all deployment timeouts/budgets are frozen.

### Freshness and next action

- Plan identity rechecked: YES, unchanged current hash.
- Worktree root/branch/git-dir/HEAD rechecked: YES.
- Required commands fresh after final product/test edits: YES; Maven required suite FAIL, bound Node/syntax PASS, separate runtime PASS, diff check PASS.
- Historical evidence used only as baseline: YES.
- Product/evidence commit reachability: N/A; no new commit because required checks are not correct.
- All eleven authorized partial files retained; original assertions/legacy payloads retained. This report is append-only; controller ledger untouched.
- Remaining blocker: the exact bounded behavioral/test authority above, not old C-1 or withdrawn C-2.
- Next action: human decision/amendment for the remaining contract conflict, then finish/freshly check/commit before controller-owned independent verification. No later-child work.

## Proposed bounded amendment — preserve production, reconcile seven FlowIT contracts

**Proposal only; not approved or implemented.** Requested by controller after the Epoch2 pause. No product/test edits, test runs or commits accompany this section. This proposal supersedes the alternatives in “Minimum decision required” above: choose preservation of current production rules, budgets, query continuation and conservative cached-cursor semantics; **do not restore older production behavior**.

Authorize edits only to existing `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`, within the seven scenarios below and its existing shared `VerificationFlowFixture.seed` helper. No tests removed/excluded; no Rules/Gate/client/Worker/Repository/queue production changes. No shared-default rule-version change.

### Common narrowly scoped fixture change

Add an optional `settingsJson: String = "{}"` parameter at the end of `VerificationFlowFixture.seed`, and forward it to the existing `repository.createRun` call instead of the hardcoded `"{}"`. Only the three expressly historical SELF scenarios below pass `"""{"ruleVersion":0}"""`; every other seed keeps its current JSON. No `apolloAccess` is added: these remain historical LEGACY runs with both access flags determined by apolloRequested. Rename those three test descriptions to say “legacy ruleVersion0” so the compatibility contract is explicit. Assert their serialized worker payload has `ruleVersion == 0` and their run snapshot has no `apolloAccess`.

Source basis: Rules accepts ruleVersion0..2 (`DiscoveryVerificationRules.kt:42–43`), includes SELF qualification only at version>=1 (`:88–89`), and defaults its direct `evaluate` API to0. Worker honors an explicit integral run ruleVersion, while a missing field defaults1 (`DiscoveryVerificationWorker.kt:217–224`). Existing WorkerTest already covers explicit0,1,2 and rejects3 (`DiscoveryVerificationWorkerTest.kt:290–307`). Blame receipts above show these three assertions originated41e95f05 before b0d2f278 introduced rule1 SELF qualification. This is an explicit historical input correction, not outcome rewriting or a blanket downgrade. Existing IntegrationTest’s current missing-version SELF Professor DIRECT_CLEAR and WorkerTest’s v2 registration cases remain unchanged.

### 1. Lowercase HTTP SELF legacy qualification scenario (current lines774–798)

- Change only `f.seed()` to `f.seed(settingsJson = """{"ruleVersion":0}""")`, plus explicit historical version/access assertions described above.
- Preserve **all** existing outcome/qualification/source/projection assertions: APOLLO_UNCLEAR, UNKNOWN, SELF source, nonnull admission, PROJECTED, one admission and one candidate.
- Preserve the `rules.evaluate(snapshot, payload.evidence, payload.search)` equality and query-read immutability checks for run revision and attempts.
- Evidence: this test explicitly says SELF must not become qualification, and its standalone recomputation uses the version0 default. Rule1 cannot represent that stated historical premise. Do not change the separate current-rule direct-clear test.

### 2. Public aliases and broad-candidate retention legacy scenario (current lines892–920)

- Pass only this scenario’s seed `settingsJson = """{"ruleVersion":0}"""`; retain both multipleInstitutions=false/true branches and add explicit historical version/access assertions.
- Preserve every existing assertion: request counts3/2, exactly one public Known Alias request, narrow count1/0, no Secret Alias request, broad candidate person-1 retained after empty narrow results, APOLLO_UNCLEAR, requestCount equal to provider request total and persisted attempt count.
- Evidence: existing SELF Professor input otherwise short-circuits before any alias search at rule1; the test originated with pre-rule1 semantics. `searchQueries` and legacy identity-employment matching still support the old route. Nothing about alias deduplication, visibility, candidate union or accounting is waived.

### 3. SELF identity-negative legacy scenario (current lines834–861)

- Pass only this scenario’s seed `settingsJson = """{"ruleVersion":0}"""`; retain all five cases ambiguous/orcid/name/historical/unknown, with explicit historical version/access assertions.
- Preserve all zero-admission/empty-candidate assertions and exact reason requirements: IDENTITY_AMBIGUOUS, ORCID_CONFLICT, NAME_CONFLICT, CURRENT_INSTITUTION_EVIDENCE_MISSING for historical/unknown.
- Evidence: current rule1 can directly qualify the SELF Professor before Apollo ambiguity, whereas these fixtures were written to exercise the historical identity route. Making that premise explicit preserves the intended negative coverage without prohibiting current direct qualification.

### 4. Provider/page/item-budget scenario (current lines617–653)

Keep the current 403/invalid/pages/transient branches and their existing inputs/assertions unchanged. Split the existing `budget` branch’s expectations from the common no-admission/error group, rename it to `apollo-budget-exempt`, and add one explicit `orcid-budget` branch. This changes the budget contract, not a blanket pass condition:

- `apollo-budget-exempt`: keep maxRequestsPerItem=3, original Researcher employment and original valid Apollo search response. Assert exactly three registered ORCID attempts, at least one APOLLO search attempt after those, total requestCount>3, and requestCount equals persisted attempts and observed provider requests. Assert no ITEM_REQUEST_LIMIT reason, completed search timestamp, state COMPLETED, one admission, one projected candidate, and final outcome equals a fresh `f.rules.evaluate(snapshot, payload.evidence, payload.search, institutionNormalization=payload.institutionNormalization, ruleVersion=payload.ruleVersion)` result. Assert that result/outcome is APOLLO_UNCLEAR with UNKNOWN qualification for this unchanged matchingVersion1/no-Apollo-title fixture. These exact expected values are source-derived, not separately exercised runtime claims.
- `orcid-budget`: set maxRequestsPerItem=2 with the same otherwise unchanged fixture. Assert only two ORCID HTTP requests/attempts, no Apollo HTTP/attempts, total requestCount2 equal to persisted attempts, COMPLETED/PENDING, route NONE, null matched person/searchCompletedAt/admission revision, zero admission/empty candidates, and both EVIDENCE_INCOMPLETE and ITEM_REQUEST_LIMIT. This is the non-Apollo cap the current repository actually enforces.
- Keep 403 auth-fault suppression of a second item, invalid-response ERROR, page-cap PENDING+SEARCH_INCOMPLETE+SEARCH_PAGE_LIMIT/no admission, and transient exhaustion three requests/three transient attempts/ERROR. Existing independent 429/credits/repository regression suites remain mandatory and unchanged; this amendment does not treat passing them historically as current evidence.
- Evidence: `DiscoveryVerificationRepository.kt:502–505` explicitly exempts APOLLO from the item cap but caps non-Apollo; `DiscoveryVerificationWorker.kt:442–451` converts a LOCAL_EVIDENCE cap to PENDING plus EVIDENCE_INCOMPLETE/ITEM_REQUEST_LIMIT. MatchingVersion1 defaults at Worker:213; Rules:179–249 handles complete identity/institution matches without changing qualification. b0d2f278 deliberately introduced the exemption after the old budget assertion.

### 5. A3 capped broad search continues saved narrow query (current lines995–1050)

- Change only the `incomplete-search` `InstitutionNegative` fixture’s expected narrow count from0 to1 (line1016); retain maxPages=1, total_entries101 and its real matching candidate.
- Preserve every shared negative assertion: zero admissions, no candidates, null admission revision, SEARCH_INCOMPLETE, and authoritative queryInstitution/normalization checks for all cases.
- Add this case’s explicit state COMPLETED/outcome PENDING/route NONE, SEARCH_PAGE_LIMIT, `payload.search.complete == false`, and all Apollo HTTP requests at page1 (no page2). Assert exactly one broad and one narrow request and persisted attempt/provider request-count equality.
- Evidence: Worker:311–325 marks truncated queries incomplete and advances to the next saved query; Worker:285 loops remaining queries; Worker:343–348 finalizes PENDING when global completeness is false. Current fixture supplies a nonempty stable queryInstitution and thus a narrow query. Continuing it is not global completion or admission. No production early-stop change is proposed.

### 6. Cached alias cursor without historical completion keys (current lines863–890)

- **Retain the original cached payload exactly**, including ruleVersion0, queryIndex1/page2 and absent finishedQueryKeys, for both `legacy=false/true` branches. Do not invent a `"0"` completion key from cursor position.
- Replace only the obsolete APOLLO_UNCLEAR outcome expectation at line887 with PENDING. Add state COMPLETED, route NONE, null matched person/admission revision, zero admission/empty projected candidates, SEARCH_INCOMPLETE and SEARCH_PAGE_LIMIT reasons, `saved.search.complete == false`, and prior index `"0"` absent from saved `finishedQueryKeys`.
- Preserve the stronger existing recovery assertions unchanged: exactly one HTTP request, page2, Frozen Alias (never new alias), no ORCID HTTP, identical frozen evidence and original person-1 candidate retained. Assert one newly registered Apollo attempt and request accounting matches the actual request.
- Evidence: current payload has a cursor but no durable completion record for query0. Worker:313–316 requires every query index in finishedQueryKeys; :343–348 conservatively completes PENDING. The fixture’s candidate union and cursor do not prove the omitted broad query was fully exhausted. Keeping the original payload and asserting safe incompleteness is stricter than fabricating completion.

### 7. A4 legacy inflight cursor without completion keys (current lines1094–1115)

- **Retain the entire original payload**, including absent institutionNormalization/queries/finishedQueryKeys, broadComplete=true, queryIndex1/page2. Do not add a completion key: broadComplete alone does not supply the per-query completion protocol the current Worker requires.
- Keep existing exactly-one-request/page2/Example University narrow-query assertions, null institutionNormalization and PENDING outcome.
- Replace only the APOLLO_NO_MATCH requirement at line1110 with both SEARCH_INCOMPLETE and SEARCH_PAGE_LIMIT; additionally assert APOLLO_NO_MATCH is absent, `saved.search.complete == false`, state COMPLETED, route NONE, null admission/person, zero admissions/empty candidates and one registered Apollo attempt with matching accounting.
- Evidence: same Worker completeness and PENDING branch as scenario6. Empty last-page response does not establish that every earlier query was complete; preserving the old payload must not silently promote it to a fully searched no-match. Existing `f.report` retains original request/normalization/reason/attempt evidence.

### Approval boundary and execution after approval

This is a single concrete test-contract reconciliation: three explicitly versioned historical inputs, one split budget contract with new non-Apollo safety coverage, one corrected continuation count with preserved global safety, and two retained historical cursor payloads asserting conservative PENDING. No production restoration or additional file authority is requested. No unresolved implementation-choice decision remains in this proposal; human approval is required specifically because scenario4/5/6/7 alter old behavioral expectations that the current invocation forbids repinning.

If approved, implement only this amendment, keep all existing assertions except the precisely enumerated replacements, rerun the exact full required JDK11 selector command with both opt-ins, actually run bound Node/syntax goals, and run the fresh standalone SQL/Worker/queue smoke from the new Surefire classpath. Any additional newly revealed discrepancy must be diagnosed within this bounded contract, not auto-repinned. Only all-correct final checks permit the requested local product commit.

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

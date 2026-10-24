# Child06a execution log

## Execution Result: PLAN_CONFLICT

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md`

Plan SHA-256: `7d4337bfeced558b78dbe78e91528ff95157d776aba560f44e221f6ffcc8deff`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md@7d4337bfeced558b78dbe78e91528ff95157d776aba560f44e221f6ffcc8deff`

Execution epoch: NEW; child06a epoch1, fix_round0. Approval basis: current delegated human-approved child06a brief, exact child seed `c18dbc060dcf4c8440f853ede4d1d3aaf737994f`, master `541411d540019428c72b31775c325087b33da303`. Read the complete child plan, master and brief before implementation; used `execute-p`, not a verification/review/repair skill.

Executor: ApolloPersistenceImplementer.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`

Target branch: `fast/discovery-paper-master`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`

Pre-execution code SHA / child_base_sha: `1b8c4981f0116e17648f064482e505d436c02edd`

Initial HEAD (including prior evidence): `3700fb3eadab04b6fa0469abece5690d9ae2051d`

Post-execution code SHA / current HEAD:

ae467257b520133ad2db999478d02bd9b02b6c85

Product commit subject: `feat(fast-p): implement 06a`

Evidence HEAD: N/A — report deliberately excluded from the product commit and left for the controller.

Implementation boundary: product-only commit `3700fb3eadab04b6fa0469abece5690d9ae2051d..ae467257b520133ad2db999478d02bd9b02b6c85`. Five authorized paths, 703 insertions, no deletions, from actual commit output. Initial dirty ledger and untracked child06a evidence were preserved; no product changes remain unstaged.

**Outcome distinction:** all child06a implementation tasks, the fresh required JVM tests and separate real SQL/TCP HTTP smoke completed. The full mandatory Maven command nevertheless exits 1 because its automatically bound Node suite has inherited resource-contract failures outside this child's authorization. This is not READY_FOR_VERIFICATION and not a claim of independent PASS. No JS repair or gate bypass was performed.

### Task status and implementation evidence

| Requirement | Status | Files / observed evidence |
|---|---|---|
| T-1 / I-1 / I-2 | IMPLEMENTED | Exact new V160 DDL; singleton id=1; independent booleans; positive saved revision; DATETIME(3). Repository exposes find/save, validates raw JDBC integers before converting booleans, detects invalid singleton rows, and uses a TransactionTemplate with SELECT FOR UPDATE plus revision-guarded UPDATE. No-op preserves revision/time; first insert starts at 1; duplicate/deadlock races map to conflict. |
| T-2 / I-1 / I-2 | IMPLEMENTED | Missing row resolves to revision0 / DEPLOYMENT_DEFAULT / null time, search from enabled/key/apolloDefault, detail=false; no default row is persisted. Saved false/true remains false/true. Effective details require service, key, search and detail; worker state is independent. Unreadable/invalid settings become sanitized unavailable errors, never enabled defaults. |
| T-2 / I-3 | IMPLEMENTED | Independent GET/PUT controller at `/api/expert-discovery/verification/apollo-settings`; existing AuthWebConfig/AuthInterceptor reused without exemptions. Strict exact fields and integral in-range nonnegative revision; boolean types cannot coerce. HTTP 401/400/409/503 observed. No key fields, fragments, Properties serialization or provider/worker dependency. |
| T-2 / I-4 | IMPLEMENTED | FrozenApolloAccess schema1, exact settingRevision/searchEnabled/detailEnabled/source protocol, one strict parser and one constructor from resolved settings. New source SAVED/DEPLOYMENT_DEFAULT only; existing snapshots may parse LEGACY. Invalid versions, omitted/unknown fields, types, overflow, negative revisions and source/revision contradictions are rejected. |
| T-3 | IMPLEMENTED | Fresh real MySQL 8.0.36 tests cover four combinations, service recreation, defaults/availability, unchanged time/revision, first-insert and existing-revision races, SQL/application constraints, bad persisted values, SQL failure, strict authenticated TCP HTTP and frozen protocol invalid/roundtrip cases. |
| A-1 / A-2 execution evidence | IMPLEMENTED, not human sign-off | Separate executable uses actual migrated disposable MySQL and embedded loopback Tomcat/controller/auth interceptor. Default GET → first PUT → new service bean → GET → false/true PUT; stale409, unknown400, anonymous401, deterministic first-write race; stop/start actual MySQL gives GET/PUT503 then same saved settings. All 93 non-setting tables retain row counts/content checksums, including seeded run settings_json. |
| Mandatory final command | CONFLICT | JVM 12 passed / 0 failures / 0 errors / 0 skipped; bound Node 1816 tests / 1787 passed / 29 failed / 0 skipped, inherited across 28 unauthorized test files. |

### Changed product/test files

1. `src/main/resources/db/migration/V160__create_discovery_apollo_setting.sql` — exact approved new table.
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryApolloSettingRepository.kt` — sole settings SQL writer, atomic CAS and strict stored-row validation.
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsService.kt` — safe defaults/effective flags, sanitized storage failures, settings DTO and frozen protocol.
4. `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryApolloSettingsController.kt` — strict authenticated settings HTTP surface and fixed error codes.
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt` — real SQL/TCP HTTP tests, fixtures and standalone runtime smoke.

No migration already present at execution start was edited. Pre-creation glob `src/main/resources/db/migration/V1{5,6}*` showed V159/V158/... and no V160; V160 was available. Actual Flyway runs validated/applied 159 migrations through version160 (there is an existing version gap; no claim of 160 migration files). No frontend, registration, worker, query, run/item/attempt, expert, mail, provider or configuration-file cutover was made.

### Commands and exact results

All repository shell commands ran with cwd equal to the target worktree above. Required test command, unchanged selector (actual existing classname confirmed in `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt:24`):

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryApolloSettingsIT,DiscoveryVerificationControllerTest -DmigrationIt=true
```

| Invocation | Exit / result | Evidence |
|---|---|---|
| First final-check attempt, finished 03:02:26 +08 | 1, test compilation | JsonNode implements Iterable and `invalid += ObjectNode` selected list concatenation; corrected only authorized IT to explicit add. Tool output `artifact://361`. |
| After compilation correction, finished 03:05:34 +08 | 1, settings fixture startup; JVM 5 run / 1 error | Testcontainers jdbcUrl had no query string, so appending `&connectTimeout` became part of database name. Corrected query separator only in IT. `artifact://363`. |
| After JDBC URL correction, finished 03:08:54 +08 | 1; JVM 12 run / 1 error | MySQL does not support MariaDB's check_constraint_checks variable. Replaced fixture-only corruption setup with MySQL ALTER CHECK NOT ENFORCED and finally restores enforcement. `artifact://365`. |
| First fully passing JVM run, finished 03:12:22 +08 | 1 overall; JVM 12 passed; bound Node failed | `artifact://368`. No unauthorized repairs. |
| After runtime restart-port fixture correction, finished 03:18:05 +08 | 1 overall; JVM 12 passed; same bound Node failure family | `artifact://375`. Re-execution followed an authorized IT launcher change, not a run solely to reconfirm inherited JS. |
| **Final product/test state**, finished 03:22:00 +08 | **1 overall; JVM 12 passed; Node 1816 / pass1787 / fail29 / skipped0** | `artifact://378`; ControllerTest 4/4 (1.333s), SettingsIT 8/8 (26.477s); all JVM failures/errors/skips0. Maven bound node-test fails; downstream node-check execution was not reached, so no separate syntax-check success is claimed. |
| Separate runtime launcher, first execution | 1 after successful main flow/race/outage statuses | Docker reassigned ephemeral MySQL host port on restart; original DataSource could not reconnect. Fixed isolated launcher to inspect restarted mapping. |
| Separate runtime launcher, second execution | 1 after successful main flow/race/outage statuses | Docker exposed both IPv4 and IPv6 bindings; single() was invalid. Fixed fixture to select actual IPv4 binding. |
| **Separate runtime launcher, final execution** | **0** | Full migrated MySQL + TCP HTTP flow, race and real stopped/restored MySQL all passed; output below. Actual previousPort32861/currentPort32862 confirms restart mapping change. |
| `git diff --cached --check` before commit | 0 | No whitespace errors. Staged name list contained exactly the five paths above. |
| Worktree/plan identity helpers, explicit stage/commit, reachability and inherited-byte diagnostics | 0 | Target branch/root/git-dir matched; plan unchanged; product commit is HEAD and ancestor of target branch; exact commands below. |

Identity and product commit commands (helpers run before both git add and git commit):

```sh
python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md
python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-branch fast/discovery-paper-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master
git add -- src/main/resources/db/migration/V160__create_discovery_apollo_setting.sql src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryApolloSettingRepository.kt src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsService.kt src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryApolloSettingsController.kt src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt
git diff --cached --check
git diff --cached --name-only
git commit -m 'feat(fast-p): implement 06a'
git rev-parse HEAD
git merge-base --is-ancestor ae467257b520133ad2db999478d02bd9b02b6c85 refs/heads/fast/discovery-paper-master
git show --format=fuller --stat HEAD
git status --short
```

No product builds/tests/lints/formatters were run mid-implementation; the listed tests are execution final checks and subsequent corrections to errors in the authorized IT. No full verification skill or later-child review ran.

### Separate actual runtime SQL/controller smoke

Executed through Python Eval subprocess, not JUnit, with the **complete classpath from the fresh final Surefire XML**, not a hand-selected/incomplete historical dependency helper. Reproducible invocation (same XML parsing, full classpath selection and Java argv as executed):

```python
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess, hashlib
worktree = Path('/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master')
report = worktree / 'target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsIT.xml'
root = ET.parse(report).getroot()
props = {p.attrib['name']: p.attrib['value'] for p in root.findall('./properties/property')}
cp = props.get('surefire.test.class.path', props['java.class.path'])
assert str(worktree / 'target/test-classes') in cp and str(worktree / 'target/classes') in cp
assert 'mysql-connector-j' in cp and 'tomcat-embed-core' in cp
command = [
    '/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java',
    '-cp', cp,
    'com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsSmokeLauncher'
]
result = subprocess.run(command, cwd=worktree, text=True,
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
print(result.stdout)
print('APOLLO_RUNTIME_EXIT', result.returncode)
```

Actual classpath property: `surefire.test.class.path`. SHA-256 of the complete UTF-8 classpath string: `f3b59342667efda4decb557407c65553243f487e1361d23b82ba322cd836228e`. Actual final Surefire XML: tests8/errors0/failures0/skipped0/time26.477s. The launcher is a meaningful reusable test-runtime entry in the authorized IT; no temporary source/script was retained elsewhere.

Observed final runtime output:

```text
Successfully applied 159 migrations to schema `apollo_settings`, now at version v160
APOLLO_RUNTIME_FLOW defaultGET=200/revision0 firstPUT=200/revision1 recreatedGET=200/SAVED independentPUT=200/false/true/effectiveDetails=false stale=409 unknown=400 anonymousGET=401 anonymousPUT=401 nonSettingTables=93 nonSettingChanges=0 providerObserverRequests=0 snapshotRoundtrip=true
APOLLO_FIRST_WRITE_RACE emptyLockedReads=2 statuses=[200, 409] revision=1
APOLLO_RUNTIME_MYSQL_RESTART previousPort=32861 currentPort=32862
APOLLO_RUNTIME_OUTAGE stoppedMysqlGET=503 stoppedMysqlPUT=503 restoredSavedRow=true nonSettingChanges=0 providerObserverRequests=0 keyLeak=false
APOLLO_RUNTIME_EXIT 0
```

The first-write test hook synchronizes **after two actual empty SELECT FOR UPDATE results**, then releases both transactions to compete in real InnoDB INSERTs; neither SQL nor settings service/controller is mocked. Existing-revision update race is also real SQL with one success/one conflict. HTTP uses loopback embedded Tomcat, existing AuthWebConfig/AuthInterceptor and an isolated fixture-session servlet; only AuthService identity lookup is stubbed. Runtime re-creates the settings service/controller bean, not the entire production application. The runtime outage actually stops/starts the disposable MySQL container and reconnects to its fresh IPv4 host binding. Non-setting row counts and CHECKSUM TABLE values, including a seeded immutable run settings_json, match before/after operations. Local provider observer count is0; captured application logs and HTTP responses reject synthetic key/full-prefix/suffix leakage. No production Spring context, production DB, provider clients or provider requests were involved. This is execution evidence, not browser/human acceptance or proof about unrelated production processes.

### Mandatory bound-Node conflict: complete bounded inventory

Fresh final output: tests1816, suites334, pass1787, fail29, cancelled0, skipped0, todo0. Do not use the brief's speculative Node number as a baseline. Each failed assertion is the inherited expected16 versus actual17 resource contract, except the explicit ordered list which omits discovery-papers.js. The new resource was introduced by prior child05, not06a.

Provenance commands, exits0:

```sh
git diff --exit-code 1b8c4981f0116e17648f064482e505d436c02edd -- src/test/js src/main/resources/static/index.html
git log -1 --format='%H %s' 1b8c4981f0116e17648f064482e505d436c02edd -- src/main/resources/static/index.html
```

First command produced **no diff**: all JS tests and index.html remain byte-identical to child06a base1b8c498. Second output:

```text
f4b192dd671d1892a76cd98dd0a102015a69c49a feat(fast-p): implement 05
```

Receipt queries used the grep tool, not an assumed file list:

```json
{"pattern":"^test at|ℹ tests|ℹ suites|ℹ pass|ℹ fail|ℹ skipped|Tests run:|APOLLO_FIRST_WRITE_RACE|BUILD FAILURE","path":"artifact://378"}
{"pattern":"at TestContext\\.<anonymous>|at SuiteContext\\.<anonymous>","path":"artifact://368:3425-4100"}
```

Parsed actual `^test at` receipts: 29 failures, 28 distinct paths. Final artifact378 failure test locations exactly match artifact368; no test was rerun just to establish base behavior. Source read confirms mailboxChatBehavior's expected array at6793–6796 omits discovery-papers.js before app.js. This is inherited-byte provenance plus fresh current gate evidence, not a fabricated execution at an earlier commit.

**Smallest necessary test-only authorization additions**, if the controller requests an explicit separate remedy: the following 28 files only. Numeric cases need the approved current resource count17 and corresponding description; the ordered array needs discovery-papers.js in its real existing position. Retain single-key, asset ordering, no-duplicate and existing behavior assertions; no frontend product or06b file is necessary for these observed failures. No such changes are authorized or performed by this executor.

| Full test path | Failing test definition line | Actual assertion/stack line(s) |
|---|---:|---:|
| `src/test/js/batchDemandProgress.test.js` | 152 | 157 |
| `src/test/js/batchEmailVerification.test.js` | 943 | 947 |
| `src/test/js/batchSenderFilter.test.js` | 230 | 234 |
| `src/test/js/checkRepliesRelocation.test.js` | 69 | 71 |
| `src/test/js/contactTimingStyle.test.js` | 174 | 176 |
| `src/test/js/discoveryScheduleSetting.test.js` | 330 | 332 |
| `src/test/js/expertContactSharedNote.test.js` | 765 | 768 |
| `src/test/js/expertManualProfile.test.js` | 925 | 928 |
| `src/test/js/expertMeetingVideo.test.js` | 997 | 1000 |
| `src/test/js/mailOpenTracking.test.js` | 274 | 276 |
| `src/test/js/mailboxChatBehavior.test.js` | 6790 | 6793 (ordered array6794–6795) |
| `src/test/js/mailboxChatStyle.test.js` | 356 | 358 |
| `src/test/js/mailboxGroupPush.test.js` | 85 | 87 |
| `src/test/js/mailboxReplyIdentity.test.js` | 504 | 512 |
| `src/test/js/mailboxSuspensionStyle.test.js` | 117 | 119 |
| `src/test/js/mailboxTemplateReferenceStyle.test.js` | 153 | 157 |
| `src/test/js/manualReplySubjectPrefill.test.js` | 91 | 93 |
| `src/test/js/meetingCalendar.test.js` | 167 | 171 |
| `src/test/js/meetingConfirmationAssets.test.js` | 38 and46 | 40 and53 |
| `src/test/js/mobileCoreNavigation.test.js` | 130 | 137 |
| `src/test/js/overlayAndDialogContrast.test.js` | 62 | 64 |
| `src/test/js/ragKnowledgeBasePage.test.js` | 340 | 347 |
| `src/test/js/ragWorkbenchRender.test.js` | 393 | 397 |
| `src/test/js/recipientPreviewDiagnostics.test.js` | 280 | 284 |
| `src/test/js/senderBindingDisplay.test.js` | 393 | 395 |
| `src/test/js/taskActivityCenter.test.js` | 676 | 678 |
| `src/test/js/taskRecordsSemantics.test.js` | 275 | 336 |
| `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 180 | 182 |

### Downstream06b protocol handoff — not runtime wiring

- Read current settings via `DiscoveryApolloSettingsService.resolve()`; create a new frozen value with `freeze()` or `FrozenApolloAccess.fromSettings(resolved)`.
- Persist its node via `frozen.toJson(mapper)`; consume exactly the same node using `FrozenApolloAccess.parse(node)`. Do not define a second schema/parser or silently coerce missing/invalid values to false.
- Fields are exactly schemaVersion1, settingRevision(Long), searchEnabled(Boolean), detailEnabled(Boolean), source enum. New values use SAVED with positive revision or DEPLOYMENT_DEFAULT with revision0; LEGACY is accepted by parser for06b's explicit old-run interpretation only.
- Independent stored search/detail flags remain independent even when search is false; execution must apply the appropriate effective availability and frozen-run semantics in06b. Saved settings do not presently change worker/registration behavior.
- No eligibility rules or system daily-request-quota field belongs in this protocol. Nothing in06a enables paid provider calls or changes existing run snapshots.

### Deviations, freshness and remaining blocker

- Product/file/commit boundary deviations: none. Evidence remains outside the product commit, as requested.
- Implementation final-check corrections were confined to the authorized IT; no applied migration, production convention or acceptance rule was weakened.
- Final required command freshly ran after the last product/test edit: YES. Fresh real SQL/HTTP runtime smoke after that command: YES, exit0.
- Plan SHA unchanged on final identity recheck: YES.
- Target root/branch/git-dir and commit reachability rechecked: YES. Current HEAD is the bare product SHA above; no evidence commit was created.
- Product working tree/index clean: YES at final status; pre-existing dirty ledger/untracked child06a evidence deliberately remain.
- Historical evidence used only as baseline/provenance: YES; final assertions/counts are from actual current output.
- Full build, full Kotlin suite, browser acceptance, production startup, deployment/provider behavior: not exercised or claimed.
- Remaining blocker: mandatory Maven's inherited bound-Node failures need a controller/human decision because all28 required test-only consumers are outside the five authorized06a paths. No new06a behavior is identified as their cause. No plan amendment, unilateral repair, push, merge, deploy, rewrite, later-child work or independent verification was performed.
- Next action: controller evaluates inherited-gate classification and any explicit bounded test-only authority; do not silently advance the child as READY or bypass the bound gate.


## Epoch2 — Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md`

Plan SHA-256: `9802f4f4b4009a8dfbf57eb158efb83e32a32e5badc7f8f27f5fddc4b6aa3fc7`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md@9802f4f4b4009a8dfbf57eb158efb83e32a32e5badc7f8f27f5fddc4b6aa3fc7`

Execution epoch: NEW identity, approved epoch2 continuation, fix_round0. Approval basis: current assignment and amended child/master A9/A10 at `6112a58b4bc94b510a6b1cff88cfd9ec159c83d6`; exact28 JS resources synchronization, original five-file implementation retained. Executor: ApolloPersistenceImplementerE2, execute-p only.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`

Target branch: `fast/discovery-paper-master`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`

Original child base: `1b8c4981f0116e17648f064482e505d436c02edd`; retained code commit: `ae467257b520133ad2db999478d02bd9b02b6c85`. Pre-execution HEAD (including governance/evidence): `40fc7a4028fadb95276a390f5cd302355018a36d`.

Post-execution code SHA / HEAD:

16a138852bea292d40b47dd9cb2d7fc0c8732578

Subject: `feat(fast-p): implement 06a`. New continuation commit, no amend/rewrite. Evidence HEAD: N/A; this appended report is left for controller evidence handling, excluded from product commit. Implementation boundary: retained five files plus continuation `40fc7a4028fadb95276a390f5cd302355018a36d..16a138852bea292d40b47dd9cb2d7fc0c8732578` (28 JS files, 35 insertions/35 deletions). Pre-existing dirty ledger preserved unstaged.

### Current identity task status

| Requirement | Status | Evidence |
|---|---|---|
| T-1 / I-1–I-3 | IMPLEMENTED, retained unchanged | Read actual ae467257 diff: exact V160 singleton DDL, repository find/save transaction/SELECT FOR UPDATE/CAS, validation/no-op/first insertion conflict, UTC milliseconds. Fresh real MySQL IT below. |
| T-2 / I-1–I-4 | IMPLEMENTED, retained unchanged | Independent flags, fail-closed storage errors, defaults without fake row, separate effective availability and worker flag, strict authenticated controller and single frozen protocol. Fresh HTTP and protocol tests below. No runtime wiring added. |
| T-3 original SQL/HTTP tests | IMPLEMENTED, retained unchanged | SettingsIT8/8, existing controller4/4; four combinations/service recreation/no-op/defaults/bad rows/storage errors/strict payloads/frozen protocol and genuine SQL concurrency. |
| T-3 / A9/A10 resource synchronization | IMPLEMENTED | Exact28 authorized JS files; counts/descriptions16→17 and exact ordered module lists insert discovery-papers.js before app.js. All key equality/order/no-duplicate/unversioned runtime/business assertions retained. Bound full1816 Node passes without exclusion. |
| A-1/A-2 execution smoke | IMPLEMENTED, not human sign-off | Separate actual SQL/TCP HTTP launcher, default/save/recreate/independent flags, stale409/malformed400/anonymous401, deterministic first-write race, actual MySQL stop/start503/recovery, unchanged93 non-setting tables and provider observer0. |

### Fresh commands and results

All shell commands used the target worktree as cwd.

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryApolloSettingsIT,DiscoveryVerificationControllerTest -DmigrationIt=true
```

Selector confirmed from `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt:24`; no selector correction/exclusion.

| Invocation | Exit/result | Evidence |
|---|---|---|
| First final check, finished09:00:44+08 | 1; JVM12 passed, Node1816/pass1815/fail1/skipped0 | `artifact://393`. After updating count, mailboxGroupPush exact ordered list exposed same missing discovery-papers.js entry. Updated only that authorized file's list at actual position; current user assignment explicitly authorizes actual ordered module list synchronization. No product change/test weakening. |
| Final state command, finished09:04:04+08 | 0 BUILD SUCCESS | `artifact://395`: ControllerTest4/4/time1.321s; SettingsIT8/8/time26.522s; JVM12/failure0/error0/skipped0. Bound fullNode1816/suites334/pass1816/fail0/cancelled0/skipped0/todo0; node-check-app and node-check-task-modal-runtime also executed successfully. |
| Separate fresh SQL/HTTP launcher | 0 | Python Eval subprocess below; complete classpath freshly parsed from final Surefire XML, not historical helper. |
| git diff --cached --check | 0 | Staged exact28 JS paths only, output `artifact://398`; no reports/ledger/products staged. |
| identity helpers/stage/commit/reachability | 0 | `artifact://398`, `artifact://399`; root/branch/git-dir bound before add and commit; HEAD SHA above, ancestor of target branch, same plan SHA after commit. Product working tree/index clean; dirty ledger preserved. |

No tests/build/lint/formatter midflight; first gate followed completed initial edits, second followed the single authorized test correction. This is execution evidence, not independent verification. No full verify-p/review-p/repair-p or later-child work.

### Separate actual SQL/HTTP smoke invocation

Executed through Python Eval exactly as follows:

```python
from pathlib import Path
import xml.etree.ElementTree as ET
import subprocess, hashlib
worktree = Path('/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master')
report = worktree / 'target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsIT.xml'
root = ET.parse(report).getroot()
props = {p.attrib['name']: p.attrib['value'] for p in root.findall('./properties/property')}
cp = props['surefire.test.class.path']
assert str(worktree / 'target/test-classes') in cp and str(worktree / 'target/classes') in cp
assert 'mysql-connector-j' in cp and 'tomcat-embed-core' in cp
command = ['/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java', '-cp', cp,
           'com.weibo.talentintroduction.discovery.service.DiscoveryApolloSettingsSmokeLauncher']
result = subprocess.run(command, cwd=worktree, text=True, stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT, timeout=180)
print('FRESH_SUREFIRE', root.attrib)
print('CLASSPATH_SHA256', hashlib.sha256(cp.encode()).hexdigest())
print(result.stdout)
print('APOLLO_RUNTIME_EXIT', result.returncode)
```

Fresh XML tests8/errors0/failures0/skipped0/time26.522s. Full classpath SHA-256 `f3b59342667efda4decb557407c65553243f487e1361d23b82ba322cd836228e` (same dependency bytes as epoch1, freshly obtained from this run).

Observed separate runtime output09:04:10–09:04:35+08:

```text
Successfully applied 159 migrations to schema `apollo_settings`, now at version v160
APOLLO_RUNTIME_FLOW defaultGET=200/revision0 firstPUT=200/revision1 recreatedGET=200/SAVED independentPUT=200/false/true/effectiveDetails=false stale=409 unknown=400 anonymousGET=401 anonymousPUT=401 nonSettingTables=93 nonSettingChanges=0 providerObserverRequests=0 snapshotRoundtrip=true
APOLLO_FIRST_WRITE_RACE emptyLockedReads=2 statuses=[200, 409] revision=1
APOLLO_RUNTIME_MYSQL_RESTART previousPort=32868 currentPort=32869
APOLLO_RUNTIME_OUTAGE stoppedMysqlGET=503 stoppedMysqlPUT=503 restoredSavedRow=true nonSettingChanges=0 providerObserverRequests=0 keyLeak=false
APOLLO_RUNTIME_EXIT 0
```

Actual disposable MySQL8.0.36, migrated159 files throughV160, embedded loopback Tomcat/controller/existing login interceptor. Race synchronizes after two real empty locked reads then real competing inserts; outage actually stops/restarts MySQL and reconnects fresh host port. Recreated settings service/controller is not a full production restart. All93 non-setting row counts/checksums (including seeded immutable run snapshot) unchanged. Only fixture identity lookup is stubbed. No provider clients/production context/production DB called; observer0 and captured response/log key leak checks pass. No browser/human/production acceptance, full Kotlin suite or WAR build claimed.

### Changed files and scope

Continuation commit contains exactly the28 JS paths in the amended child's change table (same complete inventory as the epoch1 table above). Counts/descriptions only except actual ordered list insertion in mailboxChatBehavior and mailboxGroupPush; meetingConfirmationAssets also updates unique-name cardinality to17 while retaining uniqueness assertion. No frontend product, runtime wiring, configuration, applied migration, original five-file implementation, provider/business writer or later-child file changed.

Stage command used explicit paths for all28 authorized JS files, not a directory-wide add. Helpers before both stage and commit:

```sh
python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-branch fast/discovery-paper-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master
git diff --cached --check
git diff --cached --name-only
git commit -m 'feat(fast-p): implement 06a'
git rev-parse HEAD
git merge-base --is-ancestor HEAD refs/heads/fast/discovery-paper-master
python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-23/discovery-paper-06a-apollo-persistence.md
git status --short
```

Downstream protocol unchanged: resolve/freeze/fromSettings → toJson/parse schemaVersion1, settingRevision, searchEnabled, detailEnabled, source; saved positive revision, deployment0, LEGACY parse only for06b interpretation. Saved flags do not yet alter existing registration/worker behavior;06b owns that cutover.

### Freshness / deviations / next action

- Plan identity rechecked: YES, unchanged hash above. Worktree root/branch/git-dir and target branch HEAD/reachability rechecked: YES. Required commands run freshly after final implementation edits: YES. Historical evidence only baseline/provenance: YES.
- Original five-file implementation retained; exact authorized28 test consumers updated; no exclusions. mailboxGroupPush's newly exposed ordered-list consumer uses the same explicitly requested ordered-module synchronization, not a new behavioral assertion.
- No remaining blocker or known in-scope check failure. No amend/push/merge/deploy/rewrite, plan amendment or independent PASS claim.
- Next action: controller independent verification for current plan identity. This READY_FOR_VERIFICATION does not advance or approve a later child.

### Epoch2 controller terminal record
- Fresh distinct ApolloPersistenceVerifierE2 returned LIGHT_PASS / COMPLETE_CHILD, zero repair rounds, boundary1b8c4981f0116e17648f064482e505d436c02edd..16a138852bea292d40b47dd9cb2d7fc0c8732578. FreshJVM12/Node1816/syntax and separateactualSQLTCPHTTPsmoke passed. Only childgate, no whole-system success or production acceptance.

# Aggregate Machine Verification — OpenAlex Daily Budget Design

## Epoch 1 — 2026-09-23

- Master plan: `docs/plans/2026-09-22/openalex-daily-budget-design.md` (sha256 `120646415a1af9aad8c76fcfc7a072a551259df57844dd310bdd20c880e3b47e`)
- Governing master identity: worktree sha256 `120646415a1af9aad8c76fcfc7a072a551259df57844dd310bdd20c880e3b47e`; recorded commit `ee1dfcd5439de54475c12ff51c9c713e984a82ec`
- Master identity state: `CONSISTENT`; amendments: N/A
- Boundary: `e2247680592603b091af791ef3629d70739a015b..13b82fde5d74836977d12e97b7f794a98803429d`
- Evidence head: `a79fbd58bd6b930a3313f67390b07a8df94ba9c6`
- Reviewer: `/root/aggregate_reviewer` (fresh; no implementation/light-verification context)
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/openalex-daily-budget-design/repair.md` (sha256 `545b8402eb52fce9f0e1947504f2553f784821fd960f3fd828dc21293f9c190d`), `DRAFT_READY`

### Fresh command evidence

| Command | Exit | Result |
|---|---:|---|
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,OpenAlexBudgetRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test` | 0 | 120 tests; MySQL/Testcontainers passed; 6:03 |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=DiscoveryPipelineServiceTest,DiscoveryPaperQueueRepositoryIT,ExpertDiscoveryServiceTest,RestTemplateConfigTest -DmysqlIt=true -Dapi.version=1.40 test` | 0 | 212 tests; MySQL/Testcontainers passed; 2:52 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test` | 0 | 72 tests; 2:10 |
| `node --check src/main/resources/static/app.js && node --test src/test/js/*.test.js` | 0 | 1063 passed, 0 failed, 215 suites |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DskipTests clean package` | 0 | BUILD SUCCESS; 1:56 |
| `git diff --check e2247680592603b091af791ef3629d70739a015b 13b82fde5d74836977d12e97b7f794a98803429d` | 0 | clean |

Java was Zulu 11.0.15. Docker/MySQL used the recorded OrbStack socket and API 1.40.

### Master contract matrix

| ID | Contract | Result | Findings/evidence |
|---|---|---|---|
| M-01 | Identity and ancestry | PASS | Governing SHA/commit and boundary verified. |
| M-02 | R-1 authorized aggregate scope | PASS | Combined product/test diff is in the ordered child/master union. |
| M-03 | R-2 no unrelated spill | PASS | No unrelated product changes. |
| M-04 | I-1 classified accounting/cooldown | FAIL | V-4, V-5. |
| M-05 | I-2 shared reservation/restart truth | FAIL | V-5. |
| M-06 | I-3 durable queue/consume ordering | PASS | Runtime inspection and C2 MySQL IT passed. |
| M-07 | I-4 fair/bounded continuous work | FAIL | V-6. |
| M-08 | I-5 lifecycle and terminal truth | FAIL | V-4, V-6, V-7. |
| M-09 | I-6 expert-discovery/controller contract | PASS | Fresh service/controller suites passed. |
| M-10 | C3 T-3, I-7, S-1/S-2 UI recovery | FAIL | V-1, V-2, V-3. |
| M-11 | Required automated evidence | PASS | All mandated commands passed freshly. |
| M-12 | Human acceptance | PENDING | Deferred human gate. |

### Findings

| ID | Priority | Evidence |
|---|---:|---|
| V-1 | P1 | `src/main/resources/static/app.js:6657-6692` writes pipeline GET failure then returns before modal reveal at `:6770`; `index.html:1035` starts the modal hidden. Violates C3 T-3 visible-error recovery. |
| V-2 | P1 | `app.js:7102-7106` opens modal/watcher; continuous/409 failures at `:7170-7185` restore only modal config and return. Legacy failure at `:7187-7196` restores the page trigger, proving the missed recovery path. |
| V-3 | P1 | `app.js:902-940`, specifically `:914-935`, renders new inline styles; C3 prohibits new classes/inline styles at `docs/plans/2026-09-22/03-discovery-continuous-run.md:202`. |
| V-4 | P1 | `OpenAlexRequestPolicy.kt:857-872` sleeps in reserve; `application.yml:238` permits 60 seconds and `OpenAlexRequestPolicy.kt:1076` caps at three retries. C1 requires no long scheduler wait (`01-openalex-account-budget.md:116`). |
| V-5 | P1 | `OpenAlexRequestPolicy.kt:613-652` reports local effective balance/process-local deferral; trusted-ledger guard is only in reserve (`:889-903`). `app.js:859-867` displays untrusted values despite C3's pending-sync requirement (`03-discovery-continuous-run.md:166`). |
| V-6 | P1 | `DiscoveryPipelineService.kt:1143-1148` treats no streams as progressable; tick opens a window at `:465-493`; `:707-710` and `:1231-1240` can repeat empty executions. Contradicts C2 I-7. |
| V-7 | P1 | `DiscoveryPipelineService.kt:219-231` maps non-exhausted ends to `PARTIAL_SUCCESS`; `:722-736` can end drained after `WINDOW_END`, while `:1202-1228` keeps the contradictory outcome. Violates C2 I-7/I-8. |
| V-8 | P2 | `OpenAlexBudgetRepositoryIT.kt:446-462` always uses floor zero: MySQL coverage gap only. |
| V-9 | P2 | `DiscoveryPipelineService.kt:540-572` and `ExpertDiscoveryController.kt:473-495` expose raw wait names: compatible observation. |
| V-10 | P2 | `DiscoveryPaperQueueRepository.kt:331,1233-1248` is an unreachable lease-loss seam; production `rg` found no caller. |
| V-11 | P2 | C3 prescribes the selector at `03-discovery-continuous-run.md:203`; class-local MVC tests in `ExpertDiscoveryControllerMvcTest.kt:201,473` are not selected. Prescribed-command coverage weakness. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| C1 O-2 | I-1/I-2 MySQL budget protection | P2 V-8; not a runtime defect | `OpenAlexBudgetRepositoryIT.kt:446-462` |
| C1 O-3 | I-1/I-2 trusted budget presentation | P1 V-5 | `OpenAlexRequestPolicy.kt:613-652`; `app.js:859-867` |
| C1 O-4 | I-4 non-blocking rate limit handling | P1 V-4 | `OpenAlexRequestPolicy.kt:857-872` |
| C2 O-1 | I-4/I-5 no empty execution records | P1 V-6 | `DiscoveryPipelineService.kt:1143-1148,1231-1240` |
| C2 O-2 | documented waiting reasons | P2 V-9; compatibility observation | `DiscoveryPipelineService.kt:540-572` |
| C2 O-3 | queue capacity accounting | P2 V-10; unreachable seam | `DiscoveryPaperQueueRepository.kt:331,1233-1248` |
| C2 O-4 | I-5 result/status truth | P1 V-7 | `DiscoveryPipelineService.kt:219-231,722-736` |
| C3 O-1 | C3 T-3 visible error | P1 V-1 | `app.js:6657-6692,6770`; `index.html:1035` |
| C3 O-2 | C3 UI recovery | P1 V-2 | `app.js:7102-7106,7170-7196` |
| C3 O-3 | prescribed command coverage | P2 V-11; observation | C3 plan `:203`; MVC file `:201,473` |
| C3 direct S-1 check | S-1 no new inline styles | P1 V-3 | `app.js:902-940` |

Repair planning: `DRAFT_READY`. The repair contains the required Review-Fast-P one-approval execution handoff. No product code was modified by the reviewer.

## Epoch 2 — 2026-09-23

- Master plan: `docs/plans/2026-09-22/openalex-daily-budget-design.md` (sha256 `120646415a1af9aad8c76fcfc7a072a551259df57844dd310bdd20c880e3b47e`)
- Governing master identity: worktree sha256 `120646415a1af9aad8c76fcfc7a072a551259df57844dd310bdd20c880e3b47e`; recorded commit `ee1dfcd5439de54475c12ff51c9c713e984a82ec`
- Master identity state: `CONSISTENT`; amendments: N/A
- Boundary: `e2247680592603b091af791ef3629d70739a015b..f51512591f27ab1f21a81302f7f5f767d7f06695`
- Evidence head: `fa3f16cb0cca313fd819c82b72e6ed526d4985b5`
- Reviewer: `/root/aggregate_rereviewer` (fresh; no implementation/light-verification context)
- Result: `PASS`
- Convergence: `PROGRESSING`
- Repair artifact/result: `docs/plans/fix/openalex-daily-budget-design/repair.md` (sha256 `545b8402eb52fce9f0e1947504f2553f784821fd960f3fd828dc21293f9c190d`), executed and independently reverified through durable handoff `repair-execution.md`

### Fresh command evidence

| Command | Exit | Result |
|---|---:|---|
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,OpenAlexBudgetRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test` | 0 | 121 tests (42 + 67 + 12), zero failures/errors. |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=DiscoveryPipelineServiceTest,DiscoveryPaperQueueRepositoryIT,ExpertDiscoveryServiceTest,RestTemplateConfigTest -DmysqlIt=true -Dapi.version=1.40 test` | 0 | 215 tests (45 + 31 + 121 + 18), zero failures/errors. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test` | 0 | 72 tests (4 + 17 + 13 + 12 + 26), zero failures/errors. |
| `node --check src/main/resources/static/app.js && node --test src/test/js/*.test.js` | 0 | 1067 passed, 0 failed, 216 suites. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DskipTests clean package` | 0 | BUILD SUCCESS; WAR produced. |
| `git diff --check e2247680592603b091af791ef3629d70739a015b f51512591f27ab1f21a81302f7f5f767d7f06695` | 0 | clean. |

### Master contract matrix

| ID | Contract | Result | Findings/evidence |
|---|---|---|---|
| M-01 | Identity and ancestry | PASS | Master hashes/commit consistent; prior code head ancestor of candidate. |
| M-02 | R-1 authorized aggregate scope | PASS | Full combined product/test delta is the ordered child-plan union (28 paths). |
| M-03 | R-2 no unrelated spill | PASS | Repair delta is exactly six authorized paths; only pending controller-owned review evidence is dirty. |
| M-04 | I-1 classified accounting/cooldown | PASS | V-4/V-5 repaired in `OpenAlexRequestPolicy.kt:641-666,870-890`; budget tests pass. |
| M-05 | I-2 shared reservation/restart truth | PASS | V132 account/day/reservation model; untrusted ledger reports `BUDGET_SYNC`, not local usable balance. |
| M-06 | I-3 durable queue/consume ordering | PASS | V133 lease/CAS/state model; pipeline/repository MySQL IT passes. |
| M-07 | I-4 fair/bounded continuous work | PASS | `DiscoveryPipelineService.kt:471-505,1163-1192`; deferred/no-source pipelines create no empty task window. |
| M-08 | I-5 lifecycle and terminal truth | PASS | `DiscoveryPipelineService.kt:214-237,734-755`; deadline-drained window becomes `SUCCESS`. |
| M-09 | I-6 expert-discovery/controller contract | PASS | Controller launch/status/resume/cancel chains inspected; targeted service/controller tests pass. |
| M-10 | C3 T-3, I-7, S-1/S-2 UI recovery | PASS | `app.js:859-943,6692-6700,7176-7208`; visible load failure, trigger restoration, existing table class only. |
| M-11 | Required automated evidence | PASS | All mandated commands passed freshly. |
| M-12 | Human acceptance | PENDING | A-1 through A-5 remain human/test-environment gates. |

### Finding lineage

| ID | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | Pipeline-info failure calls visible `showStatus` at `app.js:6695-6700`. |
| V-2 | RESOLVED | Every rejected continuous launch restores the page trigger via `stopTaskWatcher` at `app.js:7176-7208`. |
| V-3 | RESOLVED | Source detail uses existing `.data-table`; no inline styles at `app.js:905-943`. |
| V-4 | RESOLVED | Minute-scale 429 cooldown returns Deferred; only bounded <=1s pacing remains at `OpenAlexRequestPolicy.kt:870-883`. |
| V-5 | RESOLVED | Untrusted budget reports `BUDGET_SYNC`; UI renders 待同步 at `OpenAlexRequestPolicy.kt:641-660`, `app.js:861-871`. |
| V-6 | RESOLVED | `nextProgressAt` distinguishes runnable/deferred/drained work at `DiscoveryPipelineService.kt:1163-1192`. |
| V-7 | RESOLVED | Final drained state drives terminal result at `DiscoveryPipelineService.kt:214-237`. |
| V-8 | PERSISTENT P2 | MySQL budget IT still always passes floor zero; coverage gap only. |
| V-9 | PERSISTENT P2 | Raw source errors can appear in `waitReasons`; UI safely displays unknown values. |
| V-10 | PERSISTENT P2 | `forceFailForLeaseLoss` remains an unreachable production seam. |
| V-11 | PERSISTENT P2 | Required MVC selector runs four base-class cases, not two extra top-level MVC classes. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| C1 O-2 | I-1/I-2 MySQL budget protection | P2 V-8; coverage gap only | MySQL floor parameter remains zero. |
| C1 O-3 | I-1/I-2 trusted budget presentation | RESOLVED V-5 | `BUDGET_SYNC` and pending UI fields. |
| C1 O-4 | I-4 non-blocking rate-limit handling | RESOLVED V-4 | Cooldowns immediately defer. |
| C2 O-1 | I-4/I-5 no empty execution records | RESOLVED V-6 | Deferred/no-source work opens no window. |
| C2 O-2 | documented waiting reasons | P2 V-9; compatible observation | Unknown values safely displayed. |
| C2 O-3 | queue capacity accounting | P2 V-10; unreachable seam | No production caller. |
| C2 O-4 | I-5 result/status truth | RESOLVED V-7 | Drained deadline window succeeds. |
| C3 O-1 | C3 T-3 visible error | RESOLVED V-1 | Visible page status fallback. |
| C3 O-2 | C3 UI recovery | RESOLVED V-2 | Trigger restored on rejection. |
| C3 O-3 | prescribed command coverage | P2 V-11; observation | Selector remains master-prescribed. |
| C3 S-1 direct check | S-1 no new inline styles | RESOLVED V-3 | Existing class-only rendering. |

Runtime chains inspected: OpenAlex request classification → reserve/reconcile → persistent ledger; controller/scheduler → pipeline tick/window → queue lease/extraction/consume/task record; pipeline status → modal launch/poll/recovery.

No P1 findings remain. P2 findings V-8 through V-11 do not violate a mandatory acceptance item. Repair planning: N/A. No product code was modified by the reviewer.

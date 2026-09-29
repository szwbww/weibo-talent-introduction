# Review-Fast-P Machine Verification — docs/plans/2026-09-29/discovery-repair-00-master.md

## Epoch 1 — 2026-09-29

- Master plan: docs/plans/2026-09-29/discovery-repair-00-master.md (worktree copy sha256 `2f9025d51bbeec8b5e10684175d1093450f1ab606696b37cd29c7796edf55f9f`)
- Governing master identity: worktree sha256 `2f9025d51bbeec8b5e10684175d1093450f1ab606696b37cd29c7796edf55f9f` / recorded `commit:70f550658d078b228fe619b735d81f0e740c4db3`
- Master identity state: `CONSISTENT`; governing amendment A1 on child plan 01 (master rule M-1 / 01-T-2, approval `HUMAN:批准该修正（推荐）`, recorded 2026-09-29T14:00:40+08:00), retroactively authorized file `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` (matrix row CM-10)
- Boundary: `1cd59e31164d11e962203e31c9f61310f2bc5912`..`075dc3e0c014a0cae6a908f871e65784fb944881`
- Reviewer: AggregateReviewerE1
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A (`verify-p` returned PASS, so `repair-p` was never invoked; `docs/plans/fix/2026-09-29-discovery-repair-00-master/repair.md` does not exist)
- Controller note: the CM-1 evidence cell below was returned truncated (containing `…`) in the reviewer's own output and is preserved verbatim; no other cell was altered.

# Review-P (aggregate/master) — Machine Verification

## Verification Result: PASS

Plan: docs/plans/2026-09-29/discovery-repair-00-master.md (worktree copy sha256 `2f9025d51bbeec8b5e10684175d1093450f1ab606696b37cd29c7796edf55f9f` = governing identity; recorded commit `70f550658d078b228fe619b735d81f0e740c4db3`)
Child plans: 01 `commit:cc57128f3c2dc1f4bbadd86d2809a1ce787d7e7c` (A1-amended), 02/03/04 `commit:70f550658d078b228fe619b735d81f0e740c4db3`
Governing amendment: A1 on child 01 — master rule M-1 / 01-T-2 (HUMAN:批准该修正（推荐）, recorded 2026-09-29T14:00:40+08:00)
Implementation boundary: `1cd59e31164d11e962203e31c9f61310f2bc5912` .. `075dc3e0c014a0cae6a908f871e65784fb944881` (product-only); evidence head `298ad8991041e51f87765f85cac908f356160cba` — `git diff --name-only 075dc3e HEAD` = docs-only (empty after filtering `^docs/`), so no post-head code was reviewed as implementation.
Convergence: INITIAL (review-fast-p ledger epoch 1, `Machine result: PENDING`, no prior aggregate findings)
Manual acceptance: PENDING (master 人工验收 A-1 not performed by the machine)

### Commands (all run freshly, cwd = retained worktree root, JDK 11)
| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `mvn -Dtest=SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityTest test` | PASS | exit 0, BUILD SUCCESS, 3:39 min. Fresh surefire per class: SourceAuthorEmailResolverTest 24/0/0/0, ExpertDiscoveryServiceTest 176/0/0/0, DiscoveryPipelineServiceTest 47/0/0/0, DiscoveryIdentityTest 7/0/0/0 → **254 tests / 0 failures / 0 errors / 0 skipped**. (The ledger's 251 is child-01's boundary; the +3 is child 02's added ExpertDiscoveryServiceTest cases.) |
| 2 | `mvn -Dtest=OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest test` | PASS | exit 0, BUILD SUCCESS, 3:26 min. 85/0/0/0 + 176/0/0/0 + 6/0/0/0 → **267 tests / 0 failures / 0 errors / 0 skipped** |
| 3 | `JAVA_TOOL_OPTIONS=-Dapi.version=1.40 mvn -DmigrationIt=true -Dtest=ExpertDiscoverySchedulerTest,DiscoveryScheduleSettingServiceTest,DiscoveryScheduleControllerTest,DiscoveryScheduleSettingRepositoryIT test` | PASS | exit 0, BUILD SUCCESS, 3:13 min. ExpertDiscoverySchedulerTest 32/0/0/0, DiscoveryScheduleSettingServiceTest 14/0/0/0, DiscoveryScheduleControllerTest 8/0/0/0, DiscoveryScheduleSettingRepositoryIT 7/0/0/0 (35.7 s elapsed) → **61 tests / 0 failures / 0 errors / 0 skipped**; **Skipped: 0 on the IT class ⇒ the IT really executed** (its `@BeforeAll` guards on `DockerClientFactory...isDockerAvailable` and starts `mysql:8.0.36`; Docker server 29.4.0 present; `JAVA_TOOL_OPTIONS` is the environment-only daemon-API workaround the brief/verify-log documented, no repo file touched) |
| 4 | `node --test <4 targeted JS files>` ; `node --test src/test/js/*.test.js` ; `node --check src/main/resources/static/app.js` | PASS | exit 0 each: 104 tests / 104 pass / 0 fail; 1227 tests / 241 suites / 1227 pass / 0 fail / 0 skipped; syntax check clean |
| 5 | `mvn clean package` (master acceptance build) | PASS | exit 0, BUILD SUCCESS, 5:35 min; surefire **Tests run: 4320, Failures: 0, Errors: 0, Skipped: 13**; exec-bound Node suite `tests 1227 / pass 1227 / fail 0 / skipped 0`; WAR `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` (48,735,210 B). The 13 skips are pre-existing `migrationIt`/`mysqlIt`/network-gated classes (FlywayMigrationIntegrationTest, *MysqlTest, RAG, EuropePmcDataSourceTest …); none is a plan class, and the plan's own IT ran under command 3. |

### Contract Matrix (master level)
| ID | Requirement | Mandatory | Verdict | Evidence |
|---|---|---:|---|---|
| CM-1 | M-1 / 需求1 / 验收M-1: shared marker or shared paragraph must not be bound wholesale to the first author; ambiguous ⇒ keep the mailbox clue with no author ID/affiliation/identity credential; real-source replay produces none of the seven confirmed wrong relations; the explicit one-person-two-mailbox positive case survives | Y | PASS | `PdfAuthorContactLayout.kt:19-20,26,93,177-193,230-245` (marker set `*∗†‡§` + `∗`→`*`, `markerOwners` counts unmatched signatures via `null`, `uniqueOwner` requires `singleOrNull`, `markerSegments` refuses grouped markers, `(is|are) with` paragraph boundary with multi-initial support); `SourceAuthorEmailResolver.kt:161-168` (unchanged — null owner keeps the email as a clue with all identity fields null) is exercised by `ExpertDiscoveryServiceTest.own…verbatim from canonical output |
| CM-2 | M-2 / 需求2 / 验收M-2: one ORCID → >1 author ID ⇒ AMBIGUOUS_IDENTITY, no academic write/re-qualification/title request, identical for single/batch/worker; truncated responses never prove uniqueness | Y | PASS | `OpenAlexDataSource.kt:580-603` single classification seam (`results` array check, `meta.count` integral ≥0 == size else `ORCID_RESPONSE_INCOMPLETE`, canonical-ID dedupe 0/1/>1, missing-ID and same-ID-conflict → ApiError), reused by single (:453-459) and batch (:525-544) with `ORCID_PAGE_SIZE = 200`; `ExpertDiscoveryService.kt:3228-3236` sets `AmbiguousIdentity` for the **whole** identity group then :2560-2564 (manual `failed++ / AUTHOR_IDENTITY_AMBIGUOUS`) and :2964-2966 (`BatchOutcomeBucket.UNMATCHED`); `ExpertAcademicEnrichmentJobService.kt:144-152,197,228` (`UNMATCHED` + `AUTHOR_IDENTITY_AMBIGUOUS` + `{"outcome":"AMBIGUOUS_IDENTITY"}`, attempts unchanged); exhaustive `when` (no `else`) at all five consumption sites; fresh command 2 green |
| CM-3 | M-3 / 验收M-3: persistence-success + apply-success is the only success signal; save starts no discovery and never interrupts the running one; hourly plan and legacy cron never both start sync discovery; anchor+N hours (3/6/9, across midnight), running slot skipped, no catch-up | Y | PASS | `ExpertDiscoveryScheduler.kt` (no `@Scheduled` on the class — `grep` shows only the `@EventListener(ApplicationReadyEvent)`; `configureTasks` registers the tick only when `pipelineEnabled`; one `generation`-guarded future via `scheduler.schedule({onScheduledTrigger(generation)}, trigger)`, `cancel(false)`), `DiscoveryHourIntervalTrigger.nextExecutionTime` = `T + (floor((base−T)/N)+1)×N` with `base = max(now, lastCompletion)`; `DiscoveryScheduleSettingService.saveLocked` writes inside `TransactionTemplate` and only then calls `scheduler.reload()`, reporting `applied=true` only when `snapshot.registered && snapshot.source==OVERRIDE && snapshot.intervalHours==row.intervalHours && snapshot.anchorAt==row.updatedAt`; fresh command 3 green (incl. 13/16/19, midnight-crossing 5 h, restart, busy-slot skip) |
| CM-4 | 需求3 UI: dialog offers “每隔 N 小时执行一次” input + save button, integers 1–168 only; save adjusts future triggers with no restart; sync mode settable; continuous mode keeps run/pause rules and shows no input that cannot control the real frequency | Y | PASS | `index.html:1121-1135` verbatim S-1 DOM (normalized match to the plan block; panel `hidden`, controls `disabled`, `aria-describedby`, `role=status aria-live=polite`, no inline style/onclick), inserted as a `.modal-body` sibling before `#taskModalConfigSection`; `styles.css` +41/-0 → the plan's CSS fenced block is byte-identical and no existing rule changed; `app.js:2093-2247` (only `PUT /api/expert-discovery/schedule` with body `{intervalHours}`, `/^\d+$/` + 1–168 client validation, generation-guarded responses, controls hidden when `view.editable===false`); 22 behavior-level JS tests, fresh command 4 green |
| CM-5 | 保持 (preserve): one-person-multi-mailbox, email validation/eligibility, per-email dedup, manual run, run mutex, quotas, query checkpoint, existing cancel semantics | Y | PASS | Manual `/api/expert-discovery/run` still claims the same `TaskProgressStore` slot and returns 409 when busy (`ExpertDiscoveryController.kt:119-127`); scheduler callback uses the same slot (`startScheduledDiscovery`); scheduled criteria unchanged (`excludeCountries=[CN]`, `openAccessOnly`, `RND_TARGET`, `includeRawScan`); full build 4320 tests green incl. the pre-existing positive/eligibility/dedup cases |
| CM-6 | 范围外 (non-goals): no fuzzy person matching, no LLM judgement, no expert auto-merge, no new send gate, no generic scheduling platform, pipeline not enabled, no product write of network/history data | Y | PASS | Product diff contains no such code; `application.yml` is untouched (not in the boundary diff) so `pipelineEnabled=false`/`enabled`/`cron` defaults are as deployed; no direct writes to ES/experts beyond the existing guarded seams |
| CM-7 | Scope: only the child plans' authorized files (01 = 8 files incl. the A1-authorized pin file, 02 = 7, 03 = 9, 04 = 7); new migration = V144 or the next free version; never edit an applied migration | Y | PASS | `git diff --name-status 1cd59e3 075dc3e -- src/` = exactly 30 paths = the union of the four child lists (8+6+9+7), 13 A / 17 M, nothing else; each `feat(fast-p): implement NN` commit touches exactly its own authorized files and no `docs/` path (`git show --name-only`); `git diff --name-status … -- db/migration` = `A V144…` only; `src/main/resources/db/migration` tail = …V143, V144 |
| CM-8 | Cross-child contract 03→04: `GET/PUT /api/expert-discovery/schedule` fields (`mode`,`editable`,`source`,`intervalHours`,`anchorAt`,`nextTriggerAt`,`applied`,`reason`, plus the sanctioned `saved`), 400/409/503, GET side-effect free; frontend↔backend and cache-key triad | Y | PASS | `DiscoveryScheduleController.kt` (strict `JsonNode` integral check, `INVALID_INTERVAL_HOURS` 400 without writes, 409 NOT_EDITABLE, 503 UNAVAILABLE) ↔ `app.js` (`view.editable/applied/saved/source/intervalHours/nextTriggerAt/message`, `e.data`); `index.html` has 11 `?v=` nodes, all one value `20260929-discovery-schedule`, and no repo-wide non-doc reference to `20260929-mailbox-replied` except the new test's intentional “previous key” assertion |
| CM-9 | Cross-child chain 01→02: both edit the shared discovery consumer; the version gate for queued extraction must survive the second child | Y | PASS | `ExpertDiscoveryService.kt:2046-2047` still returns `IDENTITY_EXTRACTION_VERSION_UNSUPPORTED` on mismatch; `DiscoveryIdentity.EXTRACTION_VERSION 20261002→20261003` with `VERSION=20260925` unchanged; `DiscoveryPipelineServiceTest` version regression green in fresh command 1; `SourceAuthorEmailResolver.kt` byte-unchanged in the boundary |
| CM-10 | A1 (acid test): the retroactively authorized file `src/test/kotlin/…/expert/domain/DiscoveryIdentityTest.kt`, judged against master rule M-1 / 01-T-2 | Y | PASS | A1 exists in the amended child plan (`变更文件清单` row 8, commit `cc57128`) with `HUMAN:批准该修正（推荐）` in the fast-p ledger; the change is exactly one line (`assertEquals(20261003, DiscoveryIdentity.EXTRACTION_VERSION)`, `git diff --numstat` = 1/1) in its own commit `9330866`; the paired `VERSION==20260925` assertion is untouched and green; M-1 is unaffected (test-only pin sync, no product behavior, no weakened assertion) |
| CM-11 | Master acceptance: one full JDK 11 `mvn clean package` after the child targeted tests | Y | PASS | fresh command 5: exit 0, BUILD SUCCESS, 4320/0/0/13, Node 1227/1227, WAR produced |
| CM-12 | 人工验收 A-1 (joint manual acceptance: offline 01/02 cases, save 3 h via the 04 dialog, fixed-clock 03 start records, manual run) | PENDING (manual) | N/A | Not machine-performable by design; kept PENDING for the human gate (does not block machine verification) |

### Finding Lineage
| Finding | State | Evidence |
|---|---|---|
| V-1 (P2) | NEW | Aggregate view of child-01 RECORD_ONLY O-1 |
| V-2 (P2) | NEW | Aggregate view of child-03 RECORD_ONLY O-1 |
| V-3 (P2) | NEW | Aggregate view of child-04 RECORD_ONLY O-1 |
| V-O1 / V-O2 | NEW (observations) | Controller incident record / plan-mandated retained seam |

### Findings
#### P1
None. No confirmed mandatory violation of M-1, M-2, M-3, the preserve list, scope, the migration rule, or the master acceptance build was found, and no data-loss/privacy/authorization/concurrency/state/persistence defect was exposed in the reviewed boundary.

#### P2
- **V-1** (child 01, T-3 sub-item only): the plan asked the *added* synthetic set to also carry 合法一人两邮箱 and 纯文本/HTML 原有归属; the implementation deliberately relies on the pre-existing green `SourceAuthorEmailResolverTest` cases (`one unique PDF contact marker retains two explicit mailboxes` :355, `published source blocks preserve two owned mailboxes…` :390, `original Springer named mailto anchors…` :270 — counted in fresh command 1) and the freshly generated `target/discovery-plan-acceptance/01.json` lists only the six REAL_ORIGINAL cases. Master acceptance M-1 (“显式一人两邮箱正例继续保留”) is machine-proven green, so this is test-artifact depth/report completeness, not a behavioral violation. Smallest implicated scope: the child-01 acceptance report generator, not product code.
- **V-2** (child 03, RECORD_ONLY O-1): the GET/PUT response is a superset of the plan's fixed field list — it adds `message` (and the brief-sanctioned `saved`). Purely additive; no field renamed/removed; `reason` codes stay machine-readable; child 04's contract declares the same shape, so no consumer breaks. Non-blocking scope/contract-detail deviation against the child plan, not against master M-3.
- **V-3** (child 04, RECORD_ONLY O-1): for GET-driven CONTINUOUS/DISABLED the not-settable hint renders the server `message` (“当前为连续发现模式，小时周期设置不适用” / “系统定时发现已停用（enabled=false），小时周期设置不适用”) instead of the plan's illustrative literal sentences. Invariant I-3 / requirement 3 (explanation shown, hour controls hidden, no input that cannot control the real frequency) holds, and 03's contract explicitly assigns that display text to 04; exact wording parity only.

#### Observations
- **V-O1**: child 04's implementer briefly wrote its three static files into the **main** checkout and reverted them with `git checkout --` (declared in `children/04/execution.md`, audited in `children/04/fix-log.md`). This lies outside the reviewed boundary and is reported as an evidence source only; the retained worktree's static files are the reviewed revision and the full JS + Java suites are green. The main checkout's state was **not** re-verified here (out of boundary; all commands were run with cwd = the retained worktree).
- **V-O2**: `ExpertDiscoveryScheduler.scheduleDiscovery()` now has no production caller (`grep -rn scheduleDiscovery src/main` → definition only at :356); keeping that entry point is explicitly mandated by child-03 T-2 (“保留 `scheduleDiscovery` 入口”), and it remains the seam exercised by the legacy scheduler tests. Behaviorally unaffected; noted for future cleanup only.

### RECORD_ONLY mapping (every fast-p entry re-evaluated as an investigation lead against a master requirement)
| Fast-p entry | Source | Master requirement checked | Outcome |
|---|---|---|---|
| O-1 (01): two synthetic coverage classes delegated to pre-existing green tests; acceptance report omits them | `children/01/verify-log.md`, `fix-log.md` | M-1 acceptance (7 forbidden = 0; explicit two-mailbox positive retained) | Not promoted → **V-1 (P2)**. Both halves of M-1 are machine-proven by fresh command 1; only report/test-artifact depth differs. |
| O-1 (03): additive `message` beyond the fixed field list | `children/03/verify-log.md` | M-3 (persist+apply success signal), cross-child contract | Not promoted → **V-2 (P2)**. Additive field; `applied`/`reason` semantics unaffected; 04 consumes only documented fields. |
| O-1 (04): not-settable explanation uses server `message` wording | `children/04/verify-log.md` | 需求3 (continuous mode shows no unusable input), I-3 | Not promoted → **V-3 (P2)**. Controls hidden + explanation shown; wording is style parity only. |
| Controller: child-04 main-checkout self-revert incident | `children/04/fix-log.md` | Scope/integrity of the reviewed boundary | Not promoted → **V-O1 (Observation)**. No product path inside `1cd59e3..075dc3e` is affected. |

### Evidence Boundaries
- Master 人工验收 A-1 (and the child checklists A-1…A-4) remain PENDING — manual/human items, by design not machine-verified. In particular the operator-visible visual checks (input width, focus outline, disabled opacity, narrow-screen wrap, dark mode) and the real-DB/real-clock end-to-end run were not performed.
- The child evidence reports, `target/discovery-plan-acceptance/01.json` and the fast-p ledger/handoff were treated as evidence sources, not as the governing contract; every verdict above rests on fresh commands and on source read in the retained worktree.
- The main checkout (`/Users/lukai/IdeaProjects/weibo-talent-introduction`) was not inspected (all commands were run with cwd = the retained worktree, per the task constraint); V-O1 therefore relies on the recorded controller audit.
- The PDF/OpenAlex fixtures are the same offline archived originals already used by the child tests; no network re-fetch, no production writes, no real sending were performed.

### Next Action
- PASS → perform pending human acceptance (master 人工验收 A-1) or finish the branch.

## Repair planning: N/A

No repair plan was written. The only permitted path (`docs/plans/fix/2026-09-29-discovery-repair-00-master/repair.md`) does not exist and was not created, because verification is `PASS` (review-p routing: PASS → stop) and `repair-p` was never invoked. The un-dated `docs/plans/fix/discovery-repair-00-master/` (a different run) was not touched.

## No product code was modified.

No product code, test, configuration, migration, plan, fast-p artifact or review artifact was created or changed: the reviewed tree is `git status --porcelain` clean apart from the controller's untracked `docs/plans/review/2026-09-29-discovery-repair-00-master/` directory, `HEAD` is still `298ad8991041e51f87765f85cac908f356160cba`, and the only writes were transient build outputs under `target/` plus the temp log `/tmp/agg-clean-package.log`. Nothing was staged, committed, pushed, merged, rebased or amended.

### Fast-P RECORD_ONLY Re-evaluation
| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 (01) synthetic-coverage delegation + acceptance-report listing | M-1 acceptance | Not promoted (V-1, P2) | `children/01/verify-log.md`; fresh command 1 green incl. :270/:355/:390 |
| O-1 (03) additive `message` field | M-3 / cross-child contract | Not promoted (V-2, P2) | `children/03/verify-log.md`; CM-8 |
| O-1 (04) server-`message` wording for not-settable modes | 需求3 / I-3 | Not promoted (V-3, P2) | `children/04/verify-log.md`; CM-4 |
| Controller: main-checkout self-revert incident | Reviewed-boundary scope/integrity | Not promoted (V-O1, observation) | `children/04/fix-log.md` |

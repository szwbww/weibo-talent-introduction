# Aggregate Machine Verification — single-gate

## Epoch 1 — 2026-08-28

- Master plan: `docs/plans/2026-08-28/00-single-gate-master.md` (sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`)
- Governing master identity: recorded commit `1f5a916489933fc9b2e8e469037fc912d55edd5d`; worktree sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`
- Master identity state: `CONSISTENT`; amendments A1–A5 are recorded, HUMAN-approved, and verified against their named master rules.
- Boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..4636727749202052c6affd2550e5353139fcb4a1`
- Reviewer: `/root/aggregate_reviewer`
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/00-single-gate-master/repair.md` — `DRAFT_READY`

## Verification Result: FAIL

Plan: `docs/plans/2026-08-28/00-single-gate-master.md` (aggregate/master)

Implementation boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..4636727749202052c6affd2550e5353139fcb4a1`

Manual acceptance: `PENDING`

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; 2,969 Kotlin tests; 0 failures; 0 errors; 5 skipped; embedded JS 755/755 |
| Selected master/child Maven target regression set | PASS | exit 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; 2,969/0/0/5; WAR packaged |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 755 pass; 0 fail; 120 suites |
| `git diff --check` | PASS | exit 0 |
| `git diff --check de228e17cc0134a7c11dea7cbf82054e8d249f99..4636727749202052c6affd2550e5353139fcb4a1` | PASS | exit 0 |
| M-1 grep guards | PASS | no production `expertClassification.sendable`, `classification.sendable`, `expertSendableFilter`, or `ACCEPTED_CLASSIFICATION_VERSIONS` hits |
| M-4 audit | PASS | both `minusDays(30)` calls retained; no boundary diff change |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | FAIL | V-1: MATERIAL_REMINDER empty expert types incorrectly rejected by the editor |
| M-2 | PASS | INTRODUCTION backend configuration/manual-start validation rejects empty types |
| M-3 | PASS | classification mapping/type rules retained; only authorized gate cleanup |
| M-4 | PASS | two 30-day cutoff paths unchanged |
| 01: I1-1..I1-8, S1-1 | PASS | OpenAlex recovery, stats, controller, and write-path tests pass |
| 02: I2-1..I2-5 | PASS | explicit type filters; no implicit sendable/version gate |
| 03: I3-1 | FAIL | MATERIAL_REMINDER editor cannot save empty types |
| 03: I3-2..I3-6 | PASS | server config/start behavior, selector/filter behavior, fail-closed INTRODUCTION coverage pass |
| 03: S3-1 | FAIL | exact error exists but is incorrectly applied to MATERIAL_REMINDER |
| 04: I4-1..I4-6 | PASS | empty whitelist guard passes; single type-gate behavior verified |
| 05: I5-1..I5-5 | PASS | sendable vocabulary cleanup/mapping preservation guard passes |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `src/main/resources/static/app.js:15033` unconditionally rejects empty `expertTypes` despite the existing selected-template mail-type resolver |

### Findings

#### P1

- V-1 — An enabled `MATERIAL_REMINDER` template cannot save a configuration with `expertTypes: []`. The backend preserves that behavior, but `saveBatchConfigEditor()` at `src/main/resources/static/app.js:15033` runs the INTRODUCTION-only validation unconditionally. This violates master M-1 and child 03 I3-1.

#### P2

- N/A.

#### Observations

- N/A.

### Amendments

| Amendment | Result |
|---|---|
| A1 / child 03 fixtures | PASS under I3-1/I3-2 regression rule; alignment inside approved scope |
| A2 / child 04 test deletion + line pin | PASS under I4-6/full-regression rule |
| A3 / child 05 ExpertSearchServiceTest deletion | PASS under I5-5 scope gate |
| A4 / child 05 ManualInitialOutreach fixture | PASS under I5-5 scope gate; local triad preserves behavior |
| A5 / child 05 operator-status line pin | PASS under Task 4/full-regression rule |

### Evidence Boundaries

- None.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 helper local rename | No mandatory master contract affected | RECORD_ONLY | behavior-equivalent test-only rename |
| 02 evidence/boundary/deviation bookkeeping | No mandatory master contract affected | RECORD_ONLY | docs-only |
| 03 boundary spans 02 evidence docs | No mandatory master contract affected | RECORD_ONLY | docs-only |
| 04 grep “exactly one” wording | M-1 | RECORD_ONLY | two hits: one child-04 matcher and one pre-existing child-02 inline predicate; M-1 behavior passes |
| 04 docs/bookkeeping | No mandatory master contract affected | RECORD_ONLY | docs-only |
| 04 test-entry migration quantity | M-2 | RECORD_ONLY | M-2 consequence; assertions retained |
| 05 | N/A | N/A | no entry |

### Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-08-28/00-single-gate-master.md`

Verification result: `FAIL` / `INITIAL`

Repair artifact: `docs/plans/fix/00-single-gate-master/repair.md`

Included finding: `V-1`.

Required human decision: approve the repair plan before execution.

No product code was modified by the reviewer.

## Epoch 2 — 2026-08-28

- Master plan: `docs/plans/2026-08-28/00-single-gate-master.md` (sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`)
- Governing master identity: recorded commit `1f5a916489933fc9b2e8e469037fc912d55edd5d`; worktree sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`
- Master identity state: `CONSISTENT`; amendments A1–A5 recorded in the fast-p ledger.
- Boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..b3b7a9b78ed3f85abc0e9fc6a51a0a5a43f5695f`
- Reviewer: `01a048e2-6e40-7461-94e5-06da97888dff`
- Result: `BLOCKED`
- Convergence: `BLOCKED`
- Repair artifact/result: `docs/plans/fix/00-single-gate-master/repair.md` — N/A (verification blocked)

## Verification Result: BLOCKED

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/2026-08-28/00-single-gate-master.md`

Implementation boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..b3b7a9b78ed3f85abc0e9fc6a51a0a5a43f5695f`

Convergence: `BLOCKED`

Manual acceptance: `PENDING`

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/batchExpertTypeFilter.test.js` | PASS | exit 0; 8/8 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 757 pass, 0 fail, 120 suites |
| `git diff --check` | PASS | exit 0 |
| `git diff --check de228e1..b3b7a9b` | PASS | exit 0 |
| M-1 grep guards | PASS | zero forbidden gate/sendable-read hits |
| M-4 audit | PASS | two `minusDays(30)` calls retained; zero boundary changes |
| Master targeted Maven set | BLOCKED | Initial sandbox run exit 1 before tests: existing `target/classes/application.yml` returned `Operation not permitted`. Escalated rerun session `19119` started; polling was interrupted after 23.3 seconds. Final process status unknown; it may still be running. |
| Full `mvn test` | BLOCKED | Not run after interrupted targeted command |
| Full `mvn clean package` | BLOCKED | Not run |
| Child/master targeted Maven checks | BLOCKED | Required fresh results unavailable |

Interrupted command:

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest='OpenAlexDataSourceTest,ExpertDiscoveryServiceTest,ExpertDiscoveryControllerTest,ExpertClassificationServiceTest,ExpertSearchServiceTest,InitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendControlServiceTest,V109ExpertTypesMigrationTest,ManualInitialOutreachServiceTest,BatchSendTaskRuntimeIntegrationTest,ExpertClassificationVersionGateGuardTest,ExpertIndexWriterServiceTest,ExpertClassificationBackfillServiceTest,ExpertClassificationAdminControllerTest,ExpertClassificationSchedulerTest,OperatorStatusWriteSeamGuardTest'
```

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Identity/boundary | PASS | Master/ledger/handoff/repair hashes match; base→repair→evidence ancestry valid |
| Repair scope | PASS | `b03e81f..b3b7a9b` changes only authorized `app.js` and `batchExpertTypeFilter.test.js`; repair commit parent exactly `b03e81f` |
| M-1 | PASS | Explicit expert-type gate only; forbidden grep guards empty |
| M-2 | BLOCKED | Static fail-closed paths verified; fresh Maven runtime evidence incomplete |
| M-3 | BLOCKED | Source boundary preserves classifier rules/VERSION; required fresh classification regression incomplete |
| M-4 | PASS | Both 30-day constants unchanged |
| 01: I1-1..I1-8, S1-1 | BLOCKED | Static paths conform; fresh Maven checks incomplete |
| 02: I2-1..I2-5 | BLOCKED | Static paths conform; fresh Maven checks incomplete |
| 03: I3-1..I3-6, S3-1 | BLOCKED | V-1 browser path resolved; backend Maven checks incomplete |
| 04: I4-1..I4-6 | BLOCKED | Static guards conform; fresh guard/runtime Maven checks incomplete |
| 05: I5-1..I5-5 | BLOCKED | Static cleanup conforms; fresh Maven checks incomplete |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | MATERIAL_REMINDER empty types now reach API; INTRODUCTION remains blocked. Repair-specific JS tests 8/8; full JS 757/757. |

### Findings

#### P1

- N/A.

#### P2

- N/A.

#### Observations

- All prior RECORD_ONLY items remain non-blocking after re-evaluation.
- Existing parent-owned modification: `docs/plans/review/single-gate/ledger.md`; untouched.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| All epoch-1 RECORD_ONLY entries | M-1/M-2 or no mandatory master contract | RECORD_ONLY | Fresh reviewer reported all remain non-blocking; unavailable Maven evidence prevents no additional finding classification. |

### Evidence Boundaries

- Mandatory Maven targeted, full-test, and package evidence is unavailable because the escalated targeted run was interrupted with unknown terminal status.

### Next Action

Obtain/terminate session `19119` as appropriate, then freshly run the targeted Maven set, full `mvn test`, and full `mvn clean package`; rerun `verify-p`.

Repair planning: N/A — verification is `BLOCKED`, not an eligible `FAIL`.

No product code was modified.

## Epoch 3 — 2026-08-29T00:14:15+0800

- Master plan: `docs/plans/2026-08-28/00-single-gate-master.md` (sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`; recorded identity commit `1f5a916489933fc9b2e8e469037fc912d55edd5d`)
- Governing master identity: worktree sha256 `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`; recorded commit `1f5a916489933fc9b2e8e469037fc912d55edd5d`
- Master identity state: `CONSISTENT`; amendments A1–A5 recorded (master rule/reason/HUMAN approval each)
- Boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..b3b7a9b78ed3f85abc0e9fc6a51a0a5a43f5695f`
- Reviewer: `ReviewSG Epoch3`
- Result: `PASS`
- Convergence: `PROGRESSING`
- Repair artifact/result: N/A (prior V-1 repair executed and verified RESOLVED at `b3b7a9b`; no new eligible failure planned)
- Post-repair evidence mode: `DURABLE_HANDOFF`; executor recorded `Main (controller; direct invocation)` in `repair-execution.md`; other optional executor metadata `NOT_RECORDED` (non-blocking)

### Identity Checks

| Check | Result | Evidence |
|---|---|---|
| Repair ancestry | PASS | `b3b7a9b` descends from `4636727` (`git merge-base --is-ancestor` OK) |
| Repair commit scope | PASS | `b3b7a9b` = exactly 2 Authorized Files (`app.js` +5/-1, `batchExpertTypeFilter.test.js` +47); subject exactly `fix(fast-p): preserve material reminder empty expert types`, matching repair.md commit contract |
| Repair delta `4636727..b3b7a9b` | PASS | `src/` filter shows ONLY the 2 Authorized Files; remaining delta files are fast-p/review harness docs from evidence commits `34e5a1e`/`225e751`/`b03e81f`; no other product/test file changed |
| Document hashes | PASS | master `fb2e511d` ✓; fast-p ledger `12f1b3e3` ✓; handoff `674652aa` ✓; repair.md `9a0ba640` ✓ (matches repair-execution.md recorded identity) |

### Commands

| Command | Exit | Result |
|---|---|---|
| `node --check src/main/resources/static/app.js` | 0 | PASS |
| `node --test src/test/js/batchExpertTypeFilter.test.js` | 0 | PASS — tests 8, pass 8, fail 0; both R1 tests present (MATERIAL_REMINDER empty save reaches API; INTRODUCTION empty still blocked with exact error) |
| `node --test src/test/js/*.test.js` | 0 | PASS — tests 757, pass 757, fail 0, suites 120 (matches epoch-2 post-repair figure) |
| `JAVA_HOME=zulu-11 mvn test -Dtest='<17-class set>'` | 0 | BUILD SUCCESS — fresh surefire for exactly the 17 classes: 514 tests, 0 failures, 0 errors, 0 skipped |
| `JAVA_HOME=zulu-11 mvn test` (full) | 0 | BUILD SUCCESS — Tests run 2969, Failures 0, Errors 0, Skipped 5 (baseline de228e1: 2952/0/0/5) |
| `JAVA_HOME=zulu-11 mvn clean package` | 0 | BUILD SUCCESS — 2969/0/0/5; `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` built (stale `target/classes/application.yml` overwritten — authorized elevated Maven) |
| `git diff --check` ; `git diff --check de228e1..b3b7a9b` | 0 ; 0 | PASS — both clean |
| M-1 grep guards (`expertSendableFilter\|ACCEPTED_CLASSIFICATION_VERSIONS` in `src/main/kotlin`) | — | PASS — zero matches |
| M-4 audit (`minusDays(30)` in `ExpertDiscoveryService.kt`) | — | PASS — both calls retained at `:800` and `:886` (plan-recorded `:871` shifted +15 lines by child-01 insertions); boundary diff zero hunks touching them |
| I5-5 scope gate (`sendable` in `src/main/kotlin` + `src/test/kotlin`) | — | PASS — every hit inside a child-05 authorized file or fixed exclusion (`ManualInitialOutreachService.kt` sender-account semantics; `ExpertClassificationService.kt`/`ExpertSearchService.kt` KDoc; `ExpertClassificationBackfillService.kt:93` mapping-error string; `V109ExpertTypesMigrationTest.kt`/`BatchSendTaskRuntimeIntegrationTest.kt`/`InitialOutreachServiceTest.kt` exclusions); `gateTemplateFilter.test.js` hit is a negative assertion — exempt |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 single gate | PASS | ES `buildEsFiltersForLevel` INTRODUCTION branch = only `expertTypesFilter(scope.expertTypes) ?: MATCH_NONE_FILTER` (`ManualInitialOutreachService.kt:1317-1324`); memory `matchesExpertType` (`BatchExecutionModels.kt:127-130`) shared by `matchesExpert` + send-before gate; `expertSendableFilter()` and `searchSendableExpertsWithEmail` deleted; `ACCEPTED_CLASSIFICATION_VERSIONS` deleted; grep guards empty; `ExpertClassificationVersionGateGuardTest` both whitelists `ALLOWED_GATE_SITES`/`ALLOWED_SENDABLE_SITES = emptySet()`, both guard cases pass — M-1 machine criterion met |
| M-2 empty = fail-closed | PASS | Save side: `BatchSendTaskConfigService.kt:319-321` (INTRODUCTION require non-empty, exact message) + `BatchSendControlService.kt:432-435` (snapshot validation, same 422 channel); runtime: `MATCH_NONE_FILTER` verbatim + `matchesExpertType` empty→false; V109 fills NULL/''/'[]' rows with exact triad |
| M-3 classifier zero change | PASS | `ExpertClassificationService.kt` boundary diff = exactly 7 deletions (`ACCEPTED_CLASSIFICATION_VERSIONS` + doc comment); `VERSION rnd-v2-2026`, thresholds 50/50, `RECENT_PAPER_CUTOFF_YEAR 2021`, scoring/vocab untouched |
| M-4 `minusDays(30)` | PASS | both 30-day cutoff calls retained; zero boundary diff changes (child-01 backfill uses independent filters) |
| 01: I1-1..I1-8, S1-1 | PASS | `parseAuthorEnrichmentFromNode` = `counts_by_year` filter `works_count>0` → `mapNotNull(year)` → `maxOrNull()` (`OpenAlexDataSource.kt:299-302`), tests cover ascending/descending/zero/all-null; null → key skipped (`updateExpertAcademicFields` `?.let`); non-null unconditional overwrite; backfill filter `exists enrichedAt` + `must_not [exists lastPublicationYear, prefix EMAIL-]`; DEFAULT/INSTITUTION_TYPE_BACKFILL branches verbatim; tail `= null` default field; no VERSION change; `index.html:600` exact button + `app.js:5809` branch, no new CSS/DOM |
| 02: I2-1..I2-5 | PASS | `MailSchedulingProperties.kt:22` `emptyList()` default; both `application.yml` `${MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES:}` empty default; `sendInitialBatch` `require(types.isNotEmpty())` exact message (`InitialOutreachService.kt:34-38`); whitelist via `ALLOWED_EXPERT_TYPES` only; `searchExpertsByTypesWithEmail` filter exactly `[exists email, typesFilter]` (test asserts exactly two, no sendable/version); last-chance gate same `UNCLASSIFIED`→null semantics (`:50-58`) |
| 03: I3-1..I3-6, S3-1 | PASS | both validations gated on `mailType == INTRODUCTION.name`; MATERIAL_REMINDER empty still saves (config test) and launches (control test); V-1 fix restored same rule in editor; V109 writes exactly `["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]` = SENDABLE_TYPES first three; WHERE covers only NULL/''/'[]'; no `${`; frontend defaults three literals (`app.js:14117`/`:15136`); exact error text via `showStatus`, no CSS/class/DOM additions |
| 04: I4-1..I4-6 | PASS | INTRODUCTION reads only `expertClassification.type` vs `scope.expertTypes` (ES + memory + send-before gate); `MATCH_NONE_FILTER` verbatim; `expertTypesFilter`/`expertTypePredicate` contracts unchanged (no hunks); single `matchesExpertType` shared implementation (`:604-616`); ES/memory parity incl. UNCLASSIFIED=null (runtime integration test); `ACCEPTED_CLASSIFICATION_VERSIONS` deleted |
| 05: I5-1..I5-5 | PASS | `BackfillCounters byType` map; progress/statsMap/result per-type, no 可发信/不可发信 in main; keys from `classification.type.name`; `sendable` put deleted (`ExpertIndexWriterService`), `src/main/resources/es/` zero-diff (orphan retained); `toExpertProfile` untouched (KDoc retains `sendable 不读自 ES`); scope gate hits match authorized list + fixed exclusions |
| A1 | PASS | 5 fixture files aligned to I3-1/I3-2 default-three semantics under recorded master rule; mechanical alignment, no assertion-semantics weakening |
| A2 | PASS | I5a2-10 test deletion (asserted deleted constant, I4-6) + `OperatorStatusWriteSeamGuardTest` line pin 545→498 under I4-6 + full regression; guard green in fresh targeted run |
| A3 | PASS | `ExpertSearchServiceTest` 2 derived-property tests deleted (read deleted `c.sendable`, compile blocker) under I5-5 scope gate; semantics covered by remaining type assertions |
| A4 | PASS | `ManualInitialOutreachServiceTest` fixture local triad (SENDABLE_TYPES first three, behavior-identical) under I5-5 scope gate revision |
| A5 | PASS | `OperatorStatusWriteSeamGuardTest` line pin 436→435 (operatorStatus write point shift) under Task 4 + full regression; guard green |
| Cross-child batch send chain | PASS | 03 write path (V109 fills empty rows + save-time non-empty) → 04 read path fail-closed (empty configs impossible post-03; fail-closed defends legacy/direct API) |
| Cross-child legacy outreach | PASS | 02 config-driven `searchExpertsByTypesWithEmail` replaced `searchSendableExpertsWithEmail` → 04 deleted old method (zero remaining `src/main` references) |
| Cross-child backfill stats + DTO | PASS | 05 `byType` stats + `expertSendable` API DTO removal; `ExpertIndexController` diff exactly 2 deletions |
| Guard both whitelists empty | PASS | `ALLOWED_GATE_SITES`/`ALLOWED_SENDABLE_SITES` both `emptySet()`; both cases pass in targeted (2/2) and full suite |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | `app.js:15033-15037` empty-picker check scoped to `resolveBatchTemplateMailType(templateId)==="INTRODUCTION"` with short-circuit; `batchExpertTypeFilter.test.js` 8/8 incl. both R1 tests (MATERIAL_REMINDER empty save reaches API with `expertTypes:[]` and no error; INTRODUCTION empty blocked with exact error before request); full JS 757/757; full `mvn test` 2969/0/0/5; guard whitelists both `emptySet()` |
| NEW | NONE | no new finding |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1 | none affected (test-only behavior-equivalent rename) | PASS | — |
| 02 O-1 | none affected (fast-p harness docs in boundary) | PASS | — |
| 02 O-2 | none violated (deviations within authorized files; guard line pin verified) | PASS | — |
| 02 O-3 | none affected (bookkeeping outside boundary) | PASS | — |
| 03 O-1 | none affected (boundary spans 02 evidence commit, docs-only) | PASS | — |
| 04 O-1 | M-1 behavior holds (second grep hit is pre-existing child-02 zero-diff inline predicate) | PASS | — |
| 04 O-2 | none affected (bookkeeping docs) | PASS | — |
| 04 O-3 | M-2 consequence, assertions preserved, no violation | PASS | — |
| 05 | N/A (no entry) | PASS | — |

No product code was modified.

# Aggregate Machine Verification — discovery-resilience-two-hour

## Epoch 1 — 2026-09-28T03:17:33Z

- Master plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md` (commit `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a`)
- Governing master identity: sha256 `c94fd0e4a8aaaa54ecf1727d8622812666af1df65a64fbde4635cdacf41db88b`; recorded commit `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a`
- Master identity state: CONSISTENT
- Boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..8700a605427aaedb4c31be63a72e22a657b94208`
- Reviewer: `/root/aggregate_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/discovery-resilience-two-hour/repair.md` — DRAFT_READY

## Verification Result: FAIL

Plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md`

Implementation boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..8700a605427aaedb4c31be63a72e22a657b94208`; product implementation subset: `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a..8700a605427aaedb4c31be63a72e22a657b94208`.

Evidence HEAD: `6f39736c55fb489782fe29246802ca052952c4a4`.

Convergence: INITIAL

Manual acceptance: PENDING (A-1..A-8)

### Commands

| Command | Result | Evidence |
|---|---|---|
| Exact 11-class `mvn test -Dtest=...` | PASS | exit 0; BUILD SUCCESS; 354 tests, 0 failures/errors/skips |
| Exact `mvn clean package` | PASS | exit 0; BUILD SUCCESS; 4217 tests, 0 failures, 0 errors, 13 skipped; Node 1199 pass, 0 fail |
| `git diff --check` | PASS | exit 0 |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 | PASS | `ExpertDiscoveryService.kt:779-806,829-859,1216-1232`; bounded 3+3×3 recovery; properties validation. |
| I-2 | PASS | `ExpertDiscoveryService.kt:668-720,946-955`; exact resume cursor and checkpoint delta. |
| I-3 | PASS | `ExpertDiscoveryService.kt:699-703,714-720,1018-1023`; accumulated counters, quota, elapsed time. |
| I-4 | PASS | `ExpertDiscoveryService.kt:801,852,821,859,1023-1024`; retry wait is nonterminal and terminal failure recorded once. |
| I-5 | FAIL | `ExpertDiscoveryService.kt:1026-1033` erases `SourceStats.retry` on completed/exhausted/deferred recovery, losing required audit round/reason. Tests explicitly assert the prohibited null at `ExpertDiscoveryServiceTest.kt:4119,4171,4443`. |
| I-6 | PASS | `ExpertDiscoveryScheduler.kt:72-90`; Asia/Shanghai cron, removed daily gate, retained shared run slot. |
| I-7 | PASS | `ExpertDiscoveryService.kt:1074-1141,1160-1173`; ≤100ms cancel/deadline checks and no subsequent recovery request. |
| I-8 | PASS | `ExpertDiscoveryService.kt:766-778,2410-2435`; budget/429 bypass recovery; certificate/protocol errors remain excluded. |
| X-1 | PASS | I-2 cursor/recovery path and checkpoint delta evidence above. |
| X-2 | PASS | `ExpertDiscoveryService.kt:934-1003`; consumer failures retain entry and do not create recovery. |
| X-3 | PASS | `ExpertDiscoveryService.kt:294-300,332-366,1180-1195`; retry info traverses progress details and summary. |
| X-4 | PASS | `ExpertDiscoveryScheduler.kt:76-104`; slot, execution binding, finally cleanup retained. |
| X-5 | PASS | I-8 path retains `OpenAlexDataSource`/request-policy route; no budget code changed. |
| X-6 | PASS | Scheduler pipeline branch remains separate; focused pipeline/scheduler tests pass. |
| Scope/non-goals | PASS | `3f167a2..8700a60` changes exactly eight authorized product/test files; no prohibited product path changed. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | I-5 mandatory audit-retention violation at `ExpertDiscoveryService.kt:1026-1033`. |

### Findings

#### P1

- V-1: I-5 requires clearing only `nextRetryAt` on recovery start, cancellation, timeout, and end, retaining round/reason for audit. The implementation replaces the entire retry object with null on recovery success, terminal exhaustion, and budget deferral. Smallest scope: `ExpertDiscoveryService.kt` plus its regression tests.

#### P2

- N/A

#### Observations

- O-1 → promoted to V-1/I-5.
- O-2 → I-6-related, nonblocking: test-profile cron remains daily but discovery is disabled and this file is outside approved scope.
- O-3 → I-5-related, nonblocking: reason-label/running-summary wording is not uniquely mandated.
- O-4 → T-4-related, nonblocking: historical red-test form does not prove a runtime defect.
- O-5 → I-3-related, nonblocking: elapsed-time uses system time while deadline uses injected time; accumulated elapsed behavior is proven, but test seam is imperfect.

### Evidence Boundaries

- Manual A-1..A-8 remain PENDING.
- No amendments. One child only; no cross-child handoff gap.
- Post-repair metadata: N/A.

### Next Action

- FAIL + INITIAL: repair plan is ready for human approval.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md`

Verification result: FAIL / INITIAL

Repair artifact: `docs/plans/fix/discovery-resilience-two-hour/repair.md`

### Included Findings

- V-1

### Excluded Findings

- O-2..O-5: nonblocking observations; no confirmed mandatory violation.

### Required Human Decision

- Approve the repair plan.

No implementation was performed. No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 | I-5 | Promoted to V-1 | `ExpertDiscoveryService.kt:1026-1033`; `ExpertDiscoveryServiceTest.kt:4119,4171,4443` |
| O-2 | I-6 | Nonblocking observation | `src/test/resources/application.yml:86`; profile disabled; outside authorized scope |
| O-3 | I-5 | Nonblocking observation | reason-label/running-summary wording not uniquely required |
| O-4 | T-4 | Nonblocking observation | historical red-test form; no delivered runtime defect |
| O-5 | I-3 | Nonblocking observation | accumulated elapsed behavior proven; clock seam imperfect |

## Epoch 2 — 2026-09-28T04:55Z (post-repair aggregate re-review; persisted 2026-09-28T05:10Z)

- Master plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md` (sha256 `c94fd0e4a8aaaa54ecf1727d8622812666af1df65a64fbde4635cdacf41db88b`; recorded commit `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a`)
- Governing master identity: worktree sha256 `c94fd0e4a8aaaa54ecf1727d8622812666af1df65a64fbde4635cdacf41db88b`; recorded commit `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a`
- Master identity state: CONSISTENT (invoked identity SAME; recomputed in the worktree at review time)
- Boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..8e622680c9fafeb68c6b220da8ef47c947ff2fcd` (product/test subset `3f167a2..8e62268`; evidence head `22e4dd61a28aa2aba128ed7fac7326e091146f9a`)
- Worktree resolution: EXPLICIT — `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour`, branch `fast/2026-09-28-discovery-resilience-two-hour`
- Repair artifact: `docs/plans/fix/discovery-resilience-two-hour/repair.md` sha256 `830b2ab77e854dd31c9e18259f0545277afd5e160ac60cc8ac910da574779ade` (unchanged before/after this review); approval source HUMAN `$execute-p <path>` invocation (2026-09-28); executor `RepairExec01`; handoff `docs/plans/review/2026-09-28-discovery-resilience-two-hour/repair-execution.md` (READY_FOR_VERIFICATION, DURABLE_HANDOFF); repair delta `008f4c47..8e62268` = exactly the 2 authorized files
- Reviewer: `AggregateReviewE2`
- Result: **PASS**
- Convergence: **PROGRESSING** (single blocking finding V-1 resolved, no equal-or-higher new finding)
- Repair artifact/result: N/A for re-planning — `verify-p` returned PASS, so routing stopped before `repair-p`; the executed V-1/R-1 repair plan was neither updated nor replaced

## Verification Result: PASS

Plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md`

Implementation boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..8e622680c9fafeb68c6b220da8ef47c947ff2fcd` (product/test subset: the 8 authorized files; `git diff --name-only` over the whole boundary yields 28 paths = those 8 plus 20 plan/evidence docs, so no product path outside the authorized list changed).

Convergence: PROGRESSING

Manual acceptance: PENDING (A-1..A-8)

### Commands

| Command | Result | Evidence |
|---|---|---|
| `mvn test -Dtest=ExpertDiscoveryServiceTest` | PASS | exit 0; `Tests run: 160, Failures: 0, Errors: 0, Skipped: 0` (61.89 s; `target/surefire-reports/...ExpertDiscoveryServiceTest.txt`, written 12:40:39 +08:00 by this run) |
| `mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryPropertiesTest,DiscoveryCheckpointCodecTest,OpenAlexRequestPolicyTest,DiscoveryPromotionProgressServiceTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,DiscoveryPipelineServiceTest` | PASS | exit 0; BUILD SUCCESS; `Tests run: 354, Failures: 0, Errors: 0, Skipped: 0`; per class 160/15/5/11/44/5/26/3/13/26/46 (all 11 classes ran) |
| `mvn clean package` | PASS | exit 0; BUILD SUCCESS (4:45 min); `Tests run: 4217, Failures: 0, Errors: 0, Skipped: 13`; exec-plugin Node suite `tests 1199 / pass 1199 / fail 0`; WAR assembled |
| `git diff --check` | PASS | exit 0, no output (both against the working tree and against `8e62268`) |

All four commands were executed freshly by this reviewer in this worktree with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. Counts equal the pre-repair aggregate baseline (354 / 4217 / 13 skipped / Node 1199) and the `children/01/baseline.md` baseline (337 targeted / 4200 / Node 1199) plus the plan's 17 new cases — no regression.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 | PASS | `ExpertDiscoveryService.kt:3643-3644` (3 short attempts; 5xx whitelist), `:2411-2437` (certificate/protocol/JSON errors excluded), `:780-806` + `:830-859` (short attempts exhausted → one deferred group; 3 groups max), `:1217-1233` (`planDeferredRecovery` bounded by `delays.size`, groups not reset by a consumed page), `ExpertDiscoveryProperties.kt:95-118` + `:139-149` (≤3 items, positive, non-decreasing, ≤5 min, empty = off). Tests: 12 attempts then one `SEARCH_FAILED` and 30s/120s/300s spacing (`ExpertDiscoveryServiceTest.kt:4127-4179`), each approved 5xx retried then exhausted (`:4555-4575`), certificate/403 never retried (`:4577-4613`), empty list → existing 3-attempt path (`:4040-4054`) |
| I-2 | PASS | `:666-676` (resume uses this run's in-memory `resume.resumeCursor`; `null` is a legal first page), `:714-720`/`:1043` (`persistCheckpoint` delta; page entry kept on failure), `:158-201` (`persistSourceCheckpoint`, single writer, key from `DiscoveryCheckpointCodec.sourceKey`). Tests: `:4522-4553` (null entry kept), `:3917-3944` (page-boundary resume), `:3850-3895` (partial page does not advance), `:4868-4896` (RAW failure keeps entering cursor) |
| I-3 | PASS | `:661` (`runBudget = sourceLimit`), `:700-703` (persisted/source baselines from `resume.processedBaseline`), `:1020-1023` (`elapsedMs +=` fragment instead of overwrite), `:1066-1101` (recovery reuses `item.runBudget`; no `allocateSourceQuota`), deadline checks `:748-759`/`:905-912`. Tests: `:4248-4295` (runBudget stays 1000, 200 papers once, checkpoint total 200, `elapsedMs ≥ 60`, unique batch numbers), `:4297-4344` (global cap full → zero recovery requests, entry kept) |
| I-4 | PASS | `:801`/`:852` (`RETRY_WAIT` only, source-level), no `recordTerminalSourceFailure` before groups are exhausted (`:859-864`, `:886-891`), `:1022` (`pendingWork = !exhausted`), `:3626` (`RETRY_WAIT` documented non-terminal), terminal decision `DiscoveryResult.kt:20-26`. Tests: `:4058-4125` (wait: `sourceFailureCount=0`, stop reason later `EXHAUSTED`), `:4127-4181` (one `SEARCH_FAILED`, one failed source), `:4346-4384` (cancel is `CANCELLED`) |
| I-5 | PASS | `:294-300` (only serializer), `:317-340` (`retryWaitSegment` appears only while `nextRetryAt != null`), `:1027-1034` (repair: new pending object wins, otherwise `?: sourceStats.retry?.copy(nextRetryAt = null)`), `:1146-1153` (recovery start clears plan time, keeps round/reason), `:1161-1167` (stopped recovery keeps round/reason), `:1170-1174` + `:468` + `:523` (run end, normal and exception, clears only plan time), `:1217-1233` (the only place a retry is created — from run-local `groupsScheduled`, never from the audit object); repo-wide grep shows `sourceStats.retry` has observation-only consumers. Tests: `:4058-4125` (wait event carries round/maxRounds/`REMOTE_TLS_HANDSHAKE` + ISO-8601 UTC `nextRetryAt` equal to the real revisit instant + summary text; exactly one "开始恢复回访" event; after completion round/maxRounds/reason retained with `nextRetryAt == null`), `:4127-4179` (exhausted: round 3 / maxRounds 3 / `TIMEOUT` retained, exactly 3 wait events — no 100 ms log spam), `:4420-4458` (budget deferral during recovery retains round 1 / `REMOTE_TLS_HANDSHAKE`), `:4346-4384`/`:4386-4418` (cancel and deadline clear the plan time), `:4054` (empty recovery list → no object) and `:4894` (no-recovery-history source → `retry == null`) |
| I-6 | PASS | `ExpertDiscoveryScheduler.kt:72-73` (single `@Scheduled`, `cron = "${talent-introduction.expert-discovery.cron:-}"`, `zone = "Asia/Shanghai"`), `:74-88` (daily gate removed; `tryStartWithToken` retained), `:105-112` (finally cleanup), `application.yml:182` (default `0 0 */2 * * ?`), `:185` (recovery-delay binding). Retained shared API unaffected: `TaskExecutionService.kt:124-125`, `TaskExecutionRepository.kt:68`. Tests: `ExpertDiscoverySchedulerTest` 15 green including annotation + repository default + next three triggers 02:00/04:00/06:00 and the initialization message |
| I-7 | PASS | `:1126-1144` + `:2389-2407` + `:3647` (≤100 ms slices, cancel checked every slice), `:1136-1138`/`:2400-2402` (interrupt flag restored), `:1081-1096` (cancel outranks time and budget; no further request). Tests: `:4639-4660` (deadline → no extra request), `:4662-4691` (cancellation during wait → no extra request), `:4693-4724` (interrupt flag restored, `CANCELLED`), `:4346-4384`, `:4297-4344`. `cron=-` and the pipeline branch are unchanged |
| I-8 | PASS | `:771-780` (429 → `BUDGET_DEFERRED`, no recovery) and `:827-831` (`OpenAlexBudgetDeferredException` → `BUDGET_DEFERRED`); every retry re-enters `source.searchPapers` (no direct RestTemplate call), and `OpenAlexDataSource`/`OpenAlexRequestPolicy`/`RestTemplateConfig` are outside the 8-file diff. Tests: `:4616-4637` (429 → 1 call, `BUDGET_DEFERRED`, 0 source failures), `:4420-4458` (recovery-time budget deferral revokes the scheduled network revisit), `:4577-4613` (certificate errors never enter recovery); `OpenAlexRequestPolicyTest` 44 green |
| P-1 | PASS | The 8 changed files contain no identity/eligibility/promotion path; name-email verification, discipline admission, RAW→CANDIDATE promotion and enrichment read projections are untouched. `ExpertDiscoveryServiceTest` 160/0/0/0 green |
| P-2 | PASS | Single checkpoint writer `:162-201`; tests `:3850-3895`, `:4868-4896`, `:3917-3944`, `:3966-3982`; separate query keys per `DiscoveryCheckpointCodec` |
| P-3 | PASS | Budget ledger / request policy / TLS validation / redirect policy files are not in the diff; `OpenAlexRequestPolicyTest` 44 green; unknown-reservation and cooldown behavior untouched |
| P-4 | PASS | `tryStartWithToken` retained (`ExpertDiscoveryScheduler.kt:79`) for the scheduler and unchanged controller entries; scheduler tests assert slot loss → no `discover`, no new execution; `cron=-` semantics unchanged |
| P-5 | PASS | Pipeline branch untouched (`:104-113` dispatches `tick()` only), `DiscoveryPipelineService` not in the diff; `DiscoveryPipelineServiceTest` 46 green; persisted PAUSED is not revived by the cron change |
| P-6 | PASS | `:1022` `pendingWork = !exhausted`, `:1161-1167` real stop reasons at the boundary, `DiscoveryResult.kt:20-26` precedence; tests `:4127-4181` (only true exhaustion yields a single `SEARCH_FAILED`), `:4058-4125` (recovery success → `EXHAUSTED`, zero source failures), `:4297-4344` (`GLOBAL_PAPER_LIMIT` + `pendingWork=true`, not "exhausted") |
| O-1 | PASS | Wait message/event `:1199-1211` with Beijing display (`displayZone` `:125`) and ISO-8601 UTC storage; asserted in `:4058-4125`; revisit not earlier than 30 s, other source served first (`:4183-4246`) |
| O-2 | PASS | Two-hour default asserted against `application.yml` plus the annotation, and 04:00-style second runs are not blocked by an earlier same-day `PARTIAL_SUCCESS` (`ExpertDiscoverySchedulerTest` new case) |
| X-1 | PASS | Recovery re-enters the same query key/entry cursor (I-2 evidence), checkpoint delta unchanged |
| X-2 | PASS | Consumer failures (RAW/enqueue/dedup) keep the page entry and never create recovery (`:934-1003`; tests `:4868-4896` asserting `retry == null`) |
| X-3 | PASS | `:294-300`/`:317-340` carry the wait text through details and summary; unchanged `DiscoveryPromotionProgressService.merge` rewrites numbers only; regression `:4460-4520` proves promoted 0→1 while the wait text survives |
| X-4 | PASS | `ExpertDiscoveryScheduler.kt:74-112`: slot, `onStarted` binding, terminal state, `finally` cleanup; exception path still records FAILED |
| X-5 | PASS | Recovery requests keep the `OpenAlexDataSource`/`OpenAlexRequestPolicy` route; two cron rounds share the same ledger; no budget code changed |
| X-6 | PASS | Pipeline branch separate from the synchronous branch; `cron=-` disables only the trigger; `DiscoveryPipelineServiceTest` 46 green |
| Scope / non-goals | PASS | `git diff --name-only f98e27c..8e62268` = 28 paths: exactly the 8 authorized product/test files plus 20 plan/evidence documents; no frontend, migration, request-policy, store or pipeline file touched; repair delta `008f4c4..8e62268` touches exactly `ExpertDiscoveryService.kt` and `ExpertDiscoveryServiceTest.kt` (+18/−5); ARXIV `RAW_WRITE_INCOMPLETE` retry, TLS-client redesign and pipeline activation remain out of scope and were not attempted |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 (P1, I-5 audit retention at `ExpertDiscoveryService.kt:1026-1033`) | RESOLVED | `ExpertDiscoveryService.kt:1034` keeps the known audit object (`?: sourceStats.retry?.copy(nextRetryAt = null)`) while clearing only the plan time; `ExpertDiscoveryServiceTest.kt:4120-4123` (recovery success), `:4176-4179` (all groups exhausted), `:4452-4455` (recovery-time budget deferral) assert retained round/maxRounds/reason with `nextRetryAt == null`; the three previously prohibited `assertNull(retry)` assertions are gone; fresh `mvn test -Dtest=ExpertDiscoveryServiceTest` exit 0, 160/0/0/0 |
| new findings | none | No new P1/P2 in scope was confirmed in epoch 2 |

### Findings

#### P1

- None.

#### P2

- None.

#### Observations

- O-2 (I-6, nonblocking, re-confirmed): `src/test/resources/application.yml:86` still defaults `cron: ${EXPERT_DISCOVERY_CRON:0 0 2 * * ?}`; that profile sets `expert-discovery.enabled=false`, no assertion depends on it, and the file is outside the approved 8-file scope. Runtime default in `src/main/resources/application.yml` is two-hour.
- O-3 (I-5 presentation, nonblocking, re-confirmed): `retryReasonLabel` (`:1206-1211`) renders `TIMEOUT` as 「超时」 and `HTTP_5xx` as 「服务不可用（HTTP_5xx）」; the plan's sample text is explicitly a sample, and round/Beijing time/`先处理其他来源` match it.
- O-4 (T-1 process evidence, nonblocking, re-confirmed): the pre-fix red evidence for the new properties test could only appear as a compile failure; the behavioral red evidence is carried by two recovery assertions (`expected: <4> but was: <3>`, `expected: <12> but was: <3>`). No runtime defect.
- O-5 (I-3 clock seam, nonblocking, re-confirmed): `elapsedMs` (`:1020`) and `sourceStartTime` (`:656`) still read `System.currentTimeMillis()` while deadline/retry use the injected `PolicyTimeSource`; the I-3 test therefore uses a real ~60 ms sleep. Accumulated elapsed-time behavior is proven.
- O-6 (I-5 audit nuance, nonblocking, new in epoch 2): on the terminal-exhaustion path the retained `reason` is the last *scheduled* recovery group's sanitized code (`TIMEOUT` in the covered fixture) rather than the terminal failure's own code. I-5 requires only that round/reason be retained for audit and says the object is observation-only, so this matches the plan wording; the repair handoff already discloses it. Flagged for the human reader, not as a task.
- Informational (I-5 evidence, not a finding): no frontend consumer reads `details.bySource[*].retry` (only `world-clock.js` uses an unrelated `retry` DOM node); operator-visible waiting text travels through `message`/`summaryText`/`stopReason`, which `app.js:2367-2373` and `:2697-2708` already render. This matches I-5's "observation only" clause.

### Evidence Boundaries

- Manual acceptance A-1..A-8 remains PENDING; it is not required before machine verification and was not performed.
- Deployment-time items (T-4 effective-configuration verification, R-0 TLS probe, production rollback plan) are outside this machine boundary by the plan's own wording; nothing about the packaged default was asserted for the live server.
- No runtime observation of a real OpenAlex failure was available or required: failures were exercised through the deterministic source fixtures and the fake clock.
- Non-product worktree delta: `docs/plans/review/2026-09-28-discovery-resilience-two-hour/ledger.md` was already modified by the controller when this review started; this reviewer wrote no file at all.

### Next Action

- PASS → perform the pending human acceptance (A-1..A-8) or finish the branch.

## Repair Planning Result: N/A

Routing: `PASS` + `PROGRESSING` → stop (no `repair-p`). The existing repair artifact `docs/plans/fix/discovery-resilience-two-hour/repair.md` (sha256 `830b2ab77e854dd31c9e18259f0545277afd5e160ac60cc8ac910da574779ade`, findings V-1/R-1, already executed at `8e62268`) was not updated, replaced or re-planned by this review.

## Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 (I-5 "结束" path cleared the whole retry object) | I-5 | Promoted to V-1 in epoch 1; RESOLVED in epoch 2 | `ExpertDiscoveryService.kt:1034`; `ExpertDiscoveryServiceTest.kt:4120-4123`, `:4176-4179`, `:4452-4455` |
| O-2 (test-profile cron still daily) | I-6 | Nonblocking observation | `src/test/resources/application.yml:86`; profile disabled; outside authorized scope |
| O-3 (wait text derived from sanitized code; running summary headline) | I-5 | Nonblocking observation | `ExpertDiscoveryService.kt:1206-1211`, `:317-340`; plan does not mandate a unique wording |
| O-4 (red-evidence form of the new properties test) | T-1/T-4 | Nonblocking observation | historical process evidence; no delivered runtime defect |
| O-5 (`elapsedMs` uses system time while deadline uses the injected clock) | I-3 | Nonblocking observation | `ExpertDiscoveryService.kt:656`, `:1020`; accumulated elapsed behavior proven by `:4248-4295` |

## Verdict Summary

- Machine result: **PASS**
- Convergence: **PROGRESSING** (the single blocking finding V-1 is resolved with no equal-or-higher new finding)
- Repair path/result: **N/A** — routing stopped at PASS; `docs/plans/fix/discovery-resilience-two-hour/repair.md` left byte-identical
- Report destination (persisted by the controller, not by this reviewer): `docs/plans/review/2026-09-28-discovery-resilience-two-hour/machine-verification.md` — Epoch 2

No product code was modified.

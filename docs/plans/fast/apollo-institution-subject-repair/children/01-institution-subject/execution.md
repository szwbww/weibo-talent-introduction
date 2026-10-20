# Child 01-institution-subject execution

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair/docs/plans/2026-10-19/apollo-institution-subject-repair.md`
- Plan SHA-256: `49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397` (unchanged before/after execution)
- Execution ID: `.../docs/plans/2026-10-19/apollo-institution-subject-repair.md@49d5870…2397`
- Execution epoch: NEW (no prior execution evidence named this plan identity; the earlier dispatch failed before any change)
- Executor: fast-p child implementer `InstitutionSubjectImplementer2`, skill `execute-p`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair`
- Target branch: `fast/apollo-institution-subject-repair`
- Worktree ID: `…-fast-apollo-institution-subject-repair@fast/apollo-institution-subject-repair@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-institution-subject-repair`
- Base / pre-execution code SHA: `04ac8fc6553e73d742de8fcc1e129198422e4851` (clean working tree)
- Post-execution code SHA: `1e2ae74981ccb8b6866b135222b2a24540ea09b8` (`feat(fast-p): implement 01-institution-subject`)
- Implementation boundary: `04ac8fc..1e2ae74`, exactly 10 authorized files
- Evidence HEAD: `1e2ae74` (controller commits the fast-p reports separately; no report was part of the implementation commit)

## T-0 执行前复核（receipts）

### 环境与授权

- Worktree clean before execution: `git status --porcelain` empty at `04ac8fc`.
- Predecessor fast-p run `apollo-assist-repair-master` is terminal machine-PASS with children 01/02; its **manual acceptance is PENDING** — recorded as a fact, not used as a start gate.
- Product base `b3b3a71a865337c15c556c21225c0eb9a2300b66`; product tree equals `fcd6719`.

### 重跑计划附件中的 grep（实际输出）

`grep -rn "rules\.evaluate\|DiscoveryVerificationRules(" src/main/kotlin`

```
DiscoveryVerificationRules.kt:32: class DiscoveryVerificationRules(...)
DiscoveryVerificationReplayTool.kt:255: val rules = DiscoveryVerificationRules(clock)
DiscoveryVerificationReplayTool.kt:272: rules.evaluate(snapshot, facts, null, false)
DiscoveryVerificationReplayTool.kt:274: rules.evaluate(snapshot, facts, search, false)
DiscoveryVerificationReplayTool.kt:275: rules.evaluate(snapshot, facts, search, false)
DiscoveryVerificationAdmissionGate.kt:64: rules.evaluate(snapshot, payload.evidence, payload.search)
DiscoveryVerificationQueryService.kt:29: private val rules = DiscoveryVerificationRules()
DiscoveryVerificationQueryService.kt:136: rules.evaluate(snapshot, payload.evidence, payload.search)
DiscoveryVerificationWorker.kt:155/169/206/211/214/223: rules.evaluate(...)
```

`grep -rn "saveEvidenceAndOutcome\|saveProgress\|reschedule(\|saveFailure\|completeAttemptAndProgress\|evidenceJson" src/main/.../discovery` → 39 hits; no new SQL writer added (payload still flows through the existing `saveEvidenceAndOutcome` / `reschedule` / `saveProgress` / `saveFailure` / `completeAttemptAndProgress` paths).

Re-read files: `DiscoveryVerificationWorker.kt`, `DiscoveryVerificationRules.kt`, `DiscoveryVerificationAdmissionGate.kt`, `DiscoveryVerificationQueryService.kt`, `DiscoveryVerificationReplayTool.kt`, `DiscoveryVerificationRepository.kt`, `QualificationEvidenceParser.kt`, `ApolloPeopleSearchClient.kt`.

Confirmed predecessor contracts in current code:

- `identityEmployments` exists and binds ORCID, identity/fact hash, CURRENT + ORGANIZATION/SELF; SELF is identity-only (qualification requires ORGANIZATION facts).
- ORCID public normalisation / cache rebuild exists in `QualificationEvidenceParser.rebuild`.
- Old APOLLO_SEARCH is not reordered: `payload.queries ?: legacySearchQueries(...)`.
- Restricted replay changes only `progress.stage` (`DiscoveryVerificationRepository.replayEvidence`).
- Gate re-evaluates and requires the persisted outcome/qualification/route/personId.

### Replay tool actual signature and frozen format（计划中“工具不存在”的陈述已过期）

Actual object: `com.weibo.talentintroduction.discovery.service.DiscoveryVerificationReplayTool` (291 lines).

- Modes: `freeze | evaluate | apply | inspect` via `scripts/expert_discovery/replay_verification.sh`.
- `freeze(jdbc, manager, executionId, ids, output, clock, classpaths): Path`
- `load(path, expectedManifestHash): Frozen`
- `evaluate(frozen, classpaths): Map<String, List<EvaluationRow>>`
- `apply(frozen, jdbc, manager, clock): List<ReplayReceipt>`
- `inspect(frozen, jdbc, manager, clock): Map<String, Any>`
- DTOs: `Before(schemaVersion, executionId, itemIds, capturedAt, run, items, attempts, admissions)`, `Manifest(schemaVersion, executionId, sortedItemIds, capturedAt, evaluatedAt, beforeSha256, baselineClassHashes, oldInputStatus)`, `EvaluationRow`.
- Directory layout: `<output>/<executionId>/<beforeSha256>/{before.json,manifest.json,freeze-summary.json}` (POSIX 0700/0600).
- Frozen timestamps are epoch-second numbers (e.g. `evaluatedAt: 1.7922816E9`), not ISO strings.
- `Evaluator.evaluateInput` calls the 4-arg `rules.evaluate(snapshot, facts, search, false)`; the new 5th parameter defaults to `null`, so the old/fixed groups keep the pre-institution-subject behaviour. The new group is exercised only in the T-4 RulesTest harness with an explicit in-memory context.

Exact replay command used by the T-4 optional harness:

```bash
mvn -q -Dtest=DiscoveryVerificationRulesTest \
  -DinstitutionReplayInput=<frozen manifest directory> \
  -DinstitutionReplayOutput=<new output directory> test
```

No replay-tool file was modified (not authorized).

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-0 pre-execution review & freeze | IMPLEMENTED | — | greps/re-reads above, plan+worktree identity, clean `04ac8fc` |
| T-1 pure normalizer (resolve/compare, INST_SUBJECT_V1 DTO/builder/validation) | IMPLEMENTED | `DiscoveryInstitutionNormalizer.kt` (new), `institution-subject-cases.json` (new) | 14-case matrix + 7 comparisons; `target/institution-subject-fixtures/*` |
| T-2 Rules/Gate new-vs-old semantics, fail-closed context validation | IMPLEMENTED | `DiscoveryVerificationRules.kt`, `DiscoveryVerificationAdmissionGate.kt` | RulesTest 13 tests; GateTest 6 tests; FlowIT A-4 |
| T-3 Worker query/recovery/read-only consumption | IMPLEMENTED | `DiscoveryVerificationWorker.kt`, `DiscoveryVerificationQueryService.kt` | WorkerTest 8 tests; FlowIT A-2/A-4 |
| T-4 verification & comparison | IMPLEMENTED | Normalizer/Rules/Worker/FlowIT tests + fixture | focused 69 JVM + 1724 JS; FlowIT 27; offline audit; optional replay |
| T-5 release/runtime boundary | NOT APPLICABLE (plan: no deploy/restart/backfill) | — | no deployment performed |

## Commands（fresh, final state `1e2ae74`）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…zulu-11… mvn -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloPeopleSearchClientTest,QualificationEvidenceParserTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS | exit 0; 69 JVM tests, 0 failures/errors/skips; node suite 1724 pass / 0 fail |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=…zulu-11… mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test` | PASS | exit 0; 49 tests = FlowIT 27 + RepositoryIT 12 + ReportingIT 10; 0 skipped |
| Runtime smoke (real MySQL + real worker/clients/Gate/Commit/QueryService): `mvn -q exec:exec -Dexec.executable=…/bin/java -Dexec.classpathScope=test -Dexec.args="-Dapi.version=1.40 -cp %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationFlowIT --subject-smoke"` (with `DOCKER_HOST`) | PASS | exit 0; `RUNTIME_SUBJECT_SMOKE itemId=1 outcome=APOLLO_UNCLEAR qualification=UNKNOWN admissionRevision=0 projection=PROJECTED apolloRequests=2 narrow=Gachon%20University queryInstitution=Gachon University missing=[completedDoctorate, reliableAge] auto=1 candidates=1` |
| Offline inventory audit: `mvn -q -Dtest=DiscoveryInstitutionNormalizerTest -DinstitutionAuditInput=/Users/lukai/IdeaProjects/weibo-talent-introduction/outputs/apollo-institution-audit-20261017/pending-institution-inventory.json -DinstitutionAuditOutput=/tmp/institution-audit-20261020-01 test` | PASS | exit 0; input SHA `d2019b7f6532236e1a07f46dde9f95cc2c28ad7ca1294429f099572556ce1150`; `records=41844 withValue=26134 missing=15710`; statuses `RESOLVED=14846, AMBIGUOUS=5581, UNRESOLVED=21417`; reasons `SUBJECT_EXTRACTED=14846, MULTIPLE_SUBJECTS=5581, UNSUPPORTED_LAYOUT=5707, MISSING=15710`; truncated=0, inputLimit=0, removedParts=50341; `identityImpact=NOT_EVALUATED` |
| Optional frozen replay: `mvn -q -Dtest=DiscoveryVerificationRulesTest -DinstitutionReplayInput=<FlowIT frozen dir> -DinstitutionReplayOutput=/tmp/institution-replay-20261020-03 test` | PASS | exit 0; `denominator=1 negativeChanges=0`; old group null context, fixed group explicit in-memory context; deterministic twice |

Notes:

- The real predecessor frozen cohort is not present in this worktree; the replay harness was exercised against the FlowIT-generated synthetic frozen directory (`…/verification-replay-*/frozen/2001/e8697f…/`) and is reported as such. A real-cohort A-6 comparison remains **UNAVAILABLE** (not fabricated).
- Fixed artifact hashes after the final build: `DiscoveryInstitutionNormalizer.class` `7096fe30…a51f35`, `DiscoveryVerificationRules.class` `3001b83e…708fea`, `DiscoveryVerificationWorker.class` `2f1b05d3…739163`; fixture SHA-256 `c0145d6f460d1689c406deaa8ef740118fc055fdc344104c6e1391fda4718cac`.

## T-1 / FlowIT artifacts

- `target/institution-subject-fixtures/summary.json`: 14 cases, `RESOLVED=7, AMBIGUOUS=4, UNRESOLVED=3`, `identityImpact=NOT_EVALUATED`.
- `target/institution-subject-fixtures/cases.jsonl`: per-case raw (emails masked), subjects, reason, removed parts and rule ids.
- `target/institution-subject-flow/`: `a2-gachon.json`, `a3-{uc-campus,hospital-vs-university,same-name-two,raw-only,historical-only,orcid-conflict,incomplete-search}.json`, `a4-{gate,legacy-inflight,retry,completion-failure}.json`. Each contains the isolated input, actual Apollo URIs/narrow request JSON, persisted outcome/reasons/admission revision, and the derived normalization context; A-2 additionally contains Gate status and QueryService `missingFields`.

## Invariant coverage

- **I-1**: original RAW/profile/ORCID/snapshot values are never rewritten (FlowIT A-2 asserts the long RAW and snapshot stay byte-equal; the normalizer only reads). Derived subjects never enter `factFingerprint` (Gate `snapshot()` unchanged).
- **I-2**: T-1 matrix passes for Gachon, glued MIT (UNRESOLVED), UC campuses, Rutgers, Leidos, Renji, Seoul dedupe, CNRS joint, Paris-Sud, e-mail fragment, Example/Technical, ROR conflict, ETH, ACHISINA/3M; removed parts carry rule ids.
- **I-3**: `queryInstitution` requires every eligible CURRENT employment to be RESOLVED with one distinct subject; ambiguity yields exactly one name query (WorkerTest + FlowIT A-3 narrow counts 0/1).
- **I-4**: absent member keeps old semantics (FlowIT A-4 legacy replay through the Gate); old APOLLO_SEARCH resumes byte-identical query/page (FlowIT A-4 legacy in-flight); unknown version / rewritten subject are rejected, never downgraded.
- **I-5**: Rules/Gate rebuild the context from `snapshot + identityEmployments + search` and require exact equality (version, source, sourceRef, raw, sha, status, subjects, reason, removedParts, queryInstitution). QueryService passes the same context and tolerates a damaged single record without a 500.
- **I-6**: 429 retry keeps the same subject/page and accumulates `requestCount` (FlowIT A-4 retry); completion save failure stops the chain and preserves the derived payload (FlowIT A-4 completion-failure); sample tests unchanged and pass.
- **I-7**: full inventory report matches the plan's denominator and input SHA; extraction distribution is reported with `identityImpact=NOT_EVALUATED`; no success-rate claim is made.

## Deviations

- None in product scope. Operational limits recorded above: real-cohort frozen replay is UNAVAILABLE; the synthetic FlowIT frozen directory was used only to exercise the T-4 harness and is labelled as synthetic.

## Remaining

- Independent `verify-p` (machine verification) and the human acceptance list A-1..A-6. No deployment, push, merge or history rewrite was performed.

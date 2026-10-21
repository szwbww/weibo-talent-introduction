# Child 04 — Execution log

## Execution Result: BLOCKED

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master/docs/plans/2026-10-21/apollo-repair-04-identity-normalization.md`
Plan SHA-256: `1b1fb2670794126f2a7842988319d7b9796a244a4bd74c00e23a6cb111896b2b`
Execution ID: canonical plan path above @ that SHA-256.
Execution epoch: NEW. Approval basis: current child04 invocation, no amendments.
Executor: ApolloRetention04; execute-p, with fix-v mechanical test-harness repairs bounded to three attempts.
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master`
Branch: `fast/apollo-repair-retention-master`
Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-repair-retention-master`
Worktree ID: target root @ branch @ Git directory above.
Pre-execution code SHA / child_base_sha: `8024e30cc57c4188d829761f59444af3061edf51`
Pre-execution HEAD: `ce6985bdc682532b42b6549cee9f3245a43edf89` (child03 evidence/checkpoint commits follow code baseline).
Post-execution code SHA: `043c843774fc3c4388d01d2a3eb5114b549bb92d`
Product commit: `feat(fast-p): implement 04` — nine authorized files, 257 insertions, 20 deletions; fast-p reports excluded.
Evidence HEAD: no evidence commit by executor; controller owns evidence commits.
Implementation boundary: `8024e30cc57c4188d829761f59444af3061edf51..043c843774fc3c4388d01d2a3eb5114b549bb92d`.

### Task status and implementation evidence

| Requirement | Status | Evidence |
|---|---|---|
| I-1 frozen versions | IMPLEMENTED, runtime matrix partly blocked | Registration freezes ruleVersion=2 and aliasVersion=2. Worker initializes only absent evidence from supported integral run ruleVersion, defaults missing old setting to 1, retains existing payload ruleVersion; Rules accepts 0..2 and rejects unknown versions. Frozen-rule runtime matrix is present but not reached after failing smoke assertion. |
| I-2 sourced aliases | IMPLEMENTED | Exact versioned TUM/Geneva entries retain official URLs. Explicit IDs cannot be overridden. Rules obtains actual alias reason rather than assigning TUM universally. Normalizer regression tests passed. |
| I-3 title/headline | IMPLEMENTED | Only rule2 exact Assistenzprofessor plus Assistant Professor prefix and no negative qualifiers adds professor route. Original candidate title untouched. Version propagates through rule support/masked/alias checks and Worker surname/detail filters; emailNameMatches still only accepts institution routes. Rules negative and minimal sample tests passed. |
| I-4 Gate/Query/Commit | BLOCKED end-to-end | Rules sample improves the same synthetic person with alias only, title only, or combined, while qualification remains UNKNOWN and missing degree/age remain. Gate v2 versus v1 and Query detail tests pass. Real MySQL smoke reaches APOLLO_CLEAR but registration fixture accidentally creates AUDIT run; expected PROJECTED assertion fails before later Gate/Query checks. No bypass or weakening applied. |

### Actual changed product files

All paths are relative to this target worktree:

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizer.kt` — versioned, sourced aliases and actual alias reason.
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` — rule2, precise reason attribution, conditional title/headline route.
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — freeze new-run rule2.
4. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — consume frozen run version and propagate to prefilters.
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryInstitutionNormalizerTest.kt` — Geneva, TUM, approximate names, ID conflicts, unknown alias version.
6. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` — fixed-date minimal deidentified Nicolas shape, separate/combined effects, ambiguity/ORCID/protected/negative-headline cases.
7. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — MySQL runtime smoke and frozen legacy/version matrix; smoke currently failing.
8. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt` — persisted v2 versus old-rule gate recomputation.
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` — v2 read-only detail recomputation and preserved title.

Pre-existing controller change `docs/plans/fast/apollo-repair-retention-master/ledger.md` preserved and not staged. This execution report is the sole executor workflow artifact.

### Commands and evidence

Environment for every Maven invocation:

```sh
export JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock
export JAVA_TOOL_OPTIONS=-Dapi.version=1.44
mvn -DfailIfNoTests=true -Dsurefire.failIfNoSpecifiedTests=true -Dtest=DiscoveryInstitutionNormalizerTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,ApolloMatchingV2Test test
```

| Command / attempt | Exit / result | Evidence |
|---|---|---|
| initial `python ...plan_identity.py` | 127 | `python` executable unavailable; retried with python3. |
| `python3 .../plan_identity.py <plan>` and worktree helper | 0 | Canonical path/hash matched approved brief; HEAD ce6985b and target branch/root/git-dir bound. |
| `java -version && docker version` in required environment | 0 | Zulu OpenJDK 11.0.32.1; Docker client/server 29.8.2, engine minimum API 1.40. |
| required Maven command initial | 1 | 70 tests, 0 failures, 2 errors, 0 skipped; worker registration used disabled properties, Query synthetic APOLLO result omitted required matched timestamp. Full log `artifact://122`. |
| mechanical attempt 1 + full required Maven command | 1 | Query matched timestamp fixed; registration changed to invalid properties.copy; test compilation failed unresolved copy. |
| mechanical attempt 2 + full required Maven command | 1 | Replaced copy with actual constructor. 70 tests, 0 failures, 1 error, 0 skipped: fixture seed tried recreating immutable run with different settings. Full log `artifact://126`. |
| mechanical attempt 3 + full required Maven command | 1 | Fixture now reserves/activates item in the registered run. 70 tests, 1 failure, 0 errors, 0 skipped. Runtime smoke expected PROJECTED but saw NOT_REQUIRED. Full log `artifact://128`; total 04:16, completed 2026-10-21 14:14:25 +08:00. |
| `git diff --check` after final implementation | 0 | No whitespace errors. |
| plan/worktree identity recheck before staging and again before commit | 0 | Same approved hash/root/branch/git-dir. |
| explicit nine-path git add; `git commit -m 'feat(fast-p): implement 04'` | 0 | Commit 043c843774fc3c4388d01d2a3eb5114b549bb92d. |
| `git rev-parse HEAD && git merge-base --is-ancestor HEAD fast/apollo-repair-retention-master` | 0 | Product commit is target HEAD and branch-reachable. |

Fresh final Surefire report evidence, not historical results:

| Suite | Tests | Failures | Errors | Skipped | XML mtime epoch seconds |
|---|---:|---:|---:|---:|---:|
| DiscoveryInstitutionNormalizerTest | 9 | 0 | 0 | 0 | 1792563263.9626122 |
| DiscoveryVerificationRulesTest | 18 | 0 | 0 | 0 | 1792563263.9091544 |
| DiscoveryVerificationWorkerTest | 15 | 1 | 0 | 0 | 1792563263.8546422 |
| DiscoveryVerificationAdmissionGateTest | 8 | 0 | 0 | 0 | 1792563264.8036973 |
| DiscoveryVerificationQueryServiceTest | 11 | 0 | 0 | 0 | 1792563180.4131553 |
| ApolloMatchingV2Test | 9 | 0 | 0 | 0 | 1792563179.7876692 |

These are the current `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.<Suite>.xml` files, read after final command. Required overall result remains FAIL; no skip represented as PASS. No mid-flight tests were run before initial implementation completion. No separate package/full-suite command was required or run.

### Changed-path smoke and limits

The added runtime test uses a disposable MySQL 8.0.36 container, existing Flyway target158, real Registration.begin, real Worker, real Repository SQL/transaction proxies, existing HTTP mock server, normal Commit services and mock ES. Docker availability is checked explicitly, never skipped.

Observed final smoke: new registered run has ruleVersion=2 / aliasVersion=2; worker persists APOLLO_CLEAR. Next assertion fails: expected PROJECTED, actual NOT_REQUIRED (`DiscoveryVerificationWorkerTest.kt:54`). No successful smoke marker was printed. Subsequent saved-payload/Gate/Query/admission assertions and the old-run/frozen-version loop were not reached; they are not claimed passed.

Confirmed fixture root cause: registration was constructed with `DiscoveryVerificationProperties(enabled=true, apolloApiKey="fixture-key")`, whose mode defaults to AUDIT (`DiscoveryVerificationProperties.kt:16`), while this smoke expects ENFORCE projection. The repository correctly retains that immutable run. This is a test constructor wiring issue, not evidence that Commit should project AUDIT runs. Three mechanical repair attempts have been consumed; no fourth repair or weakened assertion is authorized in this pass. A resumed bounded execution should set explicit `mode=VerificationMode.ENFORCE` on that test registration constructor and rerun the exact required command, then inspect any further evidence. Product behavior must not be changed to satisfy the fixture.

The fixed-date deidentified Rules sample carries the archived Geneva/history/title/headline shape, not a fresh server-side replay of all frozen production records. Separate alias-only, title-only, combined outcomes refer to the same person and cannot count as three gains. E-4's historical two RND_OUT_OF_SCOPE and one IDENTITY_CHANGED discrepancies remain protected baseline differences, not new gains; no production re-evaluation/Commit was attempted here. Actual production admission gain, HTTP savings, and full archive replay are unverified.

### Downstream interfaces / unchanged boundaries

- New run settings include ruleVersion=2; missing settings on old runs retain initial-payload fallback1, missing payload ruleVersion remains DTO default0.
- Existing evidence JSON remains authoritative for persisted rule/search alias versions. Unknown rule versions fail closed. `matchReason` gains optional final ruleVersion default0; existing callers outside the frozen Worker paths retain old behavior.
- Alias reasons distinguish Geneva V2 from TUM V1; no schema, ES mapping, new JSON diagnostic root, external institution service, translation or fuzzy matching.
- No Gate, Query, Commit, admission, production SQL, production ES, historical evidence JSON, sample store, or protected-write code changed.
- Historical SELF/name protections and APOLLO_UNCLEAR behavior remain under unchanged branches and existing regressions; successful listed suites are evidence only, not independent verification.
- Existing `DiscoveryVerificationIntegrationTest` (outside whitelist and not in child04 required command) contains assertions expecting new payload ruleVersion=1. Not run or edited here; controller should account for this test-contract issue before broader regression. No authorization expansion attempted.
- A-1/A-2/A-3 human acceptance: PENDING. No deployment, production reads/writes, history rewrite, later-child review, or plan edits.

### Freshness and next action

Plan identity unchanged: YES. Worktree identity unchanged: YES. Product SHA target-branch reachable: YES. Required command freshly run: YES (FAIL). Reports/evidence excluded from product commit: YES. Product index clean after explicit commit; controller ledger/report remain uncommitted workflow artifacts. Historical evidence used only as baseline: YES.

Remaining blocker: failing smoke fixture mode plus exhausted three-attempt mechanical bound; end-to-end and legacy matrix completion cannot be asserted. Controller must authorize/resume bounded mechanical work before independent verification. Status is BLOCKED, not READY_FOR_VERIFICATION and not a PASS.

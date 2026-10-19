# Child 01 execution

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master/docs/plans/2026-10-19/apollo-assist-repair-01-evidence-matching.md
Plan SHA-256: 3d0393cf9518ce0954934b4f86f08311ef9e9b765a386244b9d4ad9e9c36ae68
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master/docs/plans/2026-10-19/apollo-assist-repair-01-evidence-matching.md@3d0393cf9518ce0954934b4f86f08311ef9e9b765a386244b9d4ad9e9c36ae68
Execution epoch: RESUME
Approval basis: Current explicit child-01 resume assignment, complete child brief, unchanged approved child/master plans. This completes implementation; fix_round remains 0.
Executor: ApolloRepair01Resume, continuing ApolloRepair01Implementer.
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master
Target branch: fast/apollo-assist-repair-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master@fast/apollo-assist-repair-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-assist-repair-master
Pre-execution code SHA: 3e8beb8841f8c2b42048667874ce61253da5fe5c
Post-execution code SHA: 41e95f05754cff6e7239abc04e515bdd386f3f80
Evidence HEAD: N/A — controller evidence remains outside the implementation commit.
Implementation boundary: 3e8beb8841f8c2b42048667874ce61253da5fe5c..41e95f05754cff6e7239abc04e515bdd386f3f80
Commit: `feat(fast-p): implement 01`

### Resume reconciliation

- Read the entire current child plan and master authority/invariants, baseline, prior executor history, actual authorized source/test changes, and historical Surefire output before running commands.
- The inherited changes covered T-1 through T-4 inside the approved whitelist. They were retained rather than discarded or blindly replayed; no further product edits were necessary in this resume invocation.
- Historical FlowIT output contained two fixture deserialization errors for `ExpertProfile.displayName`; the inherited fixture mapper already used the production-compatible unknown-property setting. The fresh integration command below exercises the reconciled state and reports no errors.
- Controller-owned `docs/plans/fast/apollo-assist-repair-master/ledger.md` was already modified on entry. It was not edited or staged by this executor. The implementation commit contains only the authorized files; this report is intentionally unstaged.
- Baseline evidence is `../../baseline.md`: focused baseline 43 passing tests and corrected Docker/MySQL baseline 30 passing tests. The reported bare-Docker API/socket failure was not rerun to confirm it. Historical commands are not substituted for fresh execution evidence.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 / I-1 / I-4 | IMPLEMENTED | QualificationEvidenceParser.kt, QualificationEvidenceParserTest.kt, DiscoveryVerificationWorker.kt, DiscoveryVerificationFlowIT.kt | Shared case-insensitive textual PUBLIC check rejects missing/null/nontext/private/limited visibility. Endpoint-specific rebuild preserves uncached/other-provider facts, rejects identity mismatch, keeps distinct endpoint provenance, and is idempotent. LOCAL_EVIDENCE and DECIDE recover cached evidence without ORCID HTTP; APOLLO_SEARCH does not rebuild. Fresh parser and FlowIT assertions pass. |
| T-2 / I-2 / I-3 / I-5 | IMPLEMENTED | DiscoveryVerificationRules.kt, DiscoveryVerificationRulesTest.kt, DiscoveryVerificationWorker.kt, DiscoveryVerificationWorkerTest.kt, DiscoveryVerificationFlowIT.kt | Shared identityEmployments enforces provider, identity/fingerprint, bound ORCID, CURRENT scope, source and institution. Qualification remains ORGANIZATION-only. SELF Professor/doctorate cannot make qualification MET. Queries preserve normalized full-name/alias order, add NARROW only for one distinct eligible institution, persist the new sequence and retain the original legacy in-flight sequence. Negative identity/institution/ORCID and ambiguity regressions pass. |
| T-3 / I-5 / I-6 | IMPLEMENTED | DiscoveryVerificationWorker.kt, DiscoveryVerificationQueryService.kt, DiscoveryVerificationQueryServiceTest.kt, DiscoveryVerificationFlowIT.kt | Page cap and item request cap persist distinct supplemental reason codes without new state/DTO/schema. Exact Chinese labels and legacy generic label are asserted. Incomplete cases remain PENDING and unadmitted; completed queries do not get truncation labels. |
| T-4 / I-1 through I-7 | IMPLEMENTED | DiscoveryVerificationFlowIT.kt and authorized unit tests | Fresh real Docker/MySQL tests cover HTTP JSON parsing, cached LOCAL/DECIDE recovery, frozen queryIndex/page, no provider replay, shared Gate re-evaluation, transactional Commit/admission revision, candidate projection, protected families, and read-only GET revision/attempt stability. Existing Gate, Repository, Reporting and sample tests also pass. Standalone forked runtime proof below succeeds. |
| I-7 / scope | IMPLEMENTED | Approved source/test whitelist only | No changes to Gate, Commit, schema, policyVersion, sample configuration/Capture/Store, frontend, or later-child implementation. No production action, live apply, push, merge or history rewrite. |

### Commands

All commands ran from the exact target worktree. Maven commands explicitly selected JDK 11. The required test groups ran freshly in this invocation after the final source/test state; no product changes followed them.

| Command | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-19/apollo-assist-repair-01-evidence-matching.md` | PASS, exit 0 | Initial and handoff canonical path/hash equal the identity above; runtime artifacts `artifact://208`, `artifact://223`. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-19/apollo-assist-repair-01-evidence-matching.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master --expect-branch fast/apollo-assist-repair-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-assist-repair-master` | PASS, exit 0 | Rechecked before staging, before commit and before handoff; `artifact://221`, `artifact://222`, `artifact://223`. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QualificationEvidenceParserTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS, exit 0 | 49 JVM tests, 0 failures/errors/skips; 1,724 JS tests, 0 failures/skips; both bound JS syntax checks ran; BUILD SUCCESS. `artifact://213`. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test` | PASS, exit 0 | 36 JVM tests (18 FlowIT, 8 RepositoryIT, 10 ReportingIT), 0 failures/errors/skips, actual isolated MySQL 8.0.36 containers; 1,724 JS tests, 0 failures/skips; both bound JS syntax checks ran; BUILD SUCCESS. `artifact://215`. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dexec.mainClass=com.weibo.talentintroduction.discovery.service.DiscoveryVerificationFlowIT -Dexec.classpathScope=test exec:java` | FAIL, exit 1 — launcher lifecycle | Runtime assertions and RUNTIME_SMOKE succeeded, but in-process exec:java interrupted the Testcontainers Ryuk thread and left JDBC cleanup threads during Maven classloader teardown. No source fix or disabled cleanup was used; the forked command below resolves the launcher isolation issue. `artifact://218`. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java '-Dexec.args=-Dapi.version=1.40 -classpath %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationFlowIT' -Dexec.classpathScope=test exec:exec` | PASS, exit 0 | Real standalone JVM/Worker/MySQL smoke; BUILD SUCCESS; `artifact://220`. Exact result below. |
| `git diff --check` | PASS, exit 0 | Final state and handoff checks; `artifact://217`, `artifact://223`. |
| `git commit -m 'feat(fast-p): implement 01'` | PASS, exit 0 | Commit 41e95f05754cff6e7239abc04e515bdd386f3f80; exact authorized file list shown before commit; 9 files, 388 insertions, 21 deletions. `artifact://222`. |
| `git merge-base --is-ancestor 41e95f05754cff6e7239abc04e515bdd386f3f80 fast/apollo-assist-repair-master` | PASS, exit 0 | Target HEAD and branch reachability checked after commit and again at handoff. `artifact://223`. |

### Standalone runtime smoke

The executable existing FlowIT `main` ran outside JUnit in its own JVM against a fresh Docker MySQL `verification_flow` database, migrated with the unchanged repository migrations. Provider responses and ES transport use the existing isolated HTTP fixture; parsing, Worker, SQL reservations/cursors, Gate, policy, transactional Commit, and projection writer are real production implementations. This is not a claim of live Apollo or production Elasticsearch access.

```text
RUNTIME_SMOKE runId=2001 itemId=1 outcome=APOLLO_UNCLEAR qualification=UNKNOWN admissionRevision=0 auto=1 projection=PROJECTED apolloRequests=2 orcidRequests=0 no-provider-replay=true candidates=1 evidenceSource=SELF
```

The fixture starts with three saved ORCID endpoint documents and empty evidence, reconstructs the lowercase-public SELF current employment, commits UNKNOWN/APOLLO_UNCLEAR admission, intentionally produces one projection HTTP 500, then reconstructs the Worker and projects successfully without provider replay. The observed admission revision is non-null (0); the smoke verifies the retained SELF evidence, zero ORCID requests, one durable admission and one projected candidate. The intentional HTTP 500 stack trace is part of the recovery scenario, not a failed smoke assertion.

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/QualificationEvidenceParser.kt` — unified strict visibility and endpoint-specific pure rebuild.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` — identity employment helper, separate from qualification.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — legal cache recovery, frozen query sequence, unambiguous institution queries, precise limit reasons.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — exact reason labels.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/QualificationEvidenceParserTest.kt` — visibility/provenance/rebuild/idempotence/mismatch coverage.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` — SELF identity versus qualification and rejection coverage.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — normalized aliases and unique institution query coverage.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` — real persistent chain, protection/cache/cursor/query/limit/read-only regressions and standalone smoke.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` — reason labels, multi-reason counts and no-write coverage.

File-count receipt: `git diff --cached --name-only` immediately before the product commit printed precisely the nine paths above (`artifact://222`). The initial broader read/write seam audit used grep pattern `discovery_verification_(run|item|attempt)|expert_discovery_admission|rules.evaluate|recordAutomaticPersisted|projectDiscoveryCandidate` over the discovery subtree and existing ExpertIndexWriterService; output confirmed the unchanged shared Gate/Commit/projection consumption path. Sampling/budget grep over DiscoverySampleProperties and DiscoveryVerificationRepository confirmed unchanged retention values and persistent ITEM/MINUTE/DAY budget seams.

### Deviations

- No implementation scope or behavioral deviation.
- Runtime-only Docker transport settings follow the documented baseline (`DOCKER_HOST` OrbStack socket and `-Dapi.version=1.40`); no repository configuration changed.
- The unsuccessful in-process smoke launcher was replaced with a forked JVM launcher based on its observed teardown failure. All required commands passed without skipping tests.
- Live operational/manual acceptance (production samples.enabled, online screenshots, external new runs, deployment, historical cohort replay/apply) was not performed and is not claimed. Those actions are outside child-01 execution authority; the approved isolated runtime/test surfaces were exercised.

### Freshness

- Plan identity rechecked: YES.
- Worktree identity rechecked: YES.
- Reported commits reachable from target branch: YES; product commit is target HEAD.
- Required commands run this invocation: YES, both groups after final source/test state.
- Historical evidence used only as baseline: YES.
- Index/product working tree: clean after product commit. Existing controller ledger change was preserved; this appended execution artifact is intentionally excluded from the implementation commit.
- No verify-p, review-p or repair-p was invoked.

### Remaining Blocker

- None for independent verification.

### Next Action

- Controller dispatches the mandated independent four-gate light verifier for child 01. READY_FOR_VERIFICATION is execution handoff, not an independent PASS.

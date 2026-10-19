# Apollo Verification Master — Repair Execution

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/fix/apollo-verification-master/repair.md
Plan SHA-256: 4ac3f47a2b54e03621a54f31c11909ba8f63658277b0824f63575f90b346e07b
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/fix/apollo-verification-master/repair.md@4ac3f47a2b54e03621a54f31c11909ba8f63658277b0824f63575f90b346e07b
Execution epoch: NEW
Approval basis: Human-originated `$execute-p docs/plans/fix/apollo-verification-master/repair.md` in this invocation; the exact plan's Review-Fast-P Execution Handoff lines68–76 explicitly authorize these bytes, the two product files, one prescribed product commit, this execution report and one prescribed docs-only evidence commit. The DRAFT label was not silently changed or treated as separate authority.
Executor: top-level omp coding assistant; no delegated executor/session identifier exposed for this invocation.
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
Target branch: fast/apollo-verification-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master@fast/apollo-verification-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master
Pre-execution code SHA: e473ffc714b840a2f42c9ca826da7c37e36ab9d7
Pre-execution HEAD: 095f2a820edd03657cbb3f79ef6fcc678cfcdd3d (aggregate review evidence commit, not a product change)
Post-execution code SHA: bffa8ff4a35fd7b066ed522dcbd41efb245a2376
Evidence HEAD: the docs-only commit containing this report; self-reference resolved by `git log -1 --format=%H -- docs/plans/review/apollo-verification-master/repair-execution.md` and emitted concretely after commit in the final execution response. It is not the product SHA.
Implementation boundary: 095f2a820edd03657cbb3f79ef6fcc678cfcdd3d..bffa8ff4a35fd7b066ed522dcbd41efb245a2376

### Task Status

| Requirement | Status | Files | Execution evidence |
|---|---|---|---|
| R-1 / V-1 | IMPLEMENTED | DiscoveryVerificationCommitService.kt; DiscoveryVerificationFlowIT.kt | Shared commit-time contacted() reads only textual externalIds.orcid and uses existing normalizeOrcid checksum/URL handling. SQL e-mail/alias checks unchanged. Isolated regression covers contact inserted after registration and after prepare, for bare and HTTPS external ORCID. All contexts return PROTECTED/CONTACT_PROTECTED, no admission revision or candidate, unchanged contact/admission/RAW/fingerprint. Standalone actual worker path also exercised. |

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt` — replace ES document-key contact lookup parameter with validated persisted external ORCID, retaining the SQL e-mail/alias branches. Missing/malformed JSON/nontextual/checksum-invalid values cannot become a matching external identity. No primary-ID convention, snapshot, rules, CAS, provider, contact/mail write or schema change.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` — consumer-visible late-contact regression against disposable MySQL and existing mock-HTTP fixture; uses actual RAW orcidId=docId/externalIds object, real transaction proxy and unchanged fingerprint. Contact e-mail deliberately differs from expert e-mail; there is no alias, so the successful protection is ORCID-only. Existing fixture/qualification/projection behavior is not replaced or bypassed.
- `docs/plans/review/apollo-verification-master/repair-execution.md` — explicitly authorized execution/evidence handoff only; not a third product file.

### Fresh Commands

All repository commands used the exact bound worktree. JDK11 and isolated Docker socket/API were explicit for Maven. Test command rows1–3 ran after the final implementation state; row0 is the deliberately failing newly added regression before the production edit, not a historical baseline substitute.

| # | Exact command | Result | Receipt |
|---|---|---|---|
| 0 | `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationCommitServiceTest` | Expected FAIL exit1 before production edit:16 run,1 assertion failure,0 errors/skips; new regression expected PROTECTED but observed PENDING at false/bare-ORCID. This proves the missing protection, not that an unprotected candidate was successfully created in this fixture. | artifact://650; complete initial command artifact://649; bg_111 |
| 1 | `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationCommitServiceTest` | PASS exit0:16 run,0 failures/errors/skips; FlowIT12 plus CommitServiceTest4. Finished2026-10-19T10:57:28+08:00. | artifact://657; bg_112 |
| 2 | `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationContractTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationFlowIT,DiscoveryVerificationIntegrationTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationControllerTest,DiscoveryVerificationReportingIT` | PASS exit0:76 run,0 failures/errors/skips. Actual disposable MySQL executed; no optional skip counted as pass. Finished2026-10-19T11:02:38+08:00. | artifact://661; bg_113 |
| 3 | `node --test src/test/js/*.test.js` | FAIL exit1:1725 tests,324 suites,1685 pass,40 fail,0 skipped/cancelled/todo. Known RECORD_ONLY recipient-preview sandbox failures include resetRecipientPreview/setRecipientPreviewState not defined. Counts and failure category match recorded O07-01; no frontend files changed, no baseline rerun just to reconfirm and no unrelated repair. This is not globally green. | artifact://653:2381-2388; failure details2390 onward |
| 4 | `git diff --check && git diff --stat && git diff -- src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` | PASS exit0; exactly the authorized two product/test files,55 insertions and1 deletion; no whitespace errors. One final execution self-check, not an aggregate code review. | artifact://660 |

Expanded backend result breakdown from `grep 'Tests run:' artifact://661`: reportingIT10, repositoryIT8, controller4, flowIT12, integration10, query6, commit4, worker4, rules7, gate6, contract5; each0 failures/errors/skips. Reporting123/10000-row EXPLAIN receipts used idx_dvi_run_batch. Existing fixture-injected ES500 warnings and invalid-config rejection warnings were expected test paths; final Surefire result is76/0/0/0.

### Standalone Runtime Smoke

Actual non-JUnit JVM driver created outside checkout at `/tmp/ApolloLateOrcidRepairSmoke.java`, launched against the fresh Surefire127-entry java.class.path using the following actual eval subprocess invocation:

```python
repair_smoke_cmd = [
    '/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java',
    '-Dapi.version=1.44', '--class-path', repair_cp,
    '/tmp/ApolloLateOrcidRepairSmoke.java'
]
repair_smoke = subprocess.run(repair_smoke_cmd, cwd=repair_wt, env=repair_env,
                             text=True, capture_output=True, timeout=180)
```

`repair_cp` was parsed from property `java.class.path` in `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryVerificationFlowIT.xml`; repair_env set the same JDK11/DOCKER_HOST as above. Driver used existing fixture startup/transaction proxies/RAW reader/rules/worker, not a JUnit test method. It reserved/activated canonical RAW, observed initial protection absent, inserted an unrelated-email contact by external HTTPS ORCID after registration, then ran real worker.tick() with qualifying Professor HTTP fixture and closed collection input. Separate invalid external-identity cases reused prepare reads against the existing contact. Exit0, emitted:

```text
RUNTIME_SMOKE lateExternalOrcid=PROTECTED reason=CONTACT_PROTECTED automaticAdmissions=0 candidates=0 contactUnchanged=true fingerprintUnchanged=true rawPrimaryIsDocId=true orcidRequests=3 apolloRequests=0
RUNTIME_SMOKE missingMalformedChecksumInvalidExternalIdentities=NO_CONTACT_MATCH
```

Missing externalIds, missing orcid, numeric orcid and checksum-invalid text were exercised without a false contact match. The runtime used actual isolated MySQL plus existing mock HTTP, not real provider credentials or deployment. Fixture stop() ran in finally; the owned throwaway Java launcher was removed after success. No runtime scaffold was added to checkout.

### Commit and Clean-State Evidence

- Entry `git status --short` returned no output; reachable history showed aggregate evidence095f2a8 above final codee473ffc7. No prior execution report for this exact repair identity existed in the review artifact directory, so this is NEW, not a reused completion claim.
- Immediately before both `git add` and `git commit`, worktree_identity.py was rerun with exact --expect-root, --expect-branch and --expect-git-dir. Plan identity was rechecked unchanged before product commit.
- Exactly one product commit: `bffa8ff4a35fd7b066ed522dcbd41efb245a2376`, subject `fix(apollo): preserve late ORCID contact protection`, only the two Authorized Files. Post-commit worktree helper recorded this commit as HEAD of the target branch; `git merge-base --is-ancestor HEAD refs/heads/fast/apollo-verification-master` exited0; `git status --porcelain=v1` returned no output (artifact://662).
- Exactly one authorized docs-only evidence commit contains only this report, with subject `docs(review-fast-p): record repair execution`. Final response reports its resolved SHA and post-commit plan/worktree/reachability/clean-state checks, avoiding a self-referential SHA inside this commit.
- Approved repair plan and fast-p/review ledgers were not edited; no stage/commit of unrelated paths, amend, history rewrite, push, merge or deployment.

### Deviations

- None. Full Node is explicitly retained as FAIL/RECORD_ONLY under the plan's Findings Excluded and Completion Criteria, not suppressed or reclassified as passing.
- Additional standalone smoke and scope/whitespace/identity diagnostics collect execution evidence without widening product scope. No independent verification verdict is claimed.

### Freshness

- Plan identity rechecked: YES — canonical path/SHA-256 above unchanged before product commit; final pre-evidence and post-evidence checks emitted in execution response.
- Worktree identity rechecked: YES — exact root/branch/git-dir pinned before staging/committing; final clean-state recheck emitted after evidence commit.
- Product commit reachable from target branch: YES; evidence commit checked after creation, concrete result in final response.
- Required commands run this invocation: YES — both approved backend matrices and full Node freshly executed after final edit.
- Historical evidence used only as baseline: YES — known excluded debt did not satisfy current command requirements.

### Remaining Blocker

None for authorized execution. Prior aggregate machine FAIL remains historical authority until fresh independent verification; this executor does not declare V-1 independently closed or whole-system PASS.

### Next Action

READY_FOR_VERIFICATION → run `verify-p` for independent repair verification. An aggregate review controller may consume this exact repair boundary/report when invoked to resume `review-fast-p`; this execute-p invocation alone does not silently perform or approve that re-review.

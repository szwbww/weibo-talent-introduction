# Child 02 Execution Log

## Execution Result: PLAN_CONFLICT

Date: 2026-11-01
Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-02-identity.md`
Plan SHA-256: `3bc964760b6cbb1fb290809821569381e676a4f2b88d62121ea0d59a2dd35e48`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-02-identity.md@3bc964760b6cbb1fb290809821569381e676a4f2b88d62121ea0d59a2dd35e48`
Execution epoch: NEW
Approval basis: approved child brief and current delegated invocation.
Executor: `PaperIdentityImplementer02`
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`
Target branch: `fast/paper-reprocess-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master@fast/paper-reprocess-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`
Pre-execution code SHA: `1979768f08ffe58ca3c919326bb0eebbc833e3a1`
Pre-execution HEAD: `3065dae4f67eb075ede4395e5586ee0311731b46`
Post-execution code SHA: N/A — no implementation commit.
Evidence HEAD: N/A — report left as orchestration artifact.
Implementation boundary: no product/test edits; existing brief/ledger modifications preserved.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Plan/worktree identity | IMPLEMENTED | Read-only checks | Approved hash matches; exact root, branch, git directory and common directory recorded; child code base reachable from target branch |
| I-4 / implementation step 5: controlled changed identity through existing persisted admission seam | CONFLICT | `DiscoveryVerificationCommitService.kt`; required dependency `DiscoveryReviewService.kt` is unauthorized | Existing persisted seam rejects successful changed-identity admission, detailed below |
| Proposal, two-phase item rebase, worker recovery/cache, query and regression implementation | BLOCKED | Authorized child files | Execution stopped at authority conflict before product edits, as required by execute-p; no partial implementation claimed |
| Required tests, isolated SQL CAS/restore and runtime original-recheck smoke | BLOCKED | Authorized test files | No implementation was established; required execution evidence not produced |

### Exact PLAN_CONFLICT: persisted admission seam rejects the authorized identity change

The approved plan I-4 (line 34) explicitly permits an admission row still bound to the original identity to enter existing `recordAutomaticPersisted`, only at the original revision and without manual/historical protection. Implementation step 5 (line 60) requires retaining that existing admission write path after a controlled repair.

Observed source in this target worktree:

- `DiscoveryVerificationCommitService.kt:50-51` makes `commit` transactional.
- `DiscoveryVerificationCommitService.kt:122-125` calls `review.recordAutomaticPersisted(...)` before attaching the persisted admission revision.
- `DiscoveryReviewService.kt:858-865` calls `recordAutomaticConclusion`, reads the persisted admission and unconditionally checks `!outcome.identityChanged` in addition to hash, decision, revision, policy and manual checks.
- `DiscoveryReviewService.kt:900-901` computes `identityChanged = existing.identityHash != identityHash`.
- `DiscoveryReviewService.kt:914-917` returns that `identityChanged` value after a successful automatic admission CAS.

Therefore, for the explicitly required case of an existing automatic admission at the original identity/revision and a valid empty-name repair that changes identity, the existing seam rejects its own successful CAS. **[INFERENCE from the above code, not a runtime reproduction]** The `check` throws `IllegalStateException("Automatic admission changed during verification commit")`; the enclosing commit transaction rolls back rather than completing the required admission path.

`DiscoveryReviewService.kt` is absent from the exact authorized list. A narrowly authorized change to its controlled persisted-admission contract, with corresponding regression authority, is needed before this behavior can be implemented. No change is proposed to ordinary admission semantics without that authority. This execution did not bypass the seam, pre-rewrite the admission identity, invoke the automatic write twice, catch and accept its failed safety assertion, or narrow acceptance to experts with no existing admission row.

Evidence collection used the specialized source grep:

```text
pattern: recordAutomaticPersisted|identityChanged|manual|LEGACY_APPROVED
path: src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt
858: fun recordAutomaticPersisted(docId: String, profile: ExpertProfile,
864: stored.policyVersion == expectedAutomatic.policyVersion && !outcome.manual && !outcome.identityChanged) {
901: val identityChanged = existing.identityHash != identityHash
916: docId, automatic.status.name, admittedDecision(automatic.status.name), false, identityChanged,
```

The complete relevant method bodies were then read at `DiscoveryReviewService.kt:835-941`, not inferred from method names.

### Commands

All shell commands ran with cwd equal to the exact target worktree.

| Command | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-11-01/paper-reprocess-02-identity.md` | PASS, exit 0; run at entry and handoff | Canonical approved path; SHA above; 13398 bytes |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-11-01/paper-reprocess-02-identity.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master` | FAIL, exit 1; known helper infrastructure issue | Line 30 strict resolution of unrelated stale `/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b`; no cleanup attempted |
| `git rev-parse --show-toplevel --abbrev-ref HEAD --git-dir --git-common-dir HEAD && git worktree list --porcelain && git status --short && git merge-base --is-ancestor 1979768f08ffe58ca3c919326bb0eebbc833e3a1 HEAD` | PASS, exit 0 | Native entry identity/registration/reachability fallback authorized by brief; registered target HEAD `3065dae4f67eb075ede4395e5586ee0311731b46`; only existing brief/ledger changes |
| `git rev-parse --show-toplevel --git-dir --git-common-dir HEAD && git symbolic-ref --short HEAD && git merge-base --is-ancestor 1979768f08ffe58ca3c919326bb0eebbc833e3a1 refs/heads/fast/paper-reprocess-master && git status --short` | PASS, exit 0 | Handoff root/gitdir/common-dir/HEAD/branch/reachability unchanged; no product changes |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationIntegrationTest` | NOT_RUN — authority conflict | Existing integration opt-in identified as `-DmysqlIt=true`; no skipped tests represented as passing |
| Isolated SQL CAS/restore evidence and original-recheck runtime smoke | NOT_RUN — authority conflict | No production writes or source corpus changes |

### Changed Files

- `docs/plans/fast/paper-reprocess-master/children/02/execution.md` — appended this execution report.
- Product/test files: none. No commit, staging, push, merge, history rewrite or approved-plan modification.

### Deviations

- Implementation stopped at the exact unauthorized dependency conflict, before partial product changes.
- Worktree helper's documented stale-registration failure was handled using native read-only identity checks; helper and worktrees left untouched.
- No implementation commit was created because there are no implementation changes to commit. An empty `feat(fast-p): implement 02` commit would falsely imply delivery.

### Freshness

- Plan identity rechecked: YES.
- Worktree identity rechecked: YES, native fallback.
- Reported commits reachable from target branch: YES for base/checkpoint; no new commit reported.
- Required commands run this invocation: NO; explicit conflict before implementation.
- Historical evidence used only as baseline: YES.
- No independent PASS or READY_FOR_VERIFICATION asserted.

### Remaining Blocker

Human-approved amendment authorizing the changed-identity persisted admission seam in `DiscoveryReviewService.kt` and its narrowly scoped regression coverage, while preserving original-revision/manual/history guards, or an explicit revised acceptance contract. Controller was notified with exact code locations. Missing prerequisite is authority, not unavailable source or environment.

### Next Action

PLAN_CONFLICT → obtain a human decision/amended approved plan; rerun execute-p bound to the resulting exact plan identity.

---

## Execution Result: READY_FOR_VERIFICATION

Date: 2026-11-02 (epoch 2)
Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-02-identity.md`
Plan SHA-256: `81395262dedd2de0ac8a4c01c71467e4f9faf4d0a06222d88c1690b93a564997` (15582 bytes)
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master/docs/plans/2026-11-01/paper-reprocess-02-identity.md@81395262dedd2de0ac8a4c01c71467e4f9faf4d0a06222d88c1690b93a564997`
Execution epoch: NEW — amended identity, not reuse of epoch 1 evidence.
Approval basis: current delegated invocation; amended plan-only commit `9ce385c729c4b3a4c6fd0fff3aa06c126773cec3`; brief/ledger A1/A2 explicitly authorize the persisted-admission seam and all eleven files.
Executor: `PaperIdentityImplementer02E2`
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master`
Target branch: `fast/paper-reprocess-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master@fast/paper-reprocess-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master`
Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Pre-execution code SHA: `1979768f08ffe58ca3c919326bb0eebbc833e3a1`
Pre-execution evidence HEAD: `b9074319bd2c599f07f75f5c5cf2df917a5277e2`
Post-execution code SHA: `25fe0ef0d89257536bf0cf56a2d6ec5da80c8961`
Evidence HEAD at execution handoff: `25fe0ef0d89257536bf0cf56a2d6ec5da80c8961`; this appended report remains an orchestration artifact for the controller's separate checkpoint.
Implementation boundary: product commit `b9074319bd2c599f07f75f5c5cf2df917a5277e2..25fe0ef0d89257536bf0cf56a2d6ec5da80c8961`, subject exactly `feat(fast-p): implement 02`. Child code-base ancestry also checked.

### Task Status

| Requirement | Status | Files | Execution evidence |
|---|---|---|---|
| I-1 proposal/application separation | IMPLEMENTED | Retained service; Worker; authorized tests | Proposal validates retained original bytes/metadata/attempt/source/hash and exact unique email owner. AUDIT uses a virtual profile; isolated original-controller runtime reports zero identity writes and no admission application. Prepare/start do not perform identity repair. |
| I-2 fail-closed protection and narrow CAS | IMPLEMENTED | Retained service; IntegrationTest; RetainedServiceTest | Live-discovery, APPLICATION, contact/alias, manual/history, cross-layer author/source IDs, email uniqueness, nonempty identity and academic-field protections precede the existing `repairEmptyNameProof`. Lookup errors fail closed. Real HTTP version-CAS fixture proves PARTIAL and remaining-layer continuation. |
| I-3 frozen baseline / effective current item | IMPLEMENTED | Repository; Worker; Query; integration/query tests | Two transactional phases preserve the original beforeSnapshot and admission baseline; receipt/effective root hashes/profile and item columns are committed together. Epoch 0 and stale token are rejected; original roster hash and completed historical SQL row remain unchanged. Active lease/snapshot are replaced with the returned row. |
| I-4 / X-4 controlled old-admission seam | IMPLEMENTED | CommitService; DiscoveryReviewService; integration/commit tests | Server-derived authority requires a valid current ENFORCE receipt/epoch/lease, persisted completed item, running parent, original admission identity/revision and nonmanual/nonhistory decision. A real old `AUTO_PASSED` revision 7 changes identity through `recordAutomaticPersisted` to revision 8 and projects. Missing receipt, manual/history, revision/contact/ES changes and cancellation reject. Ordinary three-argument persisted calls retain their identity-change guard. |
| I-4 transaction rollback | IMPLEMENTED | IntegrationTest; existing transaction seams | Ordinary changed-identity call throws and rolls back. A MySQL trigger failing item admission attachment after controlled admission CAS proves admission and item transaction rollback together: old admission retained, item RUNNING and no attached revision. No failed guard is caught as success and no admission is prewritten. |
| I-5 resume/cache isolation | IMPLEMENTED | Worker; Repository; retained codec; worker/integration tests | External ES success followed by lease expiry resumes the same stored proposal with ALREADY_VERIFIED and no repeated RAW patch or nested beforeSnapshot. Changed identity drops old provider hits/cursors/evidence and paper-owner conclusions, recollects the same source refs, records `IDENTITY_CHANGED_CACHE_NOT_REUSED`; REUSE makes no provider call, REFRESH uses effective ORCID and frozen Apollo switches. Proposal hashing preserves timestamp/decimal precision across persisted JSON. Existing identity/cache compatibility versions are not bumped. |
| Readback / downstream boundary | IMPLEMENTED | QueryService; QueryServiceTest | Frozen identity/manifest fields come from beforeSnapshot, effective processing identity and supplement are available in saved results/step trace. Existing controller/DTO signatures and ordinary discovery paths remain unchanged. No frontend/resource changes. |
| X-2–X-4 original-path runtime | IMPLEMENTED | IntegrationTest standalone runtime entry | Fresh separate Java process calls the original controller prepare/start, then real Worker/SQL/Commit/query with source bytes. AUDIT PROPOSED/0 patches and ENFORCE APPLIED/1 patch both finish PENDING under actual source-backed qualification, rather than inventing admission. Detail below. |
| Required fresh tests and product commit | IMPLEMENTED | All authorized files | Required JDK11 command with `-DmysqlIt=true`: 80 tests, zero failures/errors/skips, BUILD SUCCESS. Separate runtime exit 0. Exact staged allowlist checked; product commit contains only authorized implementation/test files. |

### Commands

All commands/processes used the exact target worktree as cwd. `python` was absent (exit 127); the skill helpers were run successfully with `python3` where applicable.

Required final command, run after the final product/test edits:

```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RetainedPaperIdentityServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DtrimStackTrace=false -Dstyle.color=never
```

Result: exit 0; **80 tests, failures 0, errors 0, skipped 0; BUILD SUCCESS**; 464.09 seconds. Output `artifact://222`; current Maven reports are under `target/surefire-reports/`. MySQL integration actually ran in a fresh MySQL 8.0.36 container, using existing migrations through V164 (the real preparation-spool table), not the earlier incomplete V161 fixture schema. No migration was edited.

Runtime dependency classpath command (exit 0, 1.74 seconds):

```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/paper-identity02e2-classpath.txt -Dstyle.color=never
```

Separate runtime process was launched using Python `subprocess.run` with these exact arguments; `<dependencies>` is the complete stripped content of `/tmp/paper-identity02e2-classpath.txt`:

```text
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java
  -DmysqlIt=true
  -cp target/test-classes:target/classes:<dependencies>
  com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest
  --retained-identity-smoke
```

Result: exit 0; 36.49 seconds; complete stdout/stderr `/tmp/paper-identity02e2-runtime.log`. This is a standalone main, not a JUnit invocation. It uses the actual original controller/service/repository/worker/parser/commit/query classes, a fresh real MySQL container, copied retained bytes, and the real ES writer over a loopback HTTP CAS fixture. It does not start the production Spring application, a live ES cluster, or a browser. No production endpoint or Apollo provider was contacted.

#### Self-check corrections retained for audit

These are execution self-checks, not independent-verifier repair rounds. Each retry followed a concrete authorized correction; none is represented as passing evidence:

| Command variant | Observed result / correction |
|---|---|
| Required five suites, same JDK/mysql flags, without `-DtrimStackTrace=false` (first invocation) | Compile exit 1: ambiguous JsonNode `+=`; changed to explicit `add`. `artifact://195`. |
| Same five-suite command | Exit 1; 77 tests, 0 failures/6 errors/0 skips. Isolated archive root rejected macOS `/var` symlink; canonicalized the new temporary directory with `toRealPath`. `artifact://197`. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationIntegrationTest -DmysqlIt=true -Dstyle.color=never` | First correction run: 20 tests, 0 failures/9 errors/0 skips; nonnull Kotlin Mockito LocalDateTime matcher corrected. `artifact://200`. Next run: 20 tests, 0 failures/6 errors/0 skips; preparation check lacked failure detail. `artifact://204`. |
| Separate diagnostic Java main, same runtime class/argument | Exit 1 exposed the nested original-controller preparation-check stack; `/tmp/paper-identity02e2-runtime-diagnostic.log`. Superseded by the final successful runtime. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test '-Dtest=DiscoveryVerificationIntegrationTest#original*' -DmysqlIt=true -DtrimStackTrace=false -Dstyle.color=never` | Exit 1; 1 test/1 error; precise cause was missing `discovery_recheck_input` under the fixture's V161 target. Advanced only this authorized fixture to existing V164 and cleaned spool rows. `artifact://208`. |
| Required five suites with trim flag | Exit 1; 78 tests/7 errors/0 skips. Original start correctly rejected disabled verification in fixture; explicitly enabled the isolated configuration. `artifact://211`. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RetainedPaperIdentityServiceTest,DiscoveryVerificationIntegrationTest -DmysqlIt=true -DtrimStackTrace=false -Dstyle.color=never` | First run: 26 tests/7 errors/0 skips; start correctly rejected missing worker provider; bound an actual worker. `artifact://214`. Also made the synthetic no-ORCID fixture primary key an `EMAIL-` key and explicitly seeded AUTO_PASSED for controlled success/rollback cases. |
| Same two-suite command | Next run: 26 tests/1 failure/6 errors/0 skips; persisted proposal hash differed on numeric JSON round-trip. Narrow retained codec/canonical numeric hashing now preserves decimal/timestamp precision; regression added without changing global cache parsing/version. `artifact://216`. |
| Same two-suite command | Next run: 27 tests/0 failures/2 errors/0 skips. Source-backed flow reached processing; remaining fixture issues were missing required campaign FK and comparing BigDecimal/Double JsonNodes with different readers. Reused the existing campaign fixture pattern and compared the preserved original with the same exact retained codec. `artifact://219`. |
| Final required five-suite command above | Exit 0, 80/0/0/0. No product/test edits followed it. |

Identity/scope/commit checks:

- `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-11-01/paper-reprocess-02-identity.md` — exit 0 at entry and final-state check; exact approved hash/size above.
- `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-11-01/paper-reprocess-02-identity.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-paper-reprocess-master --expect-branch fast/paper-reprocess-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-paper-reprocess-master` — invoked before staging/commit; known exit 1 at line 30 resolving the unrelated stale publish worktree named in the brief. No helper/worktree cleanup.
- Brief-authorized native fallback: `git rev-parse --show-toplevel`, `git symbolic-ref --short HEAD`, `git rev-parse --git-dir`, `git rev-parse --git-common-dir`, `git rev-parse HEAD`, `git worktree list --porcelain`, and `git merge-base --is-ancestor 1979768f08ffe58ca3c919326bb0eebbc833e3a1 HEAD` — root/branch/gitdir/common-dir/registered target/entry HEAD and ancestry matched. Before commit, Python assertions checked exact root, branch, gitdir, entry HEAD and exact staged allowlist.
- `git diff --check` and `git diff --cached --check` — exit 0.
- `git add` explicitly named the eleven authorized files listed below, not fast-p artifacts; the staged path set was asserted equal to that list.
- `git commit -m 'feat(fast-p): implement 02'` — exit 0, commit `25fe0ef0d89257536bf0cf56a2d6ec5da80c8961`, eleven files, 987 insertions/21 deletions.
- `git rev-parse HEAD`, `git rev-parse refs/heads/fast/paper-reprocess-master` and `git merge-base --is-ancestor 25fe0ef0d89257536bf0cf56a2d6ec5da80c8961 refs/heads/fast/paper-reprocess-master` — exact target HEAD equality/reachability passed.

### Source-backed runtime observations

The isolated existing-empty-name RAW fixture is `EMAIL-retained-empty-31372`, backed by original corpus record `31372.json`, exact target email `e.martinez-paneda@imperial.ac.uk` and original PDF SHA `488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7`. The empty RAW baseline is deliberately seeded only in the isolated fixture, not claimed as a reproduction of the production index's current blank-name state. No production document is rewritten.

Runtime output:

```text
RETAINED_IDENTITY_RUNTIME mode=AUDIT originalControllerPrepareStart=true isolatedMySQL=true loopbackEsCAS=true sourceSha=488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7 evidencePage=1 beforeNameEmpty=true effectiveName=Emilio phase=PROPOSED identityWrites=0 outcome=PENDING applied=false admissionRevision=7 providers=0 rosterHashUnchanged=true archiveUnchanged=true queryTrace=true
RETAINED_IDENTITY_RUNTIME mode=ENFORCE originalControllerPrepareStart=true isolatedMySQL=true loopbackEsCAS=true sourceSha=488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7 evidencePage=1 beforeNameEmpty=true effectiveName=Emilio phase=APPLIED identityWrites=1 outcome=PENDING applied=false admissionRevision=7 providers=0 rosterHashUnchanged=true archiveUnchanged=true queryTrace=true
```

The source paper does not supply a current qualifying professor fact for this fixture; both paths therefore correctly remain PENDING with no admission application. The separate controlled AUTO_PASSED-to-new-identity SQL regression supplies explicitly synthetic current SELF Professor evidence for that seam; it is not represented as a fact extracted from this original paper or a real corpus admission count.

SHA-256 of the original source files was checked before and after the separate runtime and was identical:

- `31372.json`: `36b6ff6de865b3c07b53feec7b6336586c3f3ab87636727dfc6366ed645f8169`.
- Original PDF body: `488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7`.

The runtime also asserts retained archive-record equality, original completed SQL-row equality, frozen roster-hash equality, source page/contact sentence, absence of old provider hits/old-identity evidence, and trace/manifest readback. Temporary copied archive directories are removed by fixture close; original corpus and prior-child reports are untouched.

### Changed Files

Product/test commit contains precisely:

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityService.kt` — readonly retained proposal, protection checks, virtual identity, narrow application receipt and integrity codec.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — step-3 supplement, two-phase active item rebind, recovery and changed-identity cache isolation.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — run/admission/item-locked stage/complete CAS preserving baseline and atomic receipt/effective fields.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt` — before/effective comparison and server-derived controlled authority.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — frozen manifest/identity and effective supplement result readback.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` — transaction/persisted-state-validated overload; ordinary strict seam retained.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/RetainedPaperIdentityServiceTest.kt` — fail-closed protections, partial receipt, full-name proof/unchanged semantics and precision-safe proposal hashing.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — changed-identity old-cache rejection.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt` — authority refuses ordinary/missing-receipt contexts.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` — original manifest/frozen baseline preservation.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` — source-backed standalone runtime, actual SQL rebind/lease/restart, old automatic admission, rollback/protection and partial/refresh cases.

Orchestration artifact only: this execution report, appended after epoch 1. Existing controller change to `docs/plans/fast/paper-reprocess-master/ledger.md` was preserved and excluded from product staging/commit.

### Deviations and concurrency boundary

- No additional product/test seam or plan amendment was needed after A1/A2. No applied migration, frontend, global cache compatibility version, admission rule or Apollo strategy changed.
- Known worktree-helper infrastructure failure used the explicit native fallback authorized in the brief.
- Fixture self-check corrections above did not weaken production guards. Final required command and standalone runtime were rerun on the final product/test state.
- This is not a distributed SQL/ES transaction. Protection is reread before narrow ES repair and again before admission commit; per-layer writes use existing seq_no/primary_term CAS. A concurrent contact/cancel after the protection read may overlap an ES name/proof patch. It cannot authorize a stale/manual/protected admission commit, and cancellation does not roll back an already committed ES patch. Strong cross-store non-overlap is not claimed.
- No browser/human acceptance, full-suite Maven run, WAR packaging, deployment, production writes, push/merge/history rewrite, later-child implementation, or independent verification/review skill was performed.

### Freshness

- Plan identity rechecked: YES; current approved epoch-2 hash.
- Worktree identity rechecked: YES; native fallback with explicit assertions.
- Product commit is exact target branch HEAD and reachable: YES.
- Required commands run freshly in this invocation after final edits: YES, including real `-DmysqlIt=true` integration and separate runtime main.
- Historical output used only as baseline: YES; epoch 1 preserved, no evidence carried forward as current execution proof.
- No independent PASS is asserted. READY_FOR_VERIFICATION is an execution handoff only.

### Remaining Blocker

None known within the approved child contract. The cross-store race limit and isolated runtime boundary are stated above rather than represented as stronger guarantees.

### Next Action

Controller dispatches the fresh independent fast-p light verifier using this brief and committed boundary, then records its checkpoint/verdict. Do not advance children or run review-fast-p from this execution.

## Epoch 2 — Controller Terminal Evidence Closure
- Initial implementation: `25fe0ef0d89257536bf0cf56a2d6ec5da80c8961`; bounded fixes: `b065357c8c6816cf7fea0651b370a924b7e920f0`, `47393695c8157c7033147ae4b041e5c5b3c52aa9`; terminal code head is the latter.
- Distinct fresh verifier PaperIdentityReverifier02E2R2 returned LIGHT_PASS at epoch2 attempt3 after round2; exact full boundary `1979768f08ffe58ca3c919326bb0eebbc833e3a1..47393695c8157c7033147ae4b041e5c5b3c52aa9`. F-01/F-02/F-03 resolved; no RECORD_ONLY finding.
- Fresh verifier evidence: required JDK11/MySQL suites 85/0/0/0 plus separate actual-controller transaction-proxied SQL runtime exit0; original source/manifest hashes unchanged. Real MySQL and loopback HTTP ES CAS only, not live ES or browser.
- This closing entry records the verifier's result; controller did not rerun product checks, perform aggregate review or claim human acceptance. Evidence checkpoint must pass before child03 dispatch.

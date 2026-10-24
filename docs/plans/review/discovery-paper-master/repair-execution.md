# Repair Execution — discovery-paper-master / V-1

## Execution Identity

- Execution result: READY_FOR_VERIFICATION, subject to the required docs-only evidence commit and final identity/clean-state checks reported by the executor. This is execution evidence, not independent machine PASS or human acceptance.
- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md
- Plan SHA-256: 4c9cc7637968e0691ba61bc362acbd3324c97d1a8d50c0d1ecdb0ec2bd991584
- Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md@4c9cc7637968e0691ba61bc362acbd3324c97d1a8d50c0d1ecdb0ec2bd991584
- Execution epoch: NEW. No prior repair-execution.md existed in this review directory; the review ledger recorded repair execution as N/A.
- Approval basis: current human-originated invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fix/discovery-paper-master/repair.md`, approving the exact current bytes and the two local commits expressly authorized in its handoff.
- Executor: current top-level coding assistant, model openai-codex/gpt-6.1-sol:max; no stable runtime agent/task identifier exposed. Not an independent reviewer.
- Governing master: commit 3c5a68ad9917fdee22e0548b5afc57a6df04d580; SHA-256 fc83cdf9149df3cb11d9433dd36cc2059ce09ad70fb550f7f647769714e17de3; A1–A16 unchanged.
- Finding lineage: Epoch 1 V-1 NEW/P1 → R-1 implemented; independent resolution verdict PENDING.

## Target and Code Boundary

- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master
- Target branch: fast/discovery-paper-master
- Git directory: /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master
- Common Git directory: /Users/lukai/IdeaProjects/weibo-talent-introduction/.git
- Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master
- Pre-execution code SHA: 37da0edbdb9737ed75c27ea8e6e525e35ef78354
- Pre-execution HEAD, including review evidence: 478a661b5e8443bf75960725637de9e485ce434a
- Post-execution code SHA: 1ebe3534be212c26eb1e490d747ae4a10daa702f
- Repair product boundary: 37da0edbdb9737ed75c27ea8e6e525e35ef78354..1ebe3534be212c26eb1e490d747ae4a10daa702f
- Cumulative product boundary: c6e9709f349f32c5feb1d067b47c697b0dff05b6..1ebe3534be212c26eb1e490d747ae4a10daa702f
- Product commit subject: `fix(discovery-papers): avoid charging reused scope blobs twice`.
- Evidence commit identity: the subsequent commit containing only this report, with subject `docs(review-fast-p): record discovery paper scope reuse repair`. Its full SHA is recorded in the executor's final response after creation; it is distinct from the post-execution code SHA. Resolve this report's commit with `git log -1 --format=%H -- docs/plans/review/discovery-paper-master/repair-execution.md`.

Boundary receipts, all in the target worktree:

- Initial `git status --short --branch`: only `## fast/discovery-paper-master`; no dirty files. `git diff --cached --name-only`: no output.
- `git diff --name-only 37da0edbdb9737ed75c27ea8e6e525e35ef78354 478a661b5e8443bf75960725637de9e485ce434a -- src`: exit 0, no output. Review-evidence commits did not alter product/test source.
- `git merge-base --is-ancestor 37da0edbdb9737ed75c27ea8e6e525e35ef78354 1ebe3534be212c26eb1e490d747ae4a10daa702f`: exit 0.
- Immediately after the product commit, `git rev-parse HEAD refs/heads/fast/discovery-paper-master` printed `1ebe3534be212c26eb1e490d747ae4a10daa702f` twice; `git merge-base --is-ancestor HEAD refs/heads/fast/discovery-paper-master`: exit 0. `git status --short`: no output, empty worktree/index before this evidence file was created.

## Task Status and Changed Files

| Requirement | Status | Authorized file | Execution evidence |
|---|---|---|---|
| R-1 / V-1: actual incremental storage accounting | IMPLEMENTED | src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt | Scope identity and deployment permission remain checked. Lookup rejects DELETING and verifies AVAILABLE content through existing file/hash/length checks before selecting zero-growth reuse. Scoped save preflight budgets 0 for verified reuse and actual incoming bytes for a new body; new-download admission retains its headroom requirement. Current cap/min-free/path/writability checks remain. Direct-save behavior and atomic write/post-write checks retained; no duplicate full-capacity scan added to the new-body path. |
| R-1: discriminating capture regression | IMPLEMENTED | src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveCaptureTest.kt | Two actual-SQL/temp-file capture tests cover free-space and archive-cap headroom separately. They failed before the fix and passed after it. Each verifies reused=true, completedBytes=1024, saved/reused counts, no physical growth, immutable first, and rejection of distinct 1024-byte content with no SAVED increase. Existing enablement-toggle/live-capacity/pre-request tests retained. |
| Required executor verification | COMPLETE | No additional product/test changes | Fresh required JVM union, standalone full Node, Maven-bound Node/syntax, and both standalone smoke launchers passed on final code. |
| Durable execution handoff | THIS REPORT | docs/plans/review/discovery-paper-master/repair-execution.md | Separately authorized evidence only; no changes to repair.md, machine-verification.md, review ledger, fast-p artifacts, or baseline plans. |

Product commit output: 2 files changed, 70 insertions, 7 deletions. No third product/test file, class, interface, state, migration, dependency, or public endpoint introduced.

## Fresh Commands

All repository commands used cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`. The required commands used JDK11 explicitly. Tests and runtime launchers used disposable Docker MySQL 8.0.36 and temporary files/strict fake HTTP; no production application context, production DB, real provider, or paid request was used.

### B0 — failing-before regression baseline

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest -DmigrationIt=true
```

- Exit 1; JVM run 11, failures 0, errors 2, skipped 0 (9 passed).
- Only the two new regression methods errored, both at reuse of the existing 1024-byte body: `PaperArchiveBlockedException [ARCHIVE_STORAGE_BLOCKED]`, caused by `ArchiveException: ARCHIVE_STORAGE_BLOCKED`.
- New methods: `accepted scope reuses verified body with less free space than body size`; `accepted scope reuses verified body with less archive cap headroom than body size`.
- This was the new authorized regression on reviewed code, not a repeat of the reviewer's existing diagnostic solely to reconfirm it.
- Session raw output: artifact://690.

### C1 — required JVM executor gate and Maven-bound frontend gates

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveCaptureTest,DiscoveryPaperArchiveFlowIT,DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest -DmigrationIt=true
```

- Exit 0, BUILD SUCCESS; JVM run/passed 345, failures 0, errors 0, skipped 0.
- Maven-bound Node: 1823 tests passed, 0 failed/cancelled/skipped/todo.
- Maven-bound `node-check-app` and `node-check-task-modal-runtime` executions completed; overall exit 0. No test exclusion or execution bypass.
- Session raw output: artifact://693.

| JVM class | Run / passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryPaperArchiveServiceTest | 15 | 0 | 0 | 0 |
| DiscoveryPaperArchiveRepositoryIT | 4 | 0 | 0 | 0 |
| DiscoveryPaperArchiveFlowIT | 8 | 0 | 0 | 0 |
| DiscoveryPaperArchiveCaptureTest | 11 | 0 | 0 | 0 |
| DiscoveryPaperArchiveControllerTest | 7 | 0 | 0 | 0 |
| DiscoverySampleStoreTest | 7 | 0 | 0 | 0 |
| DiscoverySampleCaptureTest | 4 | 0 | 0 | 0 |
| ExpertDiscoveryServiceTest | 194 | 0 | 0 | 0 |
| PdfEmailExtractorTest | 43 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 52 | 0 | 0 | 0 |

R-1 measured stdout from C1:

```text
SCOPE_REUSE limit=free-space bodyBytes=1024 headroom=1023 saved=2 reused=1 failed=1 usedBytes=1024 files=1 growth=0 first=frozen newBody=ARCHIVE_STORAGE_BLOCKED
SCOPE_REUSE limit=archive-cap bodyBytes=1024 headroom=1023 saved=2 reused=1 failed=1 usedBytes=1073740801 files=2 growth=0 first=frozen newBody=ARCHIVE_STORAGE_BLOCKED
```

The cap case includes one sparse filler file. Its logical bytes are included by the real directory-size accounting, not written directly into archive metadata. Both assertions compare storage before reuse, after reuse, and after the rejected new body. Each case's one failed attempt is the deliberately rejected distinct new body.

### C2 — required standalone full frontend suite

```sh
node --test src/test/js/*.test.js
```

- Exit 0; tests 1823, suites 334, pass 1823, fail 0, cancelled 0, skipped 0, todo 0.
- Session raw output: artifact://692.

### C3 — required standalone storage runtime

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveSmokeLauncher'
```

- Exit 0, BUILD SUCCESS; separate executable process, not a relabelled JUnit run. Test count N/A.
- Measured output: settings reread 25/15 GiB; saved 3072 bytes / 2 files; protected B leaves 2048 deletable bytes; unprotect retains bytes; controller Range=206 / 100 bytes; shared deletion removes 2 files / 3072 logical bytes; remaining=0; metadata/outcome/analysis retained; deleted content=410.
- Session raw output: artifact://698.

### C4 — required standalone capture runtime

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DYLD_LIBRARY_PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/lib mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.archive.ArchiveCaptureSmokeLauncher'
```

- Exit 0, BUILD SUCCESS; separate executable process with freshly compiled test classpath. Test count N/A.
- Measured stdout:

```text
CAPTURE_SMOKE bytes=1024 sha256=a9c40cf3c9314d60ef74257cbf948ffd117523ba94ffbde20874973db5114c02 first=frozen latest=updated savedAttempts=2 sharedFiles=1 blockedHTTP=0 queue=PENDING attempts=2 delay=60s cursor=unchanged
```

- Session raw output: artifact://699.

### Scope, identity and local commit checks

- `git diff --check`: exit 0, no whitespace errors. Final pre-commit status showed only the two Authorized Files.
- `plan_identity.py` returned the plan path/hash above at invocation start and immediately before product staging; unchanged bytes, 13029 bytes.
- `worktree_identity.py` bound the exact worktree/branch/Git directory at invocation start. It was rerun with `--expect-root`, `--expect-branch`, and `--expect-git-dir` immediately before both product staging and product commit; exit 0 each.
- Product staging named only the two Authorized Files. Product commit succeeded with the exact authorized subject and SHA recorded above. HEAD/target-branch equality, ancestry and clean index/worktree were observed after it.
- The same guarded staging/commit, post-commit HEAD/target-branch ancestry, final plan identity and empty-index/worktree checks are required for the evidence commit and are reported after creation in the executor response. This report cannot embed its own Git commit hash.
- LSP availability check reported no configured language server. No exported API or public signature changed.

## Deviations and Evidence Limits

- Deviations: none. No skipped/deleted/repinned assertion, test-only product guard, stale /tmp probe/classpath, production/provider call, push, merge, deployment, history rewrite, or extra implementation scope.
- Fresh command evidence belongs to this exact repair identity and final code; historical reports were used only for baseline/lineage.
- Original294/page34 and original23/person-label effect evidence remains explicitly incomplete; this repair neither recovers it nor claims synthetic tests prove real-original extraction effects.
- No independent aggregate review was performed by this executor. Required full Epoch 1 master matrix, expanded JVM union, comparator controls, six runtime entries, actual Chromium gates and WAR build/hash remain the independent aggregate reviewer's responsibility.
- Manual acceptance: PENDING. No machine PASS, human result, or sign-off inferred.

## Next Action

Run independent aggregate re-review against the retained handoff and repaired product head, using the current repair plan and this execution record as lineage evidence:

```text
/review-fast-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/fast/discovery-paper-master/human-review-handoff.md
```

Keep the cumulative master base at c6e9709f349f32c5feb1d067b47c697b0dff05b6 and repaired product head at 1ebe3534be212c26eb1e490d747ae4a10daa702f. Do not use the invoking main worktree's unchanged HEAD as the implementation boundary. A fresh independent reviewer, not this executor, decides V-1 resolution and the complete master verdict.

# Child 01 implementation brief

- Exact approved plan: docs/plans/2026-10-23/discovery-paper-01-storage.md (commit c18dbc060dcf4c8440f853ede4d1d3aaf737994f)
- Master contract: docs/plans/2026-10-23/discovery-paper-master.md (commit c18dbc060dcf4c8440f853ede4d1d3aaf737994f)
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master
- Branch: fast/discovery-paper-master
- Master base: c6e9709f349f32c5feb1d067b47c697b0dff05b6
- Child product base: c18dbc060dcf4c8440f853ede4d1d3aaf737994f
- Dependencies: none
- Approval: explicit user `/fast-p docs/plans/2026-10-23/discovery-paper-master.md` invocation authorizes local branch/worktree and local commits.

Read the complete exact child plan from disk. Its Authorized Files table (9 files), all T/I/A requirements, DTO/API names, invariants and downstream interfaces are the approved implementation contract. Do not inspect later child plans. No amendment is approved.

## Global constraints
- Follow master I-1 through I-4. Persisting/downloading, identity, qualification and admission stay separate. Do not alter expert/admission/mail/legacy samples.
- JDK11, existing Kotlin/Spring JDBC/PDFBox dependencies only. V159 is available. No applied migration edits.
- Product edits only to the exact nine child-table paths. Execution report is the sole additional permitted evidence output; controller owns ledger/other logs. Do not update plan/knowledge/changelog outside this authority.
- Implement real filesystem and SQL behavior; MySQL integration evidence must execute, not be mocked or silently skipped. Preserve auth interception; do not add public routes.
- Downstream 02 uses beginRecord/beginAttempt/saveBody/finishAttempt/finishExtraction. 04 uses the same service/repository/models/controller for read handles and analysis. DTO names/shape in T-3 are fixed. Plan for these exact seams without implementing 02/04.
- One writer; do not spawn writing agents or create another worktree. Read-only intelligence tools allowed. Skip build/lint/tests mid-flight; run all required checks after final edits. This orchestration-specific rule takes precedence over execute-p's mid-flight red/green sequence.

## Required commands and smoke evidence
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveRepositoryIT,DiscoveryPaperArchiveServiceTest,DiscoveryPaperArchiveControllerTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DmigrationIt=true`
- Ensure RepositoryIT actually executes against isolated MySQL, and report exact test/skipped/error counts.
- Exercise the new service/controller with real temporary files and real SQL in an isolated smoke scenario (throwaway harness or test fixture, no production Spring context/network). Observe saved bytes, protection/unprotection, shared deletion, settings reread and content/Range. Tests alone are not final behavioral proof; report observed scenario explicitly.
- No real Apollo, production DB, deployment, external data requests.

## Git and reporting
Use execute-p against this exact plan/worktree. Commit complete implementation as `feat(fast-p): implement 01`; exclude all fast-p evidence files. Record commands, smoke output, identities, scope, limitations in `docs/plans/fast/discovery-paper-master/children/01/execution.md`. Return READY_FOR_VERIFICATION, BLOCKED or PLAN_CONFLICT plus commit SHA/command summary/report path. A plan conflict needs human authority; never silently reinterpret the contract.

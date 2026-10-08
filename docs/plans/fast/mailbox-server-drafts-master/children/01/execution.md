# Child 01 execution

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/docs/plans/2026-10-08/mailbox-server-drafts-01-storage.md`
Plan SHA-256: `f2ba4e66a0b07dafd39e0a15cdcc88c89879832a61127205cefb25177141f9c4`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master/docs/plans/2026-10-08/mailbox-server-drafts-01-storage.md@f2ba4e66a0b07dafd39e0a15cdcc88c89879832a61127205cefb25177141f9c4`
Execution epoch: NEW
Approval basis: Human “批准 并 实施”, 2026-10-08, unchanged exact child/master plans; child brief and current delegated invocation.
Executor: DraftStorageImplementer
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master`
Target branch: `fast/mailbox-server-drafts-master`
Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-server-drafts-master`
Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master@fast/mailbox-server-drafts-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-server-drafts-master`
Pre-execution code SHA / child_base_sha: `7c86599f85f6462e00a3fcd2c5a74f1ca57f013d`
Pre-execution HEAD including plan/evidence commits: `56db4ac6c7c8587a0e931cf9f704ec9018ce7853`
Post-execution code SHA: `099e372c2eca32596a9db670c0f13a0a30ce2b7e`
Evidence HEAD at executor handoff: `099e372c2eca32596a9db670c0f13a0a30ce2b7e`; this execution report remains uncommitted for the controller-owned evidence commit.
Implementation boundary: `7c86599f85f6462e00a3fcd2c5a74f1ca57f013d..099e372c2eca32596a9db670c0f13a0a30ce2b7e`, product changes in local commit `feat(fast-p): implement 01`.

### Preflight

- Read execute-p, the complete exact child plan, complete master plan, child brief and relevant existing repository/service/controller/frontend contracts.
- Plan identity and target-worktree helpers completed successfully; identity receipts: `artifact://135`, `artifact://148`, `artifact://149`.
- Compared every file in the master source-manifest.json with the current worktree using SHA-256: `Manifest mismatches: []`.
- Migration filename inventory showed V150/V151 and no occupied V152 before creation.
- Preserved pre-existing controller-owned modification to `docs/plans/fast/mailbox-server-drafts-master/ledger.md`; it was not staged.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T-1 / I-1,I-2,I-3,I-4 | IMPLEMENTED | V152 migration, Models, Repository | Owner/target binary-collation unique key; atomic INSERT/CAS; monotonically increasing versions; null-body tombstones; no contact FK, TTL or delete API; real MySQL unique-key/CAS races, late saves, reopen and orphan-retention tests passed. |
| T-1 / I-6 | IMPLEMENTED | Models, Repository | DTO refs and internal validated-send projection; MANDATORY lock/bind/close/release methods; paired bind fields, newer-content preservation, rollback, matched close and explicit-reopen clearing tested on MySQL. |
| T-2 / I-1,I-4 | IMPLEMENTED | Service, Controller | Six API routes; Session-only owner; foreign/missing ID 404; closed markers remain readable; version/current-state 409 payload; pagination and account scope preserved. Anonymous, forged-owner and CAS tests passed. |
| T-2 / I-5 | IMPLEMENTED | Models, Service, Controller | Explicit typed LONGTEXT codec; strict HTTP CAS integral/range and schema decoding; unknown fields rejected; Unicode/code-point subject boundary; aggregate UTF-8 exact 1MiB boundary; rich-text/meeting/RAG/follow-up roundtrip. |
| T-2 / I-7,N-1,N-2 | IMPLEMENTED | Service, service tests, Repository IT | Only草稿-table CRUD; target repository reads; existing loadSnapshots identity/metadata reuse without file bytes; canonical ready metadata/download links; uploading→failed; foreign references rejected; already-saved missing references retain content. Real MySQL counts and contact-row state remain unchanged. |
| T-3 | IMPLEMENTED | Four authorized test files | Fresh finalized runs: 17 service/controller tests, 8 real MySQL tests, 39 real migration tests; zero failures/errors/skips. Node regression and syntax executions retained in each command. |
| Runtime smoke | IMPLEMENTED | Repository IT embedded temporary HTTP server | Actual Tomcat + Spring DispatcherServlet/controller + MySQL exercised with independent HTTP sessions, not only MockMvc. Receipts at `artifact://147:3020-3044`; temporary server/directory and test MySQL containers stopped/removed. |

### Commands

All repository commands ran in the target worktree. Every Maven invocation explicitly used JDK11:
`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.

| Exact final command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=MailReplyDraftServiceTest,MailReplyDraftControllerTest test` | PASS, exit 0; 17 tests, failures 0, errors 0, skipped 0 | `artifact://147:317-323,2436-2452`; final completion 2026-10-08 20:02:39 +08:00. |
| `DOCKER_API_VERSION=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftRepositoryIT test` | PASS, exit 0; 8 real mysql:8.0.36 tests, failures 0, errors 0, skipped 0 | `artifact://147:3020-3048,5161-5177`; final completion 20:05:09 +08:00. Actual migration to V152 and actual HTTP smoke occurred. |
| `DOCKER_API_VERSION=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.44 -Pmigration-it -Dtest=FlywayMigrationIntegrationTest test` | PASS, exit 0; 39 real mysql:8.0.36 migration tests, failures 0, errors 0, skipped 0 | `artifact://147:14515-14520,16633-16649`; final completion 20:13:21 +08:00. Fresh and historical-target upgrade cases migrated to V152; V152 schema assertions passed. |
| Existing Maven-bound `node --test src/test/js/*.test.js` | PASS in each final Maven invocation; 1528 passed, 0 failed, 0 skipped | `artifact://147:2436-2442,5161-5167,16633-16639`. |
| Existing Maven-bound `node --check src/main/resources/static/app.js` and `node --check src/main/resources/static/task-modal-runtime.js` | PASS in each final invocation | `artifact://147:2445-2447,5170-5172,16642-16644`. |
| `git diff --check` and `git diff --cached --check` | PASS, exit 0 | `artifact://148`, `artifact://149`. |
| `git commit -m 'feat(fast-p): implement 01'` | PASS, local commit `099e372c2eca32596a9db670c0f13a0a30ce2b7e`; exact authorized file set | `artifact://149`; 9 files, no fast-p artifacts. |
| `git rev-parse HEAD` / `git merge-base --is-ancestor HEAD fast/mailbox-server-drafts-master` | PASS; target HEAD is implementation commit and reachable on target branch | `artifact://149`. |

The final three Maven commands were run sequentially with `&&`; complete final combined command exited 0 after 773.20 seconds (`artifact://147`). No build/test command was parallelized. No WAR packaging command was specified by child 01; the required Maven test lifecycle compiled production and test sources.

### Runtime smoke receipts

Observed during the final MySQL command against actual HTTP port 51497:

1. Anonymous GET target →401.
2. Session A PUT OUTBOUND target for fixture contact 9101, subject “草稿01”, rich body `<b>跨设备保留</b>` / plain body “跨设备保留” →200, draft id 17, ACTIVE/version 1.
3. Independent newly created Session A GET id 17 →200 with original content; Session B GET same id →404.
4. A PUT expectedVersion=1 →200/version 2; stale PUT expectedVersion=1 →409/currentVersion 2/ACTIVE.
5. DELETE expectedVersion=2 →200/DISCARDED/version 3/content null.
6. Late PUT expectedVersion=2 →409/currentVersion 3/DISCARDED.
7. Explicit reopen expectedVersion=3, empty content →200/ACTIVE/version 4.
8. Fixture aged updated_at to 2024-10-08: GET still200.
9. Asserted business-table counts unchanged by draft CRUD; separately, the real-metadata attachment test asserted the complete expert_contact row unchanged.
10. Explicit fixture contact deletion: detail and list still200, contactExists=false, draft retained. DELETE expectedVersion=4 →200/DISCARDED/version 5.
11. Receipt `DRAFT_RUNTIME_SMOKE complete; isolated HTTP sessions, durable MySQL, business counts unchanged before explicit fixture deletion`; Tomcat stopped/destroyed and temporary directory removed in finally.

Scope of smoke: a temporary real HTTP Spring MVC server with test-only Session creation, real production draft controller/service/repository and MySQL. It is not a human/browser login acceptance or a full-application/password-auth deployment claim. A-4 sending rejection remains the explicitly deferred child-02 acceptance; no sending entrypoint was changed by 01.

### Downstream interface handoff

- Shared DTOs: `MailReplyDraftRef(id,version)`; typed `MailReplyDraftTarget`, `MailReplyDraftContent`, `MailReplyDraftContext`; internal `ValidatedDraftSendRef(owner,id,version,target,content,sendAttemptId,sendVersion)`.
- Repository transaction seams: `lockOwned(owner,id)`, `bindAttempt(owner,id,version,attemptId)`, `closeSent(owner,id,sendVersion,attemptId,now)`, `releaseCompletedBinding(owner,id,sendVersion,attemptId)`. Each enforces an existing transaction with MANDATORY annotation and runtime guard; no public HTTP SENT writer.
- `bindAttempt` leaves version/timestamps untouched and never replaces a different binding. Normal saves preserve bindings. `closeSent` clears content only for the exact owner/id/ACTIVE content version and bound attempt/send_version, increments version, and returns false for newer content. Release removes only the exact matching completed binding without changing content/version/timestamps.
- Explicit terminal reopen increments rather than resets the content version and clears previous binding. Child 02 owns when confirmed sending success invokes these interfaces.
- Raw JSON success objects match existing api() return contract. Errors expose code/message and currentVersion/currentState; no automatic retry/overwrite.
- Attachment download addresses are response-only `attachmentDownloads` entries; persisted context has no URL/path/File/Blob. Client-supplied unknown fields and binding/owner properties are rejected.

### Changed Files

- `src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql` — dedicated durable table/indexes, no foreign key or existing-table alteration.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftModels.kt` — typed protocol, persistence rows, response DTOs, refs and strict explicit codec.
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt` — parameterized reads/list/summaries, atomic CAS and MANDATORY send-transaction seams.
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt` — owner/target/snapshot/version validation and attachment metadata restoration.
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReplyDraftController.kt` — Session-owned REST API/raw responses/local error mapping.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt` — lifecycle/target/content/attachment/limits tests.
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailReplyDraftControllerTest.kt` — authentication/identity/raw-response/error/strict-request tests.
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt` — real MySQL races, persistence/rollback/binding/orphan/scope/metadata tests plus temporary actual HTTP smoke.
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` — latest-version expectations now152 and dedicated V152 schema/business-isolation assertions; historical explicit migration targets preserved.

### Deviations and earlier attempts

- No plan/file-scope amendment. Existing migrations, pom/dependencies, authentication configuration, attachments service, mail aggregation, send fingerprint, later children and frontend were not edited.
- Environment-only Docker compatibility override was used as authorized: `DOCKER_API_VERSION=1.44` and `-Dapi.version=1.44`. Diagnostic `docker version` showed Engine29.4.0, API1.54, minimum1.40; this explains incompatibility with the initial baseline API1.32. No dependency or project configuration workaround was made.
- The supplied baseline timeout (`artifact://132`) is not counted as passing. Fresh final migration run completed all39 tests including Flyway clean/re-migrate cycles without timeout. Baseline clean-timeout cause beyond that observation is unproven; no unrelated repair was attempted.
- Earlier post-implementation attempts failed during compile/test authoring: Long-vs-Int comparisons (`artifact://138`), cross-module nullable smart cast (`artifact://141`), and Mockito non-null matcher handling (`artifact://143`). Those authorized-file errors were corrected; final commands were rerun on the finalized state.
- An intermediate complete sequence passed (`artifact://145`) before strict HTTP decoder/real attachment metadata coverage was finalized. It is retained as history, not substituted for the fresh final evidence (`artifact://147`).
- No push, merge, history rewriting, unrelated cleanup or independent verification was performed.

### Freshness

- Plan identity rechecked: YES; unchanged SHA-256 as above.
- Worktree identity rechecked: YES; canonical root/branch/git-directory expectations passed.
- Reported implementation commit reachable from target branch: YES.
- Required commands run freshly on final implementation: YES.
- Historical evidence used only as baseline/history: YES.
- Only authorized product/test files staged and committed: YES; staged-name receipt and commit in `artifact://149`.
- Product worktree/index clean after implementation commit; fast-p evidence/ledger remain for controller-owned evidence handling.

### Remaining Blocker

- None known within child-01 scope.

### Next Action

- Controller invokes the fast-p four-gate independent verifier for child 01. This READY_FOR_VERIFICATION is execution evidence, not independent PASS or approval to proceed directly to child 02.

### Final handoff receipt

- `artifact://150`: plan hash unchanged; target HEAD `099e372c2eca32596a9db670c0f13a0a30ce2b7e`; expected worktree identity passed; implementation commit remains an ancestor of target branch.
- `git status --short` showed only this execution report and the pre-existing controller-owned ledger modification; `git diff --cached --name-only` was empty.
- Final `docker ps --format '{{.ID}} {{.Image}} {{.Status}}'` produced no running containers. Temporary smoke directory/server cleanup executed in the passing runtime test.

## Epoch 1 — Terminal evidence closing
- Implementer: DraftStorageImplementer
- Verifier: DraftStorageVerifier
- Independent verdict: LIGHT_PASS
- Required action: COMPLETE_CHILD
- Code head: 099e372c2eca32596a9db670c0f13a0a30ce2b7e
- Fix round: 0
- Fix commits: —
- Closing records add bookkeeping only; no additional product execution is claimed.


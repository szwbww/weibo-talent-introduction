# Child 05 — pending

Exact plan: docs/plans/2026-10-29/expert-access-05-pending-owner.md
Plan identity: sha256:557020506b327615acea966f31c3983fd5b3095ba2b3e3d3b00b7c11ba72a7bb
Not dispatched; controller supplies current product base and downstream evidence before execution.

## Epoch 1 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: a2d4b0e68691d115cff2a6ad3bcc115a06b28d27
- Exact plan: docs/plans/2026-10-29/expert-access-05-pending-owner.md
- Plan identity: sha256:557020506b327615acea966f31c3983fd5b3095ba2b3e3d3b00b7c11ba72a7bb
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit e9f93fb260c6b25518ccbbf579c420c8aa6cb406)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/resources/db/migration/V171__add_inbound_pending_owner.sql`
- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
New pending_owner_username admin/user1 is allocated only once in actual transaction/contact lock; V171 historical defaultadmin; downgrade/revoke/reopen fallback and all four narrow updates cannot overwrite owner. needsManualAttention remains global-to-expert.

### Prior evidence and command environment
Prior terminal product base is a2d4b0e68691d115cff2a6ad3bcc115a06b28d27, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 05; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/05/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 2 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: a2d4b0e68691d115cff2a6ad3bcc115a06b28d27
- Exact plan: docs/plans/2026-10-29/expert-access-05-pending-owner.md
- Plan identity: commit:6b950d7e0ce1e460e197645a6bcaca485ae83088
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 6b950d7e0ce1e460e197645a6bcaca485ae83088)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/resources/db/migration/V171__add_inbound_pending_owner.sql`
- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
New pending_owner_username admin/user1 is allocated only once in actual transaction/contact lock; V171 historical defaultadmin; downgrade/revoke/reopen fallback and all four narrow updates cannot overwrite owner. needsManualAttention remains global-to-expert.

### Prior evidence and command environment
Prior terminal product base is a2d4b0e68691d115cff2a6ad3bcc115a06b28d27, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 05; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/05/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

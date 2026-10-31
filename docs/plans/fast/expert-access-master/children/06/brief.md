# Child 06 — pending

Exact plan: docs/plans/2026-10-29/expert-access-06-pending-query.md
Plan identity: sha256:64b5c7f138adf0a2b7a5b58fb1dc979a59f48b7f6fe7a16253979982ba2b7626
Not dispatched; controller supplies current product base and downstream evidence before execution.

## Epoch 1 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: e4c5d83a580228a92f1dd2c427538829651a40a6
- Exact plan: docs/plans/2026-10-29/expert-access-06-pending-query.md
- Plan identity: commit:b8625c35f461816397b34d167493d62205e5f1d3
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 6b950d7e0ce1e460e197645a6bcaca485ae83088)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Owner-scoped pending flags/count/page/explain/badge/suspension/old queues, preserve All timeline and disabled-real-account visibility. Admin expertScope/filterCounts names and mail-vs-expert-vs-draft units are consumed by07/08.

### Prior evidence and command environment
Prior terminal product base is e4c5d83a580228a92f1dd2c427538829651a40a6, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 06; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/06/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 2 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: e4c5d83a580228a92f1dd2c427538829651a40a6
- Exact plan: docs/plans/2026-10-29/expert-access-06-pending-query.md
- Plan identity: commit:099ac62fc1cddae8d31ec5f0bee90f5c22acec95
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 099ac62fc1cddae8d31ec5f0bee90f5c22acec95)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Owner-scoped pending flags/count/page/explain/badge/suspension/old queues, preserve All timeline and disabled-real-account visibility. Admin expertScope/filterCounts names and mail-vs-expert-vs-draft units are consumed by07/08.

### Prior evidence and command environment
Prior terminal product base is e4c5d83a580228a92f1dd2c427538829651a40a6, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 06; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/06/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 3 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: e4c5d83a580228a92f1dd2c427538829651a40a6
- Exact plan: docs/plans/2026-10-29/expert-access-06-pending-query.md
- Plan identity: commit:e15b7b4c9efb1fc62dc3f5de85e7fa064437fba6
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit e15b7b4c9efb1fc62dc3f5de85e7fa064437fba6)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Owner-scoped pending flags/count/page/explain/badge/suspension/old queues, preserve All timeline and disabled-real-account visibility. Admin expertScope/filterCounts names and mail-vs-expert-vs-draft units are consumed by07/08.

### Prior evidence and command environment
Prior terminal product base is e4c5d83a580228a92f1dd2c427538829651a40a6, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 06; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/06/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 4 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: e4c5d83a580228a92f1dd2c427538829651a40a6
- Exact plan: docs/plans/2026-10-29/expert-access-06-pending-query.md
- Plan identity: commit:477ec60cfce0d60589bde5831053a75af98af9f5
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 477ec60cfce0d60589bde5831053a75af98af9f5)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Owner-scoped pending flags/count/page/explain/badge/suspension/old queues, preserve All timeline and disabled-real-account visibility. Admin expertScope/filterCounts names and mail-vs-expert-vs-draft units are consumed by07/08.

### Prior evidence and command environment
Prior terminal product base is e4c5d83a580228a92f1dd2c427538829651a40a6, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 06; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/06/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 5 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: e4c5d83a580228a92f1dd2c427538829651a40a6
- Exact plan: docs/plans/2026-10-29/expert-access-06-pending-query.md
- Plan identity: commit:8284ae04acaed6f7222774c8a4c6ac522f6690f0
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 8284ae04acaed6f7222774c8a4c6ac522f6690f0)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxSuspensionServiceIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Owner-scoped pending flags/count/page/explain/badge/suspension/old queues, preserve All timeline and disabled-real-account visibility. Admin expertScope/filterCounts names and mail-vs-expert-vs-draft units are consumed by07/08.

### Prior evidence and command environment
Prior terminal product base is e4c5d83a580228a92f1dd2c427538829651a40a6, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 06; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/06/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

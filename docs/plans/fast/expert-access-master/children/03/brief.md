# Child 03 — pending

Exact plan: docs/plans/2026-10-29/expert-access-03-resource-guard.md
Plan identity: sha256:0033f9c3d1460f43b2fc1e26a00250a88f679fcb410be0b51f02a2b018055e47
Not dispatched; controller supplies current product base and downstream evidence before execution.

## Epoch 1 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 6c0981eb4e148e9789c3af49e9d2dca9739c2508
- Exact plan: docs/plans/2026-10-29/expert-access-03-resource-guard.md
- Plan identity: sha256:0033f9c3d1460f43b2fc1e26a00250a88f679fcb410be0b51f02a2b018055e47
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 4961c395096eb4bda13e3014319aad01390d44f3)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Exact handler+verb and true-resource guard, authenticatedUsername service recheck, same two existing rich-send paths and attempts. Body-dependent handlers stay denied until04. Preserve admin/internal-auto/null contracts; never global-prefix allowlist.

### Prior evidence and command environment
Prior terminal product base is 6c0981eb4e148e9789c3af49e9d2dca9739c2508, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 03; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/03/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 2 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 6c0981eb4e148e9789c3af49e9d2dca9739c2508
- Exact plan: docs/plans/2026-10-29/expert-access-03-resource-guard.md
- Plan identity: commit:b8625c35f461816397b34d167493d62205e5f1d3
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit b8625c35f461816397b34d167493d62205e5f1d3)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialArchiveFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityServiceTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Exact handler+verb and true-resource guard, authenticatedUsername service recheck, same two existing rich-send paths and attempts. Body-dependent handlers stay denied until04. Preserve admin/internal-auto/null contracts; never global-prefix allowlist.

### Prior evidence and command environment
Prior terminal product base is 6c0981eb4e148e9789c3af49e9d2dca9739c2508, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 03; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/03/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 3 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 6c0981eb4e148e9789c3af49e9d2dca9739c2508
- Exact plan: docs/plans/2026-10-29/expert-access-03-resource-guard.md
- Plan identity: commit:42acd9796c75acf0603101b0ef97134477dba7d4
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 42acd9796c75acf0603101b0ef97134477dba7d4)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPaperArchiveControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryVerificationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryApolloSettingsIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialArchiveFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ReplyIdentityServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryIdentityRecoveryControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewCountsControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/controller/ExpertMeetingVideoControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertClassificationAdminControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/EmailSuppressionControllerAuthTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactLocationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactNoteControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertInboundNotificationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailOpenTrackingControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MeetingConfirmationControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplyIdentityDefaultsIntegrationTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Exact handler+verb and true-resource guard, authenticatedUsername service recheck, same two existing rich-send paths and attempts. Body-dependent handlers stay denied until04. Preserve admin/internal-auto/null contracts; never global-prefix allowlist.

### Prior evidence and command environment
Prior terminal product base is 6c0981eb4e148e9789c3af49e9d2dca9739c2508, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 03; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/03/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

# Child 04b — pending

Exact plan: docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md
Plan identity: sha256:3b01f10a42d1544992a23ff6166ccab9ba3638d504c75f67a3a8f26a3fa4b463
Not dispatched; controller supplies current product base and downstream evidence before execution.

## Epoch 1 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 020bda37127f92187589e9d8f10e3bb3cbccb187
- Exact plan: docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md
- Plan identity: sha256:3b01f10a42d1544992a23ff6166ccab9ba3638d504c75f67a3a8f26a3fa4b463
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit c3afa57cfe2b448470b8968ac7cc47e24963bbc9)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertScopedOperationsIT.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Current FULL+owner+real-target constraints on old personal drafts, SQL filtering before pagination, true calendar event/contact guard+CAS; review APPROVED immutable even after upgrade; no alternate send bypass.

### Prior evidence and command environment
Prior terminal product base is 020bda37127f92187589e9d8f10e3bb3cbccb187, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 04b; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/04b/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 2 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 020bda37127f92187589e9d8f10e3bb3cbccb187
- Exact plan: docs/plans/2026-10-29/expert-access-04b-calendar-drafts.md
- Plan identity: commit:e9f93fb260c6b25518ccbbf579c420c8aa6cb406
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit e9f93fb260c6b25518ccbbf579c420c8aa6cb406)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertScopedOperationsIT.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/MeetingCalendarControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingCalendarSendIntegrationTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Current FULL+owner+real-target constraints on old personal drafts, SQL filtering before pagination, true calendar event/contact guard+CAS; review APPROVED immutable even after upgrade; no alternate send bypass.

### Prior evidence and command environment
Prior terminal product base is 020bda37127f92187589e9d8f10e3bb3cbccb187, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 04b; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/04b/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

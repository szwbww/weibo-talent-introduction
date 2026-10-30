# Child 02 — pending

Exact plan: docs/plans/2026-10-29/expert-access-02-access-level.md
Plan identity: sha256:ff1565438734fd5ef9a20f7ca275354ae8b9b38f6fa7ea4d1153f39e9761ef99
Not dispatched; controller supplies current product base and downstream evidence before execution.

## Epoch 1 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 30c8af9b98f675f628d3ee98bd737f1716ecf54c
- Exact plan: docs/plans/2026-10-29/expert-access-02-access-level.md
- Plan identity: sha256:ff1565438734fd5ef9a20f7ca275354ae8b9b38f6fa7ea4d1153f39e9761ef99
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 08d6af05bae3349ca46f92df61b48ab0bac87183)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/resources/db/migration/V170__add_expert_mail_access_level.sql`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Expose accessLevel NONE/READ/FULL plus compatible canRead. Keep old no-body PUT non-downgrading. Bulk list projection and live message wrapper level are consumed by guard03 and workspace07; FULL attachments follow original feature/source checks. MigrationV170 only, after inheritedV169.

### Prior evidence and command environment
Prior terminal product base is 30c8af9b98f675f628d3ee98bd737f1716ecf54c, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 02; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/02/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 2 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 30c8af9b98f675f628d3ee98bd737f1716ecf54c
- Exact plan: docs/plans/2026-10-29/expert-access-02-access-level.md
- Plan identity: commit:1fd83001f99533b787b7dc6099678a910648fca2
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 1fd83001f99533b787b7dc6099678a910648fca2)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/resources/db/migration/V170__add_expert_mail_access_level.sql`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Expose accessLevel NONE/READ/FULL plus compatible canRead. Keep old no-body PUT non-downgrading. Bulk list projection and live message wrapper level are consumed by guard03 and workspace07; FULL attachments follow original feature/source checks. MigrationV170 only, after inheritedV169.

### Prior evidence and command environment
Prior terminal product base is 30c8af9b98f675f628d3ee98bd737f1716ecf54c, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 02; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/02/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 3 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 30c8af9b98f675f628d3ee98bd737f1716ecf54c
- Exact plan: docs/plans/2026-10-29/expert-access-02-access-level.md
- Plan identity: commit:4961c395096eb4bda13e3014319aad01390d44f3
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 4961c395096eb4bda13e3014319aad01390d44f3)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/resources/db/migration/V170__add_expert_mail_access_level.sql`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/OutboundAttachmentFlowTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewQueryIT.kt`
- `src/test/js/mailboxReadOnly.test.js`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Expose accessLevel NONE/READ/FULL plus compatible canRead. Keep old no-body PUT non-downgrading. Bulk list projection and live message wrapper level are consumed by guard03 and workspace07; FULL attachments follow original feature/source checks. MigrationV170 only, after inheritedV169.

### Prior evidence and command environment
Prior terminal product base is 30c8af9b98f675f628d3ee98bd737f1716ecf54c, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 02; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/02/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

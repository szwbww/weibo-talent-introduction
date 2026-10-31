# Child 04a — pending

Exact plan: docs/plans/2026-10-29/expert-access-04a-profile-tags.md
Plan identity: sha256:0ffa45716aa98398dceab5a11f581d5630b870e73541babd99fbb2b2af52e4aa
Not dispatched; controller supplies current product base and downstream evidence before execution.

## Epoch 1 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: d5093a2509427526784254aba7dbe98ff2a9b1d9
- Exact plan: docs/plans/2026-10-29/expert-access-04a-profile-tags.md
- Plan identity: sha256:0ffa45716aa98398dceab5a11f581d5630b870e73541babd99fbb2b2af52e4aa
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit 42acd9796c75acf0603101b0ef97134477dba7d4)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertScopedOperationsIT.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Current normalized expert identity and true-current-layer FULL checks; CAS profile behavior and strict403 mapping; READ labels remain readable but writes403. Do not expand mailbox contact authorization through shared expert profile.

### Prior evidence and command environment
Prior terminal product base is d5093a2509427526784254aba7dbe98ff2a9b1d9, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 04a; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/04a/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

## Epoch 2 — approved execution contract

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: d5093a2509427526784254aba7dbe98ff2a9b1d9
- Exact plan: docs/plans/2026-10-29/expert-access-04a-profile-tags.md
- Plan identity: commit:c3afa57cfe2b448470b8968ac7cc47e24963bbc9
- Governing master: docs/plans/2026-10-29/expert-access-master.md (commit c3afa57cfe2b448470b8968ac7cc47e24963bbc9)
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; amendments in ledger.

### Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertScopedOperationsIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerMvcTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryControllerTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`

### Implementation and invariants
Read entire exact child plan again with execute-p identity/worktree helpers. All child requirements, referenced masterI/S clauses, explicit must-not-change behavior, exact DOM/CSS, required machine commands and runtime acceptance govern. Master final UI arbitration applies: no obsolete top mailboxReviewPendingBtn. Scope is this exact list; do not edit plans/ledger or later children. Preserve previous committed output; do not reimplement prior child. No compatibility shim or permissive bypass.

### Required downstream contract
Current normalized expert identity and true-current-layer FULL checks; CAS profile behavior and strict403 mapping; READ labels remain readable but writes403. Do not expand mailbox contact authorization through shared expert profile.

### Prior evidence and command environment
Prior terminal product base is d5093a2509427526784254aba7dbe98ff2a9b1d9, NOT an interleaved evidence commit. The complete-prefix dirty and clean artifact checkpoints passed before this dispatch. Review05 backend/reader/admin flow is now committed and independently LIGHT_PASS_WITH_NOTES; its execution/verify logs andO-1 fixture timezone receipt are evidence, not waivers. Use updated genuineJava11 at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home; Docker unix:///Users/lukai/.orbstack/run/docker.sock and -DargLine=-Dapi.version=1.41 as required. External-JDBC fixtures need a fresh disposable MySQL and explicit SPRING_DATASOURCE_URL with serverTimezone=Asia/Shanghai; self-contained Testcontainers tests override their datasource. No production DB/SMTP/IMAP/ES. Required mysqlIt/migrationIt never counted as passing if skipped. Run all child-required exact commands freshly after final source changes; disclose failures and classify against recorded baseline without rerunning merely to confirm known failures. Browser-related child clauses require real Chromium at1440/390 and actual changed surface; distinguish mocked-provider fixtures from real authenticated backend behavior; never mark human acceptance complete.

### Evidence and commits
Use execute-p, exact identity before/after, preserve all unrelated changes. Actual changed-path smoke is mandatory, not just tests/source inspection. Run final commands after implementation, no concurrent build/test/formatter while writing. Commit product/test files only as feat(fast-p): implement 04a; controller owns report/evidence commits. Append full execution result, exact commands/exits/counts, runtime proof, current plan/worktree identity and committed SHA to docs/plans/fast/expert-access-master/children/04a/execution.md. No reports or approved plans in product commit. A necessary unlisted file/semantic conflict must be reported with exact evidence before mutation so controller can record the authorized bounded amendment; do not invent behavior, weaken tests, or implement later scope. No push, merge, rebase, amend, reset, deployment or new worktree.

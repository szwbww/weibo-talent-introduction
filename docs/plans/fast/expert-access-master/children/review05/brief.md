# Child review05 — approved execution brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master
- Branch: fast/expert-access-master
- child_base_sha: 59eb60cf00534a342c1019827a62f2b7b1db0f2b
- Exact plan: docs/plans/2026-10-29/draft-review-05-admin.md
- Plan identity: commit:08d6af05bae3349ca46f92df61b48ab0bac87183
- Master: docs/plans/2026-10-29/expert-access-master.md (commit 08d6af05bae3349ca46f92df61b48ab0bac87183); read its final UI arbitration and final correction records.
- Authority: HUMAN:授权 继续 后续改动都授权 直到自子计划全部完成 可以执行 review-fast-p — received after the expert-access preflight blocker on 2026-10-29; A1/A2/A3 in ledger.

## Contract
Read the entire exact child plan from this worktree using execute-p identity/worktree gates. Its final expert-access composition correction overrides obsolete top-toolbar requirements. No mailboxReviewPendingBtn is created; preserve server filter/count/selection API and verify through internal setReviewPendingOnly/API until original child08 exposes the final popover. No other deferral. Implement every review editor/approval/confirmation/send behavior now. Already verified review01–04 code is baseline; do not replay or edit those completed tasks except the single inherited strict-placeholder correction explicitly authorized in this plan. Do not read or copy the other worktree's uncommitted implementation.

## Authorized Files
- `src/main/resources/static/mailbox-chat.js`
- `src/main/resources/static/app.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/test/js/mailReviewAdmin.test.js`
- `src/test/js/mailboxServerDrafts.test.js`
- `src/test/js/mailReviewReader.test.js`
- `src/test/js/batchEntryRelocation.test.js`
- `src/test/js/discoveryRecheck.test.js`
- `src/test/js/mailboxMailReadAccess.test.js`
- `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundTrustWorkbenchTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt`

## Commands and runtime
Run all node --check, focused JS, full JS, full Maven and explicit mysqlIt/migrationIt commands required by the exact child plan and inherited01–03. Commands must be fresh after final code. Use Java11; known timezone init failure under old11.0.15 has an existing real updated runtime at /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home. Docker OrbStack endpoint unix:///Users/lukai/.orbstack/run/docker.sock; use argLine=-Dapi.version=1.41 if required. No production DB, SMTP, IMAP or deployment. No skipped required IT. Baseline failures/precise authorized fixes are in plan T5, not permission for unrelated fixes.

Required inherited DB selections: MailReviewDraftIT -DmysqlIt=true; AuthFlowIntegrationTest,FlywayMigrationIntegrationTest -DmigrationIt=true; MailReviewQueryIT,MailboxConversationRepositoryIT -DmysqlIt=true; MailboxConversationControllerTest; MailReviewSendIT,MailReplyDraftSendIntegrationTest -DmysqlIt=true; ManualReplySendAttemptServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest. Also inherited JS ragAdoptSendBridge/meetingConfirmationIntegration/mailboxOutboundAttachments. Preserve complete full-node/full-mvn gates.

## Runtime proof and downstream interfaces
Use actual browser at1440/390 and disposable isolated real HTTP/MySQL/SMTP fixtures to exercise the changed review path: unique existing editor, manual draft+attachments restored, pending review filter/count, state action matrix, approve and context-confirm produce no send, explicit send only once, changed context and stale async isolation. Browser fixture harness can serve real production modules with deterministic test API where appropriate; distinguish fixture-only visual evidence from actual backend smoke. Do not mark human acceptance complete. Preserve review model/service/controller contracts, reviewPendingOnly query and reader01–04 behavior for all later expert children.

## Execution and evidence
Use execute-p; record exact plan hash, commands/exits/counts and smoke evidence. One writer, no mid-flight build/test/formatter when coordinating edits; final required gates after implementation. Commit product/tests only as feat(fast-p): implement review05; no reports/plans in that commit. Append execution result to docs/plans/fast/expert-access-master/children/review05/execution.md; controller owns ledger/verify/fix logs. Scope conflict: report the concrete requirement/file without editing a plan; controller records authorized bounded amendment. No broad redesign, aliases, bypasses, weakened tests, source/wording pins, pushes, merges or history rewrites.

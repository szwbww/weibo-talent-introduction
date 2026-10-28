# Fast-P Child Brief — 02

- Plan (exact approved contract): `docs/plans/2026-10-27/user1-mail-02-mail-queries.md` (identity `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`)
- Master plan: `docs/plans/2026-10-27/user1-mail-master.md` (identity `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
- Branch: `fast/user1-mail-master`
- Base / product boundary: `b12b53625e2fb2e155f13e830c8647a453c13ddb` (child 01 Code head)
- Depends on: 01 (verified). You are child 2 of 5.
- Read the child plan and master plan before editing. They are the complete approved contract.

## Outputs from child 01 that you must reuse, not re-invent

- `com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy`: `READER_USERNAME="user1"`, `isReader(username)`, `MAIL_READ_FORBIDDEN` code, and `MailReadAccessDeniedException(code, message)` mapped to HTTP 403 by `GlobalExceptionHandler`.
- `com.weibo.talentintroduction.mail.service.ExpertMailReadAccessService` is the admin-side grant writer/reader; do NOT call it from the reader path and do NOT add a second grant table or cache.
- The 01 interceptor already default-denies user1 for every handler except GET list/messages and POST logout. This child adds the SQL-level scope and detail admission behind those two allowed GETs.

## Master constraints for this child

- Master I-1: permission comes from session + DB only; never trust query username or client role.
- Master I-2: no grant row = no body access; list count/page and detail apply the same authorization in SQL. Revocation must deny any request that starts after the delete commit; an in-flight already-admitted request may finish. No permission cache/push.
- Master I-3: reader responses fixed `attachmentCount=0`, `firstAttachmentNames=[]`, `calendarAttachment=null`, `outboundAttachments=[]`; no attachment resolution, no attachment URL generation.
- Master I-4: admin behavior unchanged (same SQL/fields/sort/attachments). Mailbox range still includes disabled historical accounts and excludes SIMULATOR_NOOP. No new persistent writes from reads.
- Master I-5: login remains admin-only in this step; do not create user1 and do not change the login allowlist.
- Scope: modify ONLY the 6 files in the child plan's `## 变更文件清单`. An unavoidable seventh file, new API, or plan conflict → return PLAN_CONFLICT/PAUSE; do not edit outside scope.

## Authorized files (exactly these 7; 7th added by approved amendment A1/A2, commit d66f542)

1. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxReadAccessProjectionTest.kt` (new)
7. `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt` (minimal `listMessages` mock sync only; no assertion semantics change)

## Child invariants (verbatim from the plan)

- I-1: for user1, the shared `expertPredicates` used by count/page/explain appends `grant EXISTS AND` outside all OR conditions. No take-all-then-filter; total counts only experts with mail AND an active grant; empty grant set → total=0/items=[]. Existing q/status/account/direction/date filters must be fully parenthesized before the AND.
- I-2: Controller passes session identity to the Service. user1 checks `hasReadAccess` before contact existence check, body/tag/attachment queries; missing grant or missing expert both produce 403 `MAIL_READ_FORBIDDEN` without leaking existence. Check on every before/accountCode change; cursor stays bound to expert and account.
- I-3: reader projection uses the fixed empty attachment values above and skips `resolveMessageAttachments` plus the outbound-attachment snapshot read. Body text is allowed; no attachment file content or URLs.
- I-4: non-user1 keeps the original SQL/fields/sort/attachment resolution. New identity parameters are appended with default null to preserve existing internal callers/tests, but the HTTP controller must pass the session explicitly. `activeAccountCodes` still excludes SIMULATOR_NOOP and keeps disabled historical mailboxes. Reads add no persistent writes.

## Downstream interfaces later children rely on

- Conversation list response shape stays exactly as today (`items,total,page,size` with existing item fields), scoped for user1; 03 consumes it unchanged with size 20.
- Detail/timeline response keeps `nextBefore`/`hasMore` cursor semantics; reader attachment fields are exactly `0/[]/null/[]`.
- Denied detail uses HTTP 403 + `MAIL_READ_FORBIDDEN`.
- Do not change `MailboxConversationRepository.timelineMessages` signature or semantics; admission lives in the Service.
- Preserve constructor shapes used by existing tests (`OutboundAttachmentFlowTest`, `MailReplyDraftSendIntegrationTest` manually construct objects).

## Required commands (run fresh; record exit codes and counts)

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=MailboxReadAccessProjectionTest,OutboundAttachmentFlowTest,AuthInterceptorTest test`
- `DB_URL='jdbc:mysql://127.0.0.1:3307/talent_introduction_user1_it?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest,ExpertMailReadAccessIT test`
- `git diff --check`

`CalendarAttachmentIntegrationTest` is a second class declared in `MailboxConversationControllerTest.kt`; listing it in `-Dtest` is correct.

Baseline to compare (must not add new failures): `MailboxConversationRepositoryIT` at base had 35 tests, 0 failures, exactly 1 pre-existing error: `replied filter excludes followed and dismissed experts until a new inbound arrives` (`expert_replied_dismissal.dismissed_at` has no default). That test bug is out of scope and must not be "fixed" or hidden. Every other IT class passed at base.

## Output

- Commit the implementation locally as `feat(fast-p): implement 02`.
- Exclude `docs/plans/fast/**` from the implementation commit.
- Write the full result to `docs/plans/fast/user1-mail-master/children/02/execution.md` (untracked evidence; the controller commits it separately).
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.

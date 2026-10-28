# Fast-P Child Brief — 01

- Plan (exact approved contract): `docs/plans/2026-10-27/user1-mail-01-access-boundary.md` (identity `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`)
- Master plan: `docs/plans/2026-10-27/user1-mail-master.md` (identity `commit:4d1698bb7c51120e98cfe8f6a412a4589fc9defd`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
- Branch: `fast/user1-mail-master`
- Base: `4d1698bb7c51120e98cfe8f6a412a4589fc9defd`
- Depends on: none. You are child 1 of 5.
- Read the child plan and the master plan before editing. They are the complete approved contract.

## Master constraints for this child

- Master I-1: permission comes from session + DB only. user1's business APIs default-deny; only the allowlisted handlers pass. No client role/query trust.
- Master I-2: no grant row = no body access; list count/page and detail each apply the same authorization in SQL. Do not add a permission cache.
- Master I-3: reader has no send/attachment ability.
- Master I-4: admin and background automation must not regress; do not rewrite existing send/receive services; mailbox scope still includes disabled historical accounts and excludes SIMULATOR_NOOP.
- Master I-5: this step keeps admin-only login. Do NOT create user1, do NOT open login, do NOT change mail queries. Those are children 02/05.
- Scope: modify ONLY the 10 files in the child plan's `## 变更文件清单`. A required new file/API/behavior outside that list is a PLAN_CONFLICT; stop and report it.

## Authorized files (exactly these 10)

1. `src/main/kotlin/com/weibo/talentintroduction/auth/config/MailReadOnlyPolicy.kt` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptor.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/common/controller/GlobalExceptionHandler.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt`
5. `src/main/resources/db/migration/V166__create_expert_mail_read_access.sql` (new)
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessService.kt` (new)
7. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertMailReadAccessController.kt` (new)
8. `src/test/kotlin/com/weibo/talentintroduction/auth/config/AuthInterceptorTest.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/auth/controller/AuthControllerMvcTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt` (new)

## Child invariants (verbatim from the plan)

- I-1: `MailReadOnlyPolicy.isReader(username)` returns true only for exact `user1`; login allowlist stays admin in this step. For an authenticated user1, the real MVC handlers allowed are GET `ConversationController.list/messages` and POST `AuthController.logout`; login/me keep the original exclusion rules and OPTIONS keeps current behavior. Every other mapped `/api/**` request is 403. Unauthenticated is still 401; nonexistent routes may 404 and unsupported methods may 405 — do not require every unknown URL to be 403.
- I-2: PK `(username, expert_contact_id)`; username fixed `user1`; `expert_contact_id` positive with existing FK; row presence = allowed, absence = denied; no enabled/role/read/send redundant columns. `granted_by` only from the admin session; `created_at` written on first grant, repeat PUT does not refresh it, DELETE then PUT produces a new time.
- I-3: GET/PUT/DELETE grant endpoints check exact admin session before reading contact/grant; anonymous 401, other identities 403; target user/grantedBy/permission enum never accepted from body. Bad parameters 400, admin querying a missing expert 404. Only the new table is written; no follow/suspend/mail-status/ES writes.
- I-4: login/me response gains derived `mailReadOnly:Boolean=false`; true only for exact user1, anonymous false, no session role or DB field. Permission exceptions map to explicit 403, never 500. Keep old response fields and session rotation.

## Downstream interfaces later children rely on

- Response field name `mailReadOnly` on login/me.
- Authorization table `expert_mail_read_access(username, expert_contact_id, granted_by, created_at)`.
- Endpoints `GET/PUT/DELETE /api/mail/mailbox/conversations/{contactId}/read-access/user1` returning `{username:"user1",contactId,canRead}`.
- Error codes `READ_ONLY_FORBIDDEN`, `MAIL_READ_FORBIDDEN`, `ADMIN_REQUIRED` with HTTP 403.
- Do not change `AuthInterceptor` constructor parameters (existing tests construct it manually).

## Required commands (run fresh; record exit codes and counts)

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=AuthInterceptorTest,AuthControllerMvcTest test`
- Isolated MySQL (see below) `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Pmysql-it -Dtest=ExpertMailReadAccessIT test`
- `git diff --check`

The isolated MySQL is a dedicated container started by the controller:
`DB_URL='jdbc:mysql://127.0.0.1:3307/talent_introduction_user1_it?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=root`.
Never point tests at another database. Never report a skipped IT as PASS.
Baseline note: the pre-existing `MailboxConversationRepositoryIT.replied filter ...` error is not in this child's command set.

## Output

- Commit the implementation locally as `feat(fast-p): implement 01`.
- Exclude `docs/plans/fast/**` from that commit.
- Write the full result to `docs/plans/fast/user1-mail-master/children/01/execution.md` (untracked evidence; the controller commits it separately).
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.

# Fast-P Child Brief — 05

- Plan (exact approved contract, amended by A5/A6): `docs/plans/2026-10-27/user1-mail-05-account-activation.md` (identity `commit:258f435c8faefb15c187c6cd34669ac0ad92a717`)
- Master plan: `docs/plans/2026-10-27/user1-mail-master.md` (identity `commit:258f435c8faefb15c187c6cd34669ac0ad92a717`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-user1-mail-master`
- Branch: `fast/user1-mail-master`
- Base / product boundary: `419f49ad6182dd1f62a1445c0f3cc582c21e1d5b` (child 04 Code head)
- Depends on: 01–04 (verified). You are child 5 of 5.
- Read the child plan (including the A5 sentence in step 3) and the master plan before editing.

## Outputs from children 01–04 that must keep working

- Auth metadata `mailReadOnly` and the exact-user1 policy; `expert_mail_read_access` + admin grant API.
- Reader SQL scoping/detail admission and 403 `MAIL_READ_FORBIDDEN`.
- `mailbox-readonly.js?v=20261027-user1-mail-access` registered immediately before `app.js`; admin grant header control in `mailbox-chat.js`.
- This child only creates the fixed account and opens login. Do not change those behaviors.

## Master constraints for this child

- Master I-1: login accepts only exact admin/user1 after trim; identity from DB/session, never client role.
- Master I-2: no grant row = no body; this child creates no grant.
- Master I-3: reader has no send/attachment/change-password ability.
- Master I-4: admin login/change-password and existing automation must not regress; a real-login gate is required and must not be replaced by a static preview.
- Master I-5: this is the last step; only now may user1 log in.
- Scope: modify ONLY the 4 files below. No new API/field/schema; any required 5th file is a PLAN_CONFLICT.

## Authorized files (exactly these 5; 5th added by approved amendment A6/A7, commit 258f435)

1. `src/main/kotlin/com/weibo/talentintroduction/auth/service/ReadOnlyUserBootstrap.kt` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/auth/controller/AuthController.kt` (open the allowlist to admin/user1 only)
3. `src/test/kotlin/com/weibo/talentintroduction/auth/service/ReadOnlyUserBootstrapTest.kt` (new)
4. `src/test/kotlin/com/weibo/talentintroduction/auth/AuthFlowIntegrationTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/auth/controller/AuthControllerMvcTest.kt` (replace only the obsolete user1-login-rejected test with accepted login plus non-whitelisted rejection; keep all other assertions)

## Child invariants (verbatim from the plan)

- I-1: username exactly `user1`; password `qfuser1` BCrypt-encoded live by the existing PasswordEncoder; `mustChangePassword=false`; `createdAt`/`updatedAt` set on creation, `lastLoginAt` initially null. An existing user1 row is NEVER reset (password/flags/times). On unique-key concurrency conflict, re-query and ignore only if the row exists; otherwise rethrow. Logs must not print the password/hash.
- I-2: AuthController allows only trimmed `admin`/`user1`; password verification still goes through AuthService/BCrypt; session ID rotation and DB-canonical username preserved; `mailReadOnly` derived server-side; wrong password creates no authenticated session; `qfuser1` is only the initial seed.
- I-3: bootstrap writes no grant row; no grant means an empty list; existing grants keep working. user1 has no change-password/send/attachment permission; admin first-login/change-password behavior unchanged.
- I-4: only publish a build containing this step after 01–04 pass and `auth.enabled=true`; machine passing does not replace human acceptance; skipped MySQL IT is not a pass; a static preview is not real-service verification.

## Test-context wiring authorized by A5

The mandated real-login class is currently blocked at base by a pre-existing Spring test-context defect: top-level test `@Configuration` classes are component-scanned into the full `@SpringBootTest` (first `AttachmentTransferWorker` override conflict, then two `NamedParameterJdbcTemplate` beans). A5 allows ONLY inside `AuthFlowIntegrationTest.kt`:
- `spring.main.allow-bean-definition-overriding=true` for this test context, and
- one `@Primary NamedParameterJdbcTemplate` test bean (imported test configuration) so the ambiguous autowiring resolves.

This is test-only. Do not change product code or other tests for this defect. Record the original mandated command failure as pre-existing evidence and the wired run separately; both must be in the report. Do not hide or skip the original failure.

The `ReadOnlyUserBootstrapTest` should cover: first creation (BCrypt matches, hash != plaintext, mustChange=false), repeated run does not reset a modified hash/times, concurrent unique-key conflict re-query success, non-existence errors rethrown, and `auth.enabled=false` does not create the account (use the same conditional/property mechanism as `AdminUserBootstrap`).

`AuthFlowIntegrationTest`: keep the existing "only one user" assertion in the admin-only bootstrap test. New cases must explicitly run admin+user1 bootstraps, assert the exact username set `{admin,user1}`, real user1 login/me/logout, wrong password/other usernames rejected, restart does not reset, and user1 permission negatives (grant unchanged by bootstrap; empty grant empty list; authorized A only, B 403; attachment/send/change-password 403).

## Required commands (run fresh; record exit codes/counts)

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=ReadOnlyUserBootstrapTest,AdminUserBootstrapTest,AuthControllerMvcTest,AuthInterceptorTest test`
- `JAVA_HOME=... mvn -B test` (full suite). Baseline at this branch: exit 1, Tests run 5645, Failures 0, Errors 19, Skipped 24; all 19 are pre-existing `ExpertContactLocationServiceTest.<init>:38` country-CL timezone errors. The Maven build stops at surefire, so run `node --test src/test/js/*.test.js` separately to prove the JS gate (baseline 1911 pass, 0 fail). Do not fix the 19 pre-existing errors.
- `JAVA_HOME=... mvn -B -DskipTests package`
- AuthFlow original command (must be run and recorded even though it fails pre-existing): `DOCKER_API_VERSION=1.40 JAVA_HOME=... mvn -B -Pmigration-it -Dtest=AuthFlowIntegrationTest test`
- AuthFlow wired run: same command plus `-Dspring.main.allow-bean-definition-overriding=true` (the @Primary bean comes from the test source), on Docker/Testcontainers MySQL 8.0.36. This must execute the real login cases.
- Isolated MySQL group: `DB_URL='jdbc:mysql://127.0.0.1:3307/talent_introduction_user1_it?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=... mvn -B -Pmysql-it -Dtest=ExpertMailReadAccessIT,MailboxConversationRepositoryIT,MailboxConversationControllerTest,CalendarAttachmentIntegrationTest,MailReplyDraftSendIntegrationTest test`
  Baseline: 127 tests, 7 pre-existing errors (1 `expert_replied_dismissal.dismissed_at` + 6 `CalendarAttachmentIntegrationTest` context). No new errors allowed.
- `git diff --check`

Never point tests at another database. A skipped IT is not a pass. Docker API version 1.40 is required for Testcontainers in this environment.

## Output

- Commit the implementation locally as `feat(fast-p): implement 05`.
- Exclude `docs/plans/fast/**` from the implementation commit.
- Write the full result to `docs/plans/fast/user1-mail-master/children/05/execution.md` (untracked; the controller commits it separately).
- Return only: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, commit SHA, command summary, report path.

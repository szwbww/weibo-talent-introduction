# Child Brief: 01-history-query

## Approved plan
`docs/plans/2026-09-26/batch-email-01-history-query.md` at `commit:38ba555b4147970ee77569e71f863955e2c4a2b5`. Read the exact plan in full before implementation. Human-approved target amendment A2 binds it to `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun` on `fast/batch-email-reliability-rerun`. Product base: `64c0394a940bd79c2ecc04e5c497650f045faa75`.

## Authorized files
- `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/task/service/TaskRetentionMigrationTest.kt`

## Key invariants and downstream contract
- One effective-history predicate: raw request_count>0, reused_from_id IS NULL, error_code IS NULL, checked_at in (now minus one year, now], and plan I-1 PASS/SKIP combinations.
- Choose newest effective raw record per normalized email by checked_at DESC, id DESC before deciding undeliverable. ERROR/reused/expired rows do not supersede valid raw history.
- Read-only parameterized batches ≤500, no SQL on empty input; trim/lowercase(Locale.ROOT), preserve +tag/dots; fixed Beijing-time now. Single-email lookup delegates to same read logic.
- Retention protects PASS deliverable/risky/unknown without changing cleanup bounds/order.
- No new schema/cache/ES/HTTP/writes. Child 02 consumes `findKnownUndeliverableEmails(emails, now)`; returns normalized undeliverable emails and propagates database errors.

## Required commands
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,TaskRetentionMigrationTest test`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=BatchEmailVerificationRepositoryIT test` (Docker API override if necessary; skipped tests do not pass)
- `git diff --check`

Use the previous product commit `52ff6e4fc7a09d9daa4e2328adbce18de0331eb3` only as a candidate patch; inspect its diff against this exact plan, transplant only authorized changes, and freshly run the required commands. Do not carry old evidence as current evidence. Skip formatter, linter, and project-wide suites. Commit only product/test implementation as `feat(fast-p): implement 01-history-query`; the controller commits fast-p evidence separately.

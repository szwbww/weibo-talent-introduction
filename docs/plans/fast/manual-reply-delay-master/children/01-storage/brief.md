# Child Brief — 01-storage

## Identity
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
- Branch: fast/manual-reply-delay-master
- Child base SHA: 05342b4be63bbc7d9b30416ae73ada2d44b562df
- Planned implementation commit subject: `feat(fast-p): implement 01-storage`

## Contract sources (read before editing)
- Master plan: docs/plans/2026-11-04/manual-reply-delay-master.md
- Child plan: docs/plans/2026-11-04/manual-reply-delay-01-storage.md
- Evidence directory: docs/plans/2026-11-04/manual-reply-delay-evidence/ (schemas.md, source-manifest.json, 06-schema-tests.txt, 09-followup-source.md)
- The child plan is the complete approved contract. Where the plan and this brief differ, the plan wins; the brief only states fixed gates.

## Authorized files (exactly these 7; no other product/test files)
1. src/main/resources/db/migration/V174__create_delayed_manual_reply.sql (new)
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt (new)
3. src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt (new)
4. src/main/kotlin/com/weibo/talentintroduction/config/ManualReplyDelayProperties.kt (new)
5. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/InboundPendingOwnerIT.kt
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailReadAccessIT.kt

Do not modify pom.xml, application.yml, CLAUDE.md, or any fast-p evidence file. The fast-p controller owns all docs/plans/fast artifacts.

## Fixed requirements
- Implement child plan sections T1 (table), T2 (snapshot codec), T3 (repository + properties), T4 (migration test updates) exactly as written.
- Highest existing migration is V173; V174 is free. Never edit an applied migration.
- Invariants: child I-1 (epoch milliseconds, DB clock, due=now+60000), child I-4 (schemaVersion=1 explicit DTO whitelist, no entities/credentials/file bytes, unknown version fails closed, 4 MiB limit), child I-10 (six job statuses only, existing attempt enum unchanged), plus master I-1/I-3/I-4/I-10.
- Spring Data JDBC is not used for this table; follow the existing NamedParameterJdbcTemplate/GeneratedKeyHolder/RowMapper/MANDATORY transaction patterns in MailReplyDraftRepository.kt.
- No SMTP, no HTTP, no worker, no enqueue callers in this child. Entry points stay off; `enabled` defaults false.
- Keep every existing public API/behavior unchanged; only additive repository/model/properties types.
- Flyway test updates must follow K-flyway-latest-version-test-pin: update the fresh-chain expectation to 174; historical upgrade tests keep their historical expectations and add explicit target versions (172) as the plan states; do not renumber unrelated expectations.
- Java 11 only.

## Required commands (run all, record exit codes and executed/skipped counts)
Use: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

1. `mvn -q -DskipTests test-compile`
2. `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true`
3. Any narrower test class you add/need for codec/repository compile-time or pure-unit proof (optional; list what you ran).

Docker is available for Testcontainers. Do not point any test at a production DB.

## Reporting
Write the full execution report to docs/plans/fast/manual-reply-delay-master/children/01-storage/execution.md (append-only; include commands with exit codes, files changed, key decisions, skipped items and why). Do not include fast-p evidence files in the implementation commit.

Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.

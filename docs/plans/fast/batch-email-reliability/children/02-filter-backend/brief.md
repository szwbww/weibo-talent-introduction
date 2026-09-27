# Child Brief: 02-filter-backend

## Approved plan
`docs/plans/2026-09-26/batch-email-02-filter-backend.md` at `commit:38ba555b4147970ee77569e71f863955e2c4a2b5`. Read the exact plan in full. Target: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun`, `fast/batch-email-reliability-rerun`, amendment A3. Depends on child 01; the product base is its terminal Code head recorded in the ledger, NOT its evidence commit.

## Authorized files
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`
- `src/main/resources/db/migration/V142__add_exclude_verified_unavailable_emails.sql`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`

## Key invariants and downstream contracts
- `excludeVerifiedUnavailableEmails`: DB/entity/view/snapshot/scope default false; new command default true; nullable update preserves existing; migration BOOLEAN NOT NULL DEFAULT FALSE. Legacy adapter retains field.
- Filtering independent of live verification. Off: no history lookup. On: child 01 read-only helper, no API key/paid call. Only current send address; preserve ordering, gates, dedup, quotas; no writes for pre-excluded.
- Estimate and new/retry/material reminder candidates use same filter. Material reminder uses contact.expertEmail; derive `excludedVerifiedUnavailable`. On estimate uses existing scroll; no whole-dataset collection.
- Iterate only after raw page length controls termination/offset; fully filtered page still continues; preserve shrinking buffer, cancellation and status.
- Only V142 as migration; no other schema or authorized-file expansion. Child 03 consumes this backend config/filter/count contract.

## Required commands
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchSendTaskConfigServiceTest,ManualInitialOutreachServiceTest,OutreachTargetIteratorTest,BatchSendTaskRuntimeIntegrationTest test`
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` (Docker API override if necessary; skip is not pass)
- `git diff --check`

Previous product commit `98880fba7b17af687a8bfa444da5716a27256479` is a candidate patch only. Reconcile against exact plan, rerun required commands freshly, and commit only product/test files as `feat(fast-p): implement 02-filter-backend`. Skip formatter, linter, project-wide suite. Controller commits evidence later.

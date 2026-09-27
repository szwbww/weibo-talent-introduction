# Child Brief: 03-failure-policy

## Approved plan
`docs/plans/2026-09-26/batch-email-03-failure-policy.md` at `commit:38ba555b4147970ee77569e71f863955e2c4a2b5`. Read exact plan in full. Target: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun`, `fast/batch-email-reliability-rerun`, amendment A4. Follow children 01 and 02 in master order; product base is child 02 terminal Code head, not evidence commit.

## Authorized files
- `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
- `src/main/resources/static/app.js`
- `src/main/resources/static/index.html`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt`
- `src/test/js/batchEmailVerification.test.js`

## Key invariants and downstream contract
- Only INCOMPLETE, TIMEOUT, BAD_RESPONSE defer current recipient and continue; AUTH, NO_CREDITS, RATE_LIMITED, SERVICE_ERROR, AUDIT_FAILED and unknown codes stop execution. Preserve 249 protocol retry and risky/unknown behavior.
- Deferred audit remains ERROR + SKIPPED + EMAIL_VERIFICATION_DEFERRED + NOT_REQUIRED with error details; no invalid label or undeliverable cache, no send. Audit write failure stops. Deferred targets count processed/rejected/skipped, not sent; continue and honor cancel.
- Global failure retains true sent/remaining and FAILED/PARTIAL_SUCCESS; legacy runtime PAUSED with reason; modern entry leaves future autoEnabled/cron. SMTP partial success unaffected.
- UI distinguishes actual DEFERRED rows from older ERROR rows; original error code and count retained; exact approved copy and existing warning/error classes, no CSS.
- Preserve child 02 filtering contract; no extra schema/HTTP protocol changes/classes/files. Child 04 consumes status copy/API shape.

## Required commands
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchEmailVerificationServiceTest,ManualInitialOutreachServiceTest,BatchSendControlServiceTest test`
- `node --test src/test/js/batchEmailVerification.test.js`
- `git diff --check`

Previous product commit `3208be8a98dc18a96b41fc8a472552dd6dc98687` is a candidate patch only. Reconcile with approved plan; run focused commands freshly. Skip formatter, linter, project-wide suite. Commit only authorized product/test implementation as `feat(fast-p): implement 03-failure-policy`; evidence separately controlled.

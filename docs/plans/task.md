# Task List: 2026-06-12-manual-bulk-outreach-autoreply-toggle-checkreplies-verification-fix-plan

| Task ID | Task Description | Status |
|---|---|---|
| Task-01 | Remove and rotate plaintext credentials from application.yml and run secret scan | done |
| Task-02 | Design and migrate durable mail send attempt database table, schema migration V23, entity and repository | done |
| Task-03 | Refactor ManualInitialOutreachService: phased exceptions, durable attempts, stable Message-IDs, UNKNOWN handling | done |
| Task-04 | Fix mail failure audit fields (errorSummary) and timeline API response | done |
| Task-05 | Wrap manualOutreachExecutor.execute in MailAutomationController to handle RejectedExecutionException and clean up progress | done |
| Task-06 | Fix Expert page dropdown menu (taskMenuToggle ID) and check-replies error handling branch in app.js | done |
| Task-07 | Implement missing tests 1-17, idempotent failure injection integration test, and frontend DOM tests | done |
| Task-08 | Run full verification suite (Maven, Node tests, git diff --check) and clean formatting | done |

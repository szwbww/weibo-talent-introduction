# Task List: 2026-06-22-mailbox-tab-plan

> Plan: `docs/plans/2026-06-22-mailbox-tab-plan.md`

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | MailRecordRepository.kt: Add listMailbox and countMailbox `@Query` methods | done | Added methods and MailboxRow projection. |
| Task-02 | MailboxController.kt: Define MailboxRow, MailboxItemResponse, MailboxListResponse | done | Defined DTO classes. Tested in MailboxControllerTest.kt |
| Task-03 | MailboxService.kt: Implement listMailbox with active accounts check | done | Implemented active accounts validation and query delegation. Tested in MailboxServiceTest.kt |
| Task-04 | MailboxController.kt: Add GET /api/mail/mailbox endpoint | done | Exposed endpoint mapping. Tested in MailboxControllerTest.kt |
| Task-05 | index.html: Add "收发件箱" sidebar tab button and view HTML | not_started | |
| Task-06 | app.js: Load and render mailbox list, handle filters & pagination | not_started | |

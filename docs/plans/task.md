# Task List: 2026-06-22-email-provider-filter-plan

> Plan: `docs/plans/2026-06-22-email-provider-filter-plan.md`

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | ExpertSearchService: aggregateEmailDomains, emailDomain wildcard filter in search & notContactedWithEmailFilters | done | Implemented aggregateEmailDomains aggregation, wildcard emailDomain filter on searchExperts and notContactedWithEmailFilters. Tested in ExpertSearchServiceTest.kt. |
| Task-02 | ExpertIndexController: expose /email-providers aggregation endpoint, pass emailDomain parameter to searchExperts | not_started | |
| Task-03 | BatchSendSettingService: add key/default/class fields for batchSend.emailDomain | not_started | |
| Task-04 | ManualInitialOutreachService: apply emailDomain filter to countPending and runScheduledBatch | not_started | |
| Task-05 | index.html: add email provider filter dropdown and batch-send config dropdown UI | not_started | |
| Task-06 | app.js: load email provider domains, append filter parameter, map batch-send form read/write | not_started | |

# Task List: 2026-06-22-email-provider-filter-plan

> Plan: `docs/plans/2026-06-22-email-provider-filter-plan.md`

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | ExpertSearchService: aggregateEmailDomains, emailDomain wildcard filter in search & notContactedWithEmailFilters | done | Implemented aggregateEmailDomains aggregation, wildcard emailDomain filter on searchExperts and notContactedWithEmailFilters. Tested in ExpertSearchServiceTest.kt. |
| Task-02 | ExpertIndexController: expose /email-providers aggregation endpoint, pass emailDomain parameter to searchExperts | done | Exposed GET /api/experts/email-providers and added emailDomain query parameter to listExperts. Tested in ExpertIndexControllerTest.kt. |
| Task-03 | BatchSendSettingService: add key/default/class fields for batchSend.emailDomain | done | Added KEY_EMAIL_DOMAIN and DEFAULT_EMAIL_DOMAIN. Updated BatchSendConfig and BatchSendConfigUpdateRequest to carry emailDomain. Tested in BatchSendSettingServiceTest.kt. |
| Task-04 | ManualInitialOutreachService: apply emailDomain filter to countPending and runScheduledBatch | done | countPending and runScheduledBatch query with the configured emailDomain filter. Tested in ManualInitialOutreachServiceTest.kt. |
| Task-05 | index.html: add email provider filter dropdown and batch-send config dropdown UI | done | Added expertEmailDomainFilter select dropdown in the main contacts toolbar, and batchSendEmailDomain select dropdown inside the batch config form. |
| Task-06 | app.js: load email provider domains, append filter parameter, map batch-send form read/write | done | Implemented loadEmailProviders. Wired event listeners, updated query parameters mapping, and verified form field sync. Verified in Node.js tests in batchSendControls.test.js. |

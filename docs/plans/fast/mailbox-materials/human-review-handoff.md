# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: 8a0c5360e25e875e52800d17797a7b1ea4bd452c
- Current/final code head: 009d9bab431c75184b00659905f80dcde91e3166
- Branch/worktree: fast/mailbox-materials / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials

## Child Status
| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---|---|
| 01 | LIGHT_PASS_WITH_NOTES | 8a0c5360e25e875e52800d17797a7b1ea4bd452c..8779d71f00567625ebebe206801994aefcc5725b | 0 | b8b6ae56db91c5c1f93c12148bf9fc99ea9c664d |
| 02 | LIGHT_PASS | 8779d71f00567625ebebe206801994aefcc5725b..7c2420e80f98775c1fcf4970598617faca378c0b | 0 | dd236c587848ea45701ab1fea10debb1d1aeeb60 |
| 03 | LIGHT_PASS_WITH_NOTES | 7c2420e80f98775c1fcf4970598617faca378c0b..95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 | 1 | c10f3b4914a01e2e66a3e0054f9fa0335132090e |
| 04 | LIGHT_PASS | 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79..8ba24989302ed4a4e6d0f58a70cb5e19a2ed7cd2 | 1 | 7b051f433648ff1a63266fa5caa304643113073f |
| 05 | LIGHT_PASS_WITH_NOTES | 8ba24989302ed4a4e6d0f58a70cb5e19a2ed7cd2..8766c1f5dda3ffe5fd3b0228e635f69ea20ee7b2 | 0 | 00da92e38b3a7df3cb20cf7f24335cd14b2fb526 |
| 06 | LIGHT_PASS_WITH_NOTES | 8766c1f5dda3ffe5fd3b0228e635f69ea20ee7b2..b36bcb49d04f4651c4b01fc524f40fc322678806 | 0 | ec0951cb9d77b708c7d68c6529823d902fd3229d |
| 07 | LIGHT_PASS_WITH_NOTES | b36bcb49d04f4651c4b01fc524f40fc322678806..ca0611cf04e499a5d0e08ffaea1e20abb635bb66 | 0 | bead9c99e6ffbb9d43608433e6405ec0a8c2a948 |
| 08 | LIGHT_PASS_WITH_NOTES | ca0611cf04e499a5d0e08ffaea1e20abb635bb66..b08d6e4a3f3c86bab6103a40fe1bd9168ba7fabe | 0 | 15af0e90f63423895167d32766221400554017a6 |
| 09 | LIGHT_PASS_WITH_NOTES | b08d6e4a3f3c86bab6103a40fe1bd9168ba7fabe..782a1ee144615eca1b0531443318c94386187213 | 0 | 2c95a6f13086ff89f99aa1b4ed0b3e1809f45d28 |
| 10 | LIGHT_PASS_WITH_NOTES | 782a1ee144615eca1b0531443318c94386187213..8a3e2faaa774ed1ee7cc3e27f5ccfb4fab4526ac | 0 | f5d758579860a58768ceaa4155438f48daa39c5d |
| 11 | LIGHT_PASS_WITH_NOTES | 8a3e2faaa774ed1ee7cc3e27f5ccfb4fab4526ac..009d9bab431c75184b00659905f80dcde91e3166 | 0 | 08b01758488934b7b515ed9726143aa09c051de5 |

## RECORD_ONLY Index

| Observation | Child | Evidence | Source report |
|---|---|---|---|
| O-1 list-DTO stored size vs file-response actual size asymmetry (contract-compliant; child 06 defines the distinct actualSize contract) | 01 | MailboxAttachmentService.kt:63-71, ExpertDocumentBrowseService.kt:63-70 vs :41/:94 | children/01/verify-log.md |
| O-2 execution.md "浏览响应" wording nuance | 01 | execution.md I-2 | children/01/verify-log.md |
| O-3 scripted V24-repair ERROR line in Flyway IT output is test noise (13/13 pass) | 01 | Flyway IT log | children/01/verify-log.md |
| O-4 entity defaults add no production null surface until children 03/04 writers | 01 | MailAttachmentService.kt:41/:82 | children/01/verify-log.md |
| O-1 concurrency caps 2/1 and 5000 queue cap are compile-time constants (values met; not runtime-configurable) | 02 | AttachmentTransferWorker.kt companion, AttachmentTransferService.kt companion | children/02/verify-log.md |
| O-2 stale mysqlIt surefire XML accounting (fresh full = 3121/0/8) | 02 | surefire reports | children/02/verify-log.md |
| O-1 evidence-count misquotes in execution.md (13 not 17 per class; 3151 vs 3133); raw IMAP transcript not embedded (runtime assertions + fixture-throw prove zero FETCH) | 03 | ImapMailReceiveServiceTest counts; ImapMetadataFetchIT:133-143 | children/03/verify-log.md |
| O-2 un-guarded fall-through reads non-text nameless leaves (e.g. inline image/png) via readBoundedText; base returned "" without stream; no fixture with non-empty nameless inline part | 03 | ImapMailReceiveService.kt:329-334 | children/03/verify-log.md |
| O-3 readBoundedText catch-all can surface hung source as silent empty body (legacy-consistent; required timeout paths re-thrown+tested) | 03 | ImapMailReceiveService.kt:417-420 | children/03/verify-log.md |
| O-4 IT fixture attachmentContentRequests collector never appended — vacuous; real enforcement is fixture error() + command-log absence assertions | 03 | ImapMetadataFetchIT.kt:317,420,745-754 | children/03/verify-log.md |
| O-1..O-3 I-2/I-1/I-3 confirmations verified in code (not findings) | 04 | AutoMailReplyService.kt, InboundMailProcessing*, UnmatchedInboundMailService.kt:186-192 | children/04/verify-log.md |
| O-1 execution.md full-suite aggregate predates probe-class removal (fresh 3168/0/8) | 05 | execution.md vs fresh run | children/05/verify-log.md |
| O-2 metadata-mode DMARC queue branch has no direct unit test (covered by code + consumer/02-seam tests; out of 10-file scope) | 05 | AutoMailReplyService.kt:808-831,897-957 | children/05/verify-log.md |
| O-3 command evidence as BUILD SUCCESS + surefire XML rather than raw exit-code digits | 05 | execution.md command table | children/05/verify-log.md |
| O-4 watchdog force-close uses reflection on com.sun.mail.imap internals (JavaMail 1.6.x limitation; public-API fallback, protocol-IT-proven; maintenance brittleness) | 05 | ImapMailReceiveService.kt:160-165 | children/05/verify-log.md |
| O-1 listMaterials loads full per-expert projection (≤1000) + in-memory page; stat-level file checks only, never bytes; deliberate for filesystem-truth storage state | 06 | ExpertMaterialService.kt:230-248,281 | children/06/verify-log.md |
| O-2 boundary window contains child-05 docs evidence commit (controller-owned) | 06 | range diff | children/06/verify-log.md |
| O-3 mailbox readiness via unscoped variant (ownership enforced by caller chain; same readiness core, no path bypass) | 06 | MailboxAttachmentService.kt, MailboxService.resolveAttachments | children/06/verify-log.md |
| O-4 no real-MySQL IT in child 06 (G-1 SQL proof deferred to child 07 per master) | 06 | authorized file list | children/06/verify-log.md |
| O-1 retained unrouted listMaterials delegate + its direct-call unit test (dead-code residue outside A1 list; removal needs a later child adding ExpertContactManagementControllerTest) | 07 | ExpertContactManagementController.kt:245-246; ExpertContactManagementControllerTest.kt:31-49 | children/07/verify-log.md |
| O-2 child brief (children/07/brief.md) still states pre-A1 10-file list (controller evidence staleness) | 07 | children/07/brief.md | children/07/verify-log.md |
| O-3 implementer full-suite aggregates not byte-reproducible (fresh 3215/0/9); stale-XML accounting direction; green under both methods | 07 | fresh run vs execution.md | children/07/verify-log.md |
| O-4 ConversationItemResponse.institution always null (no DB source; child-10 frontend fills from ES expert profile) | 07 | ConversationItemResponse; expert_contact schema | children/07/verify-log.md |
| O-5 mysqlIt-gated classes skipped in plain mvn test by design; evidence from explicit -Pmysql-it runs | 07 | commands 1/2b | children/07/verify-log.md |
| O-1 boundary contains child-07 evidence commit (controller-owned); impl commit = exactly 5 files | 08 | range diff | children/08/verify-log.md |
| O-2 tracked ledger.md modification during verification (controller state bookkeeping) | 08 | ledger.md | children/08/verify-log.md |
| O-3 em-stats 4-span folds sourceUnavailable into 失败 (4 spans always sum to summary.total; row-level data-state still distinguishes) | 08 | expert-materials.js:452-456,882 | children/08/verify-log.md |
| O-4 source-filter options accumulate from browsed pages (display-level convenience; server-side source params on choose) | 08 | expert-materials.js store.seenSources | children/08/verify-log.md |
| O-5 mvn Skipped 9 = pre-existing opt-in Docker/DB gates (unchanged from baseline) | 08 | surefire | children/08/verify-log.md |
| O-1 boundary contains child-08 evidence commit + tracked ledger.md (controller evidence) | 09 | range diff | children/09/verify-log.md |
| O-2 full-suite count delta 3218 vs 3251 (counting granularity; both green) | 09 | fresh run vs execution.md | children/09/verify-log.md |
| O-3 store polls only visited-page rows; unvisited frozen items refresh on browse/reopen (fail-closed ai-analysis; consistent with 06 contract and A-1/A-2) | 09 | expert-materials.js store; execution.md deviations | children/09/verify-log.md |
| O-4 unreachable elvis branch in backend guard loop (extract returns every key by construction) | 09 | ExpertDocumentAnalysisService.kt | children/09/verify-log.md |
| O-1 LATENT PRODUCTION DEFECT: mailbox-chat.js:765-766 reads app.js top-level const catalogs (operatorStatusOptions/indexLevelOptions) via window/global props — undefined for classic-script const; chat 专家状态/层级 selects render empty and save inert in a real browser (vm-simulated; tests blind) | 10 | mailbox-chat.js:765-766; app.js:656/665 | children/10/verify-log.md |
| O-2 leftover console.error debug line in mailboxChatBehavior.test.js (~:1106) | 10 | mailboxChatBehavior.test.js | children/10/verify-log.md |
| O-3 查看全部附件 opens same-contact drawer UNFILTERED (child-08 mount API lacks source prefilter; store parity preserved; needs child-08 extension or acceptance amendment) | 10 | expert-materials.js:1251-1268 | children/10/verify-log.md |
| O-4 right-column DOM order (manual-open/logs-collapsed defaults) implemented but not machine-pinned beyond workbench-collapsed | 10 | renderConversationContent mailbox-chat.js:1005-1037 | children/10/verify-log.md |
| R1 in-range docs-only commits in full-range diff-tree (product set = exactly 11 authorized files) | 11 | range diff | children/11/verify-log.md |
| R2 stale mysqlIt surefire XMLs excluded via mtime window (fresh = 3218/0/9) | 11 | surefire | children/11/verify-log.md |
| R3 old cache-key literal retained once per synced test file as deliberate negative assertion | 11 | synced test files | children/11/verify-log.md |

## Pause/Resume
- Reason: N/A
- Resume from: N/A

## Human Review Pointers
- Amendments: A1 (child 07 route retirement — campaign GET /api/expert-contacts/{id}/materials retired, new ExpertMaterialController owns the path), A2 (child 11 +1 test file for the metadataOnly default flip). Both HUMAN-approved; rows in ledger.md.
- Environment for acceptance runs: MySQL test DB container running at 127.0.0.1:3306 (root/root, talent_introduction); Flyway IT requires DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40; JAVA_HOME zulu-11. MySQL container `mailbox-mysql` (hub-managed) left running for acceptance.
- Feature is ENABLED at this head (child 11: index.html 7-key registration ?v=20260907-material-chat; MailAttachmentStorageProperties.metadataOnly default true) — human acceptance A-1..A-8 in the master plan are the next gate; browser/viewport/IMAP-protocol/manual-send checks were NOT performed by this workflow.
- Cross-child fix lineage preserved: child-04 A-1 (bridge legacy regression) and child-07 A1 (boot blocker) are the two defects the per-child gates caught and closed.

No whole-system verification was performed.

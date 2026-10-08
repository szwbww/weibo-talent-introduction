# Child 03 fix-log

## Epoch 3 — Round 1/3
- Findings: F-1,F-2
- Before: d32733b13af210519be90ca52c933e5f73199cfb
- Fix commit: 6d80d6243be6af9c78f4ce44a54a29c7c1bc9033
- Authorized files changed: src/main/resources/static/mailbox-chat.js; src/test/js/mailboxServerDrafts.test.js
- Commands:
  - `node --check src/main/resources/static/mailbox-chat.js && node --check src/main/resources/static/app.js && node --test src/test/js/*.test.js` -> exit0; both syntax checks PASS;1567 tests/298 suites,1567 pass,0 fail/cancelled/skipped. Receipt artifact://405.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=MailReplyDraftServiceTest,MailReplyDraftControllerTest test` -> exit0;17 tests,0 failures/errors/skips; bundled JS1567 PASS.
  - `DOCKER_API_VERSION=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftRepositoryIT test` -> exit0;8 real MySQL tests,0 failures/errors/skips; bundled JS1567 PASS.
  - `DOCKER_API_VERSION=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.44 -Pmigration-it -Dtest=FlywayMigrationIntegrationTest test` -> exit0;39 real MySQL migration tests,0 failures/errors/skips; bundled JS1567 PASS.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=PendingMailOperationServiceTest,ManualReplySendAttemptServiceTest test` -> exit0;100 tests,0 failures/errors/skips; bundled JS1567 PASS.
  - `DOCKER_API_VERSION=1.44 JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.44 -Pmysql-it -Dtest=MailReplyDraftSendIntegrationTest,MeetingCalendarSendIntegrationTest test` -> exit0;27 real MySQL tests,0 failures/errors/skips; bundled JS1567 PASS. Five Maven commands ran sequentially after final edits; complete receipt artifact://410, finished 2026-10-09T00:57:22+08:00.
  - `env SMOKE_BASELINE=1 node /tmp/mailbox-draft-round1-smoke.cjs` -> expected exit1 reproducing F-1 on before-SHA component: DOM A differs from persisted B.
  - `env SMOKE_BASELINE=1 SMOKE_CASE=owner node /tmp/mailbox-draft-round1-smoke.cjs` -> expected exit1 reproducing F-2 on before-SHA component: resolved B identity retained OWNER-A-SUBJECT/OWNER-A-SECRET card and count1 while B fetch delayed.
  - `node /tmp/mailbox-draft-round1-smoke.cjs && rm /tmp/mailbox-draft-round1-smoke.cjs` -> exit0; repaired actual-component controlled consumer smoke: before-debounce and after-B-ACK reversions both DOM A/PUT A/saved“已保存到服务器”; resolve and applyOptions each had two genuinely pending B list requests, zero cards/count0 and no stale A text even after stale A response. Throwaway smoke file removed; no runtime server/browser resources created.
- Result: FIXED
- Notes: Bounded F-1 suppression now requires no existing captured draft and no force; existing equality dedupe retained. F-2 clears owner-scoped list items/total/draftTotal and renders cleared list/pager at both identity seams; existing owner/sequence guards retained. Five behavioral regressions cover reversions before debounce/after ACK plus fresh-form clearing, and delayed B/stale A for both identity seams. Controlled smoke uses real component with parsed DOM/raw-JSON CAS fixture, not a new real-browser/server-persistence claim. O-1 and all RECORD_ONLY coverage remain unchanged. Approved plan02af6d42cdf3617c48335a9ad3aeddb025d1302f unchanged; evidence excluded from product commit and left for controller-owned append-only evidence commit. No push/merge/amend/squash or unauthorized repair.

## Epoch 3 — Terminal repair closing
- Actual repair count: 1
- Fix commit: 6d80d6243be6af9c78f4ce44a54a29c7c1bc9033
- Independent re-verifier: DraftUIReVerifierCapable
- Final verdict: LIGHT_PASS_WITH_NOTES
- F-1/F-2 resolved; O-1 record only. No further repair dispatched.

## Epoch 3 — Evidence correction closing
- Replacement evidence records only verifier action normalization.
- Repair count remains 1; fix commit remains 6d80d6243be6af9c78f4ce44a54a29c7c1bc9033.
- No additional product repair or check was performed.

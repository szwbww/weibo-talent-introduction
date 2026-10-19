# Aggregate Machine Verification — apollo-verification-master

## Epoch 1 — 2026-10-19T10:01:23+08:00

- Master plan: `docs/plans/2026-10-18/apollo-verification-master.md` (worktree sha256 `7275543b078d36f0ceefb475d5fdd7f114f98ddb4686b8df4eb4e5bf3f827cc0`)
- Governing master identity: recorded commit `00c0c9e75926ec9a8a9487fccf40d35d6275df3f`; invoked sha256 `fc28852df883c419768777091138f16aa811bf23134f9700450cd35a02568caf`.
- Master identity state: `AMENDMENT_RECORDED`. A12, master rule `实现方案 / I-14`: `07` cap `11→13` for two obsolete CSS-position assertion retirements only; no production expansion. Approval: `HUMAN:批准该计划并执行 后续也都授权 执行完所有子计划`.
- Boundary: `3efc192faf043badc1ef27bef124a71d3120c4c5..e473ffc714b840a2f42c9ca826da7c37e36ab9d7`
- Reviewer: `/root/aggregate_reviewer`
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/apollo-verification-master/repair.md` — `DRAFT_READY`

## Fresh command evidence

| Invocation | Exit / evidence |
|---|---|
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -q test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationContractTest,ApolloPeopleSearchClientTest,OrcidVerificationEvidenceClientTest,DiscoveryVerificationRulesTest,QualificationEvidenceParserTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationFlowIT,DiscoveryVerificationIntegrationTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationControllerTest,DiscoveryVerificationReportingIT,DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,ExpertRevalidationServiceBehaviorTest,ExpertRevalidationServiceTest,ExpertIndexWriterServiceTest,BatchRecipientSelectionServiceTest,BatchRecipientPreflightServiceTest,DiscoveryReviewRepositoryIT,DiscoveryIdentityRecoveryServiceTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,OperatorStatusWriteSeamGuardTest` | exit 1; 633 total, 579 pass, 53 known `ExpertDiscoveryServiceTest` fixture errors, 1 optional legacy skip. Verification-specific suites passed: RepoIT 8, FlowIT 11, Integration 10, ReportingIT 10. |
| `env ... mvn -q surefire:test ... -Dtest=ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest` | exit 0; 7/7. |
| `env ... mvn -q exec:exec ... DiscoveryVerificationFlowIT` | exit 0; real MySQL8/Flyway/Mock-HTTP smoke: `RUNTIME_SMOKE auto=1 projection=PROJECTED apolloRequests=2 no-provider-replay=true candidates=1`. |
| `node --test src/test/js/discoveryVerification.test.js src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js` | exit 0; 101/101. |
| `node --test src/test/js/expertManualProfile.test.js src/test/js/expertMeetingVideo.test.js` | exit 0; 50/50. |
| `node --check src/main/resources/static/app.js` | exit 0. |
| `node --test src/test/js/*.test.js` | exit 1; 1725 total, 1685 pass, 40 fail, 0 skipped/cancelled. TAP repeat used only to capture exact totals. |
| `git diff --check -- src/main src/test` | exit 0. |

Browser evidence rechecked: 114 verification HTTP requests, all GET, zero non-GET; disabled capability blocks launch; active/closed lifecycle timer receipts match intended behavior.

## Master contract matrix

| Contract | Verdict | Aggregate evidence |
|---|---|---|
| N-1 original discovery/scheduling | PASS | Existing entry paths/MVC/scheduler tests pass; no replacement flow found. |
| N-2 manual review priority | PASS | Manual/HOLD/REJECT/legacy protections retained. |
| N-3 existing contact/mail/application preservation | FAIL, V-1 | Late contact keyed only by real ORCID is not re-read correctly before commit. |
| N-4 no email/contact mutation by Apollo | PASS | Provider client sends Search-only name/institution data; no mail/contact write path introduced. |
| N-5 historical task compatibility | PASS | Null/history/read endpoint/browser receipts retained. |
| I-1 identity and qualification rules | PASS | Strict identity/qualification suites pass; Apollo evidence does not independently qualify. |
| I-2 professor/PhD/age criteria | PASS | Rules/parser suite passes current-role, 39/40, completed-doctorate, conflict cases. |
| I-3 mode semantics | PASS | AUDIT/ENFORCE/default/frozen settings paths covered. |
| I-4 MySQL truth, ES projection secondary | PASS | Flow smoke proves durable admission plus separate retryable projection. |
| I-5 business protections | FAIL, V-1 | Commit-time real-ORCID contact protection bypass. |
| I-6 ledger/CAS/lease | PASS | RepoIT and FlowIT pass; stale/no-replay behavior exercised. |
| I-7 reserve→RAW→activate/recovery | PASS | Integration/Flow tests pass recovery and owner/fingerprint path. |
| I-8 counters | PASS | ReportingIT/F123/read aggregation coverage passes. |
| I-9 Apollo funnel | PASS | Request persistence/funnel/read counts pass. |
| I-10 null/zero/503 semantics | PASS | Query/report/browser evidence retains distinct states. |
| I-11 independent verification lifecycle | PASS | Browser timer and runtime tests preserve queued verification after collection actions. |
| I-12 cost/provider boundary | PASS | Apollo Search-only, no enrich/unlock/email; ORCID allowlist and permit flow intact. |
| I-13 admission gate consistency | PASS | Gate/commit/query paths use persisted evidence and current state. |
| I-14 frozen frontend contract | PASS | A12 authority valid; discovery 101/101 and CSS 50/50 pass. |

## Finding V-1 — P1 — late ORCID contact protection bypass

Requirement: master I-5; child04 I-4 and implementation step 2 require a protection re-read before commit.

1. `DiscoveryVerificationRegistrationService.kt:82-84` intentionally persists RAW as `profile.copy(esDocId=docId, orcidId=docId)`.
2. `DiscoveryVerificationIntegrationTest.kt:433-441` proves the persisted convention: `snapshot.profile.orcidId == doc.key`, while the real ORCID survives only in `snapshot.profile.externalIds.orcid`.
3. `ExpertIndexWriterService.kt:775-785` reconstructs RAW with `orcidId` directly from the persisted field and serializes `externalIds` separately.
4. `DiscoveryVerificationCommitService.kt:31-45` rebuilds that RAW profile before commit.
5. `DiscoveryVerificationCommitService.kt:131-136` queries `expert_contact.orcid_id` only with `profile.orcidId`; here it is the document ID, not the real bound ORCID.
6. Both `prepare()` and the commit fallback-protection branch reuse that flawed snapshot.

Thus, a contact created after registration and identified solely by the real ORCID is missed; an otherwise qualifying item can be auto-admitted/projected. Registration-time checks do not close this race because the contact is created afterward.

## Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-01 | N-1 | Retained | Current full Node has 40 known recipient-preview sandbox failures; no new regression. |
| O-02-01 | N-1 | Retained | Same Node category; no unrelated repair. |
| O-03-E2-01 | I-1/I-2 | Retained | Same Node category. |
| O-03-E2-02 | I-6 | Retained | Optional legacy shared-DB coverage remains excluded/not pass. |
| O-04-E2-01 | I-11 | Retained | Same Node category. |
| O-04-E2-02 | I-6 | Retained | Legacy `DiscoveryReviewRepositoryIT` skip remains not-pass. |
| O-05-E2-01 | I-7 | Retained | `ExpertDiscoveryServiceTest`: 190 total, 137 pass, 53 known fixture-overload errors. |
| O-05-E2-02 | N-1 | Retained | Same Node category. |
| O-05-E2-03 | I-6 | Retained | `DiscoveryPaperQueueRepositoryIT` remains optional; `mysqlIt` not enabled, never counted as pass. |
| O06-01 | I-10 | Retained | Same Node category. |
| O06-02 | I-7 | Retained | Closed-child05 fixture context; no confirmation-only repair. |
| O07-01 | I-14 | Retained | 40 current recipient-preview sandbox failures; child07-specific tests pass. |

No RECORD_ONLY item waives V-1.

## Review-P Result

- Result: `FAIL`
- Convergence: `INITIAL`
- Prior aggregate finding lineage: none.
- Repair eligible: yes.
- Repair planning: `DRAFT_READY` at `docs/plans/fix/apollo-verification-master/repair.md`.
- Authorized files: `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt`; `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt`.
- The repair artifact contains the required Review-Fast-P one-approval execution handoff: exact `$execute-p docs/plans/fix/apollo-verification-master/repair.md`, product subject `fix(apollo): preserve late ORCID contact protection`, repair handoff destination `docs/plans/review/apollo-verification-master/repair-execution.md`, and docs evidence subject `docs(review-fast-p): record repair execution`.

No product code was modified.

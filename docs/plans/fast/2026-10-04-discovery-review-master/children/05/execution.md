## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-05-explicit-send.md
Plan SHA-256: 36206e603581699f4d1b6a5079111b37fc63e18ae0d5757277781eb3cce17dcf
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-05-explicit-send.md@36206e603581699f4d1b6a5079111b37fc63e18ae0d5757277781eb3cce17dcf
Execution epoch: NEW
Approval basis: fast-p child 05 controller contract (worktree `weibo-talent-introduction-fast-2026-10-04-discovery-review-master`, branch `fast/2026-10-04-discovery-review-master`)
Executor: ImplDiscoveryReview05
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master
Target branch: fast/2026-10-04-discovery-review-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master@fast/2026-10-04-discovery-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-discovery-review-master
Pre-execution code SHA: 08f5bd5421333447f9173d34fad1c55ac43c3ba5 (child_base_sha = child 04 code head)
Post-execution code SHA: (filled by controller/commit; see product commit `feat(fast-p): implement 05`)
Evidence HEAD: (none — report is controller-committed)
Implementation boundary: 08f5bd5..`feat(fast-p): implement 05`

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 sends consume persistent admission (AUTO/MANUAL/LEGACY equal; no academic re-gate; uninitialized/identity-changed/rejected distinct) | IMPLEMENTED | BatchRecipientSelectionService.kt, BatchExecutionModels.kt, ManualInitialOutreachService.kt, InitialOutreachService.kt | selector tests + ManualInitialOutreachServiceTest admission tests (green) |
| I-2 explicit conditions only (empty statuses unlimited; empty expertTypes zero; gate-off truly off; no implicit NOT_CONTACTED base) | IMPLEMENTED | BatchExecutionModels.kt, ManualInitialOutreachService.kt | filter tests updated+green |
| I-3 unified selector (per-person admission + filterKeys, reason repeat, exclusion dedup, preview no writes, execution/retry same selector, 500 batch) | IMPLEMENTED | BatchRecipientSelectionService.kt | BatchRecipientSelectionServiceTest (7) green |
| I-4 template gate same template/seed/variant/values when on; off only bare empties; residue still an error | IMPLEMENTED | IntroductionMailComposer.kt, ManualExpertMailService.kt, ManualInitialOutreachService.kt | BatchTemplateGateParityTest (3) green |
| I-5 shared components unchanged by default; other send paths preserved | IMPLEMENTED | IntroductionMailComposer.kt, ManualExpertMailService.kt | parity test default-gate assertions; InitialOutreachServiceTest regression green |
| D1 branches retained | IMPLEMENTED (retained) | ManualInitialOutreachService.kt | see D1 list below |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11 … mvn -DskipTests test-compile` | PASS | exit 0 |
| `JAVA_HOME=…/zulu-11 … mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,BatchTemplateGateParityTest test` | PASS | exit 0; Kotlin 7+170+20+3=200 tests, 0 failures, 0 errors; JS `node --test` 1434 pass / 0 fail |

### Changed Files (all inside Authorized Files)
- src/main/.../campaign/domain/BatchExecutionModels.kt — RecipientScope: `mismatchKeys`/explicit-condition split; discovery admission removed from predicate; discovery-region country-only vs non-discovery country-or-nationality; `RecipientFilterKeys`.
- src/main/.../campaign/service/BatchRecipientSelectionService.kt (new) — unified selector/explanation, 500-batch admission lookup, reason dedup.
- src/main/.../campaign/service/ManualInitialOutreachService.kt — preview/ES page/retry/material wired to selector; implicit NOT_CONTACTED base removed; gate switch threaded to compose/sendManualMail.
- src/main/.../campaign/service/InitialOutreachService.kt — old cron entry consumes admission via selector.
- src/main/.../mail/service/IntroductionMailComposer.kt — `evaluateForBatch`, `enforcePersonalizationGate` (default true), variant seed via `variantSeedFor`.
- src/main/.../mail/service/ManualExpertMailService.kt — `enforcePersonalizationGate` batch parameter (default true).
- src/test/.../BatchRecipientSelectionServiceTest.kt (new), ManualInitialOutreachServiceTest.kt, InitialOutreachServiceTest.kt, mail/service/BatchTemplateGateParityTest.kt (new).

### D1 branches retained (per plan step 6 / master I-7)
- Unsubscribe / suppression (unsubscribe + suppression check unchanged).
- History already-sent (INTRO/MATERIAL DEDUP) unchanged.
- Bound sender account (BOUND_SENDER_ALREADY_SET) unchanged.
- Permanent first-mail failure blocking unchanged.
- MATERIAL_REMINDER requires existing contact unchanged.
- Legacy cron: consumes same admission but keeps its own configuration/defaults.

### Effective scope this child
- Academic admission re-gates (`matchesDiscoveryOutreach`, institution/country/credential re-verification) removed from production send points and replaced by 04 persistent admission.
- Empty operatorStatuses no longer silently switches to the candidate-first notContacted base.
- Template gate switch (`gateFilterEnabled`) now truly disables personalization missing-key evaluation for batch (introduction + material reminder).
- No DB/ES schema change, no new migration, no pom change.

### Deviations
- Plan step 5's "preview freezes template version summary / reject stale preview token / account variable-missing pre-start config error" was NOT implemented: it requires control-service/`request_payload` writers outside the 10 authorized files. Reported for human decision.
- Existing test fixtures/assertions were updated to the new contract (admission-based send, status-agnostic base, selector-side explicit filtering). These are plan-authorized behavior updates in the two authorized test files, not rule-11 count-only resyncs.
- `BatchRecipientSelectionService(discoveryReviewService = null)` default exists only so unauthorized test files (`MailOpenTrackingPersistenceTest`, `BatchSendTaskRuntimeIntegrationTest`) compile unchanged; production Spring always injects the real bean.

### Freshness
- Plan identity rechecked: YES (SHA-256 unchanged)
- Worktree identity rechecked: YES
- Reported commits reachable from target branch: (verified at commit)
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

### Remaining Blocker
- None for the code contract implemented; plan step 5's template-version/stale-token item needs authorization of control-service files.

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`

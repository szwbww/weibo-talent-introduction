# Execution Report — 03a-per-request-evidence-version (fast-p child)

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order/docs/plans/2026-08-19/workbench-repair-03a-per-request-evidence-version.md
Plan SHA-256: 41f29bc4723819c90b967c5a9238db0ba10912b478e030a735dc07e26c954d9a
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order/docs/plans/2026-08-19/workbench-repair-03a-per-request-evidence-version.md@41f29bc4723819c90b967c5a9238db0ba10912b478e030a735dc07e26c954d9a
Execution epoch: NEW
Approval basis: fast-p child brief (03a-per-request-evidence-version/brief.md) + approved plan bytes
Executor: Impl03aPerRequestEvidenceVersion (fast-p child 03a implementer)
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order
Target branch: fast/workbench-repair-00-execution-order
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order@fast/workbench-repair-00-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-workbench-repair-00-execution-order
Pre-execution code SHA: 055d313d250053d7cbd917884745571b9580b9b4 (child 02 terminal code head)
Post-execution code SHA: e2ad440157017fb6ced066fe63ad2d5e104a8296
Evidence HEAD: e2ad440157017fb6ced066fe63ad2d5e104a8296
Implementation boundary: 055d313..e2ad440 (9 files, 1196 insertions, 184 deletions)

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Research checkpoint (`evidenceSetVersionWithMapping` callers) | IMPLEMENTED | — | grep found the symbol only at def :1578 and call :1524 of TrustReplyWorkbenchService.kt; no other caller in src/main src/test → no PLAN_CONFLICT |
| T1 requestEvidenceVersion + aggregateEvidenceVersion, delete evidenceSetVersionWithMapping | IMPLEMENTED | TrustReplyWorkbenchService.kt | companion functions; grep for `evidenceSetVersionWithMapping` in service → no output (I-1) |
| T2 ResolvedCanonicalSelection carries requestEvidenceVersions; per-subset base snapshot | IMPLEMENTED | TrustReplyWorkbenchService.kt | resolveCanonicalSelection builds per-request map + aggregate; baseSnapshotOf per subset (C-1) |
| T3 materializeVersion/validateLockedItem/requireCurrentEvidenceVersion per-request; :1051 deleted; coverage evidenceSetVersion | IMPLEMENTED | TrustReplyWorkbenchService.kt | `requireCurrentEvidenceVersion` exactly 3 lines (:473 saveState aggregate, :1025 adjustItem per-request, :1678 definition) — no assemble site (I-3) |
| T4 partial restore (PARTIALLY_RESTORED / droppedItemCount / STALE) | IMPLEMENTED | TrustReplyWorkbenchService.kt | restoreSavedStateWithFrame per-item; validateLockedSubset → LockedSubsetResult; saveState fail-closed guard |
| T5 schema v4, decodePayload v3 verbatim, v2 removed | IMPLEMENTED | TrustReplyWorkbenchStateStore.kt | SCHEMA_VERSION=v4, PREVIOUS=v3, LEGACY=v1; v2 → null (I-6) |
| T6 frontend per-request evidence (coverage field, hasVersionIdentity, makeGenerationPayload) | IMPLEMENTED | trust-reply-workbench.js | see Deviations for assemble payload field |
| T7 changeRequestFacts scoped reset + preserveVersions bootstrap + S-1 span | IMPLEMENTED | trust-reply-workbench.js | `resetVersions()` exactly 3 lines (:373/:400/:644), none in changeRequestFacts (I-5) |
| Tests (4 Kotlin + 1 JS files) | IMPLEMENTED | 5 test files | 147 targeted tests + 662 JS tests + full 2603 Kotlin suite green; I-1/I-2/I-3/I-4/I-6 acceptance tests added |

### Commands
| Command | Result | Evidence |
|---|---|---|
| Research checkpoint grep | PASS | exit 0, only the known def/call site |
| `JAVA_HOME=…zulu-11… mvn test -Dtest='TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchStateStoreTest,TrustReplyWorkbenchControllerTest,PendingMailOperationServiceTrustWorkbenchTest'` | PASS | exit 0; Tests run: 147, Failures: 0, Errors: 0; node-test execution ran; BUILD SUCCESS |
| `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | exit 0; tests 54 / pass 54 / fail 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; tests 662 / pass 662 / fail 0 |
| `node --check src/main/resources/static/trust-reply-workbench.js` | PASS | exit 0, SYNTAX_OK |
| `JAVA_HOME=…zulu-11… mvn test` (full) | PASS | exit 0; Tests run: 2603, Failures: 0, Errors: 0, Skipped: 4 (pre-existing); node-test ok; BUILD SUCCESS |
| `JAVA_HOME=…zulu-11… mvn clean package` | PASS | exit 0; Tests run: 2603, Failures: 0, Errors: 0, Skipped: 4; BUILD SUCCESS |
| `git diff --check` | PASS | exit 0, no output |

### Changed Files
- src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt — T1–T4: per-request evidence model, partial restore, coverage field, droppedItemCount
- src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt — T5: schema v4 + decodePayload v3-verbatim/v2-null
- src/main/resources/static/trust-reply-workbench.js — T6–T7: per-request identity, preserveVersions bootstrap, scoped confirm, S-1 stale span
- src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt — helpers + per-request evidence assertions + I-4 partial-restore test
- src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt — I-1/I-2/I-3 acceptance tests + fixtures per-request evidence
- src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStoreTest.kt — schema v4/v3/v2 decode tests
- src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt — assemble forwards arbitrary expected evidence version
- src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt — archive replay accepts stale expected evidence version
- src/test/js/trustReplyWorkbenchSharedMount.test.js — I-5 preserve test, A-3 confirm-narrowing test, PARTIALLY_RESTORED test, per-request coverage fixtures

### Deviations
1. **assemble payload keeps `expectedEvidenceSetVersion`** (T6 said delete it): the controller DTO `TrustReplyAssembleHttpRequest.expectedEvidenceSetVersion` is a required non-null field in a plan-prohibited file; omitting it from the HTTP body was empirically verified to fail Jackson deserialization with `MissingKotlinParameterException` (400), which would break acceptance A-5. The server-side :1051 gate is deleted as required (I-3 grep 3 lines); the field is now a pass-through no-op. Frontend keeps sending the aggregate with a comment. ControllerTest row #7 implemented as "assemble forwards any expected evidence version without validating it".
2. **JS `STATE_SCHEMA_VERSION` stays `trust-reply-workbench-state-v3`**: I-6 raises the server's SCHEMA_VERSION to v4 and removes v2; the wire (C-4) stays unchanged. v3 remains in ACCEPTED_REQUEST_SCHEMA_VERSIONS, so the client claim is accepted and stored payloads are written server-side as v4. `src/test/js/trustReplyWorkbench.test.js` (not in the authorized list) pins the v3 constant in source; keeping v3 avoids touching an unauthorized file while remaining functionally correct.
3. **`toCoverage` and `buildInitialItemVersions` signatures changed** (same authorized file): `toCoverage(sourceVersion, requestEvidenceVersions = emptyMap())`; `buildInitialItemVersions` drops the now-meaningless `evidenceSetVersion` param and derives per-request values per item from the same base snapshot function (C-6 base caliber kept).
4. **v1 locked-item restore now ends STALE**: under per-request semantics the legacy aggregate fingerprint can never match a fresh per-request value, so v1 payloads with locks restore as STALE (I-6 spirit; "v1 keeps legacy normalization" path preserved). Existing v1-restore tests updated accordingly.
5. **Line numbers**: child 02 shifted TrustReplyWorkbenchService.kt (2-line I-3 comment above :1153; :1286/:1331 changed). All anchors were located by content; the pre-02 base had `requireCurrentEvidenceVersion` at :464/:959/:1051/:1617 — the implementation leaves it at :473 (saveState) / :1025 (adjustItem) / :1678 (definition).
6. **Surefire syntax**: plan's `-Dtest='A+B+C'` class list is rejected by surefire 2.22.2 ('No tests were executed!'); the comma-separated equivalent (identical class set) was used as instructed in the brief.

### Freshness
- Plan identity rechecked: YES (sha256 unchanged 41f29bc…)
- Worktree identity rechecked: YES (root/branch/git-dir matched before commit)
- Reported commit reachable from target branch: YES (e2ad440 is HEAD of fast/workbench-repair-00-execution-order)
- Required commands run this invocation: YES (all 7, freshly, after final state)
- Historical evidence used only as baseline: YES

### Remaining Blocker
- None.

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`

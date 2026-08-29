# Repair Execution — 10-reply-orchestration-order (V-1/V-2/V-3)

- Approval source: HUMAN `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order/docs/plans/fix/10-reply-orchestration-order/repair.md` (2026-08-29 session); amendment A4 approved by user `批准 A4` (authorize UnsupportedAnswerIndexApiTest.kt + PendingMailOperationServiceTrustWorkbenchTest.kt); verification command 7 approved skipped (`批准跳过命令 7`, Docker daemon unavailable).
- Repair identity: plan sha256 `e0704ff0b89546a557531cad63d8dc0b032582958930fdc9e2f59e09c1ed753b` (unchanged before/after execution), EXECUTION_ID `docs/plans/fix/10-reply-orchestration-order/repair.md@e0704ff0…`.
- Executor: main session agent (execute-p executor); worktree `…fast-2026-08-28-reply-orchestration-order@fast/2026-08-28-reply-orchestration-order@…/worktrees/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order`.
- Pre-execution code SHA: `7f8b28d2f09c0df7551703d8037c2b521b189152` (fast-p terminal code head); pre-execution HEAD `fcdc5e78d695086e12d47c0f63120775f5b90412`.
- Post-execution code SHA: `6793ff948515e541969f76388e0af5bde1fd2f3a` (single product commit, subject `fix(reply-orchestration): preserve final paragraphs and archive eligibility`, 14 authorized files only).
- Evidence HEAD: the docs-only commit `docs(review-fast-p): record repair execution` (this file).
- Implementation boundary: `7f8b28d2f09c0df7551703d8037c2b521b189152..6793ff948515e541969f76388e0af5bde1fd2f3a`.

## Changed Files (14 authorized)

| File | Change |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt | R-1: `TrustReplyAssembleRequest`/`TrustReplyAssembleResponse` gain final-paragraph seam (`finalParagraphs`, `operatorFacts`, `finalParagraphs`, `finalParagraphByRequestKey`); `verifyAssembly` validates submitted step-03 paragraphs (partition == required fact set, six-check reuse via `AiReplyGroundedContentPlanner.validateRearrangement`, verbatim controlled/frozen/op*, single action) and composes verbatim without re-closing; deterministic per-request mapping (one-to-one/merged; ambiguous → unmapped). |
| src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt | Assemble HTTP DTO carries `finalParagraphs` + `operatorFacts`; `TrustReplyWorkbenchException` gains optional details. |
| src/main/resources/static/trust-reply-workbench.js | `assemble()` submits the authoritative step-03 draft (`finalParagraphs`/`operatorFacts`) when all paragraph texts are composed; otherwise `[]` (closer path unchanged). |
| src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt | R-1: `finalParagraphText` = mapped final paragraph (never `answerText`); eligible documents with blank/missing mapping fail closed. R-2: canonical `isArchiveEligible` (four handling × two generation kinds, optional operatorInstruction). |
| src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt | R-1: passes `finalParagraphByRequestKey`; R-2: eligibility filter → `isArchiveEligible` (rating gate kept). |
| src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt | R-1: passes final paragraphs; R-2: live eligibility + failure count → `isArchiveEligible` (send/replay gates kept). |
| 6 authorized test files + 2 A4 files | Final-paragraph validation/composition/mapping (service 7 tests), HTTP seam round-trip (controller), archive mapping + fail-closed (index), training/live allow-list reach archive, JS assemble-request test; A4: API + trust-workbench mail tests adapted. |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/trustReplyWorkbenchThreeStep.test.js` | PASS | 4 pass / 0 fail (incl. new Repair R-1 assemble test) |
| `mvn -q -Dtest=TrustReplyWorkbenchControllerTest,TrustReplyWorkbenchServiceTest,UnsupportedAnswerIndexServiceTest,AiTrainingEvaluationServiceTest,PendingMailOperationServiceTest test` | PASS | exit 0 |
| `mvn test` | PASS | `Tests run: 3014, Failures: 0, Errors: 0, Skipped: 5`, BUILD SUCCESS |
| `mvn clean package` | PASS | BUILD SUCCESS, 3014/0/0/5 |
| `node --test src/test/js/*.test.js` | PASS | 766 pass / 0 fail |
| `git diff --check` | PASS | exit 0 |
| `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | BLOCKED (environment) | Docker daemon unavailable; HUMAN-approved skip `批准跳过命令 7` (recorded exception for this repair) |

## Deviations

- A4 amendment (HUMAN-approved): authorized `src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt` and `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` — the archive signature change and strict final-paragraph rule break their archive stubs/assertions; full gate cannot pass otherwise (plan's Completion Criteria conflict with Authorized Files list; resolved by human decision).
- R-2 enumerated handling names in repair.md (`ANSWER_WITH_SAFE_TEMPLATE`, `OMIT_WITH_EXPLANATION`, `ESCALATE_TO_HUMAN`) do not exist in the codebase; the canonical allow-list is the plan-16 set implemented by the validator (`ANSWER_FROM_OPERATOR_INPUT`, `ANSWER_EVIDENCE_WITH_OPERATOR_INPUT`, `ANSWER_SUPPORTED_PART`, `ACKNOWLEDGE_PENDING` × `AI_GENERATED`/`SAFE_TEMPLATE`) — the plan's own "Keep one canonical eligibility definition consistent with document validation" and "Prohibited: Broadening beyond the plan 16 allow-list" govern; `isArchiveEligible` exposes exactly that set.
- Behavior note (per plan V-3): archive now REQUIRES a final paragraph mapping for eligible items — letters assembled without composed step-03 paragraphs (classic closer path) have no mapping and their eligible items are rejected at archive (fail closed, never answerText fallback). The frontend always submits the composed draft in the normal three-step flow.
- Mockito `isArchiveEligible` call sites in tests use the repo's `?: fallback` matcher convention (non-null param).
- Plan file itself NOT amended (A4 approval recorded here; execute-p identity gate forbids plan mutation during execution).

## Clean State

Post-docs-commit worktree clean (`git status --porcelain` empty). Plan identity rechecked (sha256 unchanged); worktree identity rechecked (branch/HEAD unchanged until the two authorized commits).

## Next Action

READY_FOR_VERIFICATION → the already authorized `review-fast-p` aggregate re-review consumes this file and `docs/plans/fast/2026-08-28-reply-orchestration-order/human-review-handoff.md`.

---

# Repair Execution — Epoch 3 (operator-fact slots)

- Approval source: HUMAN `$execute-p <repair.md>` re-invocation (2026-08-29); plan hash changed to `436406ef90623635bd3342b4ac809a6050af8ad75e84fc2a4ecdf64c1ea9a522` → NEW execution epoch. Verification command 7 approved skipped (`批准跳过命令 7`, fresh decision for epoch 3).
- Repair identity: EXECUTION_ID `docs/plans/fix/10-reply-orchestration-order/repair.md@436406ef…`.
- Executor: main-session execute-p executor; same worktree identity as epoch 1.
- Pre-execution code SHA: `6793ff948515e541969f76388e0af5bde1fd2f3a` (epoch-1 product head); pre-execution HEAD `452698921bde20adf15ed8b361243615f9705acf`.
- Post-execution code SHA: `0d45505d68261c14f3866e3f440b2ea08195f1de` (single product commit, subject `fix(reply-orchestration): preserve operator facts in final assembly`, 3 authorized files only).
- Evidence HEAD: the docs-only commit `docs(review-fast-p): record repair execution` (this appended record).
- Implementation boundary: `6793ff948515e541969f76388e0af5bde1fd2f3a..0d45505d68261c14f3866e3f440b2ea08195f1de`.

## Changed Files (3 authorized)

| File | Change |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt | R-1 (V-1/V-3): `FinalParagraphUnit.operatorOwned` marks standalone units from `ANSWER_FROM_OPERATOR_INPUT` versions; `validateFinalParagraphState` binds each submitted `op<n>` to exactly one operator-owned unit by normalized body equality (duplicate/foreign/body-mismatched/ambiguous ownership → 422), replaces the bound unit's synthetic `x<n>` identity with the op id in the exact-once closure, validates via the existing six-check rearrangement validator, and maps the owning requestKey deterministically. |
| src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt | +8 regression tests: op slot composes + maps owner; mixed `f* + op<n>`; fail-closed missing/foreign/duplicate/non-verbatim/ambiguously-owned/body-mismatched. |
| src/test/js/trustReplyWorkbenchThreeStep.test.js | Final-assemble browser test now carries the real `op<n>` paragraph/operatorFacts shape (rearrange retains op slots; assemble payload asserts matching paragraph ids, order/text, and operator facts). |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `mvn -q -Dtest=TrustReplyWorkbenchServiceTest test` | PASS | 83/0/0/0 |
| `node --test src/test/js/trustReplyWorkbenchThreeStep.test.js` | PASS | 4 pass / 0 fail |
| `mvn test` | PASS | `Tests run: 3022, Failures: 0, Errors: 0, Skipped: 5`, BUILD SUCCESS |
| `mvn clean package` | PASS | BUILD SUCCESS, 3022/0/0/5 |
| `node --test src/test/js/*.test.js` | PASS | 766 pass / 0 fail |
| `git diff --check` | PASS | exit 0 |
| `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | BLOCKED (environment) | Docker daemon unavailable: `docker ps` → `failed to connect to the docker API at unix:///Users/lukai/.orbstack/run/docker.sock … no such file or directory`; `/var/run/docker.sock` absent; `DOCKER_HOST` unset. HUMAN-approved skip `批准跳过命令 7` (fresh epoch-3 decision). |

## Deviations

- None beyond the approved command-7 environment exception (recorded above) and the epoch change itself (plan hash `e0704ff0…` → `436406ef…`).

## Clean State

Post-docs-commit worktree clean (`git status --porcelain` empty). Plan identity rechecked (`436406ef…` unchanged during execution); worktree identity rechecked (branch/HEAD unchanged until the two authorized commits).

## Next Action

READY_FOR_VERIFICATION → the authorized `review-fast-p` aggregate re-review consumes this record + the fast-p handoff.

---

# Repair Execution — Epoch 4 (operator fact ID namespace)

- Approval source: HUMAN `$execute-p <repair.md>` re-invocation (2026-08-29); plan hash changed to `a511b739a3a7e983787a29ac819f7a11530baf5b8b7eaefe706df3eb6ac634c8` → NEW execution epoch. Verification command 6 approved skipped (`批准跳过命令 6`, fresh HUMAN_EXCEPTION / NOT_RUN for epoch 4).
- Repair identity: EXECUTION_ID `docs/plans/fix/10-reply-orchestration-order/repair.md@a511b739…`.
- Executor: main-session execute-p executor; same worktree identity as prior epochs.
- Pre-execution code SHA: `0d45505d68261c14f3866e3f440b2ea08195f1de` (epoch-3 product head); pre-execution HEAD `fd2a55262dbfa771701739f5c669728cc36cd70d`.
- Post-execution code SHA: `8fa4f6ca1fde33c471662acb49f53838386177a0` (single product commit, subject `fix(reply-orchestration): enforce operator fact IDs`, 2 authorized files only).
- Evidence HEAD: the docs-only commit `docs(review-fast-p): record repair execution` (this appended record).
- Implementation boundary: `0d45505d68261c14f3866e3f440b2ea08195f1de..8fa4f6ca1fde33c471662acb49f53838386177a0`.

## Changed Files (2 authorized)

| File | Change |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt | R-1 (V-4): operator facts are accepted only in the canonical `op<n>` namespace — `CANONICAL_OPERATOR_FACT_ID = Regex("op[1-9][0-9]*")` (op + positive decimal sequence, no leading zero) enforced before the required-ID closure; duplicate/blank/body checks unchanged. |
| src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt | +1 regression: `external-1`, `op0`, `op01`, `OP1` all rejected with `TRUST_REPLY_FINAL_PARAGRAPHS_INVALID` and no composer interaction; valid `op1` accepted path retained by existing epoch-3 tests. |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `mvn -q -Dtest=TrustReplyWorkbenchServiceTest test` | PASS | 84/0/0/0 |
| `mvn test` | PASS | `Tests run: 3023, Failures: 0, Errors: 0, Skipped: 5`, BUILD SUCCESS |
| `mvn clean package` | PASS | BUILD SUCCESS, 3023/0/0/5 |
| `node --test src/test/js/*.test.js` | PASS | 766 pass / 0 fail |
| `git diff --check` | PASS | exit 0 |
| `mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true` | HUMAN_EXCEPTION / NOT_RUN | Docker daemon unavailable: `docker ps` → `failed to connect to the docker API at unix:///Users/lukai/.orbstack/run/docker.sock … no such file or directory`; `/var/run/docker.sock` absent. HUMAN-approved skip `批准跳过命令 6` (fresh epoch-4 exception). |

## Deviations

- None beyond the approved epoch-4 command-6 exception (recorded above) and the epoch change itself (plan hash `436406ef…` → `a511b739…`).

## Clean State

Post-docs-commit worktree clean (`git status --porcelain` empty). Plan identity rechecked (`a511b739…` unchanged during execution); worktree identity rechecked (branch/HEAD unchanged until the two authorized commits).

## Next Action

READY_FOR_VERIFICATION → the authorized `review-fast-p` aggregate re-review consumes this record + the fast-p handoff.

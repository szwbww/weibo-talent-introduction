# Execution — child 03 (CRS 置信分与阈值样本日志)

- Executor: `Impl03` (fast-p child implementer)
- Plan: `docs/plans/2026-08-18/03-crs-scoring-and-log.md` (AMENDED, commit `6f2ec3c`, includes A2 row 11)
- Plan SHA-256: `d9b06a79a2f7e69cf052169a96f3034b3c6c651a1fb63dc21d801245ed38c0d1`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence/docs/plans/2026-08-18/03-crs-scoring-and-log.md@d9b06a79a2f7e69cf052169a96f3034b3c6c651a1fb63dc21d801245ed38c0d1`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence@fast/auto-reply-convergence@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-auto-reply-convergence`
- Pre-execution code SHA: `77f3049` (child 02 code head; plan-amendment doc commit `6f2ec3c` on top)
- Post-execution code SHA: see implementation commit below
- Implementation commit: `feat(fast-p): implement 03` (this execution)

## Commands (all run with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`)

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `mvn test -Dtest=AutoReplyConfidenceScorerTest` | 0 | `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS |
| 2 | `mvn test -Dtest=GroundedAutoReplyDecisionServiceTest` | 0 | `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS (15 baseline + 2 new) |
| 3 | `mvn test -Dtest=AutoMailReplyServiceTest` | 0 | `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS (41 baseline + 1 new) |
| 4 | `grep -n '\${' src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql` | 1 (no matches) | output empty — no `${` placeholder sequence |
| 5 | `mvn test` (full regression) | 0 | `Tests run: 2583, Failures: 0, Errors: 0, Skipped: 4` — BUILD SUCCESS. Baseline 2574 → +9 (6 scorer + 2 decision service + 1 auto-mail-reply). 4 skips permitted (migrationIt-gated + permanent @Disabled) |
| 6 | `mvn clean package` | 0 | `Tests run: 2583, Failures: 0, Errors: 0, Skipped: 4` — BUILD SUCCESS |
| 7 | `git diff --check` | 0 | clean (no whitespace errors) |

Also verified: `grep -rn "LlmProperties(" src/test --include=*.kt | wc -l` → **184** (all named-arg constructions compile unchanged; full suite compiled and passed, incl. `AiReplyDraftServiceTest`).

FlywayMigrationIntegrationTest intentionally NOT run with `-DmigrationIt=true` (Docker not available); skipped by default as permitted.

## Files changed (exactly the 11 authorized; no others)

| # | File | Type | Purpose |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql` | NEW | DDL verbatim from plan (byte-equal to plan SQL block, checked with a diff script); indexes `idx_arcl_contact`/`idx_arcl_created`; no `${` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/AutoReplyConfidenceLog.kt` | NEW | Immutable `data class` + `@Table("auto_reply_confidence_log")` + `@Id`; `createdAt: LocalDateTime` non-null; `operatorEdited`/`operatorEditDistance` nullable, unused this round |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/AutoReplyConfidenceLogRepository.kt` | NEW | `CrudRepository<AutoReplyConfidenceLog, Long>`, no custom methods |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorer.kt` | NEW | Pure `@Service`; `AutoReplyConfidenceScore` data class; four components with constant weights (40/25/20/15); history cold-start 7.0; CRS one decimal, clamped `[0,100]` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt` | MOD | `shadowScoringEnabled: Boolean = false` appended at END of parameter list |
| 6 | `src/main/resources/application.yml` | MOD | `shadow-scoring-enabled: ${LLM_SHADOW_SCORING_ENABLED:false}` directly under `auto-reply-enabled` |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` | MOD | Kill-switch `&& !shadowScoringEnabled`; `confidence` field on `GroundedAutoReplyDecision` (null in `disabledDecision`); shadow degradation only at return construction; scorer injected (default-constructed param keeps unlisted positional constructions compiling) |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | MOD | Constructor-inject `AutoReplyConfidenceLogRepository`; best-effort log write after `decide()` (now :508) via `runCatching { }` + `log.warn("Failed to persist auto-reply confidence log: {}", it.message)`; `createdAt = LocalDateTime.now()` explicit; `tier = "SHADOW"` |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorerTest.kt` | NEW | 6 cases per plan (full-grounded 92.0; 4-request coverage 25.5; empty requestFacts → 0.0 non-NaN; BLOCKED → consistency 0.0; empty verifiedRuleIds → evidence 0.0; extreme inputs clamp into [0,100]) |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` | MOD | +2 tests: `shadow mode scores but never sends`; `both flags off skips generation entirely` (`Mockito.verify(generate, never())`, confidence null). `service()` factory gains `shadowScoringEnabled` param + real `AutoReplyConfidenceScorer()` |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` | MOD | New repo mock + constructor arg; `readyDecision()` carries a confidence score (92.0); NEW I-3 regression `confidence log write failure does not block inbound processing` — stub `save()` to throw, assert `processSingle` returns `SinglePipelineOutcome.QA_REPLIED` normal result |

## Invariant evidence (file:line)

- **I-1** (CRS never participates in send decisions): `GroundedAutoReplyDecisionService.kt` — `resolveReason()` (:133-176) and `passesSendGate()` (:178-201) bodies zero-diff (verified via `git diff -U0`; only hunks are data class field, constructor param, kill-switch, return construction, disabledDecision). `grep -n "crs\|confidence"` hits only at :40 (field decl), :89 (`confidence = autoReplyConfidenceScorer.score(...)` at return construction), :198 (`confidence = null` in `disabledDecision`). Shadow degradation `readyToSend = if (shadowOnly) false else ready` / `reason = if (shadowOnly) AI_AUTO_REPLY_DISABLED else reason` occurs exclusively at return construction (:83-84), after `ready` is fully decided by `resolveReason()` + `passesSendGate()`.
- **I-2** (shadow never sends): test `shadow mode scores but never sends` — bottom draft satisfies every send gate, yet `readyToSend == false`, `reason == AI_AUTO_REPLY_DISABLED`, `confidence != null`. Kill-switch :59-61 `if (!llmProperties.autoReplyEnabled && !llmProperties.shadowScoringEnabled) return disabledDecision(subject)`; shadow only bypasses the kill switch, never flips a send on.
- **I-3** (best-effort log write): `AutoMailReplyService.kt:515-540` — `decision.confidence?.let { score -> runCatching { autoReplyConfidenceLogRepository.save(...) }.onFailure { log.warn("Failed to persist auto-reply confidence log: {}", it.message) } }`; never throws, never touches `SinglePipelineResult`. Regression test `confidence log write failure does not block inbound processing` (AutoMailReplyServiceTest): `Mockito.doThrow(RuntimeException("db down"))` on `save()`, `processSingle` returns normal `SinglePipelineResult` (outcome `QA_REPLIED`, recorded true), mail still delivered.
- **I-4** (createdAt non-null): `AutoReplyConfidenceLog.kt:32` `val createdAt: LocalDateTime` (non-nullable); unique write path `AutoMailReplyService.kt:538` passes `createdAt = LocalDateTime.now()` explicitly.
- **I-5** (scorer derives only from draft + verified rule ids): `grep -n "Repository\|DraftService\|LlmClient" AutoReplyConfidenceScorer.kt` → no output. Scorer is pure `@Service`, no constructor deps; inputs `AiReplyDraftResult` + `verifiedRuleIds: List<Long>` only.
- **I-6** (preview untouched): `git diff --stat` does not list `AutoReplyPreviewService.kt`; `grep -n "ConfidenceLog" AutoReplyPreviewService.kt` → no output. Preview path cannot write logs (write point exists only in `AutoMailReplyService`).
- **X-1** (2 production `decide()` callers preserved): `AutoMailReplyService.kt:508`, `AutoReplyPreviewService.kt:112`.
- **X-2** (preview counterfactual/read-only): no changes to preview service; no log writes from preview path.
- **X-3** (`ANSWER_FROM_OPERATOR_INPUT`): untouched.
- **X-4** (no `generateItem()` restructuring): none performed.
- **Migration**: max migration is V104 (`V103` was max before; new file V104); DDL byte-identical to plan block; `tier` written as constant `'SHADOW'`; `operator_edited`/`operator_edit_distance` left NULL (not set at write path).
- **Scorer constants**: all weights centralized as named `const val` in `AutoReplyConfidenceScorer` companion (:94-108): COVERAGE_MAX 40.0, EVIDENCE_MAX 25.0, CONSISTENCY_MAX 20.0, HISTORY_MAX 15.0, HISTORY_COLD_START 7.0, WARNING_PENALTY 5.0, GROUNDED_WEIGHT 1.0, PARTIAL_WEIGHT 0.6, UNSUPPORTED_WEIGHT 0.35, READY_FACTOR 1.0, NEEDS_REVIEW_FACTOR 0.75, BLOCKED_FACTOR 0.0, MIN_CRS 0.0, MAX_CRS 100.0.

## Acceptance grep outputs

- `grep -n '\${' V104__create_auto_reply_confidence_log.sql` → (empty, exit 1)
- `grep -n "crs\|confidence" GroundedAutoReplyDecisionService.kt` → `40`, `89`, `198` (none inside `resolveReason`/`passesSendGate`)
- `grep -n "runCatching" AutoMailReplyService.kt` → `515` (log write), `1085` (pre-existing tag write)
- `grep -n "Repository\|DraftService\|LlmClient" AutoReplyConfidenceScorer.kt` → (empty, exit 1)
- `grep -n "ConfidenceLog" AutoReplyPreviewService.kt` → (empty, exit 1)
- `git diff --stat` → no `AutoReplyPreviewService.kt` entry

## Deviations

- None against the plan contract. Implementation notes (within plan intent):
  - `GroundedAutoReplyDecisionService`'s new `autoReplyConfidenceScorer` constructor param has a default `= AutoReplyConfidenceScorer()` so the unlisted `AiReplyDraftServiceTest.kt:928` positional construction keeps compiling; Spring injects the bean in production (single `@Service` bean, no ambiguity). Authorized-file limit (11) preserved.
  - Scorer rounds each component to one decimal as well as CRS (components stored in `DECIMAL(5,2)` columns; keeps `coverage 25.5` exact and sum-of-components == crs within ±0.05 as required by A-1).
  - Evidence component guards `requestCount == 0` → 0.0 (prevents 0/0 NaN; required by scorer test case 3).
  - The I-3 regression uses `processSingle` directly (per brief) and additionally asserts delivery still occurs; the repo mock is reset after the test to avoid cross-test stub leakage.
- `docs/plans/fast/**` artifacts (including this file and `ledger.md` modification) excluded from the implementation commit.

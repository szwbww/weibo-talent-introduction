# Child 03 Brief — CRS 置信分与阈值样本日志

- Child ID: `03`
- Plan: `docs/plans/2026-08-18/03-crs-scoring-and-log.md` (amended; commit identity `commit:6f2ec3c`; amendment A2 widened authorized files 10→11)
- Master plan: `docs/plans/2026-08-18/00-auto-reply-convergence-master.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence`
- Branch: `fast/auto-reply-convergence`
- Base: `77f3049` (child 02 code head; children 01/02 LIGHT_PASS(_WITH_NOTES))
- Plan identity: commit:6f2ec3c

## Contract

The approved plan file is the complete contract (read the AMENDED file — it includes the A2 row 11). Implement tasks T1–T6 exactly. This brief adds execution context; where they differ, the plan wins.

Use the `execute-p` skill against the plan path above.

## Authorized files (exactly these 11)

1. `src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql` (NEW)
2. `src/main/kotlin/com/weibo/talentintroduction/mail/domain/AutoReplyConfidenceLog.kt` (NEW)
3. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/AutoReplyConfidenceLogRepository.kt` (NEW)
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorer.kt` (NEW)
5. `src/main/kotlin/com/weibo/talentintroduction/config/LlmProperties.kt` (修改)
6. `src/main/resources/application.yml` (修改)
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionService.kt` (修改)
8. `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` (修改)
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyConfidenceScorerTest.kt` (NEW)
10. `src/test/kotlin/com/weibo/talentintroduction/mail/service/GroundedAutoReplyDecisionServiceTest.kt` (修改)
11. `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` (修改 — A2: I-3 regression, stub `autoReplyConfidenceLogRepository.save()` to throw, assert `processSingle` still returns a normal `SinglePipelineResult`)

No other file may change. No other new files.

## Global invariants (master plan, binding)

- **X-1**: `decide()` remains the ONLY shared decision point — exactly 2 production callers.
- **X-2**: Preview stays counterfactual and read-only — child 03 must NOT touch `AutoReplyPreviewService` (I-6) and must NOT write logs from the preview path.
- **X-3**: `ANSWER_FROM_OPERATOR_INPUT` semantics unchanged.
- **X-4**: No per-item `generateItem()` pipeline restructuring.

## Child-specific invariants (must verify in code after change)

- **I-1**: CRS never participates in send decisions — `readyToSend` decided ONLY by existing `resolveReason()` + `passesSendGate()`; `crs`/`confidence`/`tier` appear in neither method and never modify `readyToSend` before return.
- **I-2**: Shadow mode never sends — `shadowScoringEnabled=true` && `autoReplyEnabled=false` → full generation+scoring runs, but `readyToSend` forced `false`, `reason` forced `AI_AUTO_REPLY_DISABLED`.
- **I-3**: Log write is best-effort — `runCatching { }` + `log.warn` on failure; never throws, never rolls back `processSingle()`'s transaction, never changes `SinglePipelineResult`. (A2 regression test in AutoMailReplyServiceTest.)
- **I-4**: `createdAt` is non-nullable `LocalDateTime`, explicitly passed at the single write path.
- **I-5**: Scorer derives components only from `AiReplyDraftResult` + `verifyAutoEvidenceRuleIds()` output — no repository/DraftService/LLM calls inside scorer (`grep -n "Repository\|DraftService\|LlmClient" AutoReplyConfidenceScorer.kt` must be empty).
- **I-6**: `AutoReplyPreviewService` untouched (`git diff --stat` must not list it; no ConfidenceLog references there).
- Kill-switch change (T4): `if (!llmProperties.autoReplyEnabled && !llmProperties.shadowScoringEnabled) return disabledDecision(subject)`; shadow degradation only at the return construction, NOT inside `resolveReason()`/`passesSendGate()`.
- `LlmProperties.shadowScoringEnabled` appended at END of parameter list with default `false` — 184 existing test constructions must compile unchanged.
- Migration `V104` must contain NO `${` sequence; `tier` column written as `'SHADOW'` constant; `operator_edited`/`operator_edit_distance` left NULL this round.
- Scorer constants centralized (weights as named constants); history component constant 7.0; CRS clamped to [0,100], one decimal.

## Baseline (recorded)

- Child 01 landed: `decide()` now takes (inboundText, inboundSubject, contact, currentInboundMessageId); context from `AiReplyContextService.build()`; 3 new GroundedAutoReplyDecisionServiceTest regression tests (15/0/0 total there).
- Full mvn suite at 77f3049: 2574/0/0/4 (4 skips permitted: migrationIt-gated + permanent @Disabled).
- Migration number check: current max is V103; new migration MUST be V104.

## Required commands (run all; per plan's verification section)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoReplyConfidenceScorerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=GroundedAutoReplyDecisionServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AutoMailReplyServiceTest
grep -n '\${' src/main/resources/db/migration/V104__create_auto_reply_confidence_log.sql   # expect no output
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test          # full regression
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package # build
git diff --check
```

Pass criteria: exit 0 with `Failures: 0, Errors: 0`; placeholder grep empty; `git diff --check` clean. (FlywayMigrationIntegrationTest is Docker-gated by `@EnabledIfSystemProperty` — skipped by default, permitted; do NOT run with `-DmigrationIt=true` unless Docker is available.)

## Downstream

- This is the LAST child; no downstream child contract. Handoff will record RECORD_ONLY-style observations if any.

## Deliverable

- Commit the implementation locally as `feat(fast-p): implement 03`. Exclude all fast-p artifacts (`docs/plans/fast/**`) from the commit.
- Append the full execution report to `docs/plans/fast/auto-reply-convergence/children/03/execution.md`: per-command exit codes and counts, files changed, file:line evidence for I-1..I-6, grep outputs for acceptance criteria, any deviations. Do NOT commit it.
- Do not repair unrelated behavior, push, merge, or rewrite history.

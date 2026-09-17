# Child Brief — batch-research-direction-filter

- Child ID: `batch-research-direction-filter`
- Approved child plan: `docs/plans/2026-09-18/batch-research-direction-filter.md` (commit:d51c89105fb5a1f3cf6a1d3a3d71612b8898fc07)
- Master plan: `docs/plans/2026-09-18/00-university-email-template-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main`
- Branch: `fast/university-email-template-main`
- `child_base_sha` (product base = child 1 terminal Code head): `971a21d35a0d5bdffefa4aff9dc6faf93819207d`
- Branch HEAD at dispatch: `24bec04` (after it, only fast-p evidence commits `2bf2cd4` and `24bec04` sit on top of the product base)
- Execution report: `docs/plans/fast/university-email-template-main/children/batch-research-direction-filter/execution.md`

## Authority

The approved child plan is the complete contract: requirements, invariants I-1..I-3, style contract S-1, implementation steps, authorized file list, acceptance criteria, and manual acceptance list. Read it in full before editing. The master plan adds cross-plan invariants M-2 (research type and research direction are two independent filter dimensions), M-3 (configuration and execution consistent), M-4 (no data import, no task/template instantiation, no sending).

## Authorized files (exactly these 10)

1. `src/main/resources/db/migration/V128__add_research_direction_filter_to_batch_send_task_config.sql` (new file; V127 is the current highest migration)
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
7. `src/main/resources/static/app.js`
8. `src/main/resources/static/index.html`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
10. `src/test/js/batchSendTaskConsoleInteraction.test.js`

Do not edit any other file. CSS, template-services, expert ES mappings, and unrelated tests are out of scope. Never bump frontend cache keys (`?v=` in `index.html`).

## Key invariants

- I-1: `batch_send_task_config.research_direction_filter` takes only `ANY/PRESENT/ABSENT`; existing rows and absent values are `ANY`; illegal values are rejected on save and on manual start; config write, read, snapshot, and manual override all carry the value; the legacy typed API update path must not reset it.
- I-2: `ANY` adds no query; `PRESENT` uses the existing `ExpertSearchService.fieldPresenceFilter("researchFields")`; `ABSENT` is the `bool.must_not` of that filter. The in-memory retry path uses the same effective presence rule (null/empty string = absent, anything else = present). Estimation and execution both go through `ManualInitialOutreachService.resolveScope` and the same `RecipientScope`.
- I-3: "no research direction" is not "type UNKNOWN" and not "UNCLASSIFIED"; type, direction, and template gate intersect with AND; `ABSENT` combined with an enabled no-default `${primaryResearchField}` gate may legitimately yield 0 and must not bypass the send-side gate.
- S-1: reuse only the existing classes (`.bsc-input`, `.bsc-select`, `.batch-config-field`, `.batch-config-field-label`, `.batch-config-diff-badge`, `.batch-config-diff-original`); no CSS change, no new class, no inline style.
- M-2/M-3/M-4 as stated in the master plan.

## Inputs from child 1 (`template-placeholder-gate-crud`, LIGHT_PASS_WITH_NOTES)

- Code head `971a21d35a0d5bdffefa4aff9dc6faf93819207d`, exactly the 9 authorized files.
- Template gate now derives `requiredKeys`/`esFields` from the live template: `${key}` is required, `${key|non-empty default}` is not; `GET /api/compose-templates/{id}/gate-fields` returns both; `${primaryResearchField}` maps to ES field `researchFields` (`MailPlaceholderService.ES_FIELD_BY_KEY`).
- Frozen interfaces confirmed unchanged by child 1's verifier: `ManualInitialOutreachService.resolveScope`, `BatchExecutionModels.RecipientScope` (`fromSnapshot`, `matchesExpert`), `ExpertSearchService.fieldPresenceFilter` + `ALLOWED_HAS_FIELDS`. Child 2 must keep them compatible — extend the existing ES filter path, do not restructure it.
- Child 1 recorded RECORD_ONLY note O-1: a key written bare in one enabled snippet variant stays in the template-wide `requiredKeys` union, so it is still hard-blocked by `PersonalizationGateService.evaluate` even when another variant supplies a default. This is deliberate and test-pinned; child 2 must not change it.

## Required commands (run all; report exit codes and counts)

```
node --test src/test/js/batchSendTaskConsoleInteraction.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendTaskRuntimeIntegrationTest' -DfailIfNoTests=false
git diff --check
```

Baselines at the child-2 product base (`971a21d`, product-identical to the dispatch HEAD) are recorded in the ledger; the previously recorded full directed Maven baseline at the master base was 235 Kotlin tests / 990 exec-plugin JS tests, 0 failures, and after child 1 the JS suite is 996 pass with `MailComposeTemplateServiceTest` 51 + `PersonalizationGateServiceTest` 12.

## Deliverable

One local implementation commit `feat(fast-p): implement batch-research-direction-filter` containing only the 10 authorized files, plus the full execution report at the path above. Exclude `docs/plans/fast/**` and `target/` from the commit.

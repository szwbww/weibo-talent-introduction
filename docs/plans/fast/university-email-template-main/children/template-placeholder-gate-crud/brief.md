# Child Brief — template-placeholder-gate-crud

- Child ID: `template-placeholder-gate-crud`
- Approved child plan: `docs/plans/2026-09-18/template-placeholder-gate-crud.md` (commit:d51c89105fb5a1f3cf6a1d3a3d71612b8898fc07)
- Master plan: `docs/plans/2026-09-18/00-university-email-template-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main`
- Branch: `fast/university-email-template-main`
- `child_base_sha`: `d51c89105fb5a1f3cf6a1d3a3d71612b8898fc07`
- Execution report: `docs/plans/fast/university-email-template-main/children/template-placeholder-gate-crud/execution.md`

## Authority

The approved child plan is the complete contract: requirements, invariants I-1..I-4, style contract S-1/S-2, implementation steps, authorized file list, acceptance criteria, and manual acceptance list. Read it in full before editing. The master plan adds cross-plan invariants M-1, M-3, M-4 that also bind this child.

## Authorized files (exactly these 9; no other file may be created or modified)

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt`
4. `src/main/resources/static/app.js`
5. `src/main/resources/static/index.html`
6. `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateServiceTest.kt`
8. `src/test/js/composeTemplatePreview.test.js`
9. `src/test/js/gateTemplateFilter.test.js`

## Key invariants

- I-1: `${key}` in the current subject/body is required; `${key|non-empty default}` is not; empty default, unknown key, and broken tokens are rejected at template save; first-occurrence order deduplicated; legacy `required_keys` never decides and never stacks.
- I-2: send-time gate is exact for the actually selected blocks/variants (empty and whitespace-only values count as missing); ES prefilter stays conservative and limited to `ALLOWED_HAS_FIELDS`; final `${...}` residue check stays.
- I-3: new introduction templates must be recognizable by `BatchSendTaskConfigService.resolveMailType` as `INTRODUCTION`; update preserves id/mailType/templateCode; delete of an unreferenced template succeeds; delete of a template referenced by `batch_send_task_config.template_id` returns a clear business conflict without clearing the task reference or swallowing the DB error.
- I-4: the variable menu renders every key from `/api/qa/template-variables-meta` (including `institution`, `primaryResearchField`); the template editor may insert bare `${key}` and `${key|default}`; QA/reply-snippet validation rules stay exactly as they are now.
- M-1: template decides the variable gate; ES prefilter must not be stricter than the actual send gate.
- M-3: configuration and execution stay consistent — after a template change, `gate-fields`, estimation, and sending all read the current template.
- M-4: no template/task instances, no expert data writes, no send calls; tests only. Never bump frontend cache keys (`?v=` in `index.html`) — that triad change is out of scope and would require unauthorized test files.

## Downstream interface frozen for child 2 (`batch-research-direction-filter`)

- Template-gate semantics for `${primaryResearchField}` (no default) and `${primaryResearchField|your research area}` (default) must be exactly as the master plan's acceptance M-1 describes, exposed through `GET /api/compose-templates/{id}/gate-fields` (`requiredKeys`, `esFields`) and through `PersonalizationGateService`.
- Do not change the signature or call shape of `ManualInitialOutreachService.resolveScope`, `BatchExecutionModels.RecipientScope`, or `ExpertSearchService.fieldPresenceFilter`; child 2 extends the ES filter path and the in-memory retry matcher.
- `researchFields` remains the ES field behind `primaryResearchField`.

## Style contract

- S-1/S-2: reuse only the listed existing classes (`.var-chip`, `.var-insert-wrap`, `.var-insert-btn`, `.var-insert-menu`, `.var-insert-group-label`, `.var-insert-group`, `.var-validation-hint`, `.compose-template-editor`, `.batch-gate-field`, `.batch-gate-hint`, `.batch-gate-keys`, `.batch-gate-keys-label`, `.batch-gate-keys-dropped`, `.tag-chip`). No inline styles, no new classes, no CSS edits, no DOM skeleton changes — text/JS only.

## Required commands (run all; report exit codes and counts)

```
node --test src/test/js/composeTemplatePreview.test.js src/test/js/gateTemplateFilter.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='MailComposeTemplateServiceTest,PersonalizationGateServiceTest' -DfailIfNoTests=false
git diff --check
```

Baseline at `child_base_sha`: `node --test` on those two files (plus `batchSendTaskConsoleInteraction.test.js`) exited 0 with tests 87 pass / 0 fail; the Maven directed run baseline is recorded in the ledger.

## Deliverable

One local implementation commit `feat(fast-p): implement template-placeholder-gate-crud` containing only the 9 authorized files, plus the full execution report at the path above. Exclude `docs/plans/fast/**` from the commit.

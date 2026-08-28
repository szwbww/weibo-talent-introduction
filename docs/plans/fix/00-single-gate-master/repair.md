# Repair Plan: 00-single-gate-master

Status: DRAFT — HUMAN APPROVAL REQUIRED

## Review Baseline

- Governing master: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/2026-08-28/00-single-gate-master.md`
- Master SHA-256: `fb2e511d39b90da7a5d81057263b85899b97bdb2a586652e91eea371ed901184`
- Invoked master commit: `1f5a916489933fc9b2e8e469037fc912d55edd5d`
- Review boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..4636727749202052c6affd2550e5353139fcb4a1`
- Finding: `V-1` (P1, new): a `MATERIAL_REMINDER` template selected in the batch configuration editor cannot be saved with `expertTypes: []`, because the UI applies the INTRODUCTION-only non-empty check unconditionally. This violates master M1's no-effect rule and child 03 I3-1.

## Root Cause

`saveBatchConfigEditor()` already resolves `templateId`, and the file already provides `resolveBatchTemplateMailType(templateId)`, but the empty-picker check does not use it. The backend correctly scopes the requirement to INTRODUCTION, leaving the editor as the inconsistent layer.

## Authorized Files

- `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/src/main/resources/static/app.js`
- `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/src/test/js/batchExpertTypeFilter.test.js`

No backend source, migration, configuration, index mapping, plan, review artifact, or other file is authorized.

## Repair Tasks

### R1 — Scope the editor validation to INTRODUCTION

File: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/src/main/resources/static/app.js`

1. Preserve payload construction and the existing exact INTRODUCTION error text: `请至少选择一个研发类型`.
2. Determine the selected template mail type through the existing `resolveBatchTemplateMailType(templateId)` helper.
3. Block an empty `expertTypes` picker only when that resolved type is `INTRODUCTION`.
4. For `MATERIAL_REMINDER`, allow `expertTypes: []` through the existing create/update API path unchanged.

Invariants: no default picker values change; no selector/template filtering change; no backend validation change; an INTRODUCTION configuration with an empty picker still issues no API request.

### R2 — Add discriminating browser-runtime coverage

File: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/src/test/js/batchExpertTypeFilter.test.js`

1. Add a test selecting an enabled `MATERIAL_REMINDER` template and an empty expert-type picker; assert one create/update API call is made, its payload has `expertTypes: []`, and no `请至少选择一个研发类型` error is emitted.
2. Add or retain a paired empty-INTRODUCTION assertion: no API call and the exact existing error text.
3. Keep the test harness, editor DOM shape, and existing non-empty INTRODUCTION coverage unchanged except for the smallest fixture additions needed to discriminate the mail type.

## Required Verification

Run from `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate`:

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/batchExpertTypeFilter.test.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

Acceptance: both branches are proven—INTRODUCTION rejects empty types before the request; MATERIAL_REMINDER preserves its historical empty-types behavior and reaches the same API route. All commands exit 0.

## Commit Contract

After verification, create exactly one local product commit, staging only the two Authorized Files, with this resolved subject:

`fix(fast-p): preserve material reminder empty expert types`

No amend, history rewrite, push, merge, deployment, or unrelated staging is authorized.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/fix/00-single-gate-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with the resolved product commit subject `fix(fast-p): preserve material reminder empty expert types` recorded in this plan.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/review/single-gate/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with the resolved evidence commit subject `docs(review-fast-p): record repair execution` recorded in this plan.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.

## Next Action

`$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate/docs/plans/fix/00-single-gate-master/repair.md`

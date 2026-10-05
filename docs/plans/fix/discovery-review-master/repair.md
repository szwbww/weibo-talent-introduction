# Repair Plan: discovery-review-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-10-04/discovery-review-master.md
Verification report: aggregate verify-p, epoch 2, 2026-10-05, FAIL / PROGRESSING
Implementation boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..6b5c201c7a8667376cb214f74a7069594cc9a6cc

## Objective

For one real ES `_id`, produce at most one outbound target across preview, execution, levels, pages, and NEW retries, while preserving the existing normalized-ORCID guard.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-3 | P1 | Master I-6; child 05 I-3 / implementation step 1 | The call sites consume `RecipientSelection.includedDocIds` by filtering the original ORCID-deduplicated profiles. A selected real docId therefore re-expands to every input profile bearing that docId. |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | Resolved at `6b5c201`: preview/total and execution now share the normalized-ORCID/retry set. |
| V-2 | Resolved at `6b5c201`: an interrupted pending apply task derives `INTERRUPTED` and the UI has no completion transition. |
| `ExpertContactLocationServiceTest` 19 timezone errors | Pre-existing ancestor/environment defect outside this boundary and repair scope. |
| Missing live local ES index | Required evidence remains unavailable; do not treat an environment provision as a product repair. |
| D1 and child RECORD_ONLY observations | Policy, manual-acceptance, or non-repairable observation; not a confirmed V-3 root cause. |

## Unchanged Contract

- Preserve normalized-ORCID deduplication, retry precedence, selector decisions/reason accounting, template gates, cancellation, and no-write preview behavior.
- Do not alter the send attempt uniqueness policy, D1 policy, schemas/migrations, ES mappings, admission decisions, account assignment, or SMTP behavior.
- V-1 and V-2 semantics remain unchanged; interruption never auto-retries.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt | Carry one real-docId seen set through retry, ES count, and execution page filtering before converting selected docIds back to profiles. |
| src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt | Prove distinct normalized ORCIDs sharing one real docId yield one preview/total and one execution target, including retry-versus-ES. |

## Repair Tasks

### R-1: Preserve real-docId deduplication at target construction

- Resolves: V-3.
- Root cause: `BatchRecipientSelectionService.select` deduplicates real docIds (`:94-99`), but `ManualInitialOutreachService.countEsTargets` ORCID-deduplicates first (`:1897`) and then maps `includedDocIds` back onto every matching original profile (`:1902`). `OutreachTargetIterator` likewise only removes normalized ORCIDs before `filterPage` (`OutreachTargetIterator.kt:48-54`). Two profiles with the same real docId and different ORCIDs consequently survive as two targets.
- Files: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`; `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`.
- Change: Before final selector-to-profile conversion, retain the first eligible profile for each real docId across retry targets and all ES pages/levels; use that same docId set for preview and execution. Retain the existing ORCID set as an additional guard, rather than replacing it.
- Regression test: Feed two eligible profiles with different normalized ORCIDs but the same `esDocId`, including an ES/retry collision. Assert preview pending/total equals one, execution exposes one target/attempt, and the retained profile is the retry target where retry precedence applies.
- Existing verification: focused `ManualInitialOutreachServiceTest`; full Node suite; isolated `DiscoveryReviewRepositoryIT`; full JDK11 Maven suite.
- Must not change: selector's real-docId admission lookup, per-reason aggregation, retry policy, output ordering except removal of prohibited duplicates, filter/template behavior, or no-write preview.
- Prohibited: no `OutreachTargetIterator` change, no schema/migration/index work, no send-side dedup workaround, and no D1 expansion.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ManualInitialOutreachServiceTest test`
2. `node --test src/test/js/*.test.js`
3. `DB_URL="jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`

## Completion Criteria

- A same-real-docId/different-ORCID duplicate across ES pages/levels and retry/ES produces exactly one selected target in preview and execution.
- The regression proves normalized-ORCID deduplication remains active.
- Only the two Authorized Files change; V-1/V-2 behavior remains green.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md` invocation authorizes:

1. Only these Authorized Files and required verification commands:
   - `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ManualInitialOutreachServiceTest test`
   - `node --test src/test/js/*.test.js`
   - `DB_URL="jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test`
   - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test`
2. After R-1, the focused JVM test, full Node suite, isolated repository IT, and all required commands run freshly, create exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the two Authorized Files. Report the full Maven result without suppression; the already excluded 19 `ExpertContactLocationServiceTest` timezone errors may remain for independent verification, but no other failure is waived. Product commit subject: `fix(discovery-review): preserve real document target uniqueness`.
3. Append `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/review/discovery-review-master/repair-execution.md` with the exact human approval source, repair identity, pre/post code SHAs, changed files, commands and exit/count evidence, deviations, executor identity when exposed, and clean-state evidence.
4. Create exactly one docs-only evidence commit containing only that execution handoff. Evidence commit subject: `docs(review-fast-p): record repair execution`.
5. Return to the already authorized aggregate re-review in the same task only when the human invocation requests `$review-fast-p docs/plans/fast/2026-10-04-discovery-review-master/human-review-handoff.md`, using the committed repair-execution handoff.

This authorizes no extra files, amendment, history rewrite, push, merge, deployment, or product repair beyond this plan.

# Repair Execution — 00-university-email-template-main

- Repair plan: `docs/plans/fix/00-university-email-template-main/repair.md`
- Repair identity: sha256 `25a2607267d48e8e7f1da86aa0c27dd03e5146c4fce3451b9dba86d73fc8184a`
- Execution epoch: NEW (no prior `repair-execution.md`; review ledger recorded `Repair code head: N/A`)
- Approval source: human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main/docs/plans/fix/00-university-email-template-main/repair.md` invocation, 2026-09-18, per the plan's own Review-Fast-P Execution Handoff; the follow-up human instruction `授权 继续` (2026-09-18) approved amendment A1 below.
- Executor: Main (omp session, controller context; `execute-p` defines no agent gate). No subagent identity to report.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main`
- Target branch: `fast/university-email-template-main`
- Pre-execution code SHA: `decdb28dfe6431c9f238b76fa64fc9a1aad7939e`
- Pre-execution evidence HEAD: `04aeeb6b5c884dff347fbdb1ddcf5e22f6eea133`
- Post-execution code SHA: `2541ef8f91411a086ca0cda30cf796ed3b155cc4`
- Evidence commit: recorded by the review ledger (`docs/plans/review/university-email-template-main/ledger.md`); not self-referenced here.

## Approved amendment A1

| Field | Value |
|---|---|
| Plan | `docs/plans/fix/00-university-email-template-main/repair.md` |
| Before | sha256 `25a2607267d48e8e7f1da86aa0c27dd03e5146c4fce3451b9dba86d73fc8184a` |
| After | sha256 `25a2607267d48e8e7f1da86aa0c27dd03e5146c4fce3451b9dba86d73fc8184a` (plan bytes unchanged; scope extended by explicit approval only) |
| Master rule | Master M-1 / child 1 I-2 ("模板本次实际主题/正文中的 `${key}` 缺值须阻止发送；`${key|非空默认值}` 缺值须用默认值且不形成该变量门禁") |
| Reason | The plan's Authorized Files were insufficient: three tests outside them pinned the replaced behavior, so R-1 could not leave the suite green without editing unauthorized files. |
| Approval | HUMAN: `授权 继续` (2026-09-18), issued in response to the `PLAN_CONFLICT` report that named this exact amendment. |

Amendment content:

1. Authorized Files extended by `src/test/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposerTest.kt` and `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt`.
2. Three legacy expectations rewritten from a *defaulted* selected token to a *selected bare* token, preserving their intent (a selected bare key with a missing value still blocks):
   - `IntroductionMailComposerTest.kt:427` `compose throws PersonalizationGateException when required key fell back` → `… when a selected bare key has no value`, raw text `Topic ${recentWorkTitle|Untitled}` → `Topic ${recentWorkTitle}`.
   - `IntroductionMailComposerTest.kt:455` `compose throws PersonalizationGateException for template id path` → `… for a bare key on the template id path`, raw text `Focus ${primaryResearchField|N/A}` → `Focus ${primaryResearchField}`.
   - `ManualExpertMailServiceGateTest.kt:271` `gate blocks send when required key falls back to default` → `gate blocks send when a selected bare key has no value`, raw text `Subject: ${recentWorkTitle|Untitled}` → `Subject: ${recentWorkTitle}`.
3. Verification command set extended with `IntroductionMailComposerTest,ManualExpertMailServiceGateTest`; as written, the plan's own commands pass 260/0 while those two classes were red, so they could not detect this breakage.

## Changed files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt` | `evaluate` derives the gating keys from the selected raw texts with `MailPlaceholderService.requiredKeysIn` (bare tokens only) instead of generic token occurrence, then intersects with the supplied template-wide required union (V-1); KDoc updated. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateServiceTest.kt` | New discriminating regressions: a template-wide required key that the selected text defaults no longer blocks; a bare token of a key defaulted elsewhere in the same send still blocks; multi-text collection test now uses bare tokens. |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposerTest.kt` | Two gate expectations rewritten to a selected bare token (amendment A1). |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt` | One gate expectation rewritten to a selected bare token (amendment A1). |

## Commands (fresh, this invocation)

| Command | Result | Evidence |
|---|---|---|
| `mvn test -Dtest='PersonalizationGateServiceTest,MailComposeTemplateServiceTest' -DfailIfNoTests=false` | PASS | exit 0, BUILD SUCCESS; 13 + 51 = 64 tests, 0 failures, 0 errors |
| `node --test src/test/js/composeTemplatePreview.test.js src/test/js/gateTemplateFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js` | PASS | exit 0; 100 pass, 0 fail |
| `mvn test -Dtest='MailComposeTemplateServiceTest,PersonalizationGateServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendTaskRuntimeIntegrationTest' -DfailIfNoTests=false` | PASS | exit 0, BUILD SUCCESS; 260 tests, 0 failures, 0 errors |
| `git diff --check` | PASS | exit 0, no output |
| `mvn test -Dtest='IntroductionMailComposerTest,ManualExpertMailServiceGateTest' -DfailIfNoTests=false` (A1) | PASS | exit 0, BUILD SUCCESS; 12 + 5 = 17 tests, 0 failures, 0 errors (the three previously failing expectations now pass) |
| `mvn test` (full repository suite, safety net) | PASS | exit 0, BUILD SUCCESS; 3477 Kotlin tests across 248 classes, 0 failures, 0 errors; exec-plugin JS suite 1003 pass, 0 fail |

## Deviations

- Pruned one stale registered worktree (`/private/tmp/talent-deploy-a7d2a63`, already reported prunable by `git worktree list`) because the mandated `worktree_identity.py` gate aborts on a registered path that no longer exists. No other worktree or branch was touched.
- The first invocation attempt returned `PLAN_CONFLICT` after an unfruitful in-scope implementation; its edits were reverted (`git checkpoint` state `04aeeb6`, empty `git status --porcelain`) before A1 was approved and re-applied. That experiment produced the evidence for A1.

## Clean-state evidence

- `git status --porcelain` empty immediately after the product commit; `HEAD` = `2541ef8f91411a086ca0cda30cf796ed3b155cc4`; the commit changes exactly the four files above.
- Plan identity rechecked unchanged (`25a26072…84a`); worktree identity rechecked via `worktree_identity.py --expect-root/--expect-branch/--expect-git-dir`.

## Not done

- No push, merge, rebase, amend, deployment, or worktree cleanup.
- Manual acceptance items of the master plan remain with the human; this file records execution evidence only, not verification.

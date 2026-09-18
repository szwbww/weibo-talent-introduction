# Aggregate Machine Verification — university-email-template-main

## Epoch 1 — 2026-09-18T09:25:28+0800

- Master plan: `docs/plans/2026-09-18/00-university-email-template-main.md` (sha256 `411628a2d0106a44ba725467edfdf518fbb0a0eb6c3b5e5753dbf6a676f2b60e`)
- Governing master identity: sha256 `411628a2d0106a44ba725467edfdf518fbb0a0eb6c3b5e5753dbf6a676f2b60e`; recorded commit `d51c89105fb5a1f3cf6a1d3a3d71612b8898fc07`; state `CONSISTENT`.
- Boundary: `7f7b3a821f09d4255dc735c1e7b96eacf9c32164..decdb28dfe6431c9f238b76fa64fc9a1aad7939e`
- Reviewer: `/root/aggregate_reviewer`
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/00-university-email-template-main/repair.md` (sha256 `25a2607267d48e8e7f1da86aa0c27dd03e5146c4fce3451b9dba86d73fc8184a`), `DRAFT_READY`.

## Fresh command evidence

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/composeTemplatePreview.test.js src/test/js/gateTemplateFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js` | PASS | exit 0; 100 pass, 0 fail |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='MailComposeTemplateServiceTest,PersonalizationGateServiceTest,ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,BatchSendTaskRuntimeIntegrationTest' -DfailIfNoTests=false` | PASS | exit 0; 259 tests; 0 failures, 0 errors (51/12/110/64/22) |
| `git diff --check` | PASS | exit 0; no output |
| `git diff --check 7f7b3a8..decdb28` | PASS | exit 0; no output |

## Master contract matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1: actual bare/default send gate | FAIL | `PersonalizationGateService.kt:55-59` treats `${researchFields\|Science}` as a required occurrence; `IntroductionMailComposer.kt:28-29` supplies template-wide required keys. This contradicts selected-text/default semantics. |
| M-1: conservative ES prefilter; no legacy-key rule | PASS | `MailComposeTemplateService.kt:189-211`; directed tests pass. |
| M-2: direction/type independent AND | PASS | `BatchExecutionModels.kt:107-110`; `ManualInitialOutreachService.kt:1315-1336`; 110 outreach tests pass. |
| M-3: live template read through gate/prefilter/send | PASS | `MailComposeTemplateService.kt:178-211`; `IntroductionMailComposer.kt:22-29`. |
| M-3: direction persistence/snapshot/manual/legacy ANY | PASS | `BatchSendTaskConfig.kt:29-30`; `BatchExecutionModels.kt:30,173`; V128 default; 196 child-2 Kotlin tests included. |
| M-4: no task/template/data/send side effects | PASS | Boundary contains implementation, tests, migration, and frontend only; no instance/data writes or send invocation additions. |
| Child scopes/style contracts | PASS | Product diff matches combined authorized files; no `styles.css` change. |
| Cross-plan ABSENT + bare research-field gate | PASS | `ManualInitialOutreachServiceTest` included in fresh aggregate suite. |
| Manual A-1 through A-4 | PENDING | Human test environment and acceptance required. |

## Findings

### V-1 — P1 — NEW

Defaulted selected content can be blocked. `effectiveRequiredKeys` unions bare keys across possible variants (`MailComposeTemplateService.kt:178-186`); composers pass that union (`IntroductionMailComposer.kt:28-29`, `ManualExpertMailService.kt:236-238`); and `PersonalizationGateService.evaluate` checks generic token occurrence (`:55-59`). A selected `${researchFields|Science}` with an empty value therefore blocks, confirmed by the intentional conflicting test at `PersonalizationGateServiceTest.kt:68-80`. Master M-1 and child I-2 require it to render with its default.

## Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 1 O-1 | M-1 / child I-2 | Promoted to V-1 P1 | Selected defaulted token can be blocked by a bare token in another possible variant. |
| Child 1 O-2 | No mandatory requirement | Observation | Cosmetic stale wording; no contract failure. |
| Child 1 O-3 | No mandatory requirement | Observation | Test-fidelity note only. |
| Child 2 O-1 | M-3 legacy `ANY` | Observation | Migration/default behavior is source-verifiable. |
| Child 2 O-2 | No mandatory requirement | Observation | Dead method is pre-existing. |

No product code was modified by the reviewer.

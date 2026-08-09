# Aggregate Machine Verification — personalization-gate

## Epoch 1 — 2026-08-09T16:56:11+08:00

- Master plan: docs/plans/2026-08-09/personalization-gate-master.md (sha256 cbae234bc59e9ae9fe67315bd86e4a86ee1d4ddd4ef54b94dbd14ebde13b8324)
- Governing master identity: sha256 cbae234bc59e9ae9fe67315bd86e4a86ee1d4ddd4ef54b94dbd14ebde13b8324; recorded identity matches
- Master identity state: CONSISTENT; amendments N/A
- Boundary: ab5dcbb7fbb58f5e8a9b13b7e54022effd270b77..d848b8c3999fd7d67388be6d7b340ab48db43ff2
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/personalization-gate-master/repair.md — DRAFT_READY

## Verification Result: FAIL

Manual acceptance: PENDING (AM-1 through AM-7).

### Fresh Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; JVM 2231, failures 0, errors 0, skipped 4; Node 478 pass, 0 fail |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; JVM 2231, failures 0, errors 0, skipped 4; Node 478 pass, 0 fail; WAR repackaged |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='PersonalizationGateServiceTest,ManualExpertMailServiceGateTest,MailVariableServiceTest,IntroductionMailComposerTest,MailComposeTemplateServiceTest'` | PASS | exit 0; 98 tests, failures 0, errors 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ExpertSearchServiceTest,ComposeTemplateGateControllerTest'` | PASS | exit 0; 41 tests, failures 0, errors 0 |
| `node --test src/test/js/gateTemplateFilter.test.js` | PASS | exit 0; 4 pass, 0 fail |
| `git diff --check` | PASS | exit 0; no output |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| O-1 Zero placeholder residue | PASS | `ManualExpertMailService.kt:60-65`; `IntroductionMailComposer.kt:38-44`; `PersonalizationGateService.kt:62-77` |
| O-2 Required values skip sending | PASS | `PersonalizationGateService.kt:41-54`; raw template input from `MailComposeTemplateService.kt:184-190` |
| O-3 Skip reason visible | PASS | `BatchExecutionModels.kt:94-106`; `ManualInitialOutreachService.kt:322-327,:598-608` |
| O-4 Template gate filter/count | PASS | `MailComposeTemplateController.kt:51-57`; `app.js:11354-11368` |
| M-1 Existing composer variables/callers | PASS | `IntroductionMailComposer.kt:20-44` retains `MailVariableService.buildVariables`; no signature change |
| M-2 Existing five chips unchanged | PASS | `index.html:555-560`; only `recentWorkTitles` inserted |
| M-3 ES `researchFields` storage unchanged | PASS | boundary has no `ExpertDiscoveryService` or mapping write-path change |
| M-4 Existing `requireValidPlaceholders` callers | PASS | no changed caller or signature in boundary |
| M-5 NULL/empty required keys preserve send behavior | PASS | `MailComposeTemplateService.kt:140-170`; empty required set disables gate at `PersonalizationGateService.kt:46-48` |
| I-M1 / P1 I-2 residue hard-stop | PASS | regex and unconfigurable exception at `PersonalizationGateService.kt:62-77`; both send paths invoke it |
| I-M2 send-time authority | PASS | gate uses current variables/raw text; P2 only builds ES list/query |
| I-M3 server single source | PASS | controller forwards `effectiveRequiredKeys`/`requiredEsFields`; client consumes `esFields` only |
| I-M4 derived ES mapping | PASS | `MailPlaceholderService.kt:160`; no ES write-path change |
| I-M5 / P1 I-5 both paths gated | PASS | `ManualExpertMailService.kt:194-199`; `IntroductionMailComposer.kt:26-33` |
| P1 I-1 full manual variables | PASS | production branch `ManualExpertMailService.kt:173-178` resolves expert then calls `buildVariables` |
| P1 I-3 raw-text fallback decision | PASS | `PersonalizationGateService.kt:49-54`; `MailComposeTemplateService.kt:184-190` |
| P1 I-4 malformed/blank keys disable gate | PASS | `MailComposeTemplateService.kt:156-170`; focused tests pass |
| P1 I-6 batch skip/state handling | FAIL | `ManualInitialOutreachService.kt:322-327` increments `processedTotal`, `roundSent`, `roundProcessed`, then common bookkeeping at `:342-344` increments all three again |
| P1 I-7/I-8 `primaryResearchField` metadata/value | PASS | `MailVariableService.kt:146-150`; `MailPlaceholderService.kt:101-183` |
| P2 I-9 approximate count wording/filter | PASS | `ExpertSearchService.kt:24-50`; `app.js:11354-11368` |
| P2 I-10 failed gate request has no filter/fallback | FAIL | after a prior successful selection, `app.js:11401-11404` only reports error; old active chips/state/summary remain |
| P2 I-11 allow-list/explicit invalid field error | PASS | `ExpertSearchService.kt:24,799-802` |
| P2 I-12 keyword-only blank exclusion | PASS | `ExpertSearchService.kt:32-50,621-639` |
| P2 S-1 recent-paper chip | PASS | `index.html:559`; `.tag-chip` rules unchanged |
| P2 S-2 summary style/DOM | PASS | `index.html:563-571`; `styles.css:579-609` |
| P2 S-3 exact count wording/hidden unlimited state | PASS | `app.js:11354-11373` |
| Explicit out-of-scope items | PASS | no block rendering, enrichment trigger, ES write change, or unrelated UI scope in boundary |
| Authorized scope | PASS | product/test changes match P1+P2 authorized union; boundary docs are fast-p evidence only |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `ManualInitialOutreachService.kt:322-327,342-344` double-advances one gate-blocked MATERIAL_REMINDER recipient |
| V-2 | NEW | `app.js:11401-11404` leaves prior gate-derived fields active after a failed template switch |

### Findings

- V-1: P1 I-6 mandatory batch-state behavior fails. A MATERIAL_REMINDER gate rejection records the skip once, but advances progress/round counters twice. This distorts progress and consumes round quota for a skipped recipient.
- V-2: P2 I-10 mandatory failure behavior fails. Switching from a successfully loaded gate template to one whose `gate-fields` request fails leaves the old gate filter active, despite the requested selection failing.

### Evidence Boundaries

- Manual acceptance remains pending. No browser, mailbox, or ES black-box acceptance was performed.
- Product boundary ends at `d848b8c`; later commits are evidence docs only.
- The supplied evidence head did not resolve locally; observed current evidence head is `10f7578caba30fc890e15d7b09984d3318e767f1`. This does not affect the product boundary.
- No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1: legacy null `mailVariableService` test fallback | I-M1/I-M5 | RECORD_ONLY | `ManualExpertMailService.kt:179-184`; Spring production injection supplies the service and production uses `buildVariables` |
| O-1: template options populate at first focus | observable outcome 4 / I-M3 | RECORD_ONLY | no master timing requirement; preserves repository pre-auth no-network invariant |

## Repair Planning Result: DRAFT_READY

- Baseline plan: docs/plans/2026-08-09/personalization-gate-master.md
- Verification result: FAIL, INITIAL
- Repair artifact: docs/plans/fix/personalization-gate-master/repair.md
- Included findings: V-1, V-2.
- Excluded findings: O-1 test-only fallback; O-2 observable-equivalent focus timing.
- Repair artifact includes the required Review-Fast-P one-approval execution handoff.
- No implementation was performed.

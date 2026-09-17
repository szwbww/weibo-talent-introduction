# Execution Report — template-placeholder-gate-crud

- Child: `template-placeholder-gate-crud` (master `docs/plans/2026-09-18/00-university-email-template-main.md`, T1)
- Child plan: `docs/plans/2026-09-18/template-placeholder-gate-crud.md` (identity `commit:d51c89105fb5a1f3cf6a1d3a3d71612b8898fc07`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-university-email-template-main`
- Branch: `fast/university-email-template-main`
- `child_base_sha`: `d51c89105fb5a1f3cf6a1d3a3d71612b8898fc07`
- Result: **READY_FOR_VERIFICATION**
- Authorized files touched: 9 / 9 (no other file created or modified; no migrations, CSS, `?v=` cache keys, new classes or inline styles)

## 1. What changed per file

### 1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt`
- Added `requiredKeysIn(text)` (line 88): the single token parser for the gate — a token gates when it has no `|` **or** a blank default (`ParsedPlaceholder.gates`, line 275 is `fallback.isNullOrBlank()`); first-occurrence order, deduplicated. `${key|non-blank default}` never gates.
- Added `templatePlaceholderViolations(text)` (line 107) + `requireValidTemplatePlaceholders(text)` (line 123) for compose-template saves: unknown key, blank default (`ParsedPlaceholder.blankDefault`, line 278) and broken `${` fragments (`brokenTokens`, line 134) are rejected with `IllegalArgumentException` → existing global 400 mapping.
- `validatePlaceholders` / `requireValidPlaceholders` / `unknownPlaceholderTokens` / `detectFallbackKeys` / `filterableEsFields` / `placeholderKeysIn` are byte-identical: QA rules, QA fact bodies, reply snippets and content variants keep their previous strictness (`ReplySnippetService:187,226`, `QaFactBodyPolicy:24`, `ContentVariantService:86` unchanged callers).

### 2. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
- `effectiveRequiredKeys(templateId)` (line 178) now derives the gate from the **live** template: subject + every text the enabled blocks can contribute (incl. every enabled snippet variant) via `renderTextPool` (line 225) / `possibleRenderTexts` (line 235). The `required_keys` JSON column is no longer read; `parseRequiredKeys`, its `TypeReference` import and the unused logger were deleted. A stateless `MailPlaceholderService()` instance is created in the class body (line 45) so the constructor arity/order stays frozen for the 9 other call sites.
- `requiredEsFields(templateId)` (line 195) = `alwaysRequiredKeys` (line 200) mapped through `ES_FIELD_BY_KEY`, deduplicated: a key must be required by the subject or by **every** variant of a block to enter the ES prefilter (conservative subset of the send gate, I-2/M-1).
- `create` (line 71): `mailType` defaults to `INTRODUCTION` (`INTRODUCTION_MAIL_TYPE`, line 721) so `BatchSendTaskConfigService.resolveMailType` accepts a UI-created template.
- `update` (line 97): keeps the stored `mailType`/`templateCode`/id; the request value only backfills a template that has none.
- `delete` (line 129): deletes the row first, then the blocks; a FK/reference `DataIntegrityViolationException` is turned into `IllegalArgumentException("该邮件模板已被批量任务引用，无法删除（templateId=N）")` (→ 400 via `GlobalExceptionHandler.handleIllegalArgument`), any other integrity error is rethrown untouched (`isReferenceViolation`, line 142). The task reference and the template row are never cleared.
- `validateCommand` (lines 521-527) validates the subject and every `CUSTOM_TEXT` block with the new loose rule; snippet/QA block bodies keep their own editors' validation.

### 3. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt`
- `evaluate` (line 45): a key is missing when it occurs in the raw texts actually sent (`keysInText`, line 53) and its variable value `isNullOrBlank()` (line 58) — `""` and whitespace-only count as missing. Keys resolved by a default are not listed as required by the template, so they render their default instead of blocking. `requiredKeys` empty ⇒ gate disabled (unchanged). `requireNoPlaceholderResidue` is untouched (final `${...}` residue still aborts before SMTP).

### 4. `src/main/resources/static/app.js`
- `validatePlaceholderText(text, options)` (line 2679): new `lenient` mode used only for the compose-template editor — bare nullable tokens and `${key|默认值}` are legal, while unknown keys, blank defaults and broken `${` fragments stay illegal; QA/snippet callers keep the old severity.
- `brokenPlaceholderFragments` (line 2709) and `isComposeTemplateVarTarget` (line 2731, `composeTemplateSubject` / `composeBlockCustomText-*`).
- `updateVarValidationForTarget` (line 2839) passes `lenient` for template targets only.
- `bindVarChipBar` click handler (lines 2864-2880): the template editor inserts a bare `${key}` (the mandatory form); QA/snippet editors keep inserting `${key|默认值}` for nullable variables.
- `saveComposeTemplate` (lines 10096-10124): POST adds `mailType: "INTRODUCTION"`; PUT omits it so the server keeps the stored type.
- `refreshBatchGateState` (line 16693) + the stale comment at 16821: the `required_keys` term is gone from the copy (`该模板未配置门禁字段，门禁本身未启用，开启无效。`), no data-mapping change (esFields → chips untouched).

### 5. `src/main/resources/static/index.html`
- Line 1953 only: hint copy is now `提示: ${变量名} 为必填变量，缺值会阻止发送；${变量名|默认值} 缺值时使用默认值；内容块自定义文本同样支持插入变量`. Same element, same `compose-template-variable-hint` class, same DOM skeleton.

### 6–9. Tests
- `MailComposeTemplateServiceTest.kt`: replaced the 3 legacy `required_keys`-JSON tests with 14 tests for I-1/I-2/I-3 (see §2 evidence).
- `PersonalizationGateServiceTest.kt`: rewritten to the I-1/I-2 contract (8 evaluate tests incl. bare/whitespace/default/absent-key cases, 4 residue tests kept verbatim).
- `composeTemplatePreview.test.js`: new `compose template variable editor (I-1/I-4)` suite (5 tests) + `createVarEditorSandbox` / `VARIABLE_META_KEYS` helpers.
- `gateTemplateFilter.test.js`: new `batch gate copy never mentions the legacy required_keys column (S-2)` test; existing expert-gate tests untouched.

## 2. Per-invariant evidence

### I-1 — placeholders are the only gate source
| Claim | Evidence |
|---|---|
| `${institution}` + `${primaryResearchField\|your research area}` ⇒ `requiredKeys=[institution]`, `esFields=[institution]` | `MailComposeTemplateServiceTest.bare token is required while a defaulted token is optional (I-1)` (test file line 1261) |
| removing `\|...` makes `primaryResearchField` required (`esFields=[institution, researchFields]`) | `removing the default makes the key required and prefiltrable (I-1)` (line 1273) |
| legacy `required_keys` neither decides nor stacks (`requiredKeys = ["recentWorkTitle","expertName"]` on a template whose live subject is `${institution}` ⇒ `[institution]`) | `legacy required_keys column never decides the gate (I-1)` (line 1285) |
| first-occurrence dedup across subject/blocks; keys without ES field dropped | `gate keys deduplicate in first-occurrence order and drop keys without es fields (I-1)` (line 1301, asserts `[institution, senderEmail, primaryResearchField]` and `esFields=[institution, researchFields]`) |
| blank default / unknown key / broken token rejected at save | `create rejects blank default unknown key and broken token (I-1)`, `create rejects invalid placeholders inside a custom block (I-1)`, `update rejects invalid custom block placeholders without touching the stored row (I-1)`; parser: `MailPlaceholderService.kt:107-149` |
| bare token + non-blank default accepted at save | `create accepts bare tokens and non-blank defaults (I-1)` |

### I-2 — exact at send time, conservative in the prefilter
| Claim | Evidence |
|---|---|
| bare token with no value blocks with the exact keys | `PersonalizationGateServiceTest.evaluate blocks a bare required key that has no value` |
| `""`/whitespace count as missing | `evaluate treats whitespace-only values as missing` |
| default value renders instead of blocking | `evaluate never blocks when requiredKeys is empty`; Kotlin integration: `MailComposeTemplateServiceTest.bare token is required…` (union empty) + `renderText` fallback tests unchanged |
| gate is exact for the selected blocks/variants | `evaluate ignores required keys absent from the texts actually sent`, `evaluate collects missing keys across multiple raw texts in required order` |
| variant that only sometimes requires an ES field is not prefiltered but still hard-blocked | `key required by only some snippet variants gates the send but is never prefiltrable (I-2)` (union `[institution]`, `esFields=[]`); `key required by every snippet variant stays prefiltrable (I-2)`; `disabled snippet block contributes no gate keys (I-2)` |
| no unresolved `${...}` reaches SMTP | `requireNoPlaceholderResidue` untouched; `requireNoPlaceholderResidue throws…`, `…checks every rendered text`, `…accepts fully resolved and null texts`, `…accepts empty string` all green; `IntroductionMailComposerTest.compose throws PlaceholderResidueException on unresolved token` green |
| prefilter never stricter than the send gate | `requiredEsFields` ⊆ `effectiveRequiredKeys` by construction (`alwaysRequiredKeys` intersects each block's variant sets and is a subset of the union path in `effectiveRequiredKeys`); `ManualInitialOutreachService.kt:431-434` still intersects with `ExpertSearchService.ALLOWED_HAS_FIELDS` (unchanged file, its 100 mocked tests green) |

### I-3 — CRUD does not break references
| Claim | Evidence |
|---|---|
| create→list/get→update→preview→enable/disable→delete round trip; id/mailType/block order preserved | `create list get update preview enable disable delete round trip (I-3)` (in-memory repository double) |
| new template is `mailType=INTRODUCTION` (server default + explicit frontend value) | `MailComposeTemplateService.kt:71`; `create list get update preview enable disable delete round trip (I-3)` asserts `INTRODUCTION`; `app.js:10122` sends `mailType: "INTRODUCTION"` on POST |
| edit keeps the stored `mailType` even when the request sends another one | same test (`mailType = "MATERIAL_REMINDER"` in the command ⇒ stored `INTRODUCTION`); `update preserves template code and mail type when request omits them` still green |
| delete of a template referenced by `batch_send_task_config.template_id` ⇒ clear conflict, reference survives, DB error not swallowed | `delete of a template referenced by a batch task reports a clear conflict (I-3)` (asserts `已被批量任务引用`, verifies `blockRepository.deleteAllByTemplateId` never ran because the row delete is first) and `delete rethrows integrity failures that are not reference conflicts (I-3)` |
| enable/disable + list/preview consistency | round-trip test (`setEnabled(false/true)` ⇒ detail state) |

### I-4 — variable display aligned with storage
| Claim | Evidence |
|---|---|
| every `/api/qa/template-variables-meta` key reaches the menu, incl. `institution`/`primaryResearchField` | `composeTemplatePreview.test.js` → `every variable-meta key reaches the insert menu, including institution/primaryResearchField` (asserts `data-var-key` presence for all 21 keys and chip count == meta count) |
| `institution`/`primaryResearchField` insertable as bare tokens in the template editor; defaults still allowed | `inserts a bare token in the template editor and the defaulted token elsewhere` (template target ⇒ `Hello ${institution}`, QA target ⇒ `${institution|your institution}`); `relaxes bare tokens for the template editor but keeps QA/snippet rules strict` |
| save/reopen keeps text and block order | `reopening the editor keeps block order and custom text` (rendered `data-block-index` order + collected `blockOrder`/`customText`); Kotlin round-trip asserts `[0,1]` and `["First","Second"]` |
| QA/reply-snippet validation unchanged | `validatePlaceholders` code untouched; `MailVariableServiceTest` (54 tests) and `ReplySnippetServiceTest` (24) green; JS strict branch assertions in `relaxes bare tokens…` |
| hint copy documents both forms | `index.html documents the mandatory form of a template placeholder` (reads `index.html` source) |

### S-1 / S-2 — style contract
- `git diff --stat` lists `styles.css`: **absent** (9 authorized files only).
- `git diff -U0 -- src/main/resources/static/app.js src/main/resources/static/index.html | grep -E 'style='` → only the pre-existing `class="compose-template-variable-hint"` line changed (text content); no inline style, no new class, no new DOM node, no skeleton change.
- `gateTemplateFilter.test.js → batch gate copy never mentions the legacy required_keys column (S-2)` asserts the batch-gate function contains no `required_keys` and still contains `未配置门禁字段`.

### M-1 / M-3 / M-4
- M-1: `effectiveRequiredKeys`/`requiredEsFields`/`evaluate` all derive from placeholders only (no `required_keys` read anywhere in `src/main`); prefilter is the intersection subset of the gate.
- M-3: `gate-fields` (`MailComposeTemplateController:55-56`), estimation (`ManualInitialOutreachService.requiredEsFields`) and sending (`IntroductionMailComposer:28-31`, `ManualExpertMailService:236-239`) all read the same live-template methods, so a template edit is visible to all three on the next call; no cached copy exists.
- M-4: diff contains no template/task instance creation, no expert/ES write, no SMTP call (production code only reads repositories). Frozen interfaces untouched: `ManualInitialOutreachService.resolveScope`, `BatchExecutionModels.RecipientScope`, `ExpertSearchService.fieldPresenceFilter` have zero diff hunks (not in the authorized list, not edited); `GET /api/compose-templates/{id}/gate-fields` still returns `requiredKeys`/`esFields`; `researchFields` remains the ES field behind `primaryResearchField` (`ES_FIELD_BY_KEY` unchanged).

## 3. Commands, exit codes, counts

| # | Command | Result |
|---|---|---|
| 1 | `node --test src/test/js/composeTemplatePreview.test.js src/test/js/gateTemplateFilter.test.js` | exit 0 — tests 19, pass 19, fail 0 |
| 2 | `node --test src/test/js/composeTemplatePreview.test.js src/test/js/gateTemplateFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js` | exit 0 — tests 93, pass 93, fail 0 |
| 3 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -o test -Dtest='MailComposeTemplateServiceTest,PersonalizationGateServiceTest' -DfailIfNoTests=false` | exit 0 — BUILD SUCCESS; surefire: `MailComposeTemplateServiceTest` 51/0/0, `PersonalizationGateServiceTest` 12/0/0 (63 Kotlin tests, 0 failures); exec-plugin JS suite 996 pass / 0 fail |
| 4 | `mvn -o test -Dtest='…12 classes…' -DskipNodeTests=true` (regression over every real-service/mock call site of the changed seams) | exit 0 — BUILD SUCCESS; Tests run: 445, Failures: 0, Errors: 0 (MailComposeTemplateServiceTest 51, PersonalizationGateServiceTest 12, IntroductionMailComposerTest 12, ManualExpertMailServiceGateTest 5, ComposeTemplateGateControllerTest 3, MailVariableServiceTest 54, ReplySnippetServiceTest 24, QaRuleManagementServiceTest 60, MeetingConfirmationServiceTest 38, BatchSendTaskConfigServiceTest 64, ManualInitialOutreachServiceTest 100, BatchSendTaskRuntimeIntegrationTest 22) |
| 5 | `git diff --check` | exit 0 (no whitespace/conflict markers) |
| 6 | `git status --porcelain` | exactly the 9 authorized files modified (`M`), plus untracked `docs/plans/fast/…` (report/ledger, excluded from the commit) |
| 7 | `node --check src/main/resources/static/app.js` | exit 0 (also enforced by the exec-plugin binding inside command 3) |

One intermediate failure was fixed during the loop: `MailComposeTemplateServiceTest.gate keys deduplicate…` expected the subject's defaulted `primaryResearchField` before block keys; the implementation (correctly) orders by true first occurrence, so the expectation was corrected to `[institution, senderEmail, primaryResearchField]`. No production change was made for it.

## 4. Baseline comparison (child_base_sha `d51c891`)

| Suite | Baseline | After | Delta |
|---|---|---|---|
| `node --test` (3 files: composeTemplatePreview, gateTemplateFilter, batchSendTaskConsoleInteraction) | 87 pass / 0 fail | 93 pass / 0 fail | +6 (new I-1/I-4/S-2 tests) |
| `MailComposeTemplateServiceTest` (`@Test` count) | 40 | 51 | +11 (3 legacy `required_keys` tests replaced by 14 live-template/CRUD tests) |
| `PersonalizationGateServiceTest` | 9 | 12 | +3 (5 legacy evaluate + 4 residue → 8 evaluate + 4 residue) |
| exec-plugin JS suite (`src/test/js/*.test.js`) | 990 pass / 0 fail | 996 pass / 0 fail | +6, no regressions |
| Ledger directed Kotlin run (5 classes, 235 tests) | 235 pass / 0 fail | covered by command 4: all 12 affected classes 445/0/0 | no regressions |

## 5. Residual concerns / notes

1. **Mixed-variant templates (documented limitation).** `gate-fields.requiredKeys` is the union over all possible renders, while `requiredEsFields` is the intersection (as the plan's implementation step 1 and I-2 prescribe). If one snippet variant writes `${key}` and another writes `${key|default}`, a recipient whose selected render is the *defaulted* variant still carries `key` in the union, so `PersonalizationGateService.evaluate` blocks that send instead of using the default. Fixing it needs per-variant required keys at the composer (`IntroductionMailComposer`/`ManualExpertMailService` are outside the authorized file list, and their tests pin the current intersection contract: `IntroductionMailComposerTest`, `ManualExpertMailServiceGateTest`). Direction of the error is safe: the ES prefilter never excludes such recipients, and M-1's 500-person scenario (a template whose only token is `${primaryResearchField|your research area}`) yields an empty gate and is unaffected.
2. `MailComposeTemplateService` still takes `objectMapper` in the constructor although `required_keys` parsing was removed; the parameter is kept because nine out-of-scope call sites construct the service positionally. The column itself (`MailComposeTemplate.requiredKeys`, `V84`) is untouched and simply never read.
3. `placeholderDefaultFallback` (app.js) still has no entry for `primaryResearchField`, so a *QA/snippet* editor inserting that variable produces `${primaryResearchField|}` and the strict validator flags it (pre-existing behaviour, A-4's "add a non-empty default" flow). It was deliberately left unchanged: I-4 requires QA/reply-snippet behaviour to stay exactly as it was.
4. The delete-conflict mapping relies on the driver's constraint message (`foreign key` / `parent row` / `referential` / `constraint`), matching the existing `BatchSendTaskConfigService.isActiveNameUniqueViolation` precedent; any other `DataIntegrityViolationException` is rethrown, so real DB errors are never disguised as a reference conflict. End-to-end proof against MySQL needs the Docker-gated `FlywayMigrationIntegrationTest` harness, which this child was not authorized to extend.
5. Human acceptance A-1…A-4 (live UI, gate-fields on a real template, task reference protection) still requires the test environment; no template, task, expert or mail data was created, and no send path was exercised (M-4).

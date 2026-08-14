# Aggregate Machine Verification — expert-mail-preview

## Epoch 1 — 2026-08-14T14:29:35+08:00

- Master plan: `docs/plans/2026-08-14/expert-mail-preview-main.md` (SHA-256 `5ca146eeb629c7c83b159323e8659ba7251e5142b8b9caf746de8c2052172a13`)
- Governing master identity: worktree SHA-256 `5ca146eeb629c7c83b159323e8659ba7251e5142b8b9caf746de8c2052172a13`; recorded identity `commit 7a5dbdb`
- Master identity state: CONSISTENT; amendments N/A
- Boundary: `f3917cec4833199fcc9af5603e8630bb50590f9e..c2acd4fc1c2b1ec4d40a08db53b31bb44b28b77a`
- Reviewer: `/root/aggregate_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/expert-mail-preview-main/repair.md` — DRAFT_READY (SHA-256 `79ae2264c85fadd7ecd2b30693b4b4bfe09982e211a71f9a2688285f89da8d6e`)

## Verification Result: FAIL

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/2026-08-14/expert-mail-preview-main.md`

Implementation boundary: `f3917cec4833199fcc9af5603e8630bb50590f9e..c2acd4fc1c2b1ec4d40a08db53b31bb44b28b77a`

Final code / evidence HEAD: `c2acd4f` / `444e2de`

Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | FAIL (baseline only) | Exit 1. Kotlin: 2418 run, 0 failures, 0 errors, 4 skipped. Node: 518 tests, 516 pass, 2 fail. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | FAIL (baseline only) | Exit 1 at same Node stage; no WAR. Same two failures. |
| `git diff --check` | PASS | Exit 0; no output. |
| `node --test src/test/js/replySnippetLabel.test.js src/test/js/expertMailPreviewTab.test.js` | PASS | Exit 0; 14 pass, 0 fail. |

The two Node failures are both `batchManualExecutionLog.test.js` extraction errors (`buildManualExecutionSnapshot is not defined`), reproduced and recorded as pre-existing; no changed implementation path touches that test.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| O-1 Snippet names persist and display recognizably | PASS | DTO/write chain in `ReplySnippetController.kt:54-127`, `ReplySnippetService.kt:182-246`; unified server fallback `MailComposeTemplateService.kt:382-403,505-507`; UI label `app.js:2903-2910`. |
| O-2 Preview shows what selected expert receives | FAIL | V-2: panel sends `variantIndex: 0` at `app.js:8071-8085`; actual sends use `variantSeedFor(...)` at `ManualExpertMailService.kt:221-224`; preview resolves that request index at `MailComposeTemplateService.kt:210,512-516`. |
| O-3 Open editor with selected expert | PASS | Ordered load/find/view/editor/context/preview sequence at `app.js:8115-8136`; focused test passes. |
| N-1 Frame selection/validation/version unchanged | PASS | Frame methods untouched; version input remains name-free; Kotlin suite passes. |
| N-2 Workbench displays frame content | PASS | `trust-reply-workbench.js` unchanged. |
| N-3 Existing editor preview behavior unchanged | PASS | Existing drawer preview path remains `app.js:8318-8350`; no diff in that path. |
| N-4 Existing three detail tabs/lazy load unchanged | PASS | `app.js:6499-6582`; targeted test confirms first three plus fourth tab. |
| N-5 Content-variant input contract unchanged | PASS | Variant helper paths unchanged; full Node suite passes except baseline pair. |
| N-6 Placeholder validation preserved | PASS | `ReplySnippetService` still calls validation on create/update. |
| M-1 Cache triad | PASS | `index.html:11,1970-1971` all `20260814-v10-expert-mail-preview-01`; matching test assertions. |
| M-2 Targeted JS gate | PASS | Fresh targeted Node suites pass. |
| M-3 Real DOM hosts | PASS | Name input `index.html:1741`; two preview panels `app.js:6734,7194`. |
| M-4 Quantified claims | PASS | Fresh grep confirms two panel divs, three total references including lazy selector, and scope counts. |
| Scope / prohibitions | PASS | Product/test diff contains only P1/P2 authorized files; P2 adds no Kotlin file. |
| Joint-1 three identical snippet-name surfaces | FAIL | V-1: P1 produces `result.blocks[].refDisplayName`; panel consumes only subject/body/to/fallbacks at `app.js:8093-8108`. |
| Joint-2 editor dropdown equals panel label after jump | FAIL | V-1: editor dropdown has P1 label, but panel has no block-description rendering. |
| Joint-3 final cache triad | PASS | Single v10 cache key and synced assertions. |
| Joint-4 full test/build | N/A | Fresh commands fail only on proven unrelated baseline pair; recorded as observation, not repair scope. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | Expert preview omits `result.blocks[].refDisplayName`. |
| V-2 | NEW | Expert preview hard-codes `variantIndex: 0` rather than selected-expert seed. |

### Findings

#### P1

- V-1 NEW — The expert mail-preview panel discards `result.blocks[].refDisplayName`, so it lacks the required third snippet-name display surface. Smallest scope: `app.js` plus its focused test.
- V-2 NEW — The panel always requests body variant 0, while outbound sending hashes the selected expert identity. For `0000-0002`, Java seed is `-2035179089`; with a two-item pool and owner 1, outbound selects index 0 while preview selects index 1. Smallest scope: `app.js` plus its focused test.

#### P2

- N/A.

#### Observations

- Two full-suite Node failures are pre-existing and outside authorized scope.
- No post-repair evidence exists; repair evidence mode/executor metadata: N/A.

### Evidence Boundaries

- Available: master/child identities, ledger/handoff, exact boundary `f3917ce..c2acd4f`, evidence HEAD `444e2de`, source/diff, fresh required commands, focused Node tests.
- Available baseline proof: the two failing JS tests are unchanged/unrelated.
- Pending/non-blocking: manual acceptance; Docker-gated Flyway integration (skipped).
- Post-repair execution evidence, executor, and repair code SHA: N/A.
- No mandatory evidence is unavailable; result is not BLOCKED.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| R-1 missing third server label site | Joint-1 | PASS, not promoted | P1 corrected `resolveBlocks()` at `MailComposeTemplateService.kt:505-507`. |
| R-2 defensive JS/Kotlin tier-3 difference | Joint-1 | RECORD_ONLY | Defensive branch is unreachable for persisted valid snippets. |
| R-3 baseline Node failures | Joint-4 | RECORD_ONLY | Fresh 2/518 Node failures; unrelated pre-existing extraction gap. |
| O-1 panel grep count 3 vs literal 2 | M-3 | RECORD_ONLY | Two panel divs exist; third hit is mandatory lazy-load selector. |
| O-2 tab test counting method | N-4 | RECORD_ONLY | Four buttons and unchanged first three verified. |
| O-3 missing panel block-description wording | Joint-1/2 | Promoted to V-1 | New panel has no `result.blocks` consumer. |
| O-4 duplicate `scrollBackToContactsList` | Unrelated | RECORD_ONLY | Pre-existing at base. |

## Repair Planning Result: DRAFT_READY

Baseline: `expert-mail-preview-main.md`

Verification: FAIL / INITIAL

Repair artifact: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/fix/expert-mail-preview-main/repair.md`

Artifact SHA-256: `79ae2264c85fadd7ecd2b30693b4b4bfe09982e211a71f9a2688285f89da8d6e`

Included: V-1, V-2.

Excluded: R-1/R-2/R-3, O-1..O-4 as above.

### Next Action

FAIL / INITIAL routed to `repair-p`; result: DRAFT_READY. Stop; do not implement or re-review.

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/fix/expert-mail-preview-main/repair.md
```

No product code was modified.

## Epoch 2 — 2026-08-14T15:14:13+0800

- Master plan: `docs/plans/2026-08-14/expert-mail-preview-main.md` (SHA-256 `5ca146eeb629c7c83b159323e8659ba7251e5142b8b9caf746de8c2052172a13`)
- Governing master identity: worktree SHA-256 `5ca146eeb629c7c83b159323e8659ba7251e5142b8b9caf746de8c2052172a13`; recorded identity `commit 7a5dbdb`
- Master identity state: CONSISTENT; amendments N/A
- Boundary: `f3917cec4833199fcc9af5603e8630bb50590f9e..1859c5f0416b1326cbeabd690a5e2d2f86612b00`
- Reviewer: `/root/aggregate_rereviewer`
- Result: PASS
- Convergence: PROGRESSING
- Repair artifact/result: `docs/plans/fix/expert-mail-preview-main/repair.md` — applied candidate, SHA-256 `79ae2264c85fadd7ecd2b30693b4b4bfe09982e211a71f9a2688285f89da8d6e`; repair planning N/A

## Verification Result: PASS

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/2026-08-14/expert-mail-preview-main.md`

Implementation boundary: `f3917cec4833199fcc9af5603e8630bb50590f9e..1859c5f0416b1326cbeabd690a5e2d2f86612b00`

Final code / evidence HEAD: `1859c5f0416b1326cbeabd690a5e2d2f86612b00` / `6e309851cfdfaf63c01177cfebe8a351b48e1f04`

Convergence: PROGRESSING

Manual acceptance: PENDING

Post-repair metadata: `RECONSTRUCTED_FROM_GIT`; approval `APPROVAL_NOT_RECORDED`; executor `UNAVAILABLE`; `批准 继续` authorizes this read-only re-review only.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | FAIL (baseline only) | Exit 1. Kotlin: 2418 run, 0 failures, 0 errors, 4 skipped. Node: 520 tests, 518 pass, 2 fail. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | FAIL (baseline only) | Exit 1 at same Node stage; no WAR. Same 520 / 518 / 2 result. |
| `git diff --check` | PASS | Exit 0; no output. |
| `node --test src/test/js/expertMailPreviewTab.test.js src/test/js/replySnippetLabel.test.js` | PASS | Exit 0; 16 pass, 0 fail. |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | Exit 0; 63 pass, 0 fail. |
| `node --check src/main/resources/static/app.js` | PASS | Exit 0. |

The two Maven failures are unchanged `batchManualExecutionLog.test.js` extraction failures (`buildManualExecutionSnapshot is not defined`). That test and its related `app.js` paths are unchanged from `f3917cec`.

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| O-1 Recognizable persisted snippet names | PASS | `ReplySnippetService.kt:182-246`; server label resolution `MailComposeTemplateService.kt:370-410,505-536`; dropdown `app.js:2903-2910,8219-8220`. |
| O-2 Selected expert sees actual title/body | PASS | Preview passes Java-compatible trimmed ORCID hash at `app.js:8068-8095`; send path uses the same seed at `ManualExpertMailService.kt:221-224`; draft resolves that seed at `MailComposeTemplateService.kt:210,512-516`. |
| O-3 Open editor with expert selected | PASS | Required load/view/editor/context/preview order at `app.js:8135-8156`; focused test passes. |
| N-1 Frame behavior/version unchanged | PASS | `frameSlotIdentity` remains name-free at `ReplySnippetService.kt:162-174`. |
| N-2 Workbench shows full frame content | PASS | No change to `trust-reply-workbench.js`; 63-test gate passes. |
| N-3 Existing editor preview behavior | PASS | Repair delta does not touch drawer variant state/path; drawer still uses `state.previewDrawer.variantIndex` at `app.js:8347-8358`. |
| N-4 Existing detail tabs/lazy loading | PASS | First three tabs unchanged; fourth appended at `app.js:6499-6582`; both hosts at `:6734,:7194`; focused test passes. |
| N-5 Content-variant input contract | PASS | Unchanged helpers at `app.js:7770-7890`. |
| N-6 Placeholder validation | PASS | Create/update retain `requireValidPlaceholders` at `ReplySnippetService.kt:187,226`. |
| M-1 Cache triad | PASS | All three keys are `20260814-v10-expert-mail-preview-01` in `index.html:11,1970-1971`; assertions pass. |
| M-2 Targeted JS gate | PASS | Fresh focused Node suites: 16/16 and 63/63. |
| M-3 Real DOM hosts | PASS | Both mail-preview panels exist; block host is created at `app.js:8056` and queried scoped at `:8119`. |
| M-4 Quantified/scope claims | PASS | Two actual panel divs (`:6734,:7194`); cumulative product/test diff matches P1/P2 authorized union. |
| Scope / prohibitions | PASS | Repair delta `c2acd4f..1859c5f`: only `src/main/resources/static/app.js` and `src/test/js/expertMailPreviewTab.test.js`. |
| Joint-1 Identical three name surfaces | PASS | Dropdown `:8219`; template-list pill `:8163-8167`; expert panel consumes exact server `refDisplayName` via textContent `:8119-8127`. |
| Joint-2 Editor/panel label after jump | PASS | Same server display name is rendered before the tested editor jump flow. |
| Joint-3 Final cache triad | PASS | M-1 evidence. |
| Joint-4 Full test/build | N/A | Both required Maven commands fail only on the proven base-tree pair; no candidate failure added. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | Server `result.blocks[].refDisplayName` is text-rendered as scoped `compose-block-pill`; discriminating test passes. |
| V-2 | RESOLVED | `javaStringHashCode(orcidId)` matches `variantSeedFor`’s trimmed ORCID branch; test proves `0000-0002 → -2035179089`. |

### Findings

#### P1

- N/A.

#### P2

- N/A.

#### Observations

- Baseline-only Maven Node failure remains 2/520; not introduced by this boundary.
- Current worktree is docs-dirty only: review ledger and repair-execution correction. `git diff --quiet 1859c5f -- src/main src/test` exits 0; product/test evidence is exact candidate code.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| R-1 third server label site | Joint-1 | PASS | `resolveBlocks()` supplies `refDisplayName`; panel consumes it. |
| R-2 defensive JS/Kotlin tier-3 difference | Joint-1 | RECORD_ONLY | Invalid-content defensive branch; no product-semantic violation. |
| R-3 baseline JS failures | Joint-4 | RECORD_ONLY | Fresh 2 failures, same unchanged base test/path. |
| O-1 panel grep count | M-3 | RECORD_ONLY | Two panel divs; third occurrence is lazy-load selector. |
| O-2 tab test count method | N-4 | RECORD_ONLY | Four-button behavior is directly verified. |
| O-3 missing panel block description | Joint-1/2 | V-1 RESOLVED | `app.js:8119-8127` now renders server label. |
| O-4 duplicate scroll helper | Unrelated | RECORD_ONLY | Pre-existing and unchanged. |

### Evidence Boundaries

- Manual acceptance remains pending.
- Docker-gated migration coverage remains skipped.
- Executor self-test/identity evidence is optional and unavailable; fresh aggregate evidence above is authoritative.
- No mandatory evidence is unavailable.

Repair planning: N/A — PASS; `repair-p` not invoked.

### Next Action

Perform human acceptance for `1859c5f0416b1326cbeabd690a5e2d2f86612b00`.

No product code was modified.

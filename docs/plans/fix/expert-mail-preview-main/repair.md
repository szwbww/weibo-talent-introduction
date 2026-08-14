# Repair Plan: expert-mail-preview-main

Status: DRAFT — HUMAN APPROVAL REQUIRED

Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/2026-08-14/expert-mail-preview-main.md` (SHA-256 `5ca146eeb629c7c83b159323e8659ba7251e5142b8b9caf746de8c2052172a13`, recorded identity `commit 7a5dbdb`)

Verification report: aggregate/master epoch 1 review output (not persisted by this reviewer)

Implementation boundary: `f3917cec4833199fcc9af5603e8630bb50590f9e..c2acd4fc1c2b1ec4d40a08db53b31bb44b28b77a`

## Objective

The expert-detail email-preview panel both displays every returned preview block's server-provided display label and requests the same content-variant selection used when mailing that expert, so it shows the actual body and the same named reply snippet across all required surfaces.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Master cross-child close-out item 1 and observable outcomes 1–3 require identical reply-snippet names at the dropdown, template-list pill, and P2 mail-preview block description. | `renderExpertMailPreview()` consumes only `result.subject`, `result.body`, `result.toEmail`, and `result.fallbackKeys`; it never reads or renders `result.blocks[].refDisplayName`. |
| V-2 | P1 | Master observable outcome 2 requires the title and body that the selected expert actually receives. | `renderExpertMailPreview()` hard-codes `variantIndex: 0`, but the actual send path passes `MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)` and `previewDraft()` resolves body variants directly from the request's index. |

## Findings Excluded

| Finding | Reason |
|---|---|
| R-1 | Already resolved by P1 in `MailComposeTemplateService.resolveBlocks()`; no repair work remains. |
| R-2 | Defensive-only unreachable fallback divergence; master does not make it a product-semantic violation. |
| R-3 | Proven pre-existing failures in `src/test/js/batchManualExecutionLog.test.js`, outside the approved repair scope. |
| O-1 | Plan-text count inconsistency; the two required panel DOM sites exist and the selector is required for lazy loading. |
| O-2 | Test-counting method is non-behavioral and the four-tab behavior is covered. |
| O-3 | Plan-text inconsistency is evidence for V-1 only; do not amend the approved master or child plan text. |
| O-4 | Pre-existing duplicate helper, unrelated to V-1. |

## Unchanged Contract

- Keep P1's existing server-side three-tier snippet-label algorithm and `ComposeTemplatePreviewBlock.refDisplayName` contract unchanged.
- Keep P2's `POST /api/compose-templates/preview-draft` request, explicit four-field block payload mapping, `strictPlaceholders: false`, request-order guard, subject/body/recipient rendering, fallback badges, two panel DOM sites, and editor-jump behavior unchanged.
- Keep the existing template-editor preview's user-controlled `state.previewDrawer.variantIndex` rotation behavior unchanged (N-3).
- Do not add a backend endpoint, Kotlin change, schema migration, cache-key bump, CSS contract change, plan amendment, or unrelated baseline-test repair.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/resources/static/app.js` | Render the server-returned preview-block labels inside the already-rendered expert mail-preview panel. |
| `src/test/js/expertMailPreviewTab.test.js` | Add one discriminating regression test for the visible, server-provided block-label behavior. |

## Repair Tasks

### R-1: Render server-provided expert-preview block labels

- Resolves: V-1.
- Root cause: `renderExpertMailPreview()` discards `result.blocks`, even though P1 supplies the canonical `refDisplayName` and the master requires that value in the third display surface.
- Files: `src/main/resources/static/app.js`; `src/test/js/expertMailPreviewTab.test.js`.
- Change: add a scoped block-description area inside the existing expert mail-preview panel and populate it from `result.blocks`. For each returned block, use the server-provided `refDisplayName` as the label; render it as text, not unescaped HTML. Reuse existing `compose-block-pill` presentation only; add no CSS or new class. Do not reimplement the snippet-label fallback in this panel.
- Regression test: mock a preview response containing a named `REPLY_SNIPPET` block with `refDisplayName`, invoke `renderExpertMailPreview()`, and prove the panel exposes that exact label. The assertion must fail on boundary head `c2acd4f` and pass only when the panel consumes `result.blocks[].refDisplayName`.
- Existing verification: `node --test src/test/js/expertMailPreviewTab.test.js src/test/js/replySnippetLabel.test.js`; `node --check src/main/resources/static/app.js`.
- Must not change: the unchanged-contract list above, including all request payload fields and the existing fallback-badge lifecycle.
- Prohibited: touching files outside Authorized Files; deriving labels client-side; displaying raw custom-text/body content as a substitute for `refDisplayName`; staging or committing review evidence with the product commit.

### R-2: Request the selected expert's deterministic body variant

- Resolves: V-2.
- Root cause: the server's normal outbound path uses `MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail)`, while the new panel sends literal zero. `previewDraft()` passes that request value to `ContentVariantService.resolveBody()`, so an expert such as ORCID `0000-0002` can receive a different variant from the panel's result.
- Files: `src/main/resources/static/app.js`; `src/test/js/expertMailPreviewTab.test.js`.
- Change: derive the preview request's `variantIndex` from the selected nonblank ORCID using Java `String.hashCode()` semantics, matching the first branch of `variantSeedFor`. The expert-detail tab already rejects blank ORCID before previewing, so do not add an email fallback path or change the request endpoint. Do not reuse or mutate `state.previewDrawer.variantIndex`.
- Regression test: with ORCID `0000-0002`, capture `renderExpertMailPreview()`'s JSON body and assert its `variantIndex` is the deterministic Java-string-hash value `-2035179089`, not zero. This must fail on `c2acd4f` and prove the request can select the same variant as outbound rendering.
- Existing verification: `node --test src/test/js/expertMailPreviewTab.test.js src/test/js/replySnippetLabel.test.js`; `node --check src/main/resources/static/app.js`.
- Must not change: the unchanged-contract list above, especially `strictPlaceholders: false`, the four-field block mapping, and the editor drawer's independent variant rotation.
- Prohibited: touching Kotlin/server request semantics; adding a global mutable preview index; changing content-variant selection rules; staging or committing review evidence with the product commit.

## Verification Commands

1. `node --test src/test/js/expertMailPreviewTab.test.js src/test/js/replySnippetLabel.test.js`
2. `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`
3. `node --check src/main/resources/static/app.js`
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
6. `git diff --check`

The two Maven commands must be recorded with their fresh exit status. At the reviewed boundary they exit 1 solely because the proven pre-existing `batchManualExecutionLog.test.js` extraction failures are included in the full Node suite; this repair authorizes no change to that file.

## Completion Criteria

- The expert mail-preview panel visibly contains the exact server `refDisplayName` for a named reply-snippet preview block.
- The regression test proves the response-to-panel path and fails without that behavior.
- For a nonblank ORCID, the preview request's `variantIndex` matches the Java `String.hashCode()` seed used by `MailComposeTemplateService.variantSeedFor`; it no longer hard-codes zero.
- The regression test proves that selection with a concrete nonzero seed.
- Existing P1 label tests and P2 panel tests pass.
- Changed product/test files are exactly the Authorized Files.
- Required commands are freshly recorded; no new failure beyond the known two baseline JS failures is introduced.

## Human Approval

Execution is prohibited until a human explicitly approves this plan. After approval, run `execute-p` with `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/fix/expert-mail-preview-main/repair.md`.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/fix/expert-mail-preview-main/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with the resolved product commit subject `fix(fast-p): render expert mail preview block labels`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/review/expert-mail-preview/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with the resolved evidence commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.

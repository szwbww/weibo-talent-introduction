# Child frontend — approved implementation brief

- Exact plan: `docs/plans/2026-09-23/02-reply-snippet-variants-frontend.md`
- Plan identity: `commit:73bc40d5b5623d9a71b0c9ff8e5a5990f3e3ae18` (content SHA-256 `f3891555d134b2727f217bd5e14e76fd1a4d118ea4c029f608202cf74ebe96a8`)
- Read that complete plan before coding; it is the full acceptance contract.
- Dependency: backend child must reach a terminal light pass first. Consume its committed nullable `subjectSnippetId` API and random preview behavior; do not modify backend files.
- Product base: backend terminal `Code head` (not its separate evidence commit).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-reply-snippet-variants-main`; branch `fast/2026-09-23-reply-snippet-variants-main`.

## Authorized files (exactly these six)

1. `src/main/resources/static/index.html`
2. `src/main/resources/static/styles.css`
3. `src/main/resources/static/app.js`
4. `src/test/js/replySnippetVariantEditor.test.js` (new authorized file)
5. `src/test/js/composeTemplatePreview.test.js`
6. `src/test/js/expertMailPreviewTab.test.js`

Do not modify any other product/test file. Before editing, re-read the complete plan and re-check the current asset cache key and its test callsites; if new fixed-key tests exist outside the authorized list, stop with PLAN_CONFLICT and report the exact evidence. Preserve discovery traffic WIP and all unrelated existing changes.

## Required invariants and downstream contract

- I-1/I-2: single visible textarea across original plus variants; original remains `#replySnippetContent`/`name=content`, outside the variants array. Keep each `.content-variant-input` node resident and only toggle visibility. Capture all raw values including empty values before add/remove redraw. Full validation checks hidden content; errors switch to and focus the first invalid version. Variable insertion preserves current textarea and selection; cancel/reopen drops unsaved values.
- I-3/I-4: only transient `selectedSubjectSnippetId: number|null`. Option identity is full display label including `#ID`; no fuzzy/name-only selection. Custom typing immediately clears ID; exact full label or actual option choice selects a reference. API payload for custom subject explicitly sends null; referenced subject sends source text snapshot and ID. Invalid saved references retain ID and show invalid state; never silently fall back. `{}` is disabled for references and inserts through existing cursor-aware variable menu when custom.
- I-5: editor and expert detail preview both send `subjectSnippetId`, never `variantIndex`; resample only performs preview request. Retain request-sequence/stale-response protection, strict/recipient/account behavior. Show “随机样本，发送时重新生成”; do not claim combinations. Preview current edited snippet version. Escape rendered data.
- I-6/S-1..S-4: use only specified scoped CSS/DOM contracts; no global restyle or new runtime resource. Bump the 11 versioned assets uniformly to `20260923-snippet-reference-variants`, preserve order, leave `task-modal-runtime.js` unversioned. Preserve existing QA editor, page layout, WIP, and unrelated task changes.
- Complete T-1..T-4 and I-1..I-6/S-1..S-4 requirements and all plan acceptance criteria, not just the summary above.

## Required commands (fresh after final implementation)

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/replySnippetVariantEditor.test.js src/test/js/composeTemplatePreview.test.js src/test/js/expertMailPreviewTab.test.js src/test/js/varInsertAtCursor.test.js src/test/js/qaFactCardEditor.test.js src/test/js/replySnippetLabel.test.js src/test/js/meetingConfirmationAssets.test.js
node --test src/test/js/*.test.js
```

Also perform the plan's browser acceptance checks against the actual UI if a running authenticated test environment is available; do not send real mail. If not available, record that manual acceptance remains for the human. The backend plan is the upstream API contract; frontend must not change backend behavior.

# Execution Record — p1-preview-sender-account

- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head/docs/plans/2026-08-14/expert-detail-head-p1-preview-sender-account.md@088b4deba9f05a3571ceabc24ced9d9a25028c9ed144f3b949fcaf33052c9cc8`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head@fast/expert-detail-head@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/expert-detail-head`
- Executor: P1Implementer
- Epoch: NEW
- Date: 2026-08-14
- Pre-execution code SHA: `90498efb768f74a2371e895d984bde1ac4743c49` (child base SHA, worktree HEAD before execution)
- Implementation commit: `111180741ec46bea796e81a60e513769d2de534c` (`feat(fast-p): implement p1-preview-sender-account`)
- Post-execution code SHA: `111180741ec46bea796e81a60e513769d2de534c` (implementation commit is HEAD of `fast/expert-detail-head`, confirmed ancestor)

## Changes per T1-T4

### T1 — MailComposeTemplateService.kt (I-1 / I-5 / I-6 / I-7)

- `previewDraft` call site: `resolvePreviewAccount(request.senderAccountCode)` → `resolvePreviewAccount(request.senderAccountCode, contact)` (contact produced by `resolvePreviewContact` at the line above; I-7 unchanged: no local string replacement, injection stays only via `mailVariableService.renderPreview`, `resolveBlocks(..., renderVariables = false)` untouched).
- `resolvePreviewAccount` replaced exactly per plan: priority ① explicit `senderAccountCode` (trimmed, non-blank) → ② `contact.boundSenderAccountCode` (trimmed, non-blank), never entered when `contact == null` → ③ `null`. Keeps `mailSenderAccountService.getAccount(code)` (NOT `getEnabledAccount`, I-5) inside `runCatching{}.getOrNull()`. Doc comment added per plan.
- No constructor change (I-6): the test's 9-positional-arg call at `:44-54` is byte-identical; `git diff` on that range empty.
- No new imports (`ExpertContact` and `MailSenderAccount` already imported).

### T2 — app.js `renderExpertMailPreview` (I-2 / I-3 / I-4)

- Before payload construction: `const previewContact = (state.contacts || []).find((item) => item.orcidId === orcidId);` and `const previewContactId = previewContact?.contactId ?? null;` (I-3: strictly-equal `orcidId` match AND non-null/non-undefined `contactId`; all other cases → `null`).
- Payload: `contactId: previewContactId` (was `null`); `expertEmail: null` unchanged (I-4); `senderAccountCode: null` unchanged (M-1); `orcidId` still passed (I-4).
- No new `api()` call (I-2): exactly 1 call, URL `/api/compose-templates/preview-draft`, method POST — verified by existing test + new test.

### T3 — expertMailPreviewTab.test.js (+3 cases, existing 10 byte-unchanged)

Added after the variantIndex (V-2) test, before `openTemplateEditorForExpert loads templates...`:
1. `renderExpertMailPreview takes contactId from state.contacts into payload (I-3)` — `state.contacts = [{orcidId:"0000-0001", contactId: 42}]`, asserts `captured.contactId === 42`.
2. `renderExpertMailPreview sends contactId null when state.contacts has no matching orcid (I-3)` — `state.contacts = []`, asserts `captured.contactId === null`.
3. `renderExpertMailPreview resolves contactId without extra requests and keeps senderAccountCode null (I-2)` — non-empty `state.contacts`, asserts `calls.length === 1`, URL/method POST, `captured.contactId === 42`, `captured.senderAccountCode === null`.

`createSandbox()` defaults untouched (it already provides `state.contacts: []`; the 3 new tests assign `state.contacts` inside the test only).

### T4 — MailComposeTemplateServiceTest.kt (+3 @Test, existing tests byte-unchanged)

Added after `previewDraft uses request expert email for orcid preview contact`, before `stubIntroSnippetTemplate`:
1. `previewDraft falls back to the contact bound sender account when no explicit code` — `findById(42)` → contact with `boundSenderAccountCode = "LiLei"`; `getAccount("LiLei")` → account; `Mockito.verify(mailVariableService).renderPreview(..., account, contact)` for subject and body.
2. `previewDraft prefers the explicit sender account over the contact binding` — explicit `senderAccountCode = "WangFang"` + binding `"LiLei"`; verifies renderPreview with `explicitAccount` and `Mockito.verify(mailSenderAccountService, Mockito.never()).getAccount("LiLei")`.
3. `previewDraft resolves a null account when the contact has no binding` — binding null; verifies `renderPreview("Subject", null, contact)` / `renderPreview("Body", null, contact)` (same shape as existing `:901` case).

## Required commands (all run fresh in this invocation)

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | `node --test src/test/js/expertMailPreviewTab.test.js` | 0 | `# tests 13, # pass 13, # fail 0` |
| 2 | `node --check src/main/resources/static/app.js` | 0 | no output |
| 3 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailComposeTemplateServiceTest` | 0 | `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS) |
| 4 | `node --test src/test/js/*.test.js` | 0 | `# tests 524, # pass 524, # fail 0` |
| 5 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | 0 | `Tests run: 2421, Failures: 0, Errors: 0, Skipped: 4` (BUILD SUCCESS) |
| 6 | `git diff --check` | 0 | no output |

All commands were executed with `cwd = /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head`; mvn commands used the JDK-11 JAVA_HOME above.

## Zero-diff regions (verified)

- `MailComposeTemplateController.preview()` — no diff (file not touched).
- `renderServerComposeTemplatePreview` (`app.js:8347-8380`) — no diff (only hunks at `app.js:8087-8099`).
- `createSandbox()` defaults in `expertMailPreviewTab.test.js` — no diff.
- Existing 10 JS test cases and all existing Kotlin test cases — no diff (insertions only).
- `MailComposeTemplateService` constructor — no diff; test's 9-positional-arg call byte-identical.
- `strictPlaceholders` value (false) and `variantIndex` computation — unchanged.
- No `getEnabledAccount` usage in `MailComposeTemplateService.kt` (I-5); no new `renderText(` call (I-7).

## Deviation / open question (requires human decision)

The plan's 通过判据 states `# tests 从基线 13 增至 16（基线 2026-08-14 实测）`. The actual baseline at the child base SHA `90498efb` is **10** tests (verified: `git show HEAD:src/test/js/expertMailPreviewTab.test.js | grep -c 'it('` → 10; every branch ref containing the file has 10; the plan's own T3 section and the brief both state "现有 10 个用例一字不改" / "Existing 10 test cases ... only +3 new"; the master plan's 34-test baseline for the ① gate is consistent with 10 in this file).

Per the approved contract's named tasks (T3: exactly 3 new cases, existing unchanged), the file now has **13** tests, # fail 0. Reaching 16 would require 3 additional test cases whose content the plan does not specify — that would exceed the authorized scope (execute-p: no unauthorized additions). Decision needed: accept `10 → 13`, or authorize 3 additional test cases to reach 16.

## Verification acceptance per plan (as implemented)

- `node --test src/test/js/expertMailPreviewTab.test.js`: exit 0, `# fail 0`, `# tests 13` (baseline was 10, not 13 — see deviation).
- `node --check`: exit 0, no output. ✓
- `mvn test -Dtest=MailComposeTemplateServiceTest`: `Tests run: 40, Failures: 0, Errors: 0`. ✓
- Full `mvn test`: `Tests run: 2421, Failures: 0, Errors: 0, Skipped: 4`. ✓
- `git diff --check`: no output. ✓

## Non-changes honored

- No review of later children, no repair of unrelated behavior, no push/merge/amend/history rewrite.
- `docs/plans/fast/` excluded from commit (remains untracked).

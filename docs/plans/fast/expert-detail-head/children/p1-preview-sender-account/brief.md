# P1 Child Brief — p1-preview-sender-account

## Contract
- Plan (complete approved contract, read fully before implementing): `docs/plans/2026-08-14/expert-detail-head-p1-preview-sender-account.md`
- Master (shared invariants + shared verification commands): `docs/plans/2026-08-14/expert-detail-head-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-detail-head`
- Branch: `fast/expert-detail-head`
- Child base SHA: `90498efb768f74a2371e895d984bde1ac4743c49`

## Global constraints (master plan)
- M-1: authoritative sender account = DB binding `expert_contact.bound_sender_account_code`; frontend never passes unsaved account values. `send-manual-mail` branch keeps `senderAccountCode: null`; preview payload keeps `senderAccountCode: null` (backend reads binding).
- M-2: preview and send must resolve the same account: preview falls back to `contact.boundSenderAccountCode` when no explicit code.
- M-4: JS gate is `node --test <file>`; `verify.sh` runs only one file and is NOT a gate.
- JDK: MUST use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`; bare `mvn` fails.

## Authorized files (ONLY these 4 may change)
1. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
2. `src/main/resources/static/app.js`
3. `src/test/js/expertMailPreviewTab.test.js`
4. `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt`

## Key invariants (implement exactly, per plan I-1..I-7)
- I-1: `resolvePreviewAccount` priority: explicit `senderAccountCode` (non-blank) → `contact.boundSenderAccountCode` (non-blank) → null; never enter ② when `contact == null`.
- I-2: `renderExpertMailPreview` one render = exactly 1 `api()` call, URL `/api/compose-templates/preview-draft`, method POST; no new network calls for binding/contactId.
- I-3: payload `contactId` only when `state.contacts` has an item with `orcidId` strictly equal AND its `contactId` is non-null/non-undefined; else `null`.
- I-4: `contactId` non-null → backend ignores `orcidId`/`expertEmail` for recipient; `orcidId` still passed; `expertEmail` stays `null`.
- I-5: `resolvePreviewAccount` keeps `mailSenderAccountService.getAccount(code)` (NOT `getEnabledAccount`) + `runCatching{}.getOrNull()`.
- I-6: NO new constructor params on `MailComposeTemplateService` (test's 9-positional-arg call at `:44-54` stays byte-identical).
- I-7: NO local string replacement in `MailComposeTemplateService`; injection only via `mailVariableService.renderPreview(rawText, account, contact)`; `resolveBlocks(..., renderVariables = false)` unchanged.

## Explicit non-changes (must NOT touch)
- `MailComposeTemplateController.preview()` (endpoint `GET /api/compose-templates/{id}/preview`) — zero diff in that method.
- `renderServerComposeTemplatePreview` (`app.js:8347-8380`) and `collectComposeTemplatePreviewContext` payload — zero diff.
- `strictPlaceholders` value (stays false), `variantIndex` computation.
- Existing 10 test cases in `expertMailPreviewTab.test.js` — unchanged; only +3 new.
- Existing tests in `MailComposeTemplateServiceTest.kt` — unchanged; only +3 new `@Test`.
- `createSandbox()` defaults in `expertMailPreviewTab.test.js` — do NOT modify (affects other 10 cases).

## Required commands (run ALL, record command + exit code + counts)
```bash
node --test src/test/js/expertMailPreviewTab.test.js          # expect # fail 0, # tests 13 -> 16
node --check src/main/resources/static/app.js                  # exit 0, no output
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailComposeTemplateServiceTest
node --test src/test/js/*.test.js                              # all JS files
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   # full regression
git diff --check                                               # no output
```

## Downstream interfaces (P2 consumes this child)
- Rebind → re-render preview must show new signature (backend reads binding per render via contactId).
- Payload shape stays `{..., contactId, expertEmail: null, senderAccountCode: null}`.

## Deliverable
- Commit implementation locally ONLY (product code + tests), excluding `docs/plans/fast/`:
  `feat(fast-p): implement p1-preview-sender-account`
- Append full execution record to `docs/plans/fast/expert-detail-head/children/p1-preview-sender-account/execution.md` (do not commit this file; controller commits evidence separately).
- Return ONLY: `READY_FOR_VERIFICATION` | `BLOCKED` | `PLAN_CONFLICT`, implementation commit SHA, command summary, report path.
- Do NOT review later children, fix unrelated behavior, push, merge, amend, or rewrite history.

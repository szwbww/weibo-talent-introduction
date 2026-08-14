# P1 light verification log

## Light Verification: LIGHT_PASS_WITH_NOTES
Epoch 2 — Child: p1-preview-sender-account; plan docs/plans/2026-08-14/expert-detail-head-p1-preview-sender-account.md (AMENDED A1; contract pass criteria: expertMailPreviewTab 10 -> 13)
Boundary: 90498efb768f74a2371e895d984bde1ac4743c49..111180741ec46bea796e81a60e513769d2de534c
Verifier: P1Verifier (fresh, read-only, epoch 2)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Boundary diff = exactly 1 commit (`1111807 feat(fast-p): implement p1-preview-sender-account`); `git diff --name-only 90498ef..1111807` = the 4 authorized files only: MailComposeTemplateService.kt, app.js, expertMailPreviewTab.test.js, MailComposeTemplateServiceTest.kt (265 insertions / 7 deletions). Amendment docs commit c51b2d6 sits after the boundary (HEAD), not part of product review. Worktree otherwise clean (only untracked docs/plans/fast/ evidence dir). `git diff --check 90498ef..1111807` empty. |
| Plan and invariants | PASS | I-1: MailComposeTemplateService.kt:306-313 priority `senderAccountCode?.trim()?.takeIf{isNotBlank}` -> `contact?.boundSenderAccountCode?.trim()?.takeIf{isNotBlank}` -> null; `contact?.` blocks ② when contact==null; call site :222 passes contact from :221. Tests: MailComposeTemplateServiceTest.kt:943-1066 (fallback LiLei / explicit WangFang priority + `verify(never).getAccount("LiLei")` / null-binding). I-2: app.js:8086-8143 single `api()` at :8109-8111 URL `/api/compose-templates/preview-draft` method POST; new lines :8090-8091 pure in-memory lookup; JS test 3 asserts calls.length===1 + existing calls-count case green in 13/13 run. I-3: app.js:8090-8091 strict `===` orcidId match + `?? null` (null/undefined -> null); payload :8102; JS tests 1-2 assert 42 / null. I-4: app.js:8101-8104 orcidId passed, contactId=previewContactId, expertEmail stays null, senderAccountCode stays null; resolvePreviewContact :283+ pre-existing early return on contactId. I-5: :313 `runCatching { mailSenderAccountService.getAccount(code) }.getOrNull()` (getEnabledAccount only in doc comment :303). I-6: service diff = 2 hunks (:219-227, :298-311), zero constructor change; test 9-positional-arg call :44-54 byte-identical (test diff insertion-only @ :940). I-7: no new `renderText(` in diff (pre-existing :178/:482/:531/:552/:609/:611 untouched, none in previewDraft); `resolveBlocks(..., renderVariables = false)` :219 unchanged. |
| Required commands | PASS | Fresh (JDK 11.0.15 zulu-11): (1) `node --test src/test/js/expertMailPreviewTab.test.js` exit 0, `# tests 13, # pass 13, # fail 0` (node v25.7.0) — meets amended A1 criterion 10->13, matches implementer record 13/0. (2) `node --check src/main/resources/static/app.js` exit 0, no output — matches record. (3) `mvn test -Dtest=MailComposeTemplateServiceTest` (JAVA_HOME=zulu-11) exit 0, surefire XML Tests run: 40, Failures: 0, Errors: 0, Skipped: 0 — matches record 40/0/0. Recorded (not re-run per dispatch; full suites): JS 524 pass/0 fail (521 baseline + 3 new ✓), full mvn Tests run 2421/0/0/4 skipped, `git diff --check` empty (re-confirmed on boundary). |
| Downstream interfaces | PASS | Payload shape app.js:8102-8104 `{contactId, expertEmail: null, senderAccountCode: null}` ✓. resolvePreviewAccount falls back to `contact.boundSenderAccountCode` MailComposeTemplateService.kt:309-311 ✓. `MailComposeTemplateController.preview()` zero-diff — controller file absent from boundary diff ✓. `renderServerComposeTemplatePreview` (app.js:8349, preview-draft call :8367) zero-diff — app.js diff hunks only at :8087-8099 ✓. Existing 10 JS cases + all existing Kotlin tests byte-unchanged — both test diffs insertion-only (68+/0-, 181+/0-) ✓. |

### AUTO_FIX
- N/A (no four-gate violation proven)

### RECORD_ONLY
- O-1: execution.md "Deviation / open question" describes a pre-amendment pass-criterion discrepancy (plan's 通过判据 `13 -> 16` vs actual baseline 10). Amendment A1 (c51b2d6) fixed the contract to `10 -> 13`, which the implementation satisfies (13 tests, 0 fail). Stale narrative in evidence file only — no product impact, no action.
- O-2: plan I-5 acceptance (`grep getEnabledAccount` 应无输出) is technically not met: `MailComposeTemplateService.kt:303` doc comment contains the string "getEnabledAccount". The comment is the plan's own T1 code template verbatim, and the functional rule (uses `getAccount`, not `getEnabledAccount`, inside `runCatching{}.getOrNull()`) holds at :313 — plan-doc/acceptance inconsistency only, no functional divergence.

### Required Action
- COMPLETE_CHILD

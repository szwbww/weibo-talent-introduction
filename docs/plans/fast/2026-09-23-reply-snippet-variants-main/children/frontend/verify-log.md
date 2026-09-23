## Light Verification: LIGHT_PASS_WITH_NOTES
Child: frontend — `docs/plans/2026-09-23/02-reply-snippet-variants-frontend.md`
Boundary: `c5cb4cc600d265aae935100aad7ac551ed00eb32..b62f4bb63005e268ae789966257cfa400a3bb353`
Verifier: `ReplySnippetFrontendVerifier`

Epoch: 1; Attempt: 1; Timestamp: `2026-09-23T18:37:31Z`

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | The product/test diff for the specified review range is exactly the six authorized files in `children/frontend/brief.md`: `src/main/resources/static/{index.html,styles.css,app.js}` and `src/test/js/{replySnippetVariantEditor.test.js,composeTemplatePreview.test.js,expertMailPreviewTab.test.js}`. The wider range also contains fast-p documentation/evidence files because the review base precedes the documentation-only ledger ancestry transition; those are not product/test changes and were excluded from the product scope check. Execution evidence records the product commit contains only the six files (`execution.md:34-45,52-58`). |
| Plan and invariants | PASS | Single-editor DOM keeps `#replySnippetContent`/`name=content` outside hidden variants (`index.html:1964-1983`); renderer/navigation only toggle resident variant row visibility and preserve raw values for redraw (`app.js:10874-11002`), with behavior tests for navigation, add/remove and hidden-invalid focus (`replySnippetVariantEditor.test.js:107-148`). Combobox DOM carries the prescribed listbox/ARIA controls (`index.html:2020-2032`); exact ID-bearing labels, custom-null behavior, keyboard Enter selection and invalid-reference retention are covered (`replySnippetVariantEditor.test.js:151-219`). Both draft-preview request paths include `subjectSnippetId` and omit `variantIndex` (`app.js:11121-11141,11599-11613`; corresponding assertions in `composeTemplatePreview.test.js:267-309` and `expertMailPreviewTab.test.js:252-280`). The 11 versioned assets use `20260923-snippet-reference-variants` in preserved stylesheet/script order while `task-modal-runtime.js` remains unversioned (`index.html:11-15,2200-2206`). New component CSS is scoped to the prescribed editor/combobox selectors (`styles.css:8601-8745`). |
| Required commands | PASS | Baseline: selected JS command 56/56 passed; full `node --test src/test/js/*.test.js` 1139/1139 passed; `node --check src/main/resources/static/app.js` passed. Fresh post-implementation evidence in `execution.md:23-29,47-50`: `node --check src/main/resources/static/app.js` exit 0; `node --test src/test/js/replySnippetVariantEditor.test.js src/test/js/composeTemplatePreview.test.js src/test/js/expertMailPreviewTab.test.js src/test/js/varInsertAtCursor.test.js src/test/js/qaFactCardEditor.test.js src/test/js/replySnippetLabel.test.js src/test/js/meetingConfirmationAssets.test.js` exit 0, 64 passed / 0 failed / 0 skipped; `node --test src/test/js/*.test.js` exit 0, 1147 passed / 0 failed / 0 skipped. Commands were not rerun. |
| Downstream interfaces | PASS | Backend `ComposeTemplatePreviewDraftRequest.subjectSnippetId` is nullable (`Long?`, default null; `MailComposeTemplateService.kt:873-884`). Backend plan I-2 defines null as custom text and a non-null ID as the authoritative reference, with `subject` the source-text snapshot, not a fallback (`01-reply-snippet-variants-backend.md:25-28,110-112`). Frontend shared subject collection sends the source snapshot plus ID for references and explicit null plus typed text for custom subjects (`app.js:11310-11317`); editor preview tests assert these payloads (`replySnippetVariantEditor.test.js:221-245`, `composeTemplatePreview.test.js:295-309`). This matches the backend contract. N/A |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1: Browser visual acceptance remains pending: execution evidence records `http://localhost:8080` returned `net::ERR_CONNECTION_REFUSED`, no authenticated UI was available, and no real mail or templates were modified (`execution.md:30-32,60-61`). This is the documented environment limitation; human browser acceptance remains outstanding and is not a four-gate implementation defect.

### Required Action
- COMPLETE_CHILD
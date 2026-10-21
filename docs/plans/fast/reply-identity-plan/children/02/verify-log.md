# Child 02 Verification Log

Awaiting independent light verifier.

## Epoch 1 — Paused before independent verification

- Implementer Identity02Implementer returned PLAN_CONFLICT; no implementation commit or verifier dispatch.
- Mandatory S-1 completion-after-follow-up order conflicts with the unlisted mailboxChatBehavior.test.js:4109 assertion that follow-up must remain the final toolbar child.
- This is source-contract evidence, not an exercised test failure. Required frontend commands and browser proof remain outstanding.
- Required controller action: PAUSED_FOR_HUMAN for minimal legacy-test scope amendment. Do not alter the approved toolbar order or hide the new button to satisfy the old test.

## Epoch 2 — Paused before independent verification

- Implementer Identity02Epoch2Implementer returned PLAN_CONFLICT after a complete fresh frontend suite: 1780 tests, 1763 passed, 17 failed, no skipped; artifact://905.
- Full per-test failure inventory in execution.md:159-198 distinguishes unlisted obsolete contracts from authorized own-test defects. Seven additional legacy-test paths plus a broader retirement in already-authorized mailboxChatBehavior.test.js need explicit authority.
- No verifier dispatched and no light verdict exists. Syntax checks passed; browser evidence is partial only and the send/ESC/STRONG matrix remains incomplete.
- Do not treat source/cache/count/hash assertion conflicts or own-test harness errors as production behavior proof. Required controller action: PAUSED_FOR_HUMAN.

## Epoch3/Attempt1 — 2026-10-21T12:39:03+08:00 — 494b6cc569a70b2bcc5ef838b5808ad687600b28..adcc2b4e36aa578d5ab915a2af39c727bbbe079e

## Light Verification: LIGHT_FAIL
Child: 02 — docs/plans/2026-10-21/reply-identity-02-frontend.md
Boundary: 494b6cc569a70b2bcc5ef838b5808ad687600b28..adcc2b4e36aa578d5ab915a2af39c727bbbe079e
Verifier: Identity02Epoch3Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Complete current plan read; disk SHA-256 `9a9b146883f395501e98084f715ae8a40486944a6e3c8e1971ccdbd56abcc955` matches exact bytes at approved commit `f296820eb8d94d4e3985846b2156480db8ce0b90`. `git diff --name-status <base> <head>` and scoped `git diff --unified=3 <base> <head> -- src` show the exact 13 product/test paths in plan:216–228 (artifacts 1105/1107); working bytes of each match review head. Full legacy diffs were inspected: only A3/A5 authorized obsolete assertion/description retirement, no incidental replacement values or suite exclusion. Original styles.css bytes remain an unchanged prefix; only approved S-3 appended. Controller/evidence documents in the overall boundary are distinguished from product/test scope. |
| Plan and invariants | FAIL | Direct completion, isolation, persistence, confirmation, settings and style evidence mapped below. F-1 violates T6/I-5 and the API-backed I-1/I-6 path: source-specific request fields are not projected to the existing backend contract, so otherwise valid completion/settings operations can be rejected before identity inspection/preview. Browser fixture success does not establish this API compatibility. |
| Required commands | PASS | Freshly executed after source/test/runtime-evidence inspection in the target worktree: `node --check src/main/resources/static/mailbox-chat.js` exit 0; `node --check src/main/resources/static/app.js` exit 0; `node --test src/test/js/*.test.js` exit 0, 1782 tests / 331 suites / 1782 pass / 0 fail / 0 cancelled / 0 skipped / 0 todo, duration 3749.933084 ms, fresh artifact `artifact://1112`. Recorded pre-feature baseline: 1725 pass. Historical partial 1780/17 failure and executor artifact 1071 are not substituted for this run. No Maven/backend/whole-system replay. |
| Downstream interfaces | FAIL | GET/PUT defaults names and selection/response shapes match ReplySnippetService.kt:483–502; inspect/preview response consumption matches ReplyIdentityService.kt:233–243. However `mailbox-chat.js:7466–7472` sends both `senderAccountCode` and `accountScope` regardless of sourceKind. `ReplyIdentityService.kt:253–256` explicitly forbids accountScope for INBOUND and senderAccountCode for CONVERSATION. Both POST controllers invoke this validation before operations (`ReplyIdentityController.kt:15–24`). Concrete retained wire requests reproduce F-1. |

Direct requirement evidence inspected:

- **I-1:** `mailbox-chat.js:393–423` builds only p/br/text fragments, requires MISSING/canInsert, preserves existing nodes and inserts signature before verified quote. `mailboxReplyIdentity.test.js:94–224` exercises button binding, both/single/no insertion, rich-node identity, repeats, whitespace, mismatch/uncertainty/duplicates, Range splitting and unsafe tails. Actual runtime observations: execution.md:319–322. These local behaviors have evidence; backend-connected execution is affected by F-1.
- **I-2:** `mailbox-chat.js:7458–7489,7517–7550` captures user/session epoch/map/owner/target/conversation epoch/revision/editor/body/context and sequence; rejects stale results. Quote mapping checks canonical equivalence and unique tail at :330–390. Tests :155–167,226–243,405–424 plus retained real-app delayed edit/expert/scope/anchor/user/unmount observations execution.md:323,330 cover the named contexts.
- **I-3:** `app.js:21478–21569` deep-copies/freezes nested request JSON, holds submission lock, uses one shared NORMAL/STRONG flow and retries only the frozen body plus confirmation fields. Legacy local function :18171–18211 locks editor/subject/button, returns false before refresh/QA-clear on cancellation and restores in finally. `openActionDialog` :20353–20465 registers native cancel/close, settles once and restores shared labels. Tests :257–345 cover frozen retry, duplicate submission, separate typed STRONG/cancellation, legacy cancellation and native lifecycle. Real component/global/conversation/legacy execution and native ESC/cancel/STRONG observations are explicitly recorded at execution.md:324–327; retained transport records contain original/retry pairs preserving requestId, draftRef where present, body, nested data and QA/attachment order. Initial synthetic draftRef=99 rejection is explicitly excluded from success evidence at execution.md:315.
- **I-4:** `mailbox-chat.js:7538–7539` calls existing handleManualComposeInput only after insertion; no replacement draft persistence scheme. Tests :106–146 inspect saved canonical draft content. Execution.md:320–322 records actual rich quote/subject/anchor88/attachment8 preservation across switch/reload and unchanged ready meeting block. Persistence here is isolated fixture draft storage, not actual MySQL evidence.
- **I-5:** Source branch capture and inspect call are directly visible at `mailbox-chat.js:7466–7472,7529`; no client-supplied authoritative identity name or permanent validated flag is introduced. Final-send safety still uses the backend response. **F-1 is an actual branch-contract failure, not absence of evidence.**
- **I-6:** `app.js:21305–21475` performs fresh GET, server defaults-preview, dialog/edit sequence isolation, per-selection drafts, selected-pair/baseFingerprint PUT, conflict/unknown-result preservation, post-success cache invalidation and no draft rewrite. Tests :347–425 and execution.md:328–331 cover both profiles/experts, choice changes, cancel, conflict, delayed preview and PUT uncertainty. Actual preview requests under account filtering are affected by F-1.
- **S-1..S-4:** Toolbar/default-summary markup `mailbox-chat.js:5642–5648`, controls :7492–7514; escaped findings/shared dialog/schema `app.js:20353–20465,21492–21527`; singleton removable settings dialog :21305–21475; exact CSS/cache/source registration assertions `mailboxReplyIdentity.test.js:428–438`. Inspected retained desktop normal/STRONG screenshots, desktop completion/rich-quote screenshots, mobile toolbar and mobile-top settings screenshots; native shared labels, completion-last without follow-up, wrapping and white single-column settings are visible. Execution.md:332 distinguishes existing mobile 44px touch layout and native viewport-margin cap from the unchanged approved CSS.

Runtime evidence boundary: inspected execution.md:246–377 and `/tmp/reply-identity-02-epoch3/recovery-transport-evidence.json`, `recovery-no-followup-transport-evidence.json` and relevant recovery screenshots. These use actual application HTML/JS/components/dialogs with isolated fixture transport; no production SMTP, real backend identity validation or real database persistence is claimed. No optional runtime investigation or product/test modification was needed to prove F-1.

### AUTO_FIX
- **F-1 — Source-specific identity request contract mismatch (T6, I-5, I-1/I-6 and downstream-interface gate).** `replyIdentityCapture()` (`mailbox-chat.js:7466–7472`) includes `senderAccountCode: account.code || null` and `accountScope: instance.conversation.accountScope || null` for both source kinds. Actual retained `/inspect` request at `recovery-transport-evidence.json` logs[478] has CONVERSATION/anchor88/senderAccountCode=acc1; `/defaults-preview` logs[101] has INBOUND/processing101/accountScope=acc1. The existing controller immediately calls `ReplyIdentityContextRequest.validate()`; :254 requires INBOUND accountScope=null, and :256 requires CONVERSATION senderAccountCode=null. Therefore anchored conversation completion is rejected, and filtered inbound completion/settings preview is rejected. The permissive fixture and new interaction response mocks accepted these invalid requests, explaining why the required suite and browser matrix stayed green. **Smallest authorized correction:** project the shared request context by sourceKind: INBOUND uses processingId/senderAccountCode with null accountScope/anchor; CONVERSATION uses accountScope/optional anchor with null processingId/senderAccountCode. Preserve the existing full internal owner/scope/account/anchor/revision invalidation guarantees and server-owned account resolution. Apply consistently to both inspect and defaults-preview through their shared context seam. Add behavior regressions in authorized `mailboxReplyIdentity.test.js` that enforce the actual backend branch predicates for inbound with selected account scope and anchored/unanchored conversation requests; do not relax backend validation or modify any backend file. Product/test correction paths are authorized and the existing backend contract uniquely determines the valid wire fields. Then freshly rerun the three required commands and demonstrate these real request shapes with an isolated transport that rejects invalid contexts.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX

## Epoch3/Attempt2 (Round1 re-verification) — 2026-10-21T12:45:08+08:00 — 494b6cc569a70b2bcc5ef838b5808ad687600b28..2a85029d79e082358cc3ac03c54665c33fe69d1b

## Light Verification: LIGHT_PASS
Child: 02 — docs/plans/2026-10-21/reply-identity-02-frontend.md
Boundary: 494b6cc569a70b2bcc5ef838b5808ad687600b28..2a85029d79e082358cc3ac03c54665c33fe69d1b
Verifier: Identity02Round1Reverifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Read the complete approved child plan, current Epoch3 brief, previous full verification, recovery execution and round1 fix log. Fresh SHA-256 is `9a9b146883f395501e98084f715ae8a40486944a6e3c8e1971ccdbd56abcc955`, matching the supplied approved identity at `f296820eb8d94d4e3985846b2156480db8ce0b90`. `git diff --name-status 494b6cc569a70b2bcc5ef838b5808ad687600b28 2a85029d79e082358cc3ac03c54665c33fe69d1b` lists the authorized product/test paths (receipt `artifact://1129:12-24`); other paths are controller plan/evidence records. Round1 diff changes only mailbox-chat.js and mailboxReplyIdentity.test.js, both authorized. A3/A5 assertion retirements remain approved; no incidental re-pinning required. |
| Plan and invariants | PASS | F-1 closed by source-specific wire projection, while retaining the complete internal context for stale guards (`mailbox-chat.js:7458–7496`). Both completion inspect (`:7536`) and settings context (`:7565`) use that projection. Strict tests `mailboxReplyIdentity.test.js:246–319` cover filtered inbound, ordinary conversation and anchored conversation, both endpoints, mixed-field rejection and scope invalidation. Previous full report's I-1..I-6/S-1..S-4 mapping remains applicable: round1 does not alter insertion, persistence, send confirmation/locks, settings save logic, styles or cache registration. Recovery execution.md:319–332 supplies the original actual-app browser matrix; fix-log.md:17–29 and retained strict transport/screenshots supply changed-path proof. No remaining explicit deviation established within these gates. |
| Required commands | PASS | Freshly after source/test/evidence inspection: `node --check src/main/resources/static/mailbox-chat.js` exit 0; `node --check src/main/resources/static/app.js` exit 0; `node --test src/test/js/*.test.js` exit 0. Combined command exit 0, fresh output `artifact://1130`: 1785 tests, 332 suites, 1785 pass, 0 fail, 0 cancelled, 0 skipped, 0 todo; duration 4941.447583 ms. Recorded pre-feature baseline 1725 passed. Prior fixer artifact1125 and historical failed runs were not substituted for this run. No backend/Maven replay. |
| Downstream interfaces | PASS | Actual `ReplyIdentityContextRequest.validate()` at ReplyIdentityService.kt:249–257 requires INBOUND processing ID and null anchor/scope; CONVERSATION requires null processing ID/sender. Both controllers validate before operations (ReplyIdentityController.kt:15–24). Projection now emits exactly the permitted branch fields for both inspect and defaults-preview; no backend change. Existing response consumption and GET/PUT defaults interface from the previous full report are unaffected. |

F-1 closure detail: `replyIdentityCapture()` still captures senderAccountCode and accountScope internally, plus user/session/map/owner/target/epoch/revision/body/editor identity; `replyIdentityMatches()` still compares the full captured context. Only transport serialization is narrowed. INBOUND emits sourceKind/contactId/inboundProcessingId/senderAccountCode; CONVERSATION emits sourceKind/contactId/accountScope/anchorMailRecordId. The defaults host receives the same projected context as inspect. This resolves the backend's exact rejected mixed-field predicate without weakening stale-result isolation or backend validation.

Evidence limits: inspected `/tmp/identity-f1-transport.json:1–58` containing filtered INBOUND inspect/defaults-preview and anchor88 CONVERSATION inspect/defaults-preview with the forbidden fields absent; inspected `/tmp/identity-f1-inbound.png` and `/tmp/identity-f1-conversation.png`, showing the actual settings UI with rendered Morgan/Alex Lee previews. Recovery execution.md:307–348 explicitly records actual app/component/dialog browser proof and its fixture boundary. These are fixture/UI/transport evidence, not real backend execution, MySQL persistence or SMTP delivery; no such end-to-end claim is made. Source compatibility is established separately by the actual backend predicate and production projection inspection.

### AUTO_FIX
- N/A — F-1 CLOSED; no remaining proven four-gate violation.

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

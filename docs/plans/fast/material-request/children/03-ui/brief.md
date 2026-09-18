# Child brief — 03-ui

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request`
- Branch: `fast/material-request`
- `child_base_sha`: `4a91a6135c031cc60fd8091b1fe8ae61f77c4a2b` (child 02 terminal code head)
- Approved plan (complete contract): `docs/plans/2026-09-17/03-material-request-ui.md`
- Master plan cross-plan constraints: `docs/plans/2026-09-17/00-material-request-master.md`
- Child 02 observed API contract: `docs/plans/fast/material-request/children/02-status-api/execution.md` (authoritative for the live response shape and the five English `requestText` values)
- Child 01 result: the nine JS cache-key fixtures now derive the key from `index.html`, so the child 03 key bump is permitted.
- Execution report to write: `docs/plans/fast/material-request/children/03-ui/execution.md`

## Authorized files (exactly these eight; #7 and #8 added by amendment A2)

1. `src/main/resources/static/app.js`
2. `src/main/resources/static/mailbox-chat.js`
3. `src/main/resources/static/styles.css`
4. `src/main/resources/static/index.html`
5. `src/test/js/contactHeadLayout.test.js`
6. `src/test/js/materialRequestIntegration.test.js`
7. `src/test/js/mailboxOutboundAttachments.test.js` — A2: assertion-only update so the enumerated `.mc-editor-tools` list includes the new `mc-open-material-request` trigger.
8. `src/test/js/meetingConfirmationIntegration.test.js` — A2: assertion-only update of `tools.length` (7 → 8) and the adjacent index expectations for the new trigger and the follow-up button.

Do not modify `mailbox-chat.css`, `meeting-confirmation.*`, any Kotlin source, any migration, or any other test file. Do not touch the fast-p ledger or other children's artifacts.

## Invariants (from the approved plan)

- I-1: the expert action row reads only `GET /api/expert-contacts/{contactId}/material-requests` and writes `PUT .../material-requests/{code}`; re-render from the response, keep the old DOM on failure, no success toast, never treat the `/materials` paging object as the array.
- I-2: each dialog open re-reads the five items for the current contact; only `PENDING` items are selectable and default-checked; `PROVIDED`/`DECLINED` are shown but disabled; cancel performs no state write and confirm performs no state PUT; English text comes only from the response `requestText`; fixed lead `To proceed, please provide the following supporting materials:` then an unnumbered `<ul>` in API catalogue order.
- I-3: capture session owner, contactId, targetKey and editor revision at open; a late GET or confirm must not write when any identity or revision changed; confirm only appends DOM to the current editor and saves through the existing `handleManualComposeInput`/`saveDraftFromInputs`; it must not touch send, subject, QA, meeting snapshot or attachments; confirm is disabled with no selection.
- I-4: `label`/`requestText` render as text only (`textContent`/text nodes); the appended `<p>/<ul>/<li>` must survive the existing meeting draft sanitizer round-trip with no new draft field.
- I-5: all 11 existing `?v=` values in `index.html` get one identical new key, no resource added or reordered. Re-read the live key from `index.html` first; at this run's base it is `20260917-meeting-mail-global-world-clock` (not the value in the plan prose).
- I-5 addendum from child 01's verified fixture contract: four of the nine JS fixtures extract the key with `/\?v=([0-9a-z-]+)/g`, so the new key MUST consist only of characters in `[0-9a-z-]`; any other character (e.g. `.`, `_`, uppercase) truncates the extractor and breaks the shared-key assertions.

## Style contracts S-1 / S-2 (verbatim in the plan)

- S-1: reuse the existing `contact-head-status-row` / `contact-head-label` / `expert-material-tags` / `expert-material-tag` (+ `is-pending|is-provided|is-declined`) / `dropdown-menu` / `dropdown-item` rules unchanged; no new CSS and no new class for the action row; that row must not display English request text and must not gain edit/save buttons.
- S-2: paste the plan's CSS block byte-for-byte into `styles.css` immediately BEFORE the existing `/* meeting-mail-07: outbound files */` marker (never append at end of file — `mailboxOutboundAttachments.test.js:2190-2194` pins the file tail). No inline styles, no new class outside that block, no `mailbox-chat.css` change. The `材料索取` trigger is a `.button.material-request-trigger` placed between the meeting trigger (`meetingTriggerHtml()`) and the follow-up button inside `.mc-editor-tools`.
- The dialog is a native `<dialog class="material-request-dialog">` using the plan's DOM skeleton inside the same `mailbox-chat.js` session host, mounted on the `meetingEnabled() && !isOutbound` branch exactly like the meeting trigger.

## Required commands

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-material-request
node --check src/main/resources/static/app.js
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/contactHeadLayout.test.js src/test/js/materialRequestIntegration.test.js src/test/js/mailboxChatStyle.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/mailboxOutboundAttachments.test.js
node --test src/test/js/*.test.js
git diff --check
```

Recorded baseline at the run's seed commit: full JS suite exit 0, `tests 990 / pass 990 / fail 0`. Verify the new key with `rg -F '<old key>' src/main/resources/static/index.html src/test/js` returning nothing after the bump, and confirm the 11 references still name the same 5 CSS + 6 JS resources in the same order.

## Commit

Commit only the six authorized files locally as:

```
feat(fast-p): implement 03-ui
```

Exclude `docs/plans/fast/**` reports from that commit; the controller commits evidence separately.

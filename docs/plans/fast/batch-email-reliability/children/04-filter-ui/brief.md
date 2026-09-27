# Child Brief: 04-filter-ui

## Approved plan
`docs/plans/2026-09-26/batch-email-04-filter-ui.md` at `commit:38ba555b4147970ee77569e71f863955e2c4a2b5`. Read the exact plan in full. Target: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-email-reliability-rerun`, `fast/batch-email-reliability-rerun`, amendment A5. Requires completed children 01, 02 and 03; product base is child 03 terminal Code head, not evidence commit.

## Authorized files
- `src/main/resources/static/index.html`
- `src/main/resources/static/app.js`
- `src/test/js/batchEmailVerification.test.js`

## Key invariants and downstream contract
- Add independent toggle in scheduled config and manual execution with prescribed DOM IDs and existing classes only; no CSS/inline style. Keyboard and 1100px/narrow wrapping remain usable.
- Preserve `excludeVerifiedUnavailableEmails` through config create/edit/save, manual default/source/draft/clear-source/snapshot/diff/confirmation/task-list scope. New defaults true, existing/missing source false; manual override does not mutate source; old execution JSON not backfilled.
- Toggle independent of template gate, mail type, live verification. Preserve debounce/request sequence; selected preview response supplies sendable count and excluded count; no extra request or stale count on error.
- Source-text assertions for both real index.html IDs; DOM stubs alone not proof. Keep existing history display, no history UI.
- Consume child 02 backend contracts and child 03 deferred/error messaging; no backend or unlisted file edits.

## Required commands
- `node --test src/test/js/batchEmailVerification.test.js`
- `node --test src/test/js/*.test.js`
- `git diff --check`

Previous product commit `5c69d1cc1de198ea6b5d1f7194800eae0b593164` is a candidate patch only. Reconcile against plan and rerun required commands freshly. Skip formatter and linter. Commit only product/test implementation as `feat(fast-p): implement 04-filter-ui`; controller commits evidence separately.

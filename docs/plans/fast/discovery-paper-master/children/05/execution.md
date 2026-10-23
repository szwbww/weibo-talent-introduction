# Child05 execution log

## Epoch 1 — Execution Result: PLAN_CONFLICT

Date: 2026-10-24. Executor: PaperFrontendImplementer. Execution mode: NEW; fix_round0. Implementation is retained and committed, **not passing / not READY_FOR_VERIFICATION**.

### Identity and boundary

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md`
- Plan SHA-256: `fc97ef919521c96f3092db905dc0359a4d92e5d4933b6a8e652a70ea34d06b11` (34,991 bytes).
- EXECUTION_ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md@fc97ef919521c96f3092db905dc0359a4d92e5d4933b6a8e652a70ea34d06b11`.
- Approved child seed: `c18dbc060dcf4c8440f853ede4d1d3aaf737994f`; master seed: `bcf5d64fb9534759eb1cad08db3089d36e4a1fb0`. Approval basis: current child05 brief and explicit execution assignment; no scope amendment granted.
- TARGET_WORKTREE: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`.
- Branch: `fast/discovery-paper-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`.
- WORKTREE_ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`.
- Recorded child_base_sha: `bbd2700ca56d00a9e2845b61b15c1fb7e5b07a49`.
- Pre-execution code/checkpoint HEAD: `d458b594043c1a8c83d5dacea8269c18e415f5e6`.
- Post-execution code SHA / current product HEAD:

f4b192dd671d1892a76cd98dd0a102015a69c49a

- Commit subject: `feat(fast-p): implement 05`.
- Implementation boundary: `d458b594043c1a8c83d5dacea8269c18e415f5e6..f4b192dd671d1892a76cd98dd0a102015a69c49a`.
- Evidence HEAD: no separate evidence commit by this executor. This report and screenshots are deliberately excluded from the product commit.
- Pre-existing `ledger.md` modification and the child05 control-plane directory were preserved. No other worktree, branch rewrite, push, merge, deployment, backend modification or later-child review occurred.

### C-1 — Smallest required authorization amendment

The required final command fails at the untouched, unauthorized `src/test/js/discoveryVerification.test.js:90–92`:

```text
test("I4 all 16 existing resource keys share the new key and runtime stays unversioned", ...)
assert.equal(values.length, 16); assert.equal(new Set(values).size, 1);

AssertionError: 17 !== 16
```

Child05 T-3 requires the new versioned module and seventeen equal-key assets, while its authorized scope names exactly six files and excludes this test. The smallest seventh-file amendment is to authorize `src/test/js/discoveryVerification.test.js`, update only this stale asset count `16 → 17` and its test description, **retaining** the all-keys-equal assertion and unversioned task-modal-runtime assertion. No production change, test bypass, exclusion, or invariant weakening is needed.

The controller explicitly denied implicit authority to edit/bypass this file and instructed preservation of the failing result. It remains untouched. The other stale sixteen-asset assertion in authorized `discoveryReview.test.js:1003` was updated together with its earlier assertion. The final required suite exposes no additional failure. This is a bounded plan-scope conflict, not an assertion that the feature passed.

### Requirement status and changed files

| Requirement | Execution status | Files / evidence |
|---|---|---|
| T-1 / I-3 / S-1 | IMPLEMENTED | index fourth tab/pane/dialogs/module; app host mount, task identity, disposal, APV run summary and tab keyboard integration. Actual browser shows ordered four tabs and real execution #101 jump. |
| T-2 / I-1 | IMPLEMENTED | API-only settings/stats/list; null stays unknown; saved/new/reused attempts distinct from paper/file counts; measuredAt and old-snapshot warning. |
| T-2 / I-2 | IMPLEMENTED | Protected current-page rows selectable; target boolean plus row revision; clear selection after unprotect; preview/confirm/snapshotHash deletion; conflict waits for new click. |
| T-2 / I-3 | IMPLEMENTED | AbortController plus generation checks; lazy list; visible ten-second refresh; debounced query; binary fetch uses injected context path and same-origin credentials; textContent-only source rendering; native dialogs close and restore focus. Real browser found and execution corrected the existing host Escape interaction, including focus-on-body after disabling submit. |
| T-2 / I-4 | IMPLEMENTED | GET detail does not analyze; explicit POST only; separate first/new versions and times; null delta, BASELINE_MISSING and PARTIAL; historical disclaimer; deleted source keeps evidence but disables source/analysis actions. |
| S-2 / S-3 | IMPLEMENTED | Dynamic row/card elements follow approved structure; native reader/settings/delete dialogs; selection/empty/error/deleted/protected states captured. |
| S-4 | IMPLEMENTED | Exact marked CSS appended, global rules unchanged; actual HTML/CSS regression test passes; desktop/narrow computed geometry below. |
| T-3 | CONFLICT | Product cache keys and authorized tests implemented; mandatory combined suite remains 64 passed / 1 failed solely due C-1. |

Product commit contains only:

1. `src/main/resources/static/index.html` — exact new skeletons, fourth tab, module load, equal asset keys.
2. `src/main/resources/static/styles.css` — appended exact S-4 block only.
3. `src/main/resources/static/app.js` — host lifecycle, tab registration, APV summary seam, narrowly scoped Escape ownership guard.
4. `src/main/resources/static/discovery-papers.js` — authenticated archive module and operations.
5. `src/test/js/discoveryPapers.test.js` — actual-HTML-derived bounded DOM parser, request/lifecycle/safety/selection/conflict/settings/reader regression tests.
6. `src/test/js/discoveryReview.test.js` — existing review regression preserved and four-tab/cache-key contracts updated.

### Fresh command evidence

Working directory for every repository command: TARGET_WORKTREE above.

| Command | Result |
|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-23/discovery-paper-05-frontend.md` | Exit 0 at preflight and final recheck; same canonical path/hash. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-23/discovery-paper-05-frontend.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master` | Exit 0; correct root/branch/Git directory. Expect-root/branch/git-dir checks also exit 0 before staging and again before commit. |
| `node --check src/main/resources/static/app.js` | Exit 0, freshly after final product edits. |
| `node --check src/main/resources/static/discovery-papers.js` | Exit 0, freshly after final product edits. |
| `node --test src/test/js/discoveryPapers.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryVerification.test.js` | Final exit 1: 65 tests, 64 passed, 1 failed, 0 skipped/cancelled/todo; C-1 only. New archive tests and authorized review tests all pass. |
| `git diff --check` | Exit 0 before staging. |
| `git commit -m 'feat(fast-p): implement 05'` | Exit 0; six authorized product/test paths, 683 insertions and 29 deletions. |
| `git rev-parse HEAD` and `git merge-base --is-ancestor HEAD fast/discovery-paper-master` | Exit 0; HEAD is bare SHA above, reachable on target branch. |

Initial combined run was exit 1, 61/64 passed: the new stale-response test captured the already-completed settings signal rather than the pending list signal; an authorized second review asset-count assertion was stale; C-1 was present. Authorized test corrections plus browser-driven dialog Escape implementation and regression extensions intervened before the final run. The final run was not a redundant rerun to reconfirm a known failure. No Maven build, formatter, full verify-p, external provider or production Spring context was run.

### Consumed backend contracts and boundaries

- List: `GET /api/expert-discovery/papers`, `items/total/page/size`, zero-based page and size=20; filters `executionId/q/status/protected/hasGain`.
- Stats: `GET .../stats[?executionId=...]`, real `storage` and `counts`; no disk/example values in production source. Run summary uses that actual execution ID and does not reuse verification/admission counts.
- Settings: `GET/PUT .../settings`, `revision/enabled/maxBytes/minFreeBytes`; integer GiB converted with 1073741824; existing defaults remain backend-owned. Draft revision is captured when opening; errors leave draft and dialog intact.
- Protection: `POST .../protection`, `{items:[{id,revision}],protected:boolean}`; reads `changed/unchanged/conflicts/items`.
- Delete: `POST .../delete-preview {ids}` then separate `POST .../delete {ids,snapshotHash}`. Reads `deletableFiles/deletableBytes/protectedRecords/sharedFiles/busyFiles/notStored/items`; displays result skips/failures. A 409 uses the returned new preview, or explicitly fetches a preview, without repeating commit.
- Detail: `GET .../{id}` reads `record/paper/attempts/outcome/analysis`; frozen `outcome.first` remains separate from analysis. Analysis is only explicit `POST .../{id}/analysis {force:false}` followed by GET detail.
- Content: authenticated `GET .../{id}/attempts/{attemptId}/content[?download=true]`; PDF Blob URL, textual source displayed as text, safe quote matching, URL revocation.
- Existing `ArchiveAttemptDetail` does not expose per-attempt timestamps. UI explicitly labels the actual `record.firstCapturedAt` as record capture time and says file time is unavailable, instead of inventing an attempt date or changing backend DTOs. This is a disclosed API limitation, not an additional scope request.
- No expert, qualification authority, admission, queue, source provider, mail, backend schema, parser/version compatibility or old first snapshot writes were added.

### Actual Chromium evidence — isolated fixture, not production

Used managed headless Chromium (`app:{tern:false}`) against a temporary Bun loopback server on `127.0.0.1:18765`. The server served the **actual full index.html, actual stylesheets, actual modules and app host functions**. Archive API replies were an isolated mutable fixture using the backend shapes, never production example data. The fixture deliberately returned unauthenticated bootstrap, then the test harness opened the actual modal through the real host functions; this does **not** claim a fresh authenticated SQL/Spring deployment or a normal production-login smoke test. External Google font access was blocked; screenshots use available local fallback fonts. No provider/business network calls or production writes were made.

Observed results:

- At 1440×1080: discovery modal width **1180px**, computed max-height **864px = 80vh**, actual height 864px; header `position:static`, opaque `rgb(248,249,251)`; checkbox **14×14px**; no inline presentation in new surface.
- Keyboard Home/End and wrap ArrowRight/ArrowLeft selected/focused the fourth and first real tabs. Existing review/history tabs remained navigable.
- At 643×900: modal width **595px** (unchanged `100vw - 48px`, since 643 is above the existing 640 breakpoint), max-height **720px = 80vh**; document scrollWidth **643px**; table client width **551px**, scrollWidth **1010px**, observed horizontal scrollLeft **459px**. Checkbox stayed 14px. Long page uses the preserved modal's vertical scrolling; some screenshots retain a scrolled modal position, not a new sticky header.
- Protected filter showed B, select-page selected it, unprotect submitted exactly `items:[{id:2,revision:1}], protected:false`; result selection **0** and delete-call count **0**.
- GET reader's eager-analysis count was **0**. One explicit analyze click submitted `POST /papers/1/analysis {force:false}`. Source `<script>` remained visible text and did not set `window.unsafeFixture`; quote locate produced a real `mark` element. Three synthetic historical classifications and 2023 were shown with the fixed disclaimer, not asserted as real extractor gains.
- Settings 503 preserved the open dialog and **25** GiB draft. After submit temporarily disabled the button, Escape closed only settings, kept task modal open and returned focus to settings trigger; reader Escape likewise returned to view trigger. This final behavior was observed after the host guard change.
- Cancelled deletion sent **0** commits. First explicit confirm produced 409; commit count remained **1** and new preview/error were visible. Only a second user click committed again. Fixture response reported two files/3072 logical bytes; this is **UI transport evidence**, not fresh physical-disk deletion proof. Afterwards selection was 0; deleted reader retained evidence and disabled download/analysis.
- Slow execution101 list while changing to202 left selected execution202, 0 rows and `共 0 篇`; old task response did not repaint. Forced stats503 retained the prior measuredAt with `读取失败，以下为旧快照`.
- `/talent` source/download fetch succeeded through injected path. Root-context actual download URL `/api/expert-discovery/papers/1/attempts/11/content?download=true` returned **200**; fixture log also recorded `/talent` module download. Node regressions independently cover both injected path forms.
- Actual host APV detail skeleton plus module showed execution#101 run summary with saved3=new2+reused1 and measuredAt; clicking its link selected papers and execution101.
- Fixture-backed PUT25GiB and protect were re-read after a browser reload. This demonstrates server-response ownership across reload only; **no SQL restart/persistence proof is claimed**.
- Other task marker removed: modal width **700px**, papers hidden, whole discovery tabbar hidden.
- Instrumented actual module timers/Blob URLs: before disposal **1 timer / 1 PDF Blob URL**; after `resetDiscoveryReviewPanel()` **0 timers / 0 Blob URLs / 0 open module dialogs**, host view null. The PDF response used an isolated minimal synthetic Blob for lifecycle only, not a PDF visual/content fidelity assertion.
- Browser tab released and loopback server stopped with `stop(true)`; no long-running fixture/service remains.

Screenshots (retained here, excluded from product commit):

| File | Observed surface |
|---|---|
| `desktop-protected.png` | Desktop four tabs, storage, protected/unavailable rows. |
| `protected-selected.png` | Protected row selectable and current-page toolbar. |
| `reader-qualification.png` | Escaped source, PARTIAL/BASELINE_MISSING, historical cards/disclaimer. |
| `settings-503.png` | Open failed settings with retained 25GiB draft. |
| `delete-preview.png` | Server preview before commit. |
| `delete-409.png` | Refreshed preview and explicit no-auto-retry message. |
| `deleted-state.png` | Deleted rows, selection cleared and logical-byte status. |
| `narrow-deleted.png` | 643×900 deleted state and contained table overflow. |
| `narrow-empty.png` | Empty-list state. |
| `narrow-error.png` | Recoverable list error. |
| `narrow-settings.png` | Narrow native settings dialog. |
| `narrow-horizontal-scroll.png` | Narrow table independently scrolled horizontally. |
| `other-task-700.png` | Existing other-task modal, discovery tabs hidden. |

Historical child01/04 SQL evidence is baseline only, not freshly rerun here. Child03 original294/page34 coverage remains incomplete; child04 frozen23 original bodies yielded actual extractor0 facts and unavailable labels, known1+2 not covered. No current qualification, accuracy gain or real-effect acceptance is inferred from these browser fixtures.

### Freshness / remaining action

- Plan identity unchanged and worktree identity rechecked: YES.
- Required Node commands run after final edits: YES; unresolved failure explicitly preserved.
- Product SHA is target HEAD and reachable: YES.
- Product/test commit limited to authorized paths: YES; control-plane report/screenshots excluded.
- Historical evidence used only as baseline: YES.
- Remaining blocker: **C-1 seventh-file authorization**, not browser or compiler infrastructure.
- Next action: controller pauses for explicit smallest amendment. Do not mark READY, start child06, perform later-child review, bypass tests or edit the unauthorized test. After approval, use the exact amended execution identity and rerun the required command before independent lightweight verification.

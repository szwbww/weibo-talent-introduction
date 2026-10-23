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

## Epoch 2 — Execution Result: READY_FOR_VERIFICATION

Date: 2026-10-24. Executor: PaperFrontendImplementerE2. Fix round: 0. This is execution evidence, **not independent PASS**. Prior epoch text and screenshots above are unchanged.

### Exact identity, authority and retained boundary

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md`.
- Plan SHA-256: `4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00` (35,687 bytes).
- EXECUTION_ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master/docs/plans/2026-10-23/discovery-paper-05-frontend.md@4c8de3b3406ca4d1129713fe1b7ea6ae79067112ceac411fa5a3a42de3995f00`.
- Identity mode: **NEW** for these amended bytes; operationally epoch2 resumes the retained implementation. Epoch1's different plan hash and failed suite are historical evidence only.
- Approval basis: current explicit assignment, complete epoch2 brief, and approved A7/A8 seventh-file amendment. Child and master authoritative commit: `541411d540019428c72b31775c325087b33da303`. Disk plan hash matches `git show 541411d540019428c72b31775c325087b33da303:docs/plans/2026-10-23/discovery-paper-05-frontend.md | shasum -a 256`.
- TARGET_WORKTREE: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master`.
- Target branch: `fast/discovery-paper-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`.
- WORKTREE_ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master@fast/discovery-paper-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master`.
- Child base: `bbd2700ca56d00a9e2845b61b15c1fb7e5b07a49`.
- Retained product commit / pre-execution code SHA: `f4b192dd671d1892a76cd98dd0a102015a69c49a`; read actual retained module, HTML-derived tests, host diff and backend DTO/controller contracts rather than replaying completed children.
- Pre-execution target HEAD: `4834b46ac540cfb541f599c6488928c96483d3a0` (control-plane approved-resume commit).
- Post-execution product/test SHA:

1b8c4981f0116e17648f064482e505d436c02edd

- New commit subject: `feat(fast-p): implement 05`; no amend/history rewrite.
- Epoch2 implementation delta: `4834b46ac540cfb541f599c6488928c96483d3a0..1b8c4981f0116e17648f064482e505d436c02edd`.
- Full controller verification boundary remains `bbd2700ca56d00a9e2845b61b15c1fb7e5b07a49..1b8c4981f0116e17648f064482e505d436c02edd`; control-plane history within that range is not product code.
- Evidence HEAD: no separate evidence commit by this executor. This append and `epoch2-*.png` are excluded from the product/test commit, for controller-owned evidence recording. The pre-existing dirty ledger was preserved and not staged.

### Current-contract reconciliation and changed files

| Requirement | Status | Files / current execution evidence |
|---|---|---|
| Approved C-1 / T-3 | IMPLEMENTED | `src/test/js/discoveryVerification.test.js`: only description `16→17` and count `16→17`. All-equal-key assertion and exact unversioned `task-modal-runtime.js` assertion preserved. |
| T-1 / S-1 / I-3 | IMPLEMENTED, retained | Existing index/app/module integrated fourth tab, actual controls and task identity. Fresh Chromium Home/End/wrapping keys, run-summary jump and non-discovery modal observations below. |
| T-2 / I-1 | IMPLEMENTED, retained | Fresh actual surface displays API stats and measuredAt, separate saved/new/reused counts, null delta unknown and explicit stale-snapshot failure. Fresh Node suite covers null disk stats and absence of production business fixtures. |
| T-2 / I-2 | IMPLEMENTED, retained | Fresh Chromium protected selection/unprotect transport, selection clearing, cancelled preview, 409 no-auto-retry and separate second confirm. |
| T-2 / I-3 | IMPLEMENTED, retained | Fresh delayed task switch, escaped HTML source, actual root and `/talent` module downloads, native dialog Escape/focus, observed timer/blob/dialog disposal. |
| T-2 / I-4 | IMPLEMENTED, retained | Fresh GET reader has zero analysis POST; explicit analyze only; first/new versions separate, PARTIAL/BASELINE_MISSING, historical disclaimer, deleted record retains analysis and disables source actions. |
| S-2 / S-3 | IMPLEMENTED, retained | Fresh protected/selected/deleted/empty/error/reader/settings/delete screenshots; 503 draft retained. Actual IDs and exact skeletons checked by required Node suite. |
| S-4 / T-3 | IMPLEMENTED, retained | Required exact marked CSS test passes. Fresh computed desktop/narrow geometry, opaque static header, 14px checkbox and contained table overflow below. |

This epoch's product/test commit changes only the authorized seventh file, two lines. The retained six product/test files were intentionally unchanged: `index.html`, `styles.css`, `app.js`, `discovery-papers.js`, `discoveryPapers.test.js`, `discoveryReview.test.js`. No backend, dependency, global CSS, unrelated UI, later-child, plan or historical report edit was made. The existing plan/execution report is feature documentation; no separate changelog/knowledge file was created.

### Fresh command evidence after the final product/test edit

All repository commands used the exact TARGET_WORKTREE as cwd.

| Command | Result / evidence |
|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-23/discovery-paper-05-frontend.md` | Exit 0 at preflight and before commit; canonical path and current hash above unchanged. Rechecked at handoff. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-23/discovery-paper-05-frontend.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master` | Exit 0 at preflight; exact root/branch/Git directory and initial HEAD above. |
| Same worktree helper with `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-paper-master --expect-branch fast/discovery-paper-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-paper-master` | Exit 0 before `git add`, immediately before `git commit`, and at handoff. |
| `node --check src/main/resources/static/app.js` | Exit 0, freshly after the two-line edit. |
| `node --check src/main/resources/static/discovery-papers.js` | Exit 0, freshly after the two-line edit. |
| `node --test src/test/js/discoveryPapers.test.js src/test/js/discoveryReview.test.js src/test/js/discoveryVerification.test.js` | Exit 0: **65 tests / 4 suites / 65 passed / 0 failed / 0 cancelled / 0 skipped / 0 todo**; duration 339.244291ms. Run once this epoch, after final product/test edit. The former C-1 assertion now passes without bypass/exclusion. |
| `git diff --check` | Exit 0 before staging. |
| `git add -- src/test/js/discoveryVerification.test.js` | Exit 0; explicit single-file staging, evidence and ledger excluded. |
| `git commit -m 'feat(fast-p): implement 05'` | Exit 0; one test file, two insertions/two deletions. |
| `git rev-parse HEAD` and `git merge-base --is-ancestor HEAD fast/discovery-paper-master` | Exit 0; bare SHA above is target HEAD and reachable from the target branch. |
| `git show 541411d540019428c72b31775c325087b33da303:docs/plans/2026-10-23/discovery-paper-05-frontend.md | shasum -a 256` | Exit 0; exact approved child hash equals current disk identity. |

No mid-flight tests/builds/linters/formatters, Maven, full verify-p, production Spring context, deployment, push or merge were run. Required Node checks and actual browser smoke are execution evidence requested by this assignment, not a verifier decision.

### Backend contract reconciliation

Read current `DiscoveryPaperArchiveController.kt` and `DiscoveryPaperArchiveModels.kt`, and the retained production module. Existing contracts are consumed unchanged:

- List: authenticated `GET /api/expert-discovery/papers`, zero-based `page`, `size=20`; `executionId`, `q`, `status`, `protected`, `hasGain`; response `items/total/page/size`.
- Stats/settings: `GET .../stats[?executionId=...]`, `GET/PUT .../settings`; revision and integer-GiB conversion to bytes. Storage values remain API-owned, including unknown/null, disk terminology and measuredAt. Settings errors leave the draft open; no localStorage business state.
- Protection: `POST .../protection {items:[{id,revision}],protected:boolean}`. Fresh unprotect request was exactly `{items:[{id:2,revision:1}],protected:false}`.
- Delete: `POST .../delete-preview {ids}` then explicit `POST .../delete {ids,snapshotHash}`; conflict error's `preview` is rendered without automatic commit. Fresh commit logs show initial `a…a` hash then, only after second click, refreshed `b…b` hash.
- Detail/analysis: `GET .../{id}` returns `record/paper/attempts/outcome/analysis`; first and new analysis remain separate. Only explicit `POST .../{id}/analysis {force:false}` writes analysis.
- Binary content: same-origin authenticated fetch to host-injected `/{contextPath}/api/expert-discovery/papers/{id}/attempts/{attemptId}/content[?download=true]`; accepted MIME check, textContent or PDF Blob URL, release on exit.
- Current `ArchiveAttemptDetail` has no per-attempt timestamp; retained UI explicitly labels actual record capture time and “文件时间未提供”. This remains a disclosed DTO limitation, not an invented attempt date or backend scope expansion.
- No parser, provider, qualification authority, admission, expert, queue or mail writes were introduced by epoch2.

### Fresh actual Chromium final-surface evidence

Managed **headless Chromium**, CSS viewport 1440×1080 then 643×900 (deviceScaleFactor 1.25), actual complete repository `index.html`, CSS, modules and host code. Served by an ephemeral Bun loopback fixture on **127.0.0.1:18766** only. Browser allowed_domains was only `127.0.0.1`; external fonts/subresources were blocked, so available fallback fonts were used. Fixture API data was confined to server memory and was never written into production assets.

The fixture deliberately returned unauthenticated bootstrap. The harness hid the login overlay and opened the actual task modal via existing host context/init/tab functions. Therefore this is a real-browser final-asset/host/API-transport smoke, **not a fresh authenticated Spring/SQL deployment or normal login-flow acceptance**. Mutable loopback responses match current backend DTO shapes; no production/provider endpoints, credentials or business writes were used.

Observed after final edit:

1. Before paper visit, actual rows count was 0. Four real ordered tab labels were 采集运行 / 专家审核 / 审核记录 / 论文原文. Keyboard observed sequence for Home, End, ArrowRight wrap, ArrowLeft wrap: pipeline → papers → pipeline → papers, with focused/selected actual IDs.
2. Desktop modal: **1180px width**, **864px actual and max height = 80vh**. Header computed **position:static**, opaque **rgb(248,249,251)**. Checkbox **14×14px**. Four fixture rows included protected B and no-source D.
3. At 643×900: modal **595px width**, **720px max height = 80vh**, document scrollWidth **643px**; table wrapper clientWidth **551px**, scrollWidth **1010px**, actual independent scrollLeft **459px**. Header and checkbox retained the desktop computed values.
4. Protected filter selected B via select-page; unprotect request had only B's id/revision and `protected:false`. Result selection **0** and delete commits **0**. Filtering protected after success showed empty normally; original source remained.
5. GET reader analysis POST count **0**. Explicit Analyze click made one POST with `{force:false}`. `<script>window.unsafeFixture=1</script>` remained source text; `window.unsafeFixture` was null, and quote locate produced **one real mark**.
6. Reader displayed frozen first version/time separately from new analysis, **PARTIAL**, **BASELINE_MISSING**, unknown delta, all three synthetic historical qualification categories and 2023, plus the fixed “论文发表时的本人原句，不等于当前资格或自动准入结论。” These are fixture classifications, not real extraction/effect proof.
7. Settings 503 left native dialog open and **25 GiB** input intact. Escape closed only the module dialog, retained task modal and returned focus to settings trigger. Reader Escape returned focus to its view trigger after native close settled.
8. Cancelled preview caused **0** delete commits. First explicit confirm returned409; commits stayed **1**, refreshed preview/error said no auto-retry. Only another user click made commit2 with the new hash. Successful fixture response reported **2 files / 3072 logical bytes**; selection cleared. Deleted reader retained qualification/analysis content while download and Analyze were disabled.
9. Delayed execution101 list followed by setExecution202: after the old response completed, filter remained **202**, rows **0**, list **共0篇**. Forced stats503 retained measuredAt and displayed **读取失败，以下为旧快照**.
10. Actual module download at `/talent/api/.../content?download=true` reached the fixture; root endpoint returned200, then after actual root-page navigation the actual root-mounted module also issued `/api/.../content?download=true` successfully. No static/public content URL was used.
11. Actual `apvDetailHtml(101)` host summary showed execution101 and saved3=new2+reused1 with snapshot time. Clicking its existing new link selected paper tab and execution101. Other task modal measured **700px**, papers hidden and discovery tabbar hidden.
12. Successful fixture PUT25/15GiB and protection were re-read after actual page navigation/reload to root context: policy showed25GiB, protected badges persisted from fixture response state. This demonstrates API response ownership across reload only, **not SQL persistence or process-restart acceptance**.
13. Instrumented actual module lifecycle observed **1 module timer / 1 Blob URL / 1 open dialog** before `resetDiscoveryReviewPanel()`, then **0 / 0 / 0**, host module reference null. PDF used an isolated synthetic Blob only for lifecycle, **not PDF rendering or real document fidelity**.

Visual/runtime limits actually observed:

- 643px is above the preserved640px breakpoint, so595px modal width is correct unchanged48px gutter, not the smaller-mobile20px rule. The real modal's vertical scroll was retained; the narrow horizontal-scroll screenshot has the modal scrolled so title/tab tops can be partially outside the visible scroll viewport. No global sticky-header or spacing “fix” was introduced.
- Small3072-byte fixture displays as0.00MiB under the approved GiB/MiB format; raw logical deletion bytes are separately shown. This is rounding, not real disk occupancy.
- Existing review/history keyboard navigation and required Node regressions were exercised; real authenticated review writes/mail task execution and SQL restart were not exercised by this fixture.
- A harness `page.waitForFunction` waiting on a main-page instrumentation global timed out and emitted one instrumentation-only `undefined.size` page error while the synthetic PDF iframe was open. Direct main-page inspection then observed Blob/timer/dialog counts1/1/1; disposal observed0/0/0. No production module code was changed or test weakened. Earlier harness API-shape mistakes (covered login overlay, wrong `press` argument order) were corrected before observations; they were not product failures.

### Epoch2 screenshots — prior files preserved

All paths are under `docs/plans/fast/discovery-paper-master/children/05/`, excluded from the product/test commit:

| File | Final observed state |
|---|---|
| `epoch2-desktop-protected.png` | Actual desktop four tabs, stats, protected and unavailable-source rows. |
| `epoch2-protected-selected.png` | Protected B selectable with current-page toolbar. |
| `epoch2-reader-qualification.png` | Escaped source, historical facts/disclaimer, mark locator. |
| `epoch2-settings-503.png` | Failed settings retains25GiB draft. |
| `epoch2-delete-preview.png` | Server preview before commit/cancel. |
| `epoch2-delete-409.png` | Refreshed preview with no-auto-retry error. |
| `epoch2-deleted-state.png` | Deleted records and cleared selection. |
| `epoch2-narrow-deleted.png` | 643×900 deleted-state layout. |
| `epoch2-narrow-horizontal-scroll.png` | Independently scrolled1010px table in551px wrapper. |
| `epoch2-narrow-empty.png` | Empty library state. |
| `epoch2-narrow-error.png` | Recoverable list503 state. |
| `epoch2-narrow-settings.png` | Narrow native settings dialog and retained failed draft. |
| `epoch2-stale-task-old-snapshot.png` | Current execution202 with failed-stats old-snapshot warning. |
| `epoch2-other-task-700.png` | Non-discovery700px modal; discovery tabs/papers hidden. |

Browser tab `child05-e2` was explicitly released; loopback Bun server was stopped with `stop(true)` after evidence. No fixture/service/tab remains from this epoch. Temporary fixture lived only in Eval memory; no throwaway source/script file was created.

### Historical boundaries, freshness and handoff

- Prior SQL-backed child01/04 evidence is baseline only, not freshly rerun. Child02 accidental historical synthetic ARXIV requests remain historical and were not repeated. Fresh network fixtures were loopback-only.
- Child03 original294/page34 coverage remains incomplete. Child04 original23 bodies were frozen, but actual extractor returned0facts; labels unavailable/known1+2 not covered. No current qualification, real-effect gain or extra admission is asserted from UI fixtures.
- Plan unchanged during epoch and exact approved hash rechecked: YES. Worktree identity rechecked: YES. Product commit is target HEAD/reachable: YES. Required commands freshly run after final product/test edit: YES. Historical evidence used only as baseline: YES.
- Authorized product/test index is clean after commit; remaining worktree changes are this permitted report/screenshots and controller-owned pre-existing ledger only. No product/test work remains uncommitted.
- Deviations: no implementation/scope deviation; browser/server-persistence/PDF visual limits are explicitly above.
- Remaining blocker: none for execution handoff. READY_FOR_VERIFICATION is not PASS.
- Next action: controller-owned **independent lightweight verification of the full child base→new-head range**, not full verify-p and not a later-child review. Ledger/verification remain controller-owned.

### Epoch2 controller terminal record
- Fresh distinct PaperFrontendVerifierE2 returned LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD at1b8c4981f0116e17648f064482e505d436c02edd, zero repair rounds. Required65/65 Node and both syntax checks passed; exact S-4 and actual Chromium bothviewports/mutation/lifecycle evidence passed. O-1 isolated-fixture acceptance limit, O-2 missing attempt timestamp and O-3 preliminary harness assertion failures retained in verify-log. No whole-system or authenticated SQL browser acceptance claim.

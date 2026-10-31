# Child 01 — execution

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-01-read-labels.md`

Plan SHA-256: `77aef654ed91794dbadcfde5207d16b540d191802e4c2e819ee80bf47f8af0a4`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/docs/plans/2026-10-29/expert-access-01-read-labels.md@77aef654ed91794dbadcfde5207d16b540d191802e4c2e819ee80bf47f8af0a4`

Execution epoch: NEW — epoch 1 approved brief.

Approval basis: current child01 invocation and its approved brief, including the recorded continuing human authorization. The child plan's historical DRAFT wording is not substituted for the explicit execution authority.

Executor: `ExpertAccess01Impl`.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`

Target branch: `fast/expert-access-master`

Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`

Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master@fast/expert-access-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master`

Pre-execution code SHA / child base: `036fc19c88b3a7f5509bbf167a68db6a8af7a20f`

Pre-execution HEAD: `d7f1ef157ebd267bf94f88d9b799aa05e9140216`. The difference from the child code base contained only review05 execution/verification/fix evidence and ledger paths, not product files. Existing dirty controller-owned `children/01/brief.md` and `ledger.md` were preserved.

Post-execution code SHA / Evidence HEAD at handoff: `af168d86f04f1e2f439e3291d6b7b940f5b60071`

Product commit: `feat(fast-p): implement 01`

Implementation boundary: `036fc19c88b3a7f5509bbf167a68db6a8af7a20f..af168d86f04f1e2f439e3291d6b7b940f5b60071` for product changes; pre-existing evidence-only commits remain in that ancestry. This report is deliberately not included in the product commit.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-3/S-1 list and identity expert tags | IMPLEMENTED | mailbox-readonly.js, mailboxReadOnly.test.js | Text-only `mc-tag-row[data-role=readonly-expert-tags]`, `expert-tag` spans and full native titles; existing display mapping with own-property lookup; unknown values including `constructor` remain literal. Both list and identity render the same projection. |
| I-3/S-1 empty/unavailable semantics | IMPLEMENTED | same | `[]` creates no row or placeholder; null/unavailable creates the specified `p.mc-note` text. Current summary refresh replaces the selected tag snapshot and removes stale tags. |
| I-3/S-1 real inbound mail tags | IMPLEMENTED | same | Only `INBOUND_PROCESSING` consumes existing `tags`; QA/CUSTOM/inactive classes follow returned tagType/active. No editable shared renderer or extra tag API is called. |
| I-2/I-3 read-only boundary | IMPLEMENTED | same | Tag descendants contain only text spans, no controls/actions/HTML interpretation. Real authenticated backend requests denied tag writes, auto tagging, attachment access, unauthorized expert reads and global/admin endpoints. No backend file changed. |
| I-10 lifecycle/downstream public surface | IMPLEMENTED | same | Existing mount/refresh/unmount, review editor and request epochs retained. Regression includes slow A→B tags; runtime includes delayed genuine A response, revocation, refresh/focus/visible and logout cleanup. |
| Cache-key contract | IMPLEMENTED | index.html, mailboxReadOnly.test.js | Existing versioned resource URLs uniformly use `20261030-expert-access-01`; source-contract test checks uniformity and the actual reader script entry. |
| Existing identity/paging/attachments/review behavior | PRESERVED | same | Existing regression cases retained; name-only identity, pagination/disabled buttons and no attachment controls observed in the real reader browser. Review module implementation and public API were not replaced. |

### Changed Files

- `src/main/resources/static/mailbox-readonly.js` — independent text-only expert/mail tag consumers; retain expert tag snapshot on selection and refresh it from the latest list projection.
- `src/main/resources/static/index.html` — update existing resource cache keys uniformly.
- `src/test/js/mailboxReadOnly.test.js` — five tag/cache/refresh tests and expanded existing slow-response fixture/assertions; keep pre-existing regressions.

Commit receipt: 3 files changed, 215 insertions, 24 deletions. No CSS, backend, migration, endpoint, permission policy, other child, approved plan or ledger was edited by this executor. This explicitly requested execution report is the only additional executor-owned evidence edit.

### Commands and Results

All repository commands ran with cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`.

| Command / operation | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-29/expert-access-01-read-labels.md` | exit 0, unchanged | Canonical path/hash matched the brief at entry, before staging and at handoff. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-29/expert-access-01-read-labels.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master` | exit 0 | Initial root/branch/git-dir/HEAD receipt `artifact://2069`. |
| Same worktree helper with `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master --expect-branch fast/expert-access-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-access-master` | exit 0 | Repeated immediately before git add, immediately before git commit and at final handoff; receipts `artifact://2124`, `artifact://2125`, `artifact://2128`. |
| `git status --short && git log -3 --oneline && git diff --name-only 036fc19c88b3a7f5509bbf167a68db6a8af7a20f..HEAD` | exit 0 | Initial product base/evidence-only ancestry and controller dirty files recorded before editing. |
| Fixed-key search, grep tool: pattern `20261029-review-admin`, path `src/test` | no matches | No fixed-key test outside the authorized file scope required amendment. |
| First `node --test src/test/js/mailboxReadOnly.test.js` after initial implementation | exit 1; 22 tests, 17 pass, 5 fail | Selected identity originally retained only name/contactId; the new tests exposed loss of its tag projection. Fixed within the authorized module by carrying a copied tag snapshot. The `&&` syntax/diff checks in that first chain did not run. |
| First completed focused chain after snapshot/refresh implementation | exit 0; 23/23 | All existing and new cases passed; subsequent own-property mapping hardening was re-exercised freshly below. |
| Final `node --test src/test/js/mailboxReadOnly.test.js` | exit 0; 23 tests, 5 suites, 23 pass, 0 fail/skip/cancel | Final-source receipt `artifact://2122`; duration 641.834917 ms. |
| Final `node --check src/main/resources/static/mailbox-readonly.js` | exit 0 | Same final sequential chain, `artifact://2122`. |
| Final `git diff --check` | exit 0 | Same final sequential chain, `artifact://2122`. |
| `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -Dmdep.outputFile=/tmp/expert-access01-runtime.classpath -Dmdep.includeScope=test` | exit 0, BUILD SUCCESS | Current Maven runtime dependencies resolved for isolated browser/backend smoke; not a build/test claim. |
| `docker run --rm -d --name expert-access01-mysql -e MYSQL_ROOT_PASSWORD=fixture -e MYSQL_DATABASE=expert_access01 -p 127.0.0.1:23371:3306 mysql:8.0.36` | exit 0 | Fresh disposable MySQL; application Flyway applied 168 migrations, ending at v169 (`artifact://2077`). |
| `/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home/bin/java @/tmp/expert-access01-java.args` | ready on 23811 | Authenticated isolated application using existing target/classes and current dependencies; current source-served HTML/JS/CSS. Startup `artifact://2077`. |
| Real Chromium changed-path execution, browser tools + isolated HTTP requests | exercised | `/tmp/expert-access01-smoke.json`, including finalDesktop/finalMobile DOM and geometry. Details below. |
| `git add -- src/main/resources/static/mailbox-readonly.js src/main/resources/static/index.html src/test/js/mailboxReadOnly.test.js` then `git commit -m 'feat(fast-p): implement 01'` | exit 0 | Only authorized product/tests staged; commit `af168d86f04f1e2f439e3291d6b7b940f5b60071`. |
| `git rev-parse HEAD` and `git merge-base --is-ancestor HEAD refs/heads/fast/expert-access-master` after commit | exit 0 | Commit is target-worktree HEAD and reachable from the exact target branch. Explicit final SHA ancestry check also exited 0. |

### Actual Desktop/Mobile Changed-Path Proof

Environment: real Chromium loaded the actual source-served `index.html`, actual `app.js`, reader module and shared CSS from authenticated backend `http://127.0.0.1:23811`. Java was the genuine Zulu 11.0.33 specified by the brief. The backend used disposable MySQL `127.0.0.1:23371/expert_access01`, with `serverTimezone=Asia/Shanghai`. Attachments were scoped to `/tmp/expert-access01-attachments`; fixture emails used `.test`. Mail queue, automation scheduling, discovery scheduling, LLM and automatic LLM replies were disabled.

ES was an explicitly controlled loopback HTTP fixture at `127.0.0.1:23372`, not production Elasticsearch. It supplied deterministic `_search` profiles, empty tags and deliberate 503 failures; all ES HTTP calls were recorded. Backend authorization, JDBC projection, authentication/session, actual reader rendering and user interaction were real, not browser-routed successful API mocks. No production database, mailbox, SMTP or ES endpoint was used. SMTP delivery itself was not exercised.

The Java arguments explicitly selected:

- `--server.port=23811`
- `--spring.datasource.url=jdbc:mysql://127.0.0.1:23371/expert_access01?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`
- `--spring.datasource.username=root`, `--spring.datasource.password=fixture`
- `--spring.web.resources.static-locations=file:/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master/src/main/resources/static/`
- `--talent-introduction.elasticsearch.base-url=http://127.0.0.1:23372`
- auth enabled; mail-queue/scheduling/expert-discovery/LLM/LLM-auto-reply disabled; expert-discovery cron `-`; isolated attachment path.

Observed changed-path assertions:

1. Real user1 login displayed only the mailbox shell and logout control. Selected expert identity showed the long expert name and no email fallback. Expert tags “新发现” and “瑞典” appeared in both list and header; mail tags “薪资” and “补充材料” appeared on the actual inbound processing message.
2. Malicious `<img ...>` labels remained literal text; tag rows contained no img/script/a nodes or editable actions. A 180-character expert tag retained its full native title. Final-source reload with browser cache disabled also showed unknown `constructor` literally in both expert-tag rows, not inherited object properties.
3. At desktop width 1440, document width was 1440 and no tag row overflowed. Final row widths were 348, 751.046875 and 756.109375 px; write-control counts were all zero. No attachment download/file-input nodes existed.
4. At mobile width 390, document width was 390. Expert rows were x=47/41, width=308; mail row x=56, width=263. All fit, with no row overflow or write controls. Computed mail chips reused the existing 10px font and `2px 6px` padding; CUSTOM/QA colors were the existing styles. Both pagination buttons were disabled with the small fixture dataset.
5. A real empty ES projection removed expert-tag rows and showed no unavailable placeholder. An intentional ES 503 produced backend null expertTags and the exact “标签暂不可用” notes in list/header; it did not fabricate empty tags. Body reading stayed available.
6. Using the same authenticated reader session, direct expert-tag add/remove POSTs, mail-tag add POST, mail-tag DELETE, auto-tag POST, attachment download, an unauthorized expert's messages, task list, sender-account list and admin grant PUT all returned 403. ES-call delta during this denied matrix was exactly zero. Persisted mail tags stayed unchanged, with zero outbound mail records and zero send attempts. Admin independently re-read the same QA/CUSTOM tags and attachment metadata through the real API. The JSON `denied` array records the exact routes/statuses.
7. A genuine A message response was held after the backend completed it. The browser switched to B; admin revoked A; releasing the held response and refreshing retained only B body and B mail tags, removed A from the list and rendered no send/download nodes. The JSON `stale` object records the result.
8. Refresh, focus and visibility actions generated only conversation-list/messages GETs, not global account/task/tag APIs. The observed whitelist assertion was true before the intentionally separate logout request.
9. Revoking the currently selected B and refreshing produced a real 403 cleanup: zero message articles and zero mail-tag rows with “暂无权限查看该专家邮件”. The JSON `forbidden` object records the result.
10. Grants were restored only in the disposable fixture. A genuine message response was then held during logout and released; after asynchronous logout navigation finished, the reader root count was 0, neither expert body remained, and the login form was visible. The JSON `logout` object records the completed result. The earlier immediate post-click sample preceded logout completion and is not used as the final result.

Browser evidence artifact: `/tmp/expert-access01-smoke.json` (retained outside the repository).

Screenshot capture is **NOT_CAPTURED**: the shared Chromium screenshot transport and direct CDP surface capture timed out, including at mobile size. No screenshot is claimed. Required actual-browser changed-path proof is the real rendered DOM, computed styles, viewport geometry, backend/session interactions and retained JSON above; this is not source inspection presented as runtime proof.

### Validation Boundaries / Deviations

- No plan/product-scope amendment was required. The selected expert's tag snapshot is necessary to carry existing summary data through the pre-existing name-only selection object; no permission/cache authority or new API was introduced.
- The brief's deferred-gate instruction was followed: no concurrent build/test/formatter while writing. Required commands were rerun after final implementation.
- Intermediate fixture-only issues: a MySQL label update without an explicit UTF-8 client charset displayed mojibake; it was corrected using `--default-character-set=utf8mb4`. A read-only audit query initially used the nonexistent `manual_reply_send_attempt` name; the actual `mail_send_attempt` query returned 0. Neither issue caused a repository repair.
- A browser reload initially reused an earlier in-session cached reader script; explicit `Network.setCacheDisabled(true)` plus reload then exercised final source. Final machine gates and desktop/mobile `constructor` evidence correspond to the final product bytes.
- Full future NONE/READ/FULL feature matrices, S-2/S-3/S-4 controls and later-child write behavior are NOT_RUN by this child; the current implementation intentionally retains the existing READ restrictions. No claim is made that FULL or later UI has been implemented. The full JVM suite, SMTP-success flows, automated IMAP/tag pipelines and independently instrumented file-read/SMTP call counts are NOT_RUN here; zero ES calls and persisted zero send outcomes are the observations actually obtained.
- Screenshot transport failure is recorded above, not silently reported as visual screenshot success. Actual browser viewport/style evidence was obtained.

### Cleanup and Freshness

- Owned Java PID 43107 was terminated normally with SIGTERM (supervised exit 143 expected); disposable `--rm` MySQL was stopped/removed; owned ES fixture and browser tab/CDP connection were closed.
- Temporary runtime argument and classpath files were removed. No temporary source scaffolds were added to the repository; runtime JSON evidence remains in `/tmp`.
- Plan identity rechecked: YES, unchanged.
- Worktree identity rechecked: YES, exact root/branch/git-dir.
- Reported product commit reachable from target branch: YES.
- Required commands run freshly this invocation: YES.
- Historical evidence used only as baseline: YES.
- Product working tree and index were clean after commit. Final pre-report status contained only the preserved controller-owned brief/ledger modifications. This report is left uncommitted for controller-owned evidence handling.
- No push, merge, history rewrite, subagent, other worktree mutation or later-child inspection occurred.

### Remaining Blocker

None for the authorized child implementation and required node/actual-browser changed-path gates. Screenshot capture was unavailable as explicitly noted; no screenshot evidence is represented as complete.

### Next Action

Controller runs independent `verify-p` for child01 and records its evidence separately. READY_FOR_VERIFICATION is not an independent PASS.

## Controller terminal checkpoint — 2026-10-30T04:13:10.648Z
- Terminal independent verdict: LIGHT_PASS; exact report retained in verify-log.md.
- Final child code head: 30c8af9b98f675f628d3ee98bd737f1716ecf54c
- Actual repair rounds: 1; fix commits: 30c8af9b98f675f628d3ee98bd737f1716ecf54c.
- This closing entry records existing execution/fix/verifier evidence; no product command was rerun by the controller.

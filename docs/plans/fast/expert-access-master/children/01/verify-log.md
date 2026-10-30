# Child 01 — independent light verification

## Epoch 1 — Attempt 1 — 2026-10-30T04:05:50.829Z

## Light Verification: LIGHT_FAIL
Child: 01 — docs/plans/2026-10-29/expert-access-01-read-labels.md
Boundary: 036fc19c88b3a7f5509bbf167a68db6a8af7a20f..af168d86f04f1e2f439e3291d6b7b940f5b60071
Verifier: ExpertAccess01Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary changes three product/test paths, all authorized: mailbox-readonly.js, index.html, mailboxReadOnly.test.js. Other boundary paths are previously committed review05 logs/ledger, not product changes. HEAD is af168d86f04f1e2f439e3291d6b7b940f5b60071 on fast/expert-access-master; working product files have no diff against that head. |
| Plan and invariants | FAIL | I-3/S-1 projection is text-only and correctly distinguishes empty/null; source mailbox-readonly.js:107–140, 652–682, 713–730 and tests mailboxReadOnly.test.js:413–543 directly cover it. However, new selected.expertTags survives revokeAccess and is rendered after 403; direct real-browser reproduction below proves violation of I-2 current authorization and I-10 revocation/context cleanup. F-01. |
| Required commands | PASS | Fresh `node --test src/test/js/mailboxReadOnly.test.js`: exit 0, 23 tests / 5 suites, 23 passed, 0 failed/cancelled/skipped (623.289709 ms). Fresh subsequent `node --check src/main/resources/static/mailbox-readonly.js`: exit 0. Actual Chromium desktop 1440 and mobile 390 geometry/behavior exercised independently, with the explicit fixture boundary below. No recorded baseline command failure was used as a waiver. |
| Downstream interfaces | PASS | mailbox-readonly.js:994–1037 preserves mount, handle.refresh/flushDrafts/hasPendingDrafts/unmount and module.unmount. Review implementation is unchanged by the exact product diff; name-only identity, paging and disabled attachments retained. No backend API/policy change; resource keys uniformly 20261030-expert-access-01 (fresh source-contract test). |

Independent runtime evidence:

- Real headless Chromium opened actual source-served index.html at `http://127.0.0.1:23813/`, loading the committed reader JS and existing shared CSS. Server command: `python3 -m http.server 23813 --bind 127.0.0.1 --directory src/main/resources/static`, cwd the retained worktree. The readiness log timed out because of buffering, but actual browser navigation/resources succeeded. The verifier then mounted the actual module in a real isolated main host with an explicitly injected deterministic API responder; this is real browser DOM/style evidence, **not** a fresh authenticated backend or unchanged full-app host claim.
- Desktop viewport/document width 1440/1440. Expert row widths 238, 238, 955.484375; mail row width 784. Mobile viewport/document width 390/390. Expert rows x=22/22/16, widths=322/322/358; mail row x=31, width=328. Every row had scrollWidth <= clientWidth and fit within its viewport.
- Both sizes displayed “新发现”/“瑞典” in list and identity and QA “薪资”/CUSTOM “补充材料” on INBOUND_PROCESSING. A 180-character expert tag preserved its native title, `constructor` remained literal, and malicious img/script strings remained text with zero descendant img/script/a/input/button/data-action nodes. Existing classes computed expert font/padding 11px / 1px 6px and mail font/padding 10px / 2px 6px. Pagination buttons were disabled; attachment/download/file-input nodes were absent.
- With A expertTags=[], A's list/header rows and unavailable notes disappeared (the other expert B correctly retained its own tag row). With A expertTags=null, exactly two “标签暂不可用” notes appeared. A held message response released after switching to B did not restore A body; identity remained “专家乙B标签”, B body was present and A body absent. Focus/visibility requests stayed GET-only within `/api/mail/mailbox/conversations`. Unmount emptied the host, localStorage remained empty.
- **F-01 reproduction:** mount expert A with expertTags=["撤权前标签"], select A, then reject refreshed list/detail requests with status=403. Real DOM after awaiting handle.refresh(): identity=`长姓名专家甲 Long Academic Name 标签只读验收撤权前标签`, readonly-expert-tags=`["撤权前标签"]`, articles=0, alert=`暂无权限查看该专家邮件`. Existing body/review denial works, but the newly added tag snapshot remains visible for the now-denied expert. Source `revokeAccess()` at mailbox-readonly.js:338–343 leaves selected intact; `renderIdentity()` at 679–682 unconditionally renders selected.expertTags. Test suite currently misses this newly added revoked-tag case.
- Retained baseline inspected, not accepted as a verdict: ledger Baseline and child execution.md; `/tmp/expert-access01-smoke.json:143–223` records actual same-session tag write/auto-tag/attachment/admin denials (403), delayed response isolation, forbidden body clearing and logout cleanup. This verifier did not rerun the authenticated JVM matrix, SMTP/IMAP pipelines or future-child UI; no fresh backend-auth success is claimed. Exact diff does not change those backend or review paths.
- Screenshots NOT_CAPTURED; no screenshot is fabricated or claimed. The actual-browser evidence above is structured DOM, computed styles, geometry and behavior. Owned browser tab was closed and static service stopped normally with SIGTERM (exit 143). No product/test/index/commit/plan mutation was performed.

### AUTO_FIX
- F-01 — Child I-2 requires current target authorization; I-10 requires revocation to invalidate the mounted context, and the brief explicitly preserves 403 cleanup while adding tags. Evidence: deterministic real-browser 403 reproduction above and mailbox-readonly.js:338–343 / 679–682. Smallest authorized correction: invalidate the selected expert-tag projection when access is revoked, without changing the existing name/forbidden-body/review cleanup behavior; add a regression asserting no previously displayed expert tag survives list or detail 403 (and 401 through the same seam). Limit repair to authorized mailbox-readonly.js and mailboxReadOnly.test.js, then rerun both required commands and changed-surface browser checks. No API, permission-policy or CSS change is needed.

### RECORD_ONLY
- N/A

### Required Action
- AUTO_FIX

## Epoch 1 — Attempt 2 — 2026-10-30

## Light Verification: LIGHT_PASS
Child: 01 — docs/plans/2026-10-29/expert-access-01-read-labels.md
Boundary: 036fc19c88b3a7f5509bbf167a68db6a8af7a20f..30c8af9b98f675f628d3ee98bd737f1716ecf54c
Verifier: ExpertAccess01Reverify1

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Read-only `git diff --stat 036fc19c88b3a7f5509bbf167a68db6a8af7a20f 30c8af9b98f675f628d3ee98bd737f1716ecf54c` and focused product diff show exactly the authorized product/test paths: src/main/resources/static/mailbox-readonly.js, src/main/resources/static/index.html, src/test/js/mailboxReadOnly.test.js. The remaining boundary paths are review05 evidence and ledger, not product/test changes. Repair diff af168d86f04f1e2f439e3291d6b7b940f5b60071..30c8af9b98f675f628d3ee98bd737f1716ecf54c changes only the reader and its authorized test. Initial HEAD is the exact repair SHA, branch fast/expert-access-master; initial dirty paths are controller-owned child01 evidence and ledger only. |
| Plan and invariants | PASS | mailbox-readonly.js:107–140, 653–683, 714–731 implements S-1 text-only expert/mail tags, null/empty behavior and INBOUND_PROCESSING-only mail projection. Tests :413–543 cover these and uniform cache keys. F-01 is FIXED by :338–344 clearing selected.expertTags at the existing revocation seam, with four list/detail × 401/403 regressions at mailboxReadOnly.test.js:622–662. Fresh real-browser checks below independently verify tags/body/cursor/review cleanup while preserving the selected name and forbidden message, plus successful late-response isolation. |
| Required commands | PASS | Fresh final sequential `node --test src/test/js/mailboxReadOnly.test.js && node --check src/main/resources/static/mailbox-readonly.js`, cwd the retained worktree, exit 0. Test command: 27 tests, 5 suites, 27 passed, 0 failed/cancelled/skipped/todo, duration 736.443792 ms. Subsequent syntax command: exit 0. Both ran after code/test inspection and browser exercise; no recorded baseline failure was waived. Real headless Chromium changed-surface checks ran at actual 1440px and 390px viewports, details below. |
| Downstream interfaces | PASS | mailbox-readonly.js:995–1037 retains MailboxReadOnly.mount/unmount and handle.refresh/flushDrafts/hasPendingDrafts/unmount; selected name remains name-only, pagination and attachment restrictions remain intact. Existing review reset/editor implementation is retained, not replaced. No backend API, permission-policy or CSS file changes occur in the boundary. index.html resource URLs use the same 20261030-expert-access-01 key, exercised by the fresh source-contract test. |

Independent fresh browser evidence:

- Environment: real managed headless Chromium; unchanged source resources served by `env PYTHONUNBUFFERED=1 python3 -m http.server 23817 --bind 127.0.0.1 --directory src/main/resources/static`, cwd `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`. Browser opened actual index.html, then installed an isolated main host loading the actual mailbox-readonly.js, styles.css and mailbox-chat.css from that server. Requests passed to the actual module through its api option used an explicitly injected deterministic responder. This is actual browser DOM/style/geometry/behavior evidence, **not fresh real backend authentication, backend authorization, ES/JDBC persistence, or full-app host evidence**.
- Actual viewport/document widths were 1440/1440 and 390/390; deviceScaleFactor was set to 1 for the checks. Desktop expert-row x/width pairs: 22/238, 22/238, 345/955.046875; mail row 359/784. Mobile expert-row x/width pairs: 22/322, 22/322, 16/358; mail row 31/328. Every measured tag row fit within its viewport and had scrollWidth <= clientWidth. Computed existing expert chip font/padding was 11px / 1px 6px, mail chip 10px / 2px 6px at both sizes.
- Allowed state at both sizes: list/header showed 新发现 and 瑞典; unknown constructor remained literal; a 180-character tag retained its full native title. Expert tags and QA/CUSTOM/inactive mail tags containing literal img/script markup produced no img/script/a/input/button/data-action/inline-style descendants. Mail tags showed 薪资, 补充材料 and the literal malicious string with the existing qa/custom/inactive classes. Selected identity used the long expert name, not its private fixture email. Pagination buttons were disabled; attachment/download/file-input controls were absent.
- Empty/null at both sizes: replacing A's current summary tags with [] removed A's list/header tag rows without any unavailable note; replacing with null showed exactly two 标签暂不可用 notes in the corresponding regions. Restoring tags and triggering refresh/focus/visibility generated GET-only conversation-list/messages requests before separate deliberate draft creation.
- **F-01, fresh matrix: list401, list403, detail401, detail403, each at both actual viewport sizes (8 cases).** Before each denial, A's expert tags and message body were visible and an explicit fixture review draft populated the review subject/editor. After the injected denial, both list and selected expert-tag projections and all mail-tag/message/cursor nodes were absent; no 标签暂不可用 placeholder appeared. The selected long name and 暂无权限查看该专家邮件 alert remained. Review section was hidden, editor text and subject were empty, contenteditable was false, and hasPendingDrafts() was false.
- For every denial case, the concurrent other conversation endpoint's successful response was deliberately held until after denial cleanup, then released and awaited. No tags, body, cursor, or review state reappeared. This directly exercises the generation/sequence boundary alongside the repaired tag invalidation rather than only checking an immediate empty render.
- Prior late-response invariants at both sizes: held A detail response, switched to B, then released A; identity remained 专家乙B标签 and the message body remained B正文, without A正文 in the conversation. A separate held detail response released after handle.unmount() left the host empty. localStorage remained empty. This verifies the module unmount boundary; fresh full-app logout/authentication was NOT_RUN.
- Screenshots NOT_CAPTURED and not claimed. Evidence is structured actual-browser DOM, computed styles, viewport geometry and behavior, not a screenshot or human acceptance sign-off. Owned browser tab was closed and owned static service stopped.

Lineage and retained backend evidence:

- Original implementation: af168d86f04f1e2f439e3291d6b7b940f5b60071. Bounded repair: 30c8af9b98f675f628d3ee98bd737f1716ecf54c. Prior Attempt 1 and Epoch 1 Round 1 fix-log were read without alteration; the same stable F-01 identity is retained.
- Retained baseline inspected: child01 execution.md:80–118 and `/tmp/expert-access01-smoke.json:143–223` describe the prior real authenticated isolated backend smoke, including tag add/remove/delete/auto-tag, attachment, unauthorized detail and global/admin 403 results, delayed-response isolation, forbidden-body cleanup and logout completion. Those are historical receipts, **not fresh backend-auth proof from this attempt**. No backend code changed in the reviewed boundary.
- Full JVM/NONE/READ/FULL/admin matrices, SMTP/IMAP/background-tag pipelines, fresh ES/JDBC mutation checks and later-child UI were NOT_RUN in this lightweight attempt; no skips are counted as passes and no whole-system or human acceptance is claimed. The exact fresh child-required node and actual-browser changed-surface gates above completed.
- This verifier changed only this append-only report. Product files, tests, index, branch, commits, approved plans and other evidence were read-only; existing controller-owned dirty artifacts were preserved.

### AUTO_FIX
- N/A. F-01 disposition: FIXED and independently reverified against repair 30c8af9b98f675f628d3ee98bd737f1716ecf54c. No new correction is required.

### RECORD_ONLY
- N/A. Prior Attempt 1 had no RECORD_ONLY findings; none are dropped or reopened.

### Required Action
- COMPLETE_CHILD

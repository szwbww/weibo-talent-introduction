# Child p1 Execution — 计划 P1

- Status: PLAN_CONFLICT (plan gap: required commands cannot pass without editing an unlisted test file — see Remaining Blocker)
- Boundary: bb34ca2001d0abeac3bd7a8fc13995769e14143e..9b90e41c678c396c7e720832c58e162e717f34da
- Agent: P1Implementer-2

## Identity

- Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-ui-tweaks-00-execution-order/docs/plans/2026-08-21/ui-tweaks-01-check-replies-move-and-auto-preview-removal.md
- Plan SHA-256: 6b111be9896f9330ebe2b9166ba77aa3e6b9596a156f3f587d25933b3a8bfffc
- Execution ID: <plan>@6b111be9896f9330ebe2b9166ba77aa3e6b9596a156f3f587d25933b3a8bfffc
- Execution epoch: NEW
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-ui-tweaks-00-execution-order
- Target branch: fast/ui-tweaks-00-execution-order
- Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-ui-tweaks-00-execution-order@fast/ui-tweaks-00-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-ui-tweaks-00-execution-order
- Pre-execution code SHA: bb34ca2001d0abeac3bd7a8fc13995769e14143e
- Post-execution code SHA: 9b90e41c678c396c7e720832c58e162e717f34da
- Evidence HEAD: N/A (controller commits evidence separately)
- Implementation boundary: bb34ca2..9b90e41 (8 authorized files only)

> Note: `scripts/plan_identity.py` ran OK (identity above). `scripts/worktree_identity.py` crashes on two stale
> `/sessions/rcw-*` worktree entries that no longer exist on this machine (`resolve(strict=True)` → FileNotFoundError);
> the same identity fields were computed manually via `git rev-parse`/`branch --show-current`/`worktree list --porcelain`
> and match the script's intended semantics. Not a plan/worktree mismatch.

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1-1 移除专家列表「检查回复」按钮 | IMPLEMENTED | index.html | button line deleted from `.toolbar-actions`; S-2 grep: view-contacts fragment contains 0 `checkRepliesBtn`, still has `bulkAutoReplyBtn` + `backfillOperatorStatusBtn` |
| T1-2 mailbox `.panel-head` S-1 骨架 | IMPLEMENTED | index.html | S-1 verbatim skeleton in place (检查回复 + 批量发送 wrapped in `.panel-head-actions`); I-1: `id="checkRepliesBtn"` exactly 1× in index.html |
| T1-3 `.panel-head-actions` 规则块 | IMPLEMENTED | styles.css | contract block inserted verbatim after `.panel-head h2::before`, before `/* Tables */`; `grep -c 'panel-head-actions' index.html` = 1 |
| T1-4 CHECK_REPLIES 链路不动 | IMPLEMENTED | app.js | `git diff app.js \| grep checkRepliesBtn` empty; count `checkRepliesBtn` in app.js = 5 (683/694/4878/4900/5201) |
| T2-1..T2-7 删自动回复预览 | IMPLEMENTED | app.js | 12 retired tokens + degraded copy + endpoint path gone from app.js/index.html/styles.css (I-4 grep exit 1, 0 hits); `reply-workflow-detail` count 8→6 (S-3); `autoPreviewHtml` diff = deletions only |
| T3-1 缓存键 v9 | IMPLEMENTED | index.html | three `?v=` sites all `20260821-v9-check-replies-move`; `grep -o '\?v=[^"]*' \| sort -u` → single line (bare `?` needs escaping in this grep flavor) |
| T3-2 测试键断言 v9 | IMPLEMENTED | batchSendTaskConsoleVisualFix.test.js | 3 asserts updated; test file passes 19/19 |
| T3-3 宿主计数 4→3 | IMPLEMENTED | trustReplyWorkbenchSharedMount.test.js | assert updated; `grep -cF 'requireTrustReplyWorkbenchRuntime(host)' app.js` = 3; test file passes 58/58 |
| T3-4 I3-2 用例改写 | IMPLEMENTED (1 deviation) | batchEntryRelocation.test.js | new `.panel-head-actions` case; 7/7 pass — see Deviation D-1 |
| T3-5 退休守卫重写 | IMPLEMENTED | autoPreviewWorkbenchHost.test.js | full rewrite per plan; 5/5 pass |
| T3-6 新增测试 | IMPLEMENTED | checkRepliesRelocation.test.js (NEW) | 8/8 pass (I-1/I-2/I-3/S-1/S-2 assertions) |
| I-5 统一卸载入口 | IMPLEMENTED | app.js | `grep -c 'unmountMailboxTrustReplyHosts()'` = 9; body = `unmountLiveTrustReply();` (regex match in T3-5 test) |
| I-6 宿主数 4→3 | IMPLEMENTED | app.js + test | count = 3; shared-mount test 58/58 |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/checkRepliesRelocation.test.js` | PASS | exit 0; tests 8, pass 8, fail 0, cancelled 0 |
| `node --test src/test/js/autoPreviewWorkbenchHost.test.js` | PASS | exit 0; tests 5, pass 5, fail 0, cancelled 0 |
| `node --test src/test/js/batchEntryRelocation.test.js` | PASS | exit 0; tests 7, pass 7, fail 0, cancelled 0 |
| `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | exit 0; tests 58, pass 58, fail 0, cancelled 0 |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js` | PASS | exit 0; tests 19, pass 19, fail 0, cancelled 0 |
| `node --test src/test/js/*.test.js` | FAIL | exit 1; tests 689, pass 688, **fail 1** (unmatchedQaReplySource.test.js:28 "mounts the read-only AUTO_PREVIEW workbench host from source" — asserts the 3 identifiers I-4 retires), cancelled 0 — see Remaining Blocker |
| `node --check src/main/resources/static/app.js` | PASS | exit 0, no output |
| `node --check src/main/resources/static/task-modal-runtime.js` | PASS | exit 0, no output |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | FAIL (node goal only) | exit 1; Java/Kotlin `Tests run: 2693, Failures: 0, Errors: 0, Skipped: 4` (matches ledger baseline); build aborts in exec-maven-plugin `node-test` goal with the SAME single node failure above; `node --test` DID execute in the test phase (skipNodeTests not active) |
| `git diff --check` | PASS | exit 0, no output |

## Changed Files

- `src/main/resources/static/index.html` — T1-1/T1-2 (button relocation + S-1 skeleton), T3-1 (triad v9)
- `src/main/resources/static/app.js` — T2-1..T2-7 (auto-preview removal, teardown shrink)
- `src/main/resources/static/styles.css` — T1-3 (`.panel-head-actions` rule)
- `src/test/js/batchEntryRelocation.test.js` — T3-4 (I3-2 rewrite)
- `src/test/js/autoPreviewWorkbenchHost.test.js` — T3-5 (full retirement-guard rewrite)
- `src/test/js/trustReplyWorkbenchSharedMount.test.js` — T3-3 (1 line, 4→3)
- `src/test/js/batchSendTaskConsoleVisualFix.test.js` — T3-2 (3 lines, key asserts)
- `src/test/js/checkRepliesRelocation.test.js` — T3-6 (NEW; I-1/I-2/I-3/S-1/S-2 source-text assertions, node:test + fs per repo convention)

Guards: `trust-reply-workbench.js` byte-identical (0 diff lines vs bb34ca2); 0 Kotlin/Java changes; fast-p evidence excluded from commit.

## Deviations

- D-1 (T3-4, applied): the plan's literal escape regex `/[.*+?^${}()|[\\]\\\\]/g` + replacement `"\\\\$&"` carries one extra escaping layer versus the rest of the same snippet (`\\s*`, `\\(` are single-layer) and does NOT escape `(`, `)` — verified in node: the verbatim pattern does not match the S-1 skeleton, so the plan's own S-1 acceptance ("改写后的 I3-2 用例通过") would fail. Used the single-layer escapeRegExp identical to the original file's proven pattern `/[.*+?^${}()|[\]\\]/g` + `"\\$&"`; everything else (test name, assertions, structure) verbatim. Verified: pattern matches the S-1 skeleton (node, `matches skeleton: true`); 7/7 pass.
- D-2 (environment, informational): `grep -o '?v=[^"]*'` (plan I-3 acceptance) fails in this grep flavor because bare `?` is a repetition operator; escaped `grep -o '\?v=[^"]*'` outputs exactly one unique line (3 identical matches). Triad value itself verified correct via `grep -n '?v='`.
- D-3 (environment, informational): `scripts/worktree_identity.py` crashes on stale `/sessions/rcw-*` worktree entries (see Identity note); identity computed manually with identical semantics.

## Freshness

- Plan identity rechecked: YES (unchanged 6b111be9…)
- Worktree identity rechecked: YES (root/branch/HEAD/git-dir unchanged)
- Reported commits reachable from target branch: YES (9b90e41 is HEAD of fast/ui-tweaks-00-execution-order, ancestor chain bb34ca2→2cbf6d3→9b90e41)
- Required commands run this invocation: YES (all 10)
- Historical evidence used only as baseline: YES (ledger baseline 2693/0/0/4 + 680 JS pass used for comparison only)

## Remaining Blocker

`src/test/js/unmatchedQaReplySource.test.js` (:28-35, case "mounts the read-only AUTO_PREVIEW workbench host from source") asserts
`mountAutoPreviewTrustReply` / `data-trust-reply-auto-preview-host` / `data-auto-preview-status` MUST exist in app.js — the exact
identifiers I-4 mandates be retired. This file is NOT in the plan's 8-file authorized list and NOT in the plan's "现有测试对本次改动的直接约束（必须同步）" table, yet the plan's own required commands (`node --test src/test/js/*.test.js`, `mvn test`) and its K-source
(K-ui-removal-retires-obsolete-contract-tests) cannot pass without syncing it. Baseline at bb34ca2 was green (688 JS tests pass in this run, 680 at ledger baseline), so the failure is caused by this plan's deletion, not pre-existing.

Per execute-p scope rules ("Edit an unlisted implementation or test file" is prohibited; unlisted-file completion → PLAN_CONFLICT),
the fix was NOT self-authorized. Missing authority: human/controller approval to edit `src/test/js/unmatchedQaReplySource.test.js`
(retire the obsolete case: flip the 3 existence asserts to absence asserts per the plan's own I-4/T3-5 retirement-guard pattern, or drop the case; the other 7 cases stay). Once authorized this is a 1-case, 3-assert change.

## Next Action

- PLAN_CONFLICT → obtain human decision / plan amendment (authorize the single unlisted test file), then resume for a bounded fix round; or amend the plan to document the file in the change list.

# Child Brief — c6 · 05 可信工作台前端替换：正文 / 用到哪些事实 / 未识别的提问

- Plan: `docs/plans/2026-09-02/05-workbench-frontend-replace.md` (Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `db89054f32a51f79f4cc86f5b21a9871a8dac729` (c5 terminal code head; branch HEAD carries c5 evidence `4f11bc6`)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (identity `commit:92b0519a18a3a46989f8733259af4649f7748a72`)
- Design baseline: `docs/mockups/trust-workbench-rag.html` (界面基准; 样式契约 S-1..S-5 为准)
- c4 (03b) already implemented the adopt-side split: `adoptTrustReplyAssembly` branches on assembly shape (`usedFactCodes` → ragFactCodes context + preflight skip; `canonicalFactIds` → legacy). c6 only PRODUCES the new `onComplete` payload shape `{ text, usedFactCodes, ragCorpusFingerprint, unaddressed }` — it must NOT touch app.js adoption logic.

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 46cc5c46395814b1ef03e52ab8b8bfb5197f372c -- docs/plans/2026-09-02/05-workbench-frontend-replace.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order`. Follow execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Modify ONLY the 10 authorized files in the plan's `## 变更文件清单`. `app.js` is NOT in scope (row 3 = "—"). Nothing else. Do not touch `docs/plans/fast/**`, backend code of any kind, c4's app.js adoption logic, ReplySnippetService etc. When the plan says delete/replace CSS classes, run the residue grep first (S-5 + plan 验证命令 list the exact greps).
3. Preserve invariants I-24..I-29 and master globals G-1..G-8 exactly. What must NOT change 1-3 verbatim (mount contract; adopt→send chain; frame-snippet management).
4. Key mechanics:
   - I-24: rewritten `trust-reply-workbench.js` keeps idempotent IIFE + `window.TrustReplyWorkbench` + `mount(host, options)`/`unmount()`; options keys unchanged (mode/source/contextPath/autoBootstrap/onUnauthorized/onChange/onComplete). app.js mount call sites (mountAiTrainingTrustReply ~3941, mountLiveTrustReply ~10279), unmount site ~1664, requireTrustReplyWorkbenchRuntime ~197 unchanged.
   - I-25: per-instance requestSeq + AbortController set; unmount aborts in-flight + unbinds listeners; late responses never write to a newer instance's state.
   - I-26: verbatim styling ONLY from `bodyParagraphs[].renderMode` from `/api/rag-reply/compose` (never text-compare against answers; grep `answer ===` in trust-reply-workbench.js must be empty).
   - I-27: dirty=true → regenerate() must confirm first (visible dirty marker); cancel keeps edits.
   - I-28: addFact/removeFact only mutate forcedFactCodes/excludedFactCodes request params and re-call compose; never locally splice body paragraphs.
   - I-29: no hard gates on send (unaddressed count / REVIEW facts never disable send — only styling/note).
   - Single flow: compose() → render 3 blocks (draft/facts/unaddressed) → 4 operations (regenerate/add fact/remove fact/edit body). All requests to `/api/rag-reply/compose`; NEVER `/api/trust-reply/workbench/*`.
   - `onComplete(assembly)` payload = `{ text, usedFactCodes, ragCorpusFingerprint, unaddressed }` (c4's adopt split consumes it).
   - S-1 reuse c5's `--verbatim*` tokens + `.rag-badge` (do NOT redefine); S-2..S-4 verbatim CSS; `.trust-reply-send` background MUST be literal `rgba(255, 255, 255, .96)` + backdrop-filter blur(8px), never `var(--panel-bg)`; S-5 disposal table honored exactly (保留不改: .trust-reply-workbench, .trust-reply-busy-*, .trust-reply-readonly, .trust-reply-mode-note; 就地修改: .trust-reply-toolbar remove .ai-reply-model-row descendants; 删除: .trust-reply-factset*, .trust-reply-autorun*, .trust-reply-preanalysis, .trust-reply-autofilled, .trust-reply-gate-*). 04-era style baseline first: `git show HEAD:src/main/resources/static/styles.css | sed -n '5515,5625p;7393,7620p'` archived.
   - G-5: pre-grep FIRST; bump all three `?v=` keys in index.html AND EVERY pinning test file (this run's c5 learned: 4 files pin — batchSendTaskConsoleVisualFix.test.js:49-51, checkRepliesRelocation.test.js:11, manualReplySubjectPrefill.test.js:13, overlayAndDialogContrast.test.js:15 — but RE-GREP, the set may differ) to the single new value `20260902-rag-workbench`.
   - G-7: delete trustReplyWorkbench.test.js + trustReplyWorkbenchThreeStep.test.js; rewrite trustReplyWorkbenchSharedMount.test.js to ONLY the I-24 mount contract + cache-key triad + unmount-no-residue assertions; rewrite autoPreviewWorkbenchHost.test.js as needed (host container assertions); grep `trust-reply/workbench` in src/test/js must be empty after.
   - G-8: ragWorkbenchRender.test.js asserts `trust-reply-workbench.js?v=` in index.html source + the contract items (I-26/I-27/I-28/I-29/S-4 assertions per plan T5).

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# cache-key pre-grep (BEFORE index.html edits) + residue greps (S-5 deletes)
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/
grep -rn "trust-reply-factset" src/main/resources/static/ src/test/js/
grep -rn "trust-reply-autorun" src/main/resources/static/ src/test/js/
grep -rn "trust-reply-gate-list" src/main/resources/static/ src/test/js/
# new/rewritten frontend tests
node --test src/test/js/ragWorkbenchRender.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/autoPreviewWorkbenchHost.test.js
# frontend full + syntax
node --test src/test/js/*.test.js
node --check src/main/resources/static/trust-reply-workbench.js
node --check src/main/resources/static/app.js
# full regression gate + build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 `Failures: 0, Errors: 0`; `node --test` exit 0 `# fail 0`; `node --check` clean; residue greps empty after deletions; `git diff --check` clean. Record every command's exit code/counts in the execution report.

## Downstream interfaces (consumed by c8 — must not drift)

- `window.TrustReplyWorkbench.mount/unmount` contract (hosts in app.js unchanged).
- New `onComplete` payload shape `{ text, usedFactCodes, ragCorpusFingerprint, unaddressed }` — matches c4's adopt split exactly.
- No other backend interface changes; old `/api/trust-reply/workbench/*` endpoints remain but are no longer called (c8 retires them).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c6
```

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c6/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commit.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

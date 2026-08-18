# Child 02 Brief — 自动回复预览并入可信回复工作台

- Child ID: `02`
- Plan: `docs/plans/2026-08-18/02-preview-into-workbench.md` (sha256:0cdc88d7a7734adb2a6de6f3be89433bc1576d87db6cb8f5c3a8a146b433f15f)
- Master plan: `docs/plans/2026-08-18/00-auto-reply-convergence-master.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-reply-convergence`
- Branch: `fast/auto-reply-convergence`
- Base: `f867dd4e` (child 01 code head; child 01 LIGHT_PASS, evidence c96a60c)
- Plan identity: sha256:0cdc88d7a7734adb2a6de6f3be89433bc1576d87db6cb8f5c3a8a146b433f15f

## Contract

The approved plan file is the complete contract. Read it fully and implement every task T1–T5 exactly as specified. This brief adds only execution context; where this brief and the plan differ, the plan wins.

Use the `execute-p` skill against the plan path above.

## Authorized files (exactly these 6)

1. `src/main/resources/static/trust-reply-workbench.js`
2. `src/main/resources/static/app.js`
3. `src/main/resources/static/styles.css`
4. `src/test/js/unmatchedQaReplySource.test.js`
5. `src/test/js/trustReplyWorkbenchSharedMount.test.js`
6. `src/test/js/autoPreviewWorkbenchHost.test.js` (NEW file)

No other file may change. No other new files.

## Global invariants (master plan, binding on all children)

- **X-1**: `decide()` remains the ONLY shared decision point — exactly 2 production callers. (Child 01 landed; do not touch backend.)
- **X-2**: Preview stays counterfactual and read-only: no `@Transactional`, no `save`/`send`; runtime gates are `wouldBeBlockedBy` markers only, never hide the body. The `AUTO_PREVIEW` host must render the body regardless of gate list contents.
- **X-3**: `ANSWER_FROM_OPERATOR_INPUT` semantics unchanged.
- **X-4**: No per-item `generateItem()` pipeline restructuring. `AUTO_PREVIEW` shows the whole-body `decide()` output.
- This plan is PURE FRONTEND: backend endpoint `/api/mail/unmatched-inbound/{id}/auto-reply-preview`, `AutoReplyPreviewResponse` fields, and 01's `decide()` semantics are untouched.

## Child-specific invariants (must verify in code after change)

- **I-1**: `validateMount` uses explicit `MODE_SOURCE` mapping table (SIMULATION:TRAINING_MAIL, LIVE:LIVE_INBOUND, AUTO_PREVIEW:LIVE_INBOUND); the old `=== MODES.SIMULATION ?` ternary must be gone.
- **I-2**: `AUTO_PREVIEW` is read-only: no handling dropdown, no operatorInstruction input, no generate/adopt/lock/integrate buttons; `requestJson` fail-closed assertion blocks every path except `/bootstrap`; `onComplete` never fires.
- **I-3**: Gates are markers only — every `wouldBeBlockedBy` item rendered as `.trust-reply-gate-item`; body renders even when the array is non-empty (and when empty).
- **I-4**: `record.expertContactId == null` → never mount AUTO_PREVIEW; render the exact degraded static copy (`该来信尚未绑定专家联系人，无法解析自动回复上下文。请先在上方完成绑定。`).
- **I-5**: Old-UI contract tests in `unmatchedQaReplySource.test.js` rewritten (old function names / DOM ids gone; new DOM ids asserted present in source); new `autoPreviewWorkbenchHost.test.js` with the 4 specified cases.
- **S-1**: DOM skeleton matches the plan's verbatim skeleton; reuse listed classes only; NO inline styles; no new classes beyond the contract.
- **S-2**: `styles.css` gains exactly the 5 verbatim rule blocks from the plan (`.trust-reply-readonly`, `.trust-reply-readonly-banner`, `.trust-reply-gate-list`, `.trust-reply-gate-item`, `.trust-reply-gate-list:empty`), appended after `.compose-workbench-section`'s rule block; `display:none !important` kept verbatim.
- Unmount symmetry: `unmountAutoPreviewTrustReply` called at every `unmountLiveTrustReply` call site (or both folded into one `unmountMailboxTrustReplyHosts` used at all 8 sites).
- Dangling references: after T4 deletions, `grep -n "autoReplyPreview\|preview-auto-reply\|auto-reply-preview"` over `app.js`, `styles.css`, `index.html` must return NOTHING.

## Baseline (recorded, all green at 4583525 / pre-change)

- `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/unmatchedQaReplySource.test.js`: tests 58, pass 58, fail 0, duration 246ms.
- `node --check src/main/resources/static/app.js` and `trust-reply-workbench.js`: syntax OK.
- Dangling-identifier grep: 19 matches at baseline (must be 0 after T4).

## Required commands (run all; per plan's verification section)

```bash
node --test src/test/js/autoPreviewWorkbenchHost.test.js
node --test src/test/js/trustReplyWorkbenchSharedMount.test.js
node --test src/test/js/unmatchedQaReplySource.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
node --check src/main/resources/static/trust-reply-workbench.js
grep -n "autoReplyPreview\|preview-auto-reply\|auto-reply-preview" \
  src/main/resources/static/app.js src/main/resources/static/styles.css src/main/resources/static/index.html
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test          # full regression
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package # build
git diff --check
```

Pass criteria: `node --test` → `# fail 0`; `node --check` → no output, exit 0; the grep → no output (exit 1 is the expected "no matches"); `mvn` → exit 0 with `Failures: 0, Errors: 0` (full suite baseline ~2574/0/0/4, 4 skips = migrationIt-gated + permanent @Disabled, permitted).

## Downstream interfaces (consumed by child 03)

- `AUTO_PREVIEW` host renders body + gates; CRS score display is explicitly OUT of scope for 02 (03's preview shows scores later). No backend changes, so no Kotlin contract for 03.
- `autoPreviewWorkbenchHost.test.js` must remain green — 03 does not touch it.

## Deliverable

- Commit the implementation locally as `feat(fast-p): implement 02`. Exclude all fast-p artifacts (`docs/plans/fast/**`) from the commit.
- Append the full execution report to `docs/plans/fast/auto-reply-convergence/children/02/execution.md`: per-command exit codes and counts, files changed, file:line evidence for I-1..I-5 + S-1/S-2, grep outputs for acceptance criteria, any deviations. Do NOT commit it.
- Do not review child 03, repair unrelated behavior, push, merge, or rewrite history.

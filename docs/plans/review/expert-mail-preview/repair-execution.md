# Repair Execution — expert-mail-preview-main

## Identity

- Repair plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/fix/expert-mail-preview-main/repair.md`
- Repair plan SHA-256: `79ae2264c85fadd7ecd2b30693b4b4bfe09982e211a71f9a2688285f89da8d6e`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/fix/expert-mail-preview-main/repair.md@79ae2264c85fadd7ecd2b30693b4b4bfe09982e211a71f9a2688285f89da8d6e`
- Execution epoch: NEW
- Approval source: human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/fix/expert-mail-preview-main/repair.md` (2026-08-14), satisfying the plan's "Human Approval" gate and the "Review-Fast-P Execution Handoff" section.
- Executor: `RepairImplementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview`
- Target branch: `fast/expert-mail-preview`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview@fast/expert-mail-preview@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/expert-mail-preview`
- Pre-execution code SHA: `52e636700cb0e4cbc6cef0e7aae90ad059e4b2c3`
- Post-execution code SHA: `1859c5f0416b1326cbeabd690a5e2d2f86612b00` (product commit)
- Evidence HEAD: `1859c5f0416b1326cbeabd690a5e2d2f86612b00` until the docs evidence commit in this file's commit
- Boundary (from repair plan): `f3917cec4833199fcc9af5603e8630bb50590f9e..c2acd4fc1c2b1ec4d40a08db53b31bb44b28b77a`

## Repair Tasks

| Task | Resolves | Status | Files | Evidence |
|---|---|---|---|---|
| R-1 render server-provided expert-preview block labels | V-1 | IMPLEMENTED | `src/main/resources/static/app.js`; `src/test/js/expertMailPreviewTab.test.js` | New `data-role="mail-preview-blocks"` area inside the expert mail-preview panel; per block a `compose-block-pill` span whose textContent is the server `refDisplayName`; new regression test "renderExpertMailPreview exposes the server-provided refDisplayName for preview blocks (V-1)" passes on fixed code and fails on boundary head `c2acd4f`. |
| R-2 request the selected expert's deterministic body variant | V-2 | IMPLEMENTED | `src/main/resources/static/app.js`; `src/test/js/expertMailPreviewTab.test.js` | New `javaStringHashCode()` (Java `String.hashCode()` semantics: `h=(31*h+charCode)|0` over the trimmed ORCID); payload `variantIndex` now `javaStringHashCode(orcidId)`; new regression test "renderExpertMailPreview derives variantIndex from the trimmed ORCID via Java hashCode (V-2)" asserts `-2035179089` for ORCID `0000-0002` and fails on `c2acd4f` (hard-coded 0). |

## Changed Files

- `src/main/resources/static/app.js` — block-description area in panel; `javaStringHashCode` helper; `variantIndex` derivation; `result.blocks[].refDisplayName` pill rendering (text only, no client-side label derivation, no custom-text/body substitution).
- `src/test/js/expertMailPreviewTab.test.js` — `makePanel` block container capturing pills; helper extraction in existing sandboxes; two new regression tests (V-1, V-2).

## Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/expertMailPreviewTab.test.js src/test/js/replySnippetLabel.test.js` | PASS | Exit 0; 16 tests, 16 pass, 0 fail (incl. new V-1/V-2 tests). |
| `node --test src/test/js/expertMailPreviewTab.test.js` against `c2acd4f` app.js/styles.css (temp dir) | FAIL (expected) | New V-1/V-2 assertions fail on boundary head; proves tests are discriminating. |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | Exit 0; 63 tests, 63 pass, 0 fail. |
| `node --check src/main/resources/static/app.js` | PASS | Exit 0; syntax OK. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | FAIL (baseline only) | Exit 1. Kotlin: 2418 run, 0 failures, 0 errors, 4 skipped (`FlywayMigrationIntegrationTest` skipped 未执行（无 Docker）). Node stage (exec-maven-plugin `node --test src/test/js/*.test.js`): 520 tests, 518 pass, 2 fail — both pre-existing `batchManualExecutionLog.test.js` extraction failures (`ReferenceError: buildManualExecutionSnapshot is not defined`). No new failure. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | FAIL (baseline only) | Exit 1 at the same node-test stage; no WAR. Same two pre-existing failures only. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package -DskipNodeTests=true` | PASS | Exit 0; WAR built `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` (46124547 bytes). Recorded honestly: WAR only with `-DskipNodeTests=true`. |
| `git diff --check` | PASS | Exit 0; no output. |

## Deviations

- None. Scope stayed within the 2 Authorized Files; no Kotlin, CSS, index.html, cache-key, plan-text, ledger, or unrelated test changes.

## Clean-State Evidence

- Product commit `1859c5f0416b1326cbeabd690a5e2d2f86612b00` (`fix(fast-p): render expert mail preview block labels`) contains ONLY the 2 Authorized Files (77 insertions, 3 deletions) and is HEAD-anchored on `fast/expert-mail-preview` (parent `52e6367`).
- Docs-only evidence commit (this handoff, staged alone) subject `docs(review-fast-p): record repair execution`.
- `git status --porcelain` empty after both commits; worktree identity rechecked before each `git add`/`commit` with `--expect-root`/`--expect-branch`/`--expect-git-dir`; plan identity rechecked at start (SHA-256 above). No push/merge/amend/rebase performed.

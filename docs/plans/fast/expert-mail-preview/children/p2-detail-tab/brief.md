# Fast-P Child Brief — p2-detail-tab

- Master plan: `docs/plans/2026-08-14/expert-mail-preview-main.md` (commit 7a5dbdb) — **read first**
- Child plan: `docs/plans/2026-08-14/expert-mail-preview-p2-detail-tab.md` (commit 7a5dbdb) — **read fully; it is the binding contract**
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview`
- Branch: `fast/expert-mail-preview`
- Child base SHA: `15633f7a` (= p1 terminal Code head; p1 completed LIGHT_PASS_WITH_NOTES)
- Execution report: `docs/plans/fast/expert-mail-preview/children/p2-detail-tab/execution.md` (relative to worktree)
- Implementation commit (exact subject): `feat(fast-p): implement p2-detail-tab`

## Global constraints (from master plan — M-1..M-4, shared verification)

### Invariant M-1: static cache-key triad
`index.html` `styles.css?v=`, `trust-reply-workbench.js?v=`, `app.js?v=` must all equal ONE new value and change together; bump to `20260814-v10-expert-mail-preview-01` — MUST differ from p1's value (`20260814-v9-snippet-name-01`, current). Sync the three hardcoded assertions in `src/test/js/batchSendTaskConsoleVisualFix.test.js:37-39`.

### Invariant M-2: JS gate = `node --test <target file>`
Frontend acceptance runs `node --test` on specific files; `verify.sh` is NOT a gate; `mvn test` (exec-maven-plugin) is the full regression.

### Invariant M-3: rendering hosts verified in real DOM
P2's new `.detail-tab-panel[data-panel="mail-preview"]` host is the `app.js` template strings inside `showExpertDetail` and `loadContactDetail` — grep `app.js` for the generation sites (existence evidence = 2 occurrences, I-5). Test DOM stubs are NOT existence evidence.

### Invariant M-4: quantified claims need grep receipts
"两套详情面板" and call counts must come with grep output.

### Shared verification commands (JDK 11 mandatory — zulu-11)
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```
Pass = `mvn test` exit 0 `Tests run: N, Failures: 0, Errors: 0`; `mvn clean package` exit 0 with WAR; `git diff --check` no output. **Recorded baseline**: full `mvn test` / `mvn clean package` exit 1 ONLY on 2 pre-existing JS failures in `src/test/js/batchManualExecutionLog.test.js` (`ReferenceError: buildManualExecutionSnapshot is not defined`, reproduced at base, unrelated to both children; `FlywayMigrationIntegrationTest` skipped 未执行（无 Docker）). Expected: identical exit-1 cause only; WAR requires `-DskipNodeTests=true`.

## Authorized files (P2 — exactly these 5 + Phase 6 writeback; ZERO Kotlin changes)
1. `src/main/resources/static/app.js`
2. `src/main/resources/static/styles.css`
3. `src/main/resources/static/index.html`
4. `src/test/js/expertMailPreviewTab.test.js` (new)
5. `src/test/js/batchSendTaskConsoleVisualFix.test.js`

Plus, per child plan `## Phase 6 知识回写` (mandatory): update `docs/knowledge/mail/K-mail-body-display-sites.md` (add the new `class="pre"` body site, bump `last_used`/`hit_count`) and sync the one-line summary in root `CLAUDE.md`「团队沉淀知识」.

**Forbidden**: any `src/main/kotlin/**`; `trust-reply-workbench.js`; `trustReplyWorkbenchSharedMount.test.js`; `GET /api/compose-templates/{id}/preview`; preview drawer structure; `styles.css` rules other than the exact S-3/S-4 blocks; `.toolbar`; `.field-label`.

## Key invariants (child plan I-1..I-7 — read plan for full text)
- I-1: preview MUST come from `POST /api/compose-templates/preview-draft`; NO client-side `${...}` replacement; NOT `GET /{id}/preview`.
- I-2: new tab must ensure `state.composeTemplates` loaded idempotently (`ensureComposeTemplatesLoaded` + `loadComposeTemplates`), never assume it's populated.
- I-3: jump sequence: template data ready BEFORE `setView("mail-templates")` → `switchMailTemplatesSubTab("compose-templates")` → `openComposeTemplateEditor(template)` → write expert context → `await openComposeTemplatePreview()`. Missing template → `showStatus(...)` and return WITHOUT setView.
- I-4: expert context DOUBLE-write: `#previewComposeExpertInput.value` = `composeTemplatePreviewExpertLabel` format (`姓名 <邮箱>`) AND `state.previewDrawer.orcidId/contactId/expertEmail`.
- I-5: `.detail-tab-panel[data-panel="mail-preview"]` added in BOTH `showExpertDetail` and `loadContactDetail`; tab array entry once in `renderDetailSubTabs`.
- I-6: `strictPlaceholders: false` literal; `fallbackKeys` rendered as `兜底: KEY` badges.
- I-7: panel elements scoped via `data-role` + `panel.querySelector`; no global ids inside new panel.

## Style contract (S-1..S-4 — verbatim strings in plan)
S-1 tabs array entry; S-2 panel placeholder/loading/error/no-ORCID states; S-3 toolbar CSS blocks + DOM (reuse `.button small`, NOT `.toolbar`); S-4 subject/body/meta CSS + `.pre` body + `.badge warn` fallback badges. No inline styles; no new classes beyond the four declared rules.

## P1 upstream facts (downstream interface)
- `resolveRefDisplayName` and `resolveBlocks` (MailComposeTemplateService.kt) already produce meaningful snippet names (p1 done). P2's preview renders `blocks[].refDisplayName` as-is — display, no re-derivation.
- **Line numbers in the P2 plan are PRE-P1** (P1 inserted 13 lines into app.js at/after :2900). Re-locate every site by symbol/grep (`renderDetailSubTabs`, `showExpertDetail`, `loadContactDetail`, `activateDetailSubTab`, `loadComposeTemplates`, `composeTemplatePreviewExpertLabel`, `randomComposeTemplatePreviewExpert`, `renderServerComposeTemplatePreview`, `#contactDetail` delegate ~:10862, etc.), not by stale line numbers. M-4 grep receipts required.
- Knowledge files K-compose-template-preview-endpoint-split / K-compose-templates-state-scope / K-expert-detail-two-panel-render-sites exist only in the MAIN worktree (`/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/knowledge/...`) as untracked docs — read them there if needed; do NOT create/copy them into the fast worktree.

## Evidence rules
- Implementation commit contains ONLY authorized files above; never fast-p artifacts (`docs/plans/fast/**`, including the uncommitted modified ledger.md), which the controller commits.
- One implementation commit, exact subject above. No push/merge/amend/rebase/squash.
- Execute-p plan-identity and worktree-identity gates: plan file `docs/plans/2026-08-14/expert-mail-preview-p2-detail-tab.md` in the worktree, plan SHA-256 `1e6257ca205734d7dc5f0329427b6712c56c4f6088ea98a17351a8035fdb2089`.
- Return exactly: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

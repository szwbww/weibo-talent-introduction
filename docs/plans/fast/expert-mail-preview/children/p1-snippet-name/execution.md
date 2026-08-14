# Execution Report — p1-snippet-name

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/2026-08-14/expert-mail-preview-p1-snippet-name.md
Plan SHA-256: 467b4adea09b428c8c4aa26a9a850e903cb85cacc468804c1ff9871086bf20c4
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/2026-08-14/expert-mail-preview-p1-snippet-name.md@467b4adea09b428c8c4aa26a9a850e903cb85cacc468804c1ff9871086bf20c4
Execution epoch: NEW (ledger attempt 0, all prior logs empty)
Approval basis: current invocation (fast-p master ledger, children brief.md + binding child plan)
Executor: P1Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview
Target branch: fast/expert-mail-preview
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview@fast/expert-mail-preview@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/expert-mail-preview
Pre-execution code SHA: 7a5dbdbe3dc73b968711bd418b6aa06f36c3f5ce
Post-execution code SHA: 15633f7a159a8ce898aa76ff0810ca847c0e6410
Evidence HEAD: N/A (plan requires one implementation commit; no separate evidence commit)
Implementation boundary: 7a5dbdb..15633f7

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| A-1 V96 migration | IMPLEMENTED | src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql | `${` count 0; placeholder-replacement: false untouched (I-5) |
| A-2 ReplySnippet.name | IMPLEMENTED | src/main/kotlin/.../reply/domain/ReplySnippet.kt | `val name: String? = null` after variantGroup |
| B-1 service commands + create/update | IMPLEMENTED | ReplySnippetService.kt | 4 `name` hits: 2 commands, create constructor, update copy (I-3, I-4) |
| B-2 controller DTO 7 layers | IMPLEMENTED | ReplySnippetController.kt | 6 `name` hits: Create/Update Request, toCommand×2, Response, toResponse (I-3) |
| B-3 resolveRefDisplayName 3-tier + snippetContentExcerpt + EXCERPT_MAX_CHARS | IMPLEMENTED | MailComposeTemplateService.kt | 3-tier fallback verbatim; excerpt fn; `const val EXCERPT_MAX_CHARS = 40` |
| B-3a (plan-gap) resolveBlocks preview displayName site | IMPLEMENTED | MailComposeTemplateService.kt | See Deviations D-1 |
| C-1 index.html name input + cache triad | IMPLEMENTED | src/main/resources/static/index.html | S-1 label verbatim; 3 keys = `20260814-v9-snippet-name-01` (M-1); `name="name"` count 1 (M-3) |
| C-2 app.js label fn + dropdown + table + form | IMPLEMENTED | src/main/resources/static/app.js | diff reviewed: S-2/S-3 verbatim; colspan 7/6; dropdown only label line changed |
| D-1 ReplySnippetServiceTest 5 cases | IMPLEMENTED | src/test/kotlin/.../ReplySnippetServiceTest.kt | 24 tests run, 0 fail (19 base + 5 new) |
| D-2 replySnippetLabel.test.js | IMPLEMENTED | src/test/js/replySnippetLabel.test.js | node --test 6 pass / 0 fail, incl. EXCERPT_MAX_CHARS cross-source assertion |
| D-3 cache-key assertions sync | IMPLEMENTED | src/test/js/batchSendTaskConsoleVisualFix.test.js | 63 pass / 0 fail with trustReplyWorkbenchSharedMount.test.js |
| Phase 6 K-spring-data-jdbc-null-default writeback | IMPLEMENTED | docs/knowledge/mail/K-spring-data-jdbc-null-default.md | appended updated_at explicit-write-back implication |
| Phase 6 K-variant-pool-dto-chain (conditional) | NOT NEEDED | — | 7-layer checklist complete; no 8th missing point discovered (see I-3 grep) |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ReplySnippetServiceTest` | PARTIAL (Kotlin PASS) | Tests run: 24, Failures: 0, Errors: 0, Skipped: 0. mvn exit 1 — only exec node-test step fails (2 pre-existing JS, see below) |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=MailComposeTemplateServiceTest` | PARTIAL (Kotlin PASS) | Tests run: 37, Failures: 0, Errors: 0, Skipped: 0. mvn exit 1 — same 2 pre-existing JS failures |
| `node --test src/test/js/replySnippetLabel.test.js` | PASS | exit 0; tests 6, pass 6, fail 0 |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | exit 0; tests 63, pass 63, fail 0 |
| `node --test src/test/js/*.test.js` (pom exec-maven-plugin glob, M-2) | PARTIAL | tests 510, pass 508, fail 2 — both pre-existing in batchManualExecutionLog.test.js |
| `JAVA_HOME=...zulu-11... mvn test` (full regression) | PARTIAL (Kotlin PASS, JS 2 pre-existing fail) | Kotlin aggregate 2418 run / 0 fail / 0 err / 4 skipped (187 classes); FlywayMigrationIntegrationTest skipped → 未执行（无 Docker）. mvn exit 1 — same 2 pre-existing JS failures |
| `JAVA_HOME=...zulu-11... mvn clean package` | PARTIAL | exit 1 — test phase exec node-test step fails on the same 2 pre-existing JS tests; no WAR produced |
| `JAVA_HOME=...zulu-11... mvn clean package -DskipNodeTests=true` (supplementary, not a required command) | PASS | exit 0; BUILD SUCCESS; WAR produced: target/weibo-talent-introduction-1.0.0-SNAPSHOT.war (46,123,011 bytes) |
| `git diff --check` | PASS | exit 0, no output |
| `node --check src/main/resources/static/app.js` (pom node-check-app step) | PASS | syntax OK |

### Changed Files
- src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql — new; ADD COLUMN name VARCHAR(120) NULL (no `${`)
- src/main/kotlin/com/weibo/talentintroduction/reply/domain/ReplySnippet.kt — `name: String? = null`
- src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt — Request×2 / toCommand×2 / Response / toResponse
- src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt — Command×2 / create constructor / update copy (normalized `command.name?.trim()?.takeIf { it.isNotBlank() }`)
- src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt — resolveRefDisplayName REPLY_SNIPPET 3-tier fallback + snippetContentExcerpt + EXCERPT_MAX_CHARS=40 + resolveBlocks preview displayName site
- src/main/resources/static/index.html — S-1 name input + cache-key triad bump (M-1)
- src/main/resources/static/app.js — replySnippetDisplayLabel + dropdown label + table name column/header/colspan + form fill/save
- src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt — 5 new cases + helper `name` param
- src/test/js/replySnippetLabel.test.js — new; 6 assertions incl. EXCERPT_MAX_CHARS cross-source equivalence
- src/test/js/batchSendTaskConsoleVisualFix.test.js — 3 cache-key assertions → v9 value
- docs/knowledge/mail/K-spring-data-jdbc-null-default.md — Phase 6 writeback (updated_at explicit write-back implication)

### Acceptance greps (M-4 receipts)

I-1 — display-label sites after change:
```
$ grep -rn 'snippetType} #' src/main/resources/static/app.js src/main/kotlin
src/main/kotlin/.../template/service/MailComposeTemplateService.kt:386:  ?: "${snippet.snippetType} #${snippet.id}"   (resolveRefDisplayName tier-3 defensive)
src/main/kotlin/.../template/service/MailComposeTemplateService.kt:507:  ?: "${snippet.snippetType} #${snippet.id}"   (resolveBlocks tier-3 defensive)
```
Only tier-3 defensive branches remain (both unreachable in practice: `content` is TEXT NOT NULL and create/update require non-blank). app.js has no `snippetType} #`.

I-2 — frame logic name-free: `frameSlotIdentity` (:162-174) reads only `slot.name` (enum, pre-existing), `id/snippetType/enabled/updatedAt/sha256(content)`; `frameVersion`/`resolveFrameSlot`/`listSelectableFrameOptions` contain no snippet `name`. Unit test `name does not affect frame version` passes.

I-3 — 7-layer `name` grep: ReplySnippetController.kt 6 hits (CreateRequest:65, toCommand:76, UpdateRequest:87, toCommand:97, Response:110, toResponse:126); ReplySnippetService.kt 4 hits (CreateCommand:388, UpdateCommand:398, create constructor:201, update copy:238). All 7 layers present; no 8th missing point → K-variant-pool-dto-chain.md NOT updated (conditional not met).

I-4 — `create normalizes blank name to null` and `update clears name when blank` pass.

I-5 — `grep -c '\${' V96__add_name_to_reply_snippet.sql` → 0; `grep -n "placeholder-replacement" src/main/resources/application.yml` → `13: placeholder-replacement: false` (file untouched).

M-1 — `grep -o 'v=[^"]*' index.html | sort -u` → exactly `v=20260814-v9-snippet-name-01`; batchSendTaskConsoleVisualFix.test.js:36-39 asserts the same 3 keys.

M-3 — `grep -c 'name="name"' index.html` → 1 (host is index.html static structure).

### Deviations
- **D-1 (plan-gap completion, within authorized file)**: the plan's audit claimed "共 2 处实现" of the snippet display label, but M-4-mandated grep found a third site the plan missed: `MailComposeTemplateService.resolveBlocks()` builds `ComposeTemplatePreviewBlock.refDisplayName` inline at former :491 (`"${snippet.snippetType} #${snippet.id}"`). This feeds the preview panel at app.js:8248, which the plan's observable outcome 3 ("预览里的块说明，与下拉显示同一个名字"), the I-1 acceptance grep (no `snippetType} #` besides the tier-3 defensive branch), the master-plan cross-check ("P2 邮件预览面板的块说明…逐字相同"), and the brief's downstream interface ("ComposeTemplatePreviewBlock.refDisplayName … no rework allowed") all require to show meaningful names. Without it, every one of those acceptance criteria fails. Fixed with the plan's verbatim 3-tier algorithm (`snippet.name?.takeIf { it.isNotBlank() } ?: snippetContentExcerpt(snippet.content) ?: "${snippet.snippetType} #${snippet.id}"`), reusing the already-loaded `snippet` (no extra query). Same file, same decided behavior — not a new behavioral decision. Existing MailComposeTemplateServiceTest (unmodified) still green (37/0/0); no existing test asserted the old preview display name.
- **D-2 (pre-existing failures, out of scope, not repaired)**: `src/test/js/batchManualExecutionLog.test.js` has 2 failing tests (`confirmManualExecution without source calls openBatchExecutionLogs (I-6)` and `...with source still calls openBatchConfigLogs(source.id) (I-6 regression)`) with `ReferenceError: buildManualExecutionSnapshot is not defined` — the test extracts only `confirmManualExecution` but never loads its `buildManualExecutionSnapshot` dependency. Proven pre-existing at base: `confirmManualExecution` is byte-identical to base commit 7a5dbdb (diff empty), and the same test file run against base app.js fails identically (17 tests, 15 pass, 2 fail). Unrelated to this child (batch manual execution feature; file not authorized; fixing would be "repair unrelated behavior", explicitly forbidden). Therefore the required full `mvn test` / `mvn clean package` gates exit 1 on this baseline noise; the WAR is proven buildable via supplementary `mvn clean package -DskipNodeTests=true` (exit 0, WAR produced). verify-p should treat these 2 failures as baseline, not P1 regressions.
- No other deviations. styles.css / trust-reply-workbench.js / trustReplyWorkbenchSharedMount.test.js untouched (forbidden list honored).

### Freshness
- Plan identity rechecked: YES (SHA-256 unchanged 467b4ade…)
- Worktree identity rechecked: YES (root/branch/git-dir matched with --expect flags)
- Reported commits reachable from target branch: YES (15633f7 is HEAD of fast/expert-mail-preview; `git merge-base --is-ancestor` OK)
- Required commands run this invocation: YES (all freshly; the 2 mvn invocations exit 1 solely on proven pre-existing JS failures)
- Historical evidence used only as baseline: YES (base-comparison of batchManualExecutionLog failure via /tmp/basejs reproduction)

### Remaining Blocker
- None in-scope. Pre-existing baseline: 2 JS failures in batchManualExecutionLog.test.js (proven identical at base 7a5dbdb; fix requires unauthorized file → deferred, verify-p to treat as baseline).

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`

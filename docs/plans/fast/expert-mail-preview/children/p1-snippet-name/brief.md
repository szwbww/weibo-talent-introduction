# Fast-P Child Brief — p1-snippet-name

- Master plan: `docs/plans/2026-08-14/expert-mail-preview-main.md` (commit 7a5dbdb) — **read first**
- Child plan: `docs/plans/2026-08-14/expert-mail-preview-p1-snippet-name.md` (commit 7a5dbdb) — **read fully; it is the binding contract**
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview`
- Branch: `fast/expert-mail-preview`
- Child base SHA: `7a5dbdb`
- Execution report: `docs/plans/fast/expert-mail-preview/children/p1-snippet-name/execution.md` (relative to worktree)
- Implementation commit (exact subject): `feat(fast-p): implement p1-snippet-name`

## Global constraints (from master plan — M-1..M-4, shared verification)

### Invariant M-1: static cache-key triad
`index.html` `styles.css?v=`, `trust-reply-workbench.js?v=`, `app.js?v=` must all equal ONE new value and change together; bump to `20260814-v9-snippet-name-01` (P2 bumps separately later, different value). Must also sync the three hardcoded string assertions in `src/test/js/batchSendTaskConsoleVisualFix.test.js:37-39`. Current value: `20260814-v8-expert-layout-default-01`.

### Invariant M-2: JS gate = `node --test <target file>`
Frontend acceptance runs `node --test` on the specific test file(s). `verify.sh` is NOT a gate (it only runs `normalizeDiscoveryResultSummary.test.js`). `mvn test` runs the full JS suite via exec-maven-plugin and is the full regression.

### Invariant M-3: rendering hosts must be verified in real DOM
Every "grab element by id/selector and write" render function must be checked against its real host: `index.html` static structure (grep `index.html`) or `app.js` template-string generation. Test DOM stubs always return elements — a green test is NOT existence evidence. P1's new snippet-name input host is `index.html` (grep `name="name"`).

### Invariant M-4: quantified claims need grep receipts
Any "N call sites", "only this one", "no other write path" claim must come with the actual grep command and output. Applies to P1's "display name 2 implementations" audit.

### Shared verification commands (JDK 11 mandatory — zulu-11; bare `mvn` fails)
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```
Pass = `mvn test` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; `mvn clean package` exit 0 with WAR produced; `git diff --check` no output. Child-local commands: see child plan `## 验证命令`.

## Authorized files (P1 — exactly these 10; anything else is out of scope)
1. `src/main/resources/db/migration/V96__add_name_to_reply_snippet.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/reply/domain/ReplySnippet.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
6. `src/main/resources/static/index.html`
7. `src/main/resources/static/app.js`
8. `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt`
9. `src/test/js/replySnippetLabel.test.js` (new)
10. `src/test/js/batchSendTaskConsoleVisualFix.test.js`

Plus, per child plan `## Phase 6 知识回写` (mandatory): update `docs/knowledge/mail/K-spring-data-jdbc-null-default.md` (append the `updated_at`-explicit-write-back implication; if the update-copy test disproves it, the plan says correct the knowledge AND the I-2 argument, and re-evaluate A-8 — do not only patch a test assertion) and, conditionally, `docs/knowledge/template/K-variant-pool-dto-chain.md` (only if the 7-layer checklist proves incomplete).

**Forbidden** (from master plan): `styles.css`; `trust-reply-workbench.js`; `trustReplyWorkbenchSharedMount.test.js`; any change to `GET /api/compose-templates/{id}/preview`; `ReplyFrameOption`; QA `displayName` semantics; `variantGroup`; subject variants; name uniqueness/search; `docs/introduction-mail-template-v2.md`.

## Key invariants (child plan I-1..I-5 — read plan for full text)
- I-1: display-label algorithm identical in BOTH implementations (`app.js` `replySnippetDisplayLabel` + `MailComposeTemplateService.resolveRefDisplayName` REPLY_SNIPPET branch): `name` (trimmed, non-blank) → else first non-empty trimmed content line, first 40 chars + `…` if longer → else `<snippetType> #<id>`. `EXCERPT_MAX_CHARS = 40` in the existing companion object.
- I-2: `name` never enters frame resolution/version logic; `frameSlotIdentity` inputs unchanged (no name). Renaming must NOT change `frameVersion` (update goes through `existing.copy`, `updated_at` explicitly written back → no MySQL `ON UPDATE`).
- I-3: `name` threads all 7 DTO layers; `update()` must explicitly pass normalized `name` (copy() otherwise keeps old value; clearing the name must persist).
- I-4: blank/whitespace name normalizes to `null` (`command.name?.trim()?.takeIf { it.isNotBlank() }`), same as `variantGroup`.
- I-5: migration `V96` contains no `${`; do not touch `application.yml` flyway `placeholder-replacement: false`.

## Downstream interface (for p2-detail-tab)
`ComposeTemplatePreviewBlock.refDisplayName` (produced by `resolveRefDisplayName`) must render meaningful snippet names for REPLY_SNIPPET blocks after this child. P2's preview panel displays it directly — no rework allowed.

## Evidence rules
- Implementation commit contains ONLY authorized files above; never fast-p artifacts (`docs/plans/fast/**`), which stay untracked for the controller to commit.
- One implementation commit with exact subject above. No push/merge/amend/rebase/squash.
- Execute-p plan-identity and worktree-identity gates: plan file at `docs/plans/2026-08-14/expert-mail-preview-p1-snippet-name.md` in the worktree, plan SHA-256 `467b4adea09b428c8c4aa26a9a850e903cb85cacc468804c1ff9871086bf20c4`.
- Return exactly: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

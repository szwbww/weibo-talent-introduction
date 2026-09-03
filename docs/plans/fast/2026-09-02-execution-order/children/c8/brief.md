# Child Brief — c8 · 07 旧链路入口下线：旧 QA 页摘除、`qa_rule` 转只读、旧工作台端点摘除

- Plan: `docs/plans/2026-09-02/07-legacy-entry-retire.md` (Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `e76b5a9f49c866afffbccdffcae78443cb16cab3` (c7 terminal code head; branch HEAD carries c7 evidence `56e74a4`)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (identity `commit:92b0519a18a3a46989f8733259af4649f7748a72`)
- Depends on c5 (04), c6 (05), c7 (06) — all terminal LIGHT_PASS/LIGHT_PASS_WITH_NOTES. MUST run last.

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 46cc5c46395814b1ef03e52ab8b8bfb5197f372c -- docs/plans/2026-09-02/07-legacy-entry-retire.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order`. Follow execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Authorized files: plan's rows 1-7 (QaRuleManagementController.kt, TrustReplyWorkbenchController.kt, TrustReplyWorkbenchControllerTest.kt, app.js, index.html, batchSendTaskConsoleVisualFix.test.js, QaRuleReadOnlyTest.kt NEW) + rows 8-10 filled by the T1 grep results (obsolete QA-table contract tests: delete/rewrite per G-7; actual set determined by grep — may exceed 3 → SPLIT per plan: if >3 hit files would exceed 10, do T2+T7 backend stop-writes FIRST in one commit, then T3..T6 frontend/endpoint cleanup in a SECOND commit, and state the split in execution.md). Plus G-5 cache-key pin syncs (master-G-5 authority, single-string only — re-grep the current pin set; it was 7 files at c7: batchSendTaskConsoleVisualFix, checkRepliesRelocation, manualReplySubjectPrefill, overlayAndDialogContrast, ragKnowledgeBasePage, ragWorkbenchRender, trustReplyWorkbenchSharedMount — but c8's own deletions may remove some; sync the survivors). Anything else: STOP and ask.
3. Preserve invariants I-35..I-38 and master globals G-1..G-8 exactly. What must NOT change 1-6 verbatim (qa_rule/qa_category structure+data; mail_record_qa_rule read/write; inbound_mail_tag auto+manual tagging; MailComposeTemplateService QA_RULE block; MailMonitoringService display name; new chain behavior).
4. Key mechanics:
   - I-35: NO DELETE FROM qa_rule / DROP / TRUNCATE anywhere; NO new migration file at all (this plan has none); stop-writes ONLY via controller 403s.
   - T2: the SEVEN write endpoints in QaRuleManagementController (~:96/:100/:104 categories + :112/:116/:123/:127 rules) throw `ResponseStatusException(HttpStatus.FORBIDDEN, "QA_RULE_READ_ONLY")` at method top; signatures/routes KEPT (403 not 404); GET /rules (:108) and all read endpoints UNTOUCHED (I-36); QaRuleManagementService write methods KEPT (D-10).
   - T3: delete the NINE `/api/trust-reply/workbench/*` endpoint methods (bootstrap / generations/stream / generations/{id}/cancel / assemble / rearrange / state PUT / state/item PATCH / state DELETE / state/reset) + now-unused DTOs/toDomain() + unused service injections in TrustReplyWorkbenchController.kt; if the class ends up empty → delete the whole file. Delete TrustReplyWorkbenchControllerTest.kt (I-38). Do NOT touch TrustReplyWorkbenchService / AiReplyGenerationCoordinator / their 4 service tests (D-10).
   - T4: delete app.js renderAiTrainingQaPager/renderAiTrainingQaTable/loadAiTrainingQa + their internal-only helpers + stale state.aiTraining QA-table fields; index.html: grep aiTabQa/data-tab="qa" residue (c5 should have left none) + three cache keys; per-function grep before each deletion (G-8: no dangling refs).
   - T5: retire contract tests per T1 grep list (delete or rewrite; rewrite only if the file also asserts live features).
   - T7: QaRuleReadOnlyTest (Kotlin): 7 write endpoints → 403 with body code QA_RULE_READ_ONLY; GET /api/qa/rules → 200 with non-empty list (I-36; mock service for the GET, docker-free).
   - G-5: pre-grep; bump three ?v= keys + every surviving pinning test file to single value `20260902-legacy-retire`.
   - styles.css diff MUST be empty (S-1: no new/modified CSS blocks; class deletion only with per-class grep evidence — prefer zero CSS changes).
   - app.js/index.html DO get touched → G-5 bump applies (this is the c8 bump).
   - Env: this plan needs no migrations → no Flyway IT involvement expected; plain suite docker-free green; QaRuleReadOnlyTest mock-based.
   - Read-path regression names to confirm on disk (find src/test/kotlin -iname '*InboundMailTag*' -o -iname '*MailComposeTemplate*'); run the actually-existing classes.

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# T1 pre-greps (BEFORE edits; record output — they fill authorized rows 8-10)
grep -rn "trust-reply/workbench" src/main/resources/static/ src/test/
grep -n "aiTabQa\|data-tab=\"qa\"" src/main/resources/static/index.html
grep -rln "loadAiTrainingQa\|renderAiTrainingQaTable\|aiTabQa" src/test/js/
grep -rln "/api/qa/rules" src/test/
# cache-key pre-grep
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/
# new test + read-path regressions
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaRuleReadOnlyTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=<InboundMailTagServiceTest actual name>
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=<MailComposeTemplateServiceTest actual name>
# frontend full + syntax
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
# full gate + build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# I-35/I-36/S-1/G-5/I-37/I-38 greps
git diff --stat src/main/resources/static/styles.css          # must be empty
git diff | grep -iE "DELETE FROM qa_rule|DROP TABLE qa_rule|TRUNCATE"   # must be empty
grep -rn "loadAiTrainingQa\|renderAiTrainingQaTable\|aiTabQa" src/main/resources/static/ src/test/js/   # must be empty
grep -rn "trust-reply/workbench" src/main src/test            # must be empty
grep -rn "20260902-rag-prompt-console" src/                    # must be empty (old key fully gone)
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 `Failures: 0, Errors: 0`; `node --test` exit 0 `# fail 0`; `node --check` clean; all greps as specified; `git diff --check` clean. Record every command's exit code/counts in the execution report.

## Downstream interfaces (post-run state)

- `/api/qa` write endpoints → 403 QA_RULE_READ_ONLY (routes alive); GET /rules reads normally.
- `/api/trust-reply/workbench/*` gone (404); frontend has no callers (c6 rewrote it).
- New chain (rag tables, compose, send bridge, KB page, prompt console, workbench) fully wired and untouched by c8.
- No new migrations; qa_rule/qa_category data intact; mail_record_qa_rule/inbound_mail_tag/template QA_RULE block/monitoring display all functional (X-2/X-3/X-4/X-5 registered for future plans).

## Commit

If no split: ONE local implementation commit. If the T1 grep forces the plan's split rule: TWO commits in order —
```text
feat(fast-p): implement c8 backend stop-writes
feat(fast-p): implement c8 endpoint and frontend cleanup
```
(no fast-p files, no evidence; state split + file allocation in execution.md).

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c8/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commits.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA(s), command summary, report path.

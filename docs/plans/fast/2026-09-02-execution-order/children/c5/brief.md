# Child Brief — c5 · 04 RAG 知识库管理页（替换「QA 知识库」子 Tab）

- Plan: `docs/plans/2026-09-02/04-rag-knowledge-base-page.md` (Plan identity: `commit:92b0519a18a3a46989f8733259af4649f7748a72` — amended by A1: fingerprint constant now `e62421a42c432cf3`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `ae755c417d2be4cecda52c5adf20a7f52227a072` (c4 terminal code head; branch HEAD carries c4 evidence `f72dc18`)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (identity `commit:92b0519a18a3a46989f8733259af4649f7748a72`)
- Design baseline: `docs/mockups/rag-knowledge-base.html` (界面基准, not style contract — 样式契约 S-1..S-6 为准)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 92b0519a18a3a46989f8733259af4649f7748a72 -- docs/plans/2026-09-02/04-rag-knowledge-base-page.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order`. Follow execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Modify ONLY the 9 authorized files in the plan's `## 变更文件清单`. Nothing else. Do not touch `docs/plans/fast/**`, `qa_rule`/`qa_category` or `/api/qa/*` behavior, migrations V1..V113 (new V114 only), c1-c4 committed rag/mail files (read-only consumers: `RagKnowledgeBase.republish()`/`fingerprint()`/`snapshot()` from c1; audit writes ride inside the same republish transaction), `TrustReplyWorkbenchService`/`AiReplyDraftService`/old QA backend.
3. Preserve invariants I-20..I-23 and master globals G-1..G-8 exactly. What must NOT change 1-3 verbatim. S-1..S-6 CSS verbatim (grep-asserted by tests); 样式契约禁止 inline style and undeclared classes.
4. Key mechanics:
   - I-20: ALL rag_fact writes via `ragKnowledgeBase.republish { repository.save(...); auditService.record(...) }` — never call `verifyAndPublish()` from admin code (P0-3); return the NEW fingerprint; snapshot republished post-commit by republish (c1 I-3b). Two consecutive edits must both succeed (acceptance).
   - I-21: per-field `rag_fact_audit` rows inside the SAME transaction: fact_code/field/old_value/new_value/fingerprint_before (pre-republish snapshot fingerprint)/fingerprint_after (republish's new value)/operator. `answer` old/new = FULL text. NEVER reuse `QaRuleAuditService` (G-4). Audit table V114 per plan T0 (no FK to rag_fact; KEY idx on fact_code+id and fingerprint_after; comment records the 03b closure path).
   - I-22: fact_code/area/seq/legacy_rule_id read-only — server IGNORES (not rejects) those inputs; DB values win.
   - I-23: NO create/delete endpoints (grep: no DeleteMapping; no POST "" create).
   - G-6 three-point subtab sync: index.html button `data-tab="ragKb"` (was qa, text 现「RAG 知识库」), panel id `aiTabRagKb` (content = S-2 skeleton), app.js whitelist chain `(tab === "ragKb" && panelId === "aiTabRagKb")` replacing the qa entry — all three or the button does nothing.
   - T3: add loadRagKb/renderRagKbFilters/renderRagKbList/renderRagKbDetail/saveRagFact; swap loadAiTrainingQa() → loadRagKb() inside loadAiTraining()'s Promise.all. KEEP renderAiTrainingQaPager/Table/loadAiTrainingQa functions (unused; c8 removes them + their tests per G-7).
   - G-5 cache-key bump: run the pre-grep command FIRST (`grep -rn "v=$(grep -o 'styles.css?v=[^"]*' index.html | cut -d= -f3)" src/test/js/`); bump all three `?v=` keys in index.html AND the pin test batchSendTaskConsoleVisualFix.test.js:49-51 to a single new value `20260902-rag-knowledge-base`. (Note: c4 did NOT bump — current keys may still be `20260902-monitoring-window` or whatever the last bump set; grep to confirm.)
   - G-8: any render function taking elements by id MUST be covered by a test asserting those ids exist in index.html source text.
   - Fingerprint in page header + acceptance A-1/A-2: seed value is `e62421a42c432cf3` (A1), displayed in the KB page header; after edit it changes; revert restores it.
   - Env: V114 migration — FlywayMigrationIntegrationTest fresh-chain env-blocked (V82 pre-existing + docker API mismatch, base-reproduced; precedent c1/c4); verify V114 via scratch patched chain (V1..V81+alignment+V82..V113+V114) table/columns/audit-write behavior. RagFactAdminServiceTest needs a real MySQL (republish/audit transaction semantics) — reuse c1's mechanism (migrationIt-gated test class with external-DB mode; run against scratch patched chain; plain mvn test must stay docker-free green).

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# cache-key pre-grep (BEFORE touching index.html)
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/
# new tests
node --test src/test/js/ragKnowledgeBasePage.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagFactAdminServiceTest
# frontend full + syntax
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
# full regression gate + build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene + contract diffs
git diff --check
```

Pass criteria: every `mvn` exit 0 `Failures: 0, Errors: 0`; `node --test` exit 0 `# fail 0`; `node --check` clean; `git diff --check` clean. Record every command's exit code/counts in the execution report.

## Downstream interfaces (consumed by c8 and the 04 human acceptance — must not drift)

- `rag_fact_audit` (V114) shape + the fingerprint_before/after closure path with c4's `mail_record_rag_fact.corpus_fingerprint` (I-21; c8/audit UI reads may come later).
- `RagFactAdminService.list()/update(factCode, dto, operator)/toggleEnabled(...)`; `RagFactAdminController` `/api/rag/facts` GET/PUT/enable/disable only.
- Frontend: `aiTabRagKb` subtab, `loadRagKb()` replaces QA table loader; cache-key value `20260902-rag-knowledge-base` (c6/c7 will re-bump with their own values later — do NOT preempt).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c5
```

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c5/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commit.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

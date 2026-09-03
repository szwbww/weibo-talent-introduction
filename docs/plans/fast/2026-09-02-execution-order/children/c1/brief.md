# Child Brief — c1 · 01 RAG 知识库数据层：V112 五张表 + 45 条种子 + 快照与指纹

- Plan: `docs/plans/2026-09-02/01-rag-knowledge-base-schema.md` (Plan identity: `commit:92b0519a18a3a46989f8733259af4649f7748a72` — amended by A1/A2)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `bbf08287d91bd7a540401bfe71c8dc8baecd34f3` (branch HEAD carries plan seed `46cc5c4` + amendments `92b0519`; product tree at implement commit)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 92b0519a18a3a46989f8733259af4649f7748a72 -- docs/plans/2026-09-02/01-rag-knowledge-base-schema.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order` (check with `git -C <worktree> rev-parse --abbrev-ref HEAD`). Then implement per execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Modify ONLY the 11 authorized files listed in the plan's `## 变更文件清单` (10 original + #11 `FlywayMigrationIntegrationTest.kt` added by amendment A2). Nothing else. Do not touch `docs/plans/fast/**` (fast-p evidence; the controller commits it separately). Do not touch `qa_rule`/`qa_category` or any existing migration (What must NOT change 1-3).
3. Preserve every invariant I-1..I-6 and the master-plan globals G-1 (fact_code is the only business key; auto-increment `id` never enters prompts/responses/audit), G-2 (corpus fingerprint `e62421a42c432cf3` is a startup gate; A1 canonical algorithm = fact_code ascending, V112 data columns joined '|' per row, rows joined '\n', SHA-256 hex[:16]), G-3 (only `answer` is outward text; `title` never enters any prompt or outward text), G-4 (zero runtime coupling with `qa_rule`; `legacy_rule_id` read-only reconciliation only), G-9 (this migration is V112 — first of the four in deploy order).
4. Decision D-3: mandatory rule with `sort_order 15` = `COMPENSATION -> KB-FUND-033` must exist (6 mandatory rows total). D-4: exactly 45 facts; do NOT add the 4 legacy-only facts.
5. `rag_prefilter_exclusion` ends with FOUR rows (the four explicit rules in T2; the section heading "（3 行）" is a typo — A-1 and the four listed rows both say 4). Mandatory rows: 6 (sort_order 10/15/20/30/40/50). `rag_intent_coverage`: 21 rows. `rag_phrase_group`: ~120 rows generated from the script's `_XXX_PHRASES` constants + named inline groups + COMPENSATION group.
6. Facts, phrases, keys MUST be generated from `scripts/spike_deepseek_reply.py`'s `RAG_KNOWLEDGE_BASE` by the new export script — 禁止手抄 (machine-generated content, I-4/I-5 separators `|` vs `,` are data contracts). Run the export script and redirect its output into V112 (T1). Python fingerprint must equal `e62421a42c432cf3`.
7. `RagKnowledgeBase` must expose exactly two write/verify entry points with the I-3/I-3b semantics: `verifyAndPublish()` (@PostConstruct startup-only, read-only, throws IllegalStateException with expected+actual fingerprint on mismatch) and `@Transactional republish(writeInTx: () -> Unit)` (no old-fingerprint comparison; meta UPDATE inside the tx; snapshot published only after commit). The acceptance test for P0-3 (republish then verifyAndPublish passes) is mandatory.

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# new test class
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagKnowledgeBaseTest
# Flyway migration integration test (Docker available: docker info OK)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# full regression gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# seed/script consistency (human-verified outputs)
python3 scripts/spike_deepseek_reply.py --dump-kb | python3 -c "import sys,json,hashlib;d=json.load(sys.stdin);print(len(d))"
python3 scripts/export_rag_kb_sql.py | grep -o "fingerprint[^;]*"
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; export fingerprint output = `e62421a42c432cf3`; `git diff --check` exit 0. Record every command's exit code and counts in the execution report.

## Downstream interfaces (consumed by later children — must not drift)

- Tables (V112): `rag_fact` (45 rows; `fact_code` UNIQUE `KB-<AREA>-<NNN>`; columns incl. `answer MEDIUMTEXT`, `render_mode`, `enabled`, `sort_order`, `legacy_rule_id NULL`; no auto-increment id exposed anywhere), `rag_phrase_group` (~120; UNIQUE(group_code, phrase)), `rag_intent_coverage` (21), `rag_mandatory_rule` (6; `match_groups` any-of `,`-joined, `fact_codes` ordered `,`-joined), `rag_prefilter_exclusion` (4), `rag_kb_meta` (single row `fingerprint=e62421a42c432cf3`, `fact_count=45`).
- `RagKnowledgeBase`: `@Volatile` immutable `RagCorpusSnapshot`; `snapshot()`; `verifyAndPublish()` (startup); `republish(writeInTx)` returning new fingerprint; `enabledFacts()` 44 rows excluding `KB-APP-017` (I-2 normalization).
- `rag/config/RagProperties.kt` defaults + `application.yml` `rag:` block: prefilterLimit=18, retrievalLimit=14, minLexicalScore=2, coverageWeight=100.0, phraseWeight=12.0, overlapWeight=1.0, retrievalTemperature=0.0, generationTemperature=0.2, retrievalMaxTokens=900, generationMaxTokens=2600.

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c1
```

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c1/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commit.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

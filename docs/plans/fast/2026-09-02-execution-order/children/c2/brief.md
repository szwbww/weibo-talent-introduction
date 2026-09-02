# Child Brief — c2 · 02 确定性检索层：归一化 / 短语组 / 覆盖键 / 强制 / 剔除 / 预筛

- Plan: `docs/plans/2026-09-02/02-rag-deterministic-retrieval.md` (Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `acb88c1e77d172a7f252690b1da1203f08c01817` (c1 terminal code head; branch HEAD carries c1 evidence `cd2c363`)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (identity `commit:92b0519a18a3a46989f8733259af4649f7748a72` — amended)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 46cc5c46395814b1ef03e52ab8b8bfb5197f372c -- docs/plans/2026-09-02/02-rag-deterministic-retrieval.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order`. Follow execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Modify ONLY the 8 authorized files in the plan's `## 变更文件清单`. Nothing else. Do not touch `docs/plans/fast/**` (fast-p evidence; the controller commits it separately). Do not touch `qa_rule`/`qa_category`, existing migrations, or any c1 `rag` file already committed (RagFact/RagRetrievalRule/RagFactRepository/RagRetrievalRuleRepository/RagKnowledgeBase/RagProperties/V112) — c2 is read-only consumer of c1's snapshot API.
3. Preserve invariants I-7..I-12 and master-plan globals G-1..G-4 exactly, plus D-3 (only deliberate deviation, registered in the parity test). Word-for-word script equivalence (I-7): token regex hardcoded `[a-z0-9]+` (ASCII, never Unicode classes); normalize = lowercase → tokens joined single-space → padded with one space each side; phrase hit = `normalize(text).contains(normalize(phrase))`. Prefilter order I-8 (sort → requested/score≥2 filter → exclusions → mandatory front-merge from FULL enabled corpus → truncate 18) is not commutable; dedupe keeps first occurrence (I-9); requested coverage keys derive ONLY from intentCoverage + matched groups, never from any LLM output (I-10); exclusion rules kept verbatim so `KB-FUND-036` stays suppressed when only compensation is asked (I-11); `shouldRequestCv` = four-condition AND (I-12), appends `application.required_materials` when true.
4. c1 consumed: `RagKnowledgeBase.snapshot()` exposes immutable `facts/phraseGroups/intentCoverage/mandatoryRules/exclusions`; read ONLY from the snapshot, never query the DB (plan 现状审计). c1 V112 seeds: rag_mandatory_rule 6 rows (COMPENSATION sort_order 15), rag_prefilter_exclusion 4 rows, rag_intent_coverage 21 rows, rag_phrase_group 87 rows (O-1 recorded at c1: plan prose "约 120" is approximate — machine-derived count is authoritative; do NOT try to force 120).
5. Parity corpus: `scripts/dump_rag_parity_fixtures.py` must produce `src/test/resources/rag-parity/fixtures.json` with ≥28 entries (≥20 real inbound emails from historical `mail_record` INBOUND bodies, de-identified + the 8 constructed scenarios in the plan's 实测基线 table; note spike script's `SAMPLE_INBOUND_EMAIL` counts as real). Regeneration must be deterministic: rerun → `git diff --stat fixtures.json` empty. D-3 deviation must be registered in one dedicated test method asserting the 8-row expectation table (033 hit/036 miss columns).
6. Environment notes from c1 (all recorded): fresh mysql:8.0.36 Testcontainers chains fail at pre-existing V82 (SQLSTATE 45000) in this environment — base-reproduced, accepted env-blocked per precedent; plain `mvn test` must stay green (no un-gated Docker test in the suite — every Docker/chain test needs `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")`). c1's RagKnowledgeBaseTest runs against a scratch patched chain via `RAG_KB_TEST_DB_URL` (see c1 execution.md/verify-log.md for the mechanism). If your parity/unit tests need seeded rag_* tables in a real MySQL, reuse that scratch-chain approach or inject snapshot data in-memory; if a required command is blocked by the V82 defect, reproduce at base and record environment-blocked exactly like c1.

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# new test classes
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagPrefilterParityTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagTextNormalizerTest
# D-3 deviation registration method only
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagPrefilterParityTest#compensationMandatoryIsTheOnlyDeliberateDeviation
# regenerate parity corpus (must be deterministic: rerun diff empty)
python3 scripts/dump_rag_parity_fixtures.py
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# full regression gate (must be green — no un-gated docker tests)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; `dump_rag_parity_fixtures.py` rerun leaves `git diff --stat src/test/resources/rag-parity/fixtures.json` empty; `git diff --check` exit 0. Record every command's exit code and counts in the execution report.

## Downstream interfaces (consumed by c3 — must not drift)

- `RagTextNormalizer.normalize(text): String` + `tokens(text): Set<String>`.
- `RagPhraseMatcher.containsAny(text, phrases): Boolean` + `matchedGroups(text, groups): List<String>` (group_code ascending for stable display).
- `RagMandatoryResolver.resolve(inbound): List<String>` — ordered mandatory fact_codes, first-occurrence dedupe, enabled-only (never KB-APP-017).
- `RagPrefilterService.requestedCoverageKeys(inbound, context): List<String>` (incl. CV append rule I-12), `shouldRequestCv(...): Boolean`, `lexicalScore(query, fact, requested)`, `prefilter(inbound, context): List<RagFact>` (≤18, I-8 order).
- All consume `RagKnowledgeBase.snapshot()` + `RagProperties` (c1). Zero LLM involvement; zero production callers yet (c3 wires them).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c2
```

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c2/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commit.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

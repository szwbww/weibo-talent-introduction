# Child Brief — c7 · 06 「AI 提示词与约束」页改为可编辑约束清单

- Plan: `docs/plans/2026-09-02/06-prompt-console.md` (Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `60efcbaaa14e919ff8b4cfa9539cca41fd6a6d62` (c6 terminal code head; branch HEAD carries c6 evidence `e7ddb20`)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (identity `commit:92b0519a18a3a46989f8733259af4649f7748a72`)
- Design baseline: `docs/mockups/ai-prompt-console.html` (界面基准; 样式契约 S-1..S-5 为准)
- c3 produced: `rag/service/RagPromptConstraints.kt` (RETRIEVAL_RULES 5 / GENERATION_RULES 22, 12th rewritten per I-18, 22nd added per D-6, 18/19/21 derived) and `rag/service/RagPromptBuilder.kt` — c7 modifies RagPromptBuilder's value source (T3) to read effective() each call (I-34). 04 (c5) produced `.rag-badge` base + `--verbatim*` tokens (S-3 reuses base, adds 4 variants; tokens NOT used here).

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 46cc5c46395814b1ef03e52ab8b8bfb5197f372c -- docs/plans/2026-09-02/06-prompt-console.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order`. Follow execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Modify ONLY the 10 authorized files in the plan's `## 变更文件清单`. Nothing else. Do not touch `docs/plans/fast/**`, `qa_rule`/`qa_category`, old free-form prompt config behavior or its endpoints (`/api/ai-training/prompt-config` + AiTrainingController), migrations V1..V114 (new V115 only), c1-c6 committed files beyond `RagPromptBuilder.kt` (#4, the ONLY c3 file modified — its constants stay as defaults, only the value source changes).
3. Preserve invariants I-30..I-34 and master globals G-1..G-8 exactly. What must NOT change 1-3 verbatim: old free-form config + its effect on the legacy path (D-14: form stays, even in c8 — never delete the two textareas); other four subtabs; default prompt content byte-identical to RagPromptConstraints when no customization.
4. Key mechanics:
   - I-30: `rag_prompt_config.retrieval_constraints/generation_constraints` NULL → effective() returns 03's constants verbatim, isCustom=false; resetToDefault = set both columns NULL (never store a snapshot copy). DTO two-layer shape like AiPromptConfigDto (nullable) / Effective (non-null + isCustom).
   - I-31: generation rules index 17/18/20 (18th/19th/21st) are DERIVED from `rag_mandatory_rule` at read time (c3's renderDerivedRules logic); never persisted; save() ignores inputs for those three; response marks derived:true. Mandatory-rule change → page 18/19/21 text follows (A-4).
   - I-32: stored JSON array entries carry NO `no`/`index` fields; display numbering is render-time; constraint TEXT must never reference rule numbers (a binding rule for edited content).
   - I-33: every save writes an audit of changed indices + before/after values, additions, deletions, operator, time — follow the repo's existing audit conventions; V115 is rag_prompt_config only (do NOT invent an extra migration/table).
   - I-34: RagPromptBuilder reads RagPromptConfigService.effective() on EVERY build (inject service); grep 'RagPromptConstraints\.' in RagPromptBuilder.kt must be empty (constants referenced via the service default path only).
   - T1 V115: single-row table id=1, both constraint columns NULL, comment per I-30.
   - T4 frontend: insert TWO cards + savebar BEFORE the existing `<section>` inside `#aiTabPrompts` (old form stays; ONLY its panel-head title text changes to「自由回复提示词（旧链路 · 兜底路径）」+ one `.muted` explanatory line appended below — nothing else in that old DOM changes; two textareas' ids/rows/placeholder/structure untouched; grep aiTrainingFreeFormPrompt/aiTrainingConstraints each still 1 hit).
   - G-6: subtab registration untouched this plan (c5 did it) — do NOT touch the whitelist chain.
   - G-5: pre-grep FIRST; bump three `?v=` keys + EVERY pinning test file to single value `20260902-rag-prompt-console` (RE-GREP the current pin set — 4-5 files at c5/c6: batchSendTaskConsoleVisualFix, checkRepliesRelocation, manualReplySubjectPrefill, overlayAndDialogContrast, ragKnowledgeBasePage — confirm on disk; single-string sync only).
   - G-8: ragPromptConsole.test.js asserts ragPromptRetrieval/ragPromptGeneration/ragPromptSaveBar ids in index.html source.
   - S-1..S-4 verbatim CSS appended at styles.css EOF (no edits to existing blocks; .rag-badge base NOT redefined — only the 4 variants S-3); .rag-prompt-savebar literal rgba(255,255,255,.96) + backdrop blur(8px), no var(--panel-bg).
   - Env: V115 verified on scratch patched chain (V1..V81+alignment+V82..V115); Flyway IT env-blocked recorded (base-reproduced, precedent c1/c4/c5); RagPromptConfigServiceTest must run docker-free in plain suite (mock JDBC or unit-level; derive-path test may need mandatory rules — construct in-memory if possible, else migrationIt-gated scratch-chain like c1/c5; keep plain suite green).

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# cache-key pre-grep (BEFORE index.html edits)
grep -rn "v=$(grep -o 'styles.css?v=[^"]*' src/main/resources/static/index.html | cut -d= -f3)" src/test/js/
# new tests
node --test src/test/js/ragPromptConsole.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagPromptConfigServiceTest
# full frontend + syntax
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
# full regression gate + build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene + I-34 grep
git diff --check
grep -n "RagPromptConstraints\." src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPromptBuilder.kt   # must be empty
```

Pass criteria: every `mvn` exit 0 `Failures: 0, Errors: 0`; `node --test` exit 0 `# fail 0`; `node --check` clean; I-34 grep empty; `git diff --check` clean. Record every command's exit code/counts in the execution report.

## Downstream interfaces (consumed by c8 + 06 acceptance — must not drift)

- `rag_prompt_config` (V115) + `RagPromptConfigService.effective()/save()/resetToDefault()` + `RagPromptConfigController` GET/PUT/POST /reset.
- `RagPromptBuilder` now reads effective() per call (I-34) — compose behavior follows saved constraints immediately.
- Frontend cards `ragPromptRetrieval`/`ragPromptGeneration` + savebar `ragPromptSaveBar` in aiTabPrompts; cache-key value `20260902-rag-prompt-console` (c8 may touch index.html for 旧 QA 页下线 — if so it re-bumps per G-5; noted for c8).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c7
```

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c7/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commit.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

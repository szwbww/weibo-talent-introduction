# Child Brief — c3 · 03 整封生成：两次 LLM 调用 + 令牌逐字替换 + 未识别提问

- Plan: `docs/plans/2026-09-02/03-rag-letter-composer.md` (Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `af8fb5fad2bb28ebf18324242e2959d11d297aad` (c2 terminal code head; branch HEAD carries c2 evidence `163bd43`)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (identity `commit:92b0519a18a3a46989f8733259af4649f7748a72` — amended)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 46cc5c46395814b1ef03e52ab8b8bfb5197f372c -- docs/plans/2026-09-02/03-rag-letter-composer.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order`. Follow execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Modify ONLY the 10 authorized files in the plan's `## 变更文件清单` (T0 `HttpLlmDraftClient.kt` + 6 new rag main files + 3 new test files). Nothing else. Do not touch `docs/plans/fast/**`, `qa_rule`/`qa_category`, existing migrations, c1/c2 committed `rag` files (read-only consumers: `RagKnowledgeBase.snapshot()`/`RagProperties`, c2 services `RagPrefilterService`/`RagMandatoryResolver`), or any existing `llm`/`reply`/`campaign`/`mail` code beyond the single T0 file.
3. Preserve invariants I-13..I-19, I-45, I-46, I-10 and master globals G-1..G-4 exactly. D-2 (script verbatim) with I-46's two registered deviations (`thinking`/`stream` — register only, never implement); D-5 (frame/closing preserved; model never writes salutation/signature); D-6 (unaddressed via constraints + server-side verbatim-substring validation, no third LLM call); D-12 (max_tokens ONLY via new defaulted 4-arg overload — never touch `chatWithModelObserved`/`chat`/`chatWithModel` signatures; 22 stubs must stay intact).
4. T0 first: `interface LlmDraftClient` gains a defaulted 4-arg overload `chatWithModelObservedJson(messages, temperature, providerModel, maxTokens): LlmChatResult = chatWithModelObservedJson(messages, temperature, providerModel)`; `HttpLlmDraftClient` overrides it and adds `body["max_tokens"] = maxTokens` only when non-null, in both streaming and non-streaming request paths. 3-arg path byte-identical behavior.
5. VERBATIM handling (I-13): generation prompt chunks for VERBATIM facts drop `answer`, carry `render_token = "{{FACT:<fact_code>}}"` + `render_instruction`. Retrieval prompt candidates DO carry `title` + `retrieval_text` per script `retrieval_record()` (G-3 layered rule — title allowed on retrieval side only, never in generation prompt). Token render (I-15): dedupe keep-first; missing-token 3-level fallback insertion exactly per script `render_verbatim_facts()`; final check (I-14): every VERBATIM answer must be a substring of the final body, else throw `RagComposeException(422, "RAG_VERBATIM_MISSING")` listing missing fact_codes — never degrade, never fallback.
6. I-16 server-side authority: model-returned fact_ids validated against candidate list (invalid dropped + warn); then mandatory front-merge, then coverage-intersect non-selected candidates appended; truncate retrievalLimit 14; empty/invalid result falls back to candidates[:12]. I-17 unaddressed: quote must be verbatim substring after foldWhitespace (reuse InboundAskEnumerator.kt:107-146 shape), folded length < 8 dropped, dupes dropped, silent discard. I-18: GENERATION_RULES[11] (12th rule) = no salutation/thanks/signature (no `Sign as`); return frame + bodyParagraphs separately. I-19 ProcessContext mapping: CV status PROVIDED→RECEIVED / DECLINED→UNKNOWN / missing row→MISSING; expertReplyCount = INBOUND mail_record count for contact.
7. c1/c2 consumption notes: snapshot corpus fingerprint = `e62421a42c432cf3` (A1); cache key = `sha256(inbound) + ":" + corpusFingerprint`. RagProperties exists (c1) with retrievalTemperature 0.0 / generationTemperature 0.2 / retrievalMaxTokens 900 / generationMaxTokens 2600. c2 services wired as plan T5 step 1-2. `rag_phrase_group` group codes follow the SCRIPT (GOVERNMENT_ORGANIZATION etc. — c2 aliases at consumption; do not re-litigate).
8. Endpoint: new namespace `/api/rag-reply/*` (never `/api/trust-reply/*`). Request `{sourceType: TRAINING_MAIL|LIVE_INBOUND, sourceId, model?, forcedFactCodes?, excludedFactCodes?, frameSelection?}`; `forcedFactCodes`/`excludedFactCodes` validated as existing+enabled fact_codes else `400 RAG_FACT_CODE_INVALID` (G-1 — no auto-increment ids anywhere in requests/responses). Exceptions mapped: 422 RAG_VERBATIM_MISSING / 502 RAG_LLM_UNAVAILABLE / 400 RAG_FACT_CODE_INVALID. Do NOT persist drafts; do NOT call any auto-send path.
9. Reply frame: read snippets via `ReplySnippetService` (4th consumer — do NOT modify it); resolve source via the two repo paths described in the plan (do NOT reuse `TrustReplyWorkbenchService.resolveSource`). Tests use stub `LlmDraftClient` (no real LLM, no docker); `RagProcessContextResolverTest` may mock repositories (no DB needed) — keep the plain suite docker-free and green (V82 fresh-chain env-block still stands; do not add un-gated docker tests).

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# new test classes
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagLetterComposerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagVerbatimRendererTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagProcessContextResolverTest
# token-failure-must-fail-whole-compose
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagLetterComposerTest#verbatimMissingFailsWholeCompose
# verbatim-leak check
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagLetterComposerTest#generationPromptHidesVerbatimAnswers
# I-45 guard: count must stay 22
grep -rc "override fun chatWithModelObserved\b" src/main src/test | awk -F: '{s+=$2} END {print s}'
# build + full regression gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; I-45 override count 22 unchanged; `git diff --check` exit 0. Record every command's exit code and counts in the execution report.

## Downstream interfaces (consumed by c4/c6/c7/c8 — must not drift)

- `POST /api/rag-reply/compose` response `RagComposeResult(frame, bodyParagraphs, usedFacts, unaddressed, modelCoverage, warnings, corpusFingerprint, retrievalUsage, generationUsage)`; `usedFacts` items carry `factCode/title/renderMode/riskLevel/status/origin(MANDATORY|MODEL)` (c4 consumes; c6 workbench UI consumes).
- `RagLetterComposer.compose(...)`, `RagPromptBuilder.buildRetrievalPrompt/buildGenerationPrompt`, `RagVerbatimRenderer.render/violations`, `RagProcessContextResolver.resolve`, `RagPromptConstraints` constants (06 edits constraints UI later; keep constants data-driven where the plan says derived rules are computed at runtime from `rag_mandatory_rule`).
- T0 4-arg overload `chatWithModelObservedJson(..., maxTokens)` — the only max_tokens path for the rag chain.

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c3
```

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c3/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commit.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

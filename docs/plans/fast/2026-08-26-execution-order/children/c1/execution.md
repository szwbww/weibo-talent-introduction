## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/2026-08-26/01-llm-fact-retrieval.md
Plan SHA-256: 7cf649022bc8bc0018b8223bc1d5073954bca16d6ffb0e51511642fd2e5ec264
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/2026-08-26/01-llm-fact-retrieval.md@7cf649022bc8bc0018b8223bc1d5073954bca16d6ffb0e51511642fd2e5ec264
Execution epoch: NEW
Approval basis: approved child brief c1 (docs/plans/fast/2026-08-26-execution-order/children/c1/brief.md) referencing the plan above at Plan identity commit ee0749d3beedea7e26f4bf4e097b3d33a1684b7d
Executor: C1Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order
Target branch: fast/2026-08-26-execution-order
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order@fast/2026-08-26-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-26-execution-order
Pre-execution code SHA: f2935072c819a9167e75220a6a959b0769462fde (child product base; pre-execution HEAD ee0749d3beedea7e26f4bf4e097b3d33a1684b7d)
Post-execution code SHA: de5e130a84fba33296ea906734a1c7f071e3383a
Evidence HEAD: N/A (execution report is fast-p evidence, committed separately by the controller; not part of the implementation commit)
Implementation boundary: f2935072c819a9167e75220a6a959b0769462fde..de5e130a84fba33296ea906734a1c7f071e3383a (single commit, 7 files, no fast-p files)

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1.1 FactRetrieverProperties (I-9) | IMPLEMENTED | src/main/kotlin/com/weibo/talentintroduction/llm/config/FactRetrieverProperties.kt | main compile; QaFactRetrieverTest truncation test |
| T1.2 QaFactRetriever (I-4/I-7/I-8/I-9) | IMPLEMENTED | src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt | QaFactRetrieverTest: 17/17 green (verbatim prompt, 4-check rejection, cache/temperature 0.0, 6 fail-open paths, cap+truncation, prompt cap, fixed log line) |
| T2.1-T2.8 QaFactSelectionService wiring (I-1/I-2/I-3/I-5/I-6) | IMPLEMENTED | src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt | QaFactSelectionRetrievalTest: 13/13 green (I-1 intents unchanged, I-2 matrix bypass + never(), I-3 union order, I-5 sendQaRuleIds, I-6 three states + passesSendGate, I-8 equality, I-4/I-9 log counts, A-3 DISABLED line) |
| RequestFactItem.retrievedFactRuleIds (I-1) | IMPLEMENTED | src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt (single appended field with default) | main compile; affected-suite green |
| T3.1 application.yml keys | IMPLEMENTED | src/main/resources/application.yml (4 llm.fact-retriever keys only) | main compile |
| T3.2/T3.3 new tests | IMPLEMENTED | src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt, src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionRetrievalTest.kt | 17 + 13 tests green |

Invariants: I-1 `assignRulesToIntents(strictCandidateRules, …)` and `intents = intentCoverages` byte-identical (grep-verified); I-2 all 5 `buildRequestFact` call sites set `retrievedRuleIds` explicitly (grep-verified: select real, matrix empty, legacy-empty empty, legacy-main empty, auto real); I-3 union `strictCandidateRules + retrievedRules` with keyword hits first; I-4 four checks in `QaFactRetriever.rejectionReason` with per-id `log.warn`; I-5 `orderEvidenceRuleIds` appends per-request `factRuleIds` after intent evidence, `workbenchResult()` untouched; I-6 `naturalStatus == UNSUPPORTED && factRuleIds.isNotEmpty() -> PARTIAL` appended after the unchanged `when`; I-7 explicit `temperature = 0.0` + `ConcurrentHashMap` cache keyed by `sha256(inboundText):poolFingerprint(pool)` (id|updatedAt|sha256(answerBody), id-ascending); I-8 all failure paths return `FactRetrieval(false, emptyMap())` with outcome DISABLED/CLIENT_ABSENT/TRANSPORT_ERROR/EMPTY_RESPONSE/PARSE_ERROR/ALL_REJECTED, never throw; I-9 per-request cap `maxFactsPerRequest` (default 3) with `log.warn` of count + all ids, plus prompt cap warn.

### Commands

All commands ran in the target worktree with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` (JDK 11 mandatory), freshly after the final implementation state.

| Command | Result | Evidence |
|---|---|---|
| `mvn test -Dtest=QaFactRetrieverTest` | PASS | exit 0, `Tests run: 17, Failures: 0, Errors: 0` |
| `mvn test -Dtest=QaFactSelectionRetrievalTest` | PASS | exit 0, `Tests run: 13, Failures: 0, Errors: 0` |
| `mvn test -Dtest=QaFactSelectionServiceTest` | PASS | exit 0, `Tests run: 69, Failures: 0, Errors: 0` |
| `mvn test -Dtest=AiReplyDraftServiceTest` | PASS | exit 0, `Tests run: 183, Failures: 0, Errors: 0` |
| `mvn test -Dtest=TrustReplyWorkbenchItemFlowTest` | PASS | exit 0, `Tests run: 52, Failures: 0, Errors: 0` |
| `mvn test -Dtest=GroundedAutoReplyDecisionServiceTest` | PASS | exit 0, `Tests run: 17, Failures: 0, Errors: 0` |
| `mvn test` (full gate incl. node-test / node-check JS executions) | PASS | exit 0, `Tests run: 2860, Failures: 0, Errors: 0, Skipped: 4` (4 pre-existing skips), BUILD SUCCESS |
| `git diff --check` | PASS | exit 0, silent |

### Changed Files

- src/main/kotlin/com/weibo/talentintroduction/llm/config/FactRetrieverProperties.kt — new; self-registering `@ConfigurationProperties(prefix = "talent-introduction.llm.fact-retriever")` (enabled / enabledForAutoReply / maxFactsPerRequest=3 / maxRulesInPrompt=60 / cacheEntries=200)
- src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetriever.kt — new; fail-open retriever, I-4 four-check validation, I-7 fingerprint cache + temperature 0.0, I-8 outcomes, I-9 per-request cap; `FactRetrieval` result; verbatim `FACT_RETRIEVAL_SYSTEM_PROMPT`; pure `buildFactRetrievalLogLine`
- src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt — modified; constructor params (T2.1), retrieval calls in `select()` (gated by enabledForAutoReply) and workbench auto branch, explicit `retrievedRuleIds` at all 5 `buildRequestFact` call sites (I-2), candidate union (I-3), factRuleIds merge + `retrievedFactRuleIds` (T2.4), UNSUPPORTED→PARTIAL (I-6), `orderEvidenceRuleIds` append (I-5), `[FACT_RETRIEVAL]` log lines (T2.8)
- src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt — modified; ONLY `retrievedFactRuleIds: List<Long> = emptyList()` appended to `RequestFactItem` (with comment)
- src/main/resources/application.yml — modified; ONLY 4 keys added under `talent-introduction.llm.fact-retriever`
- src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactRetrieverTest.kt — new (17 tests)
- src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionRetrievalTest.kt — new (13 tests)

### Deviations

1. **Worktree identity gate run manually.** `scripts/worktree_identity.py` crashes during `git worktree list --porcelain` iteration on a stale, deleted worktree entry (`/sessions/rcw-…/.worktrees/review-2026-08-20-execution-order`, locked, path no longer exists) in the shared repo — unrelated to the target worktree. The gate was replicated with the script's exact git commands for the target worktree (root / branch / absolute-git-dir / common-dir / HEAD), checked before and after execution and before commit; values match the brief. No repo state was modified.
2. **`FactRetrieval` carries `outcome` + stats fields** (`requested/returned/accepted/rejected/truncated`), beyond the plan's minimal `(available, byRequestIndex)` shape. Required so the call-site `[FACT_RETRIEVAL]` line (T2.8) can carry the outcome and the I-4/I-9 counts that the acceptance criteria assert in the log line. The plan's literal construction `FactRetrieval(available = false, byRequestIndex = emptyMap())` still compiles unchanged.
3. **`candidateRules` intermediate omitted.** T2.3's unused `val candidateRules = strictCandidateRules + retrievedRules` was not emitted as a dead local; the union is computed exactly as T2.4 specifies in the `factRuleIds` expression. `assignRulesToIntents` keeps `strictCandidateRules` (I-1).
4. **Workbench "gate" semantics.** A-3 requires the `[FACT_RETRIEVAL] source=WORKBENCH … outcome=DISABLED` line when `LLM_FACT_RETRIEVER_ENABLED=false`, so the workbench auto branch always calls `retrieve()` when requests/pool are non-empty and lets the retriever's internal `!enabled → DISABLED` guard (I-8) produce the observable outcome. `select()` (AUTO path) gates on `enabledForAutoReply` exactly per T2.7 and emits no line when the flag is off.
5. **`[]` (valid empty JSON array) is `available = true`** with an empty map — the model explicitly said "no facts", which I-8's consequence distinguishes from "the model did not run" (blank response → EMPTY_RESPONSE, available=false).

### Freshness

- Plan identity rechecked: YES (SHA-256 unchanged: 7cf649022bc8bc0018b8223bc1d5073954bca16d6ffb0e51511642fd2e5ec264)
- Worktree identity rechecked: YES (root/branch/git_dir/HEAD unchanged; script replicated manually — see Deviations 1)
- Reported commits reachable from target branch: YES (de5e130 is HEAD of fast/2026-08-26-execution-order, parent ee0749d = pre-execution HEAD)
- Required commands run this invocation: YES (all 8 commands, freshly, after final state)
- Historical evidence used only as baseline: YES (none used)

### Remaining Blocker

- None

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`

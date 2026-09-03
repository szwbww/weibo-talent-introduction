# Child Brief — c4 · 03b RAG 草稿采用 → 人工发送桥接

- Plan: `docs/plans/2026-09-02/03b-rag-send-bridge.md` (Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `10a38bb6457280f7104a333faa46fad6f7cb078f` (c3 terminal code head; branch HEAD carries c3 evidence `2987af4`)
- Master plan: `docs/plans/2026-09-02/00-execution-order.md` (identity `commit:92b0519a18a3a46989f8733259af4649f7748a72` — amended)
- c3 produced: `POST /api/rag-reply/compose` returning `RagComposeResult` with `usedFacts` items carrying `factCode/title/renderMode/riskLevel/status/origin(MANDATORY|MODEL)` + `corpusFingerprint` (value `e62421a42c432cf3` at seed). `RagKnowledgeBase` exposes `fingerprint()` (c1). This plan's `usedFactCodes` context = c3's `usedFacts[].factCode` in order.

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Bind to its identity: confirm `git diff 46cc5c46395814b1ef03e52ab8b8bfb5197f372c -- docs/plans/2026-09-02/03b-rag-send-bridge.md` is empty and you are in the named worktree on branch `fast/2026-09-02-execution-order`. Follow execute-p discipline: authorized files only, smallest change, fresh required commands at the end.
2. Modify ONLY the 8 authorized files in the plan's `## 变更文件清单`. Nothing else. Do not touch `docs/plans/fast/**`, `qa_rule`/`qa_category`, `mail_record_qa_rule` read/write paths, migrations V1..V112 (new file V113 only), c1-c3 committed rag/mail/llm files (read-only consumers: `RagKnowledgeBase.fingerprint()`, c3's composer contract), `index.html`, `styles.css`.
3. Preserve invariants I-39..I-44, I-47 and master globals G-1..G-8 exactly. What must NOT change 1-5 verbatim: old workbench path (verifyAssembly + qaRuleIds element-equal check at PendingMailOperationService.kt:181-196) byte-identical; legacy no-assembly path (`canonicalizeFactRuleIds` :208) byte-identical; `mail_record_qa_rule` untouched; second-confirmation flow intact; pre-send processing (mail variables, subject validation, sender account) untouched.
4. Key mechanics:
   - `sendManualRichReply(...)` gains two trailing nullable params `ragFactCodes: List<String>? = null`, `ragCorpusFingerprint: String? = null` (zero change to existing call sites).
   - Mutual exclusion (I-39) checked BEFORE the :181 assembly block: `assembly != null && ragFactCodes != null` → `400 SEND_EVIDENCE_SOURCE_CONFLICT`.
   - RAG branch: fingerprint null → `400 RAG_FINGERPRINT_REQUIRED`; `!= ragKnowledgeBase.fingerprint()` → `409 RAG_CORPUS_STALE` (I-41, no auto-regenerate, no silent pass); each fact_code must exist + enabled in snapshot else `422 RAG_FACT_CODE_UNKNOWN` (I-40 — NEVER read qa_rule/legacy_rule_id); carriesQa = ragFactCodes.isNotEmpty(); factResolution = CanonicalFactResolution(emptyList(), emptyList()); serverSuggestedFactIds = emptyList() (no qaFactSelectionService.select()); primaryRuleId = null (I-43 — no legacy_rule_id fallback).
   - `collectSafetyFindings` (I-47): add `ragSend: Boolean = false` param; RAG call passes `carriesQa=false` + `ragSend=true`; when ragSend, short-circuit the whole `:860-880` selection/trust-gap/intent section; KEEP the pure-text checks (`containsHallucinatedNumberOrUrl`/`containsUnbackedHighRiskDeclarations`/`containsTrustRhetoric`) — they are the second-confirmation trigger source (D-1: human is the only gate). Existing two paths pass ragSend=false, logic byte-identical.
   - Success → write `mail_record_rag_fact` rows ordered by original ragFactCodes index (I-42, no sort, no dedupe); NEVER write `mail_record_qa_rule` on the RAG path.
   - Controller DTO (:1012/:1027 — grep to confirm which is live) gains `ragFactCodes: List<String>? = null` + `ragCorpusFingerprint: String? = null`, forwarded to the service. Existing `qaRuleIds` untouched.
   - Frontend `app.js`: `adoptTrustReplyAssembly` branches on assembly shape — `usedFactCodes` present → `manualReplyQaContext = {ragFactCodes, ragCorpusFingerprint, baselineText}` + SKIP `schedulePreflightCheck()` (I-44); `canonicalFactIds` shape → existing qaRuleIds assembly unchanged. Send-request assembly picks qaRuleIds vs ragFactCodes+ragCorpusFingerprint by context shape.
5. CACHE-KEY DECISION (plan's two-option note): take the "03b 与 05 一起发布" branch — this run executes 03b and 05 on the same branch before any release; 05 (c6) does the unified G-5 cache-key bump. c4 therefore does NOT touch `index.html`/cache keys; `git diff --stat styles.css index.html` must be EMPTY.
6. V113 migration per plan T1 (no FK to rag_fact; UNIQUE(mail_record_id, ordinal); table comment per I-39; corpus_fingerprint column). Env note: FlywayMigrationIntegrationTest fresh-chain is V82-env-blocked in this environment (pre-existing; base-reproduced; precedent c1 + 02b/03) — scratch patched chain (V1..V81 + row alignment + V82..V112) exists; extend it with V113 and verify table creation + shape there, record the IT command environment-blocked with base reproduction exactly like c1.
7. Tests: `RagSendBridgeTest.kt` (Kotlin, mocked repos/services — plain suite docker-free green; per acceptance I-39..I-43/I-47 incl. zero-interaction asserts on qaFactSelectionService/qaRuleRepository/aiReplyDraftService/canonicalizeFactRuleIds and the hallucinated-number text check still firing WARNING_CLAIM_HALLUCINATED_FACT with confirmation required; old-path smoke tests stay green). `src/test/js/ragAdoptSendBridge.test.js` (node): usedFactCodes shape → ragFactCodes present, qaRuleIds absent, schedulePreflightCheck call count 0; canonicalFactIds shape unchanged. NOTE the repo's frontend-test convention (G-8): DOM-stub tests hide dangling refs — for any element-id rendering add an assertion that the id exists in index.html source. Existing JS tests pinning app.js behavior must stay green.

## Required commands (run fresh, after final state, from the worktree root)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# new tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagSendBridgeTest
node --test src/test/js/ragAdoptSendBridge.test.js
# old-path regression
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnmatchedInboundTrustWorkbenchTest
# migration IT (Docker; fresh-chain V82 env-blocked — record base reproduction, verify V113 on scratch patched chain)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
# frontend full + syntax
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
# existing validation block unchanged (diff must show only added branch, not rewrite of :181-215)
git diff src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
# styles/index.html zero-change
git diff --stat src/main/resources/static/styles.css src/main/resources/static/index.html
# build + full regression gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 `Failures: 0, Errors: 0`; `node --test` exit 0 `# fail 0`; styles.css/index.html diff empty; `git diff --check` clean. Record every command's exit code/counts in the execution report.

## Downstream interfaces (consumed by c6/c8 — must not drift)

- `mail_record_rag_fact` table (V113) + `MailRecordRagFact`/`MailRecordRagFactRepository.findByMailRecordIdOrderByOrdinalAsc`.
- `sendManualRichReply` RAG branch semantics (I-39..I-43) + `collectSafetyFindings(ragSend)` — c6's workbench adopt UI consumes the usedFactCodes context shape; c8 retires old endpoints knowing the three paths.
- Frontend context shape `{ragFactCodes, ragCorpusFingerprint, baselineText}` vs legacy `{qaRuleIds, baselineText}` — c6 builds the RAG workbench adopt flow on the ragFactCodes shape.

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c4
```

Write the full execution report to `docs/plans/fast/2026-09-02-execution-order/children/c4/execution.md` (create it) using the execute-p output contract. Exclude fast-p reports/logs from the implementation commit.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

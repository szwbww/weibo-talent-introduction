# Child Brief — c6 · 16-unsupported-index（入库放宽 + topic 检索 + 双回流通道）

- Plan: `docs/plans/2026-08-28/16-unsupported-index.md` (Plan identity: `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order`
- Branch: `fast/2026-08-28-reply-orchestration-order`
- Child base (product boundary): c5 terminal Code head (branch HEAD at dispatch time is the true base)
- Master plan: `docs/plans/2026-08-28/10-reply-orchestration-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the authorized files in the plan's `## 变更文件清单` (11 files after amendment A3, HUMAN-approved 2026-08-28T19:01:46Z): `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt`, `src/main/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexController.kt`, `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt`, `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`, `src/main/resources/es/trust_reply_unsupported_answer_v1.json`, `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestrator.kt` (T-4 channel-A sample injection at buildPrompt, default-OFF switch; A3 corrected the seam — NOT AiReplyDraftService.kt), `src/main/resources/static/app.js`, `src/test/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexServiceTest.kt`, `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`, `src/test/js/aiTrainingUnsupportedAnswers.test.js`, `src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt` (A3: `mapping is strict with only V1 fields and non-searchable bodies` field-set assertion 23→26). A3 ALSO permits a wiring touch to `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterCloser.kt` — constructor pass-through / call site only, deterministic fallback behavior unchanged. Nothing else. Do not touch `docs/plans/fast/**`.
3. Mapping evolution per plan: choose 方案 A (mapping-patch step in `bootstrapIndex()`) and state the choice in the implementation notes. `documentId = sha256("<sourceType>|<sourceId>|<requestKey>|<versionId>")` inputs MUST NOT change (idempotency key — test T-6.4 proves identical hash with and without the three new fields).
4. Index content must NEVER become a fact source — channel A is style-sample injection only, gated by the default-OFF config switch, and channel B requires operator confirmation (no auto rule creation).
5. Write triggers stay exactly at the two existing sites; archive failure never blocks the main flow (warn only). Preserve every invariant I-1..I-6 and every `What must NOT change` item exactly as written.

## Global constraints (master plan 10)

- Channel A safety depends ENTIRELY on c4's source-closure validation (13 G1 / `ORCH_FACT_ID_UNKNOWN`): sample propositions have no legal factId. Channel A must stay OFF in environments where 13 has not landed (IP-3).
- `topic` filtering is keyword-exact only (I-3: `requestText`/`answerText` are `index: false` in v1 mapping — no body search).

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# backend tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsupportedAnswerIndexServiceTest,PendingMailOperationServiceTest
# JS authority gate (per-file)
node --test src/test/js/aiTrainingUnsupportedAnswers.test.js
# full JS regression
node --test src/test/js/*.test.js
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; every `node --test` exit 0 with `pass N, fail 0`; `git diff --check` exit 0.

## Downstream interfaces

- None after this child (last in order). The `finalParagraphText` / `topic` / `editedByOperator` fields are the archive seam for c5's operator facts and c2/c4's post-close paragraphs.

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c6
```

Write the full execution report to `docs/plans/fast/2026-08-28-reply-orchestration-order/children/c6/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

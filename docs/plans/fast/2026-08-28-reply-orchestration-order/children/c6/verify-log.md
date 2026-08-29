# Verify Log — c6 · 16-unsupported-index（入库放宽 + topic 检索 + 双回流通道）

Epoch 2 header — light verification (fast-p four gates), child c6, both implementation commits.

## Light Verification: LIGHT_FAIL

Child: c6 (16-unsupported-index, A3-amended) — plan `docs/plans/2026-08-28/16-unsupported-index.md` (Plan identity `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`), child brief `children/c6/brief.md`
Boundary: `0a2a8f58acf93da9b2b903082af938be92d2783e..41d94c9410312f8e35539c5192dfcd3606b30a57` (base = c5 head 870a54f; head = c6 epoch 2 82410c6; spans implementation commits e9e035e epoch 1 + 82410c6 epoch 2; intermediate f3eea5f/adaf450/eaba4b4/d722984 are docs-only fast-p evidence, ignored)
Verifier: C6Ver

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Changed product/test files inside Authorized Files (A3) | PASS | `git diff --name-status 870a54f..82410c6` product/test files = exactly the 11 A3-authorized files: UnsupportedAnswerIndexService.kt, UnsupportedAnswerIndexController.kt, AiTrainingEvaluationService.kt, PendingMailOperationService.kt, es/trust_reply_unsupported_answer_v1.json, AiReplyLetterOrchestrator.kt (T-4 channel-A seam per A3), app.js, UnsupportedAnswerIndexServiceTest.kt (A, new), PendingMailOperationServiceTest.kt, aiTrainingUnsupportedAnswers.test.js, UnsupportedAnswerIndexApiTest.kt (23→26). Epoch 1 = 8 files, epoch 2 = 4 files (service in both). `AiReplyLetterCloser.kt` and `AiReplyDraftService.kt`: grep over boundary returns NOTHING — A3 conditional wiring NOT triggered, both untouched. Remaining boundary entries are docs only (16-unsupported-index.md, c5/c6 docs, ledger.md). |
| 2. Explicit requirements + invariants I-1..I-6, A3 items | FAIL | I-1 PASS — `grep UnsupportedAnswerIndex` in `AiReplyGroundedContentPlanner.kt` = no matches; samples injected in dedicated `## Style samples` prompt section with verbatim instruction 「以下段落仅供参考句式、语气与过渡方式，**不得引用其中任何事实或数字**；每个段落的 factIds 必须来自本次提供的事实清单。」; never wrapped as `PlanFact` (all PlanFact constructions are existing fact paths); default-OFF switch `@Value("\${talent-introduction.llm.style-sample-injection-enabled:false}")`. I-2 PASS — 方案 A mapping-patch in `bootstrapIndex()`: HEAD-success AND post-create both `PUT <index>/_mapping` (topic keyword / finalParagraphText text index:false / editedByOperator boolean), failure warn-only (catch Throwable); mapping resource updated, `"dynamic":"strict"` intact; T-6.6 ×3 pass. I-3 PASS — `listQuery` filters only `term(topic)` / `term(sourceMode)` / match_all; recentCandidateSamples/pendingTopics/activate use keyword term/terms on topic/status only; zero query clauses on requestText/answerText. I-4 PASS — allow set `{ANSWER_FROM_OPERATOR_INPUT, ANSWER_EVIDENCE_WITH_OPERATOR_INPUT, ANSWER_SUPPORTED_PART, ACKNOWLEDGE_PENDING} × {AI_GENERATED, SAFE_TEMPLATE}`, OMIT/ANSWER_WITH_EVIDENCE rejected (T-6.1); operatorInstruction blank passes / >4000 rejected (T-6.2); `rating == MEETS_EXPECTATION` retained in AiTrainingEvaluationService; live verbatim gate replaced by archive-with-editedByOperator=true (mail test: edited body → SAVED not FAILED). I-5 FAIL — (F-1) `liveDocument()` still writes `status = ACTIVE` at creation (baseline behavior, unchanged from 870a54f), so fresh live entries are labeled ACTIVE without ever having gone through channel-B conversion — contradicts I-5 「ACTIVE = 已由运营转化为 QA 事实（通道 B 完成）」 and observable outcome 4 (同主题累计命中 ≥N 次的**历史回答**进入待转队列, source-agnostic): live entries are excluded from channel-A samples (CANDIDATE-only) and the pending queue (CANDIDATE-only), and the UI renders unconverted live entries 「ACTIVE / 已转化」; `validate()` unbundling itself IS correctly implemented (code evidence). (F-2) plan acceptance I-5 「测试断言 TRAINING + ACTIVE 与 LIVE + CANDIDATE 均合法」 — no such test exists anywhere in src/test. I-6 PASS — parseListItem excludes operatorInstruction/topic/editedByOperator from blank-drop (OPTIONAL_LIST_FIELDS), blank operatorInstruction renders `—` (T-6.3); remaining required fields still drop with warn; total-vs-items diagnostic comment preserved. documentId PASS — T-6.4: identical hash with/without the three new fields, equals sha256("sourceType|sourceId|requestKey|versionId"), 4 inputs untouched. What-must-NOT-change PASS — write triggers still exactly 2 sites (AiTrainingEvaluationService:105, PendingMailOperationService:685); archive failure warn-only (both catch+log.warn+failedArchive; `requireNotNull(resolveSource)` → IllegalArgumentException caught by generic catch, main flow unaffected); 503 `UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE` degradation preserved; index never fact source. A3 PASS — T-4 seam is AiReplyLetterOrchestrator.kt (NOT AiReplyDraftService.kt); ApiTest field-set assertion 23→26 + strict + non-searchable bodies all asserted and passing. |
| 3. Required commands run fresh (zulu-11) vs baseline | PASS | Backend focused `mvn test -Dtest=UnsupportedAnswerIndexServiceTest,PendingMailOperationServiceTest`: BUILD SUCCESS, 9/0/0/0 + 2/0/0/0. JS authority `node --test src/test/js/aiTrainingUnsupportedAnswers.test.js`: 7 pass / 0 fail. Full JS `node --test src/test/js/*.test.js`: 765 pass / 0 fail (baseline 764 → +1 new test). Full gate `mvn test`: BUILD SUCCESS, total 3004 tests / 0 failures / 0 errors / 5 skipped (baseline seed 2952, c5 head 2993 → +11 = exactly the 9+2 new Kotlin tests; mvn test output shows the exec-maven-plugin node record, 765 JS pass inside too). Build `mvn clean package`: BUILD SUCCESS. Hygiene `git diff --check`: exit 0. |
| 4. Downstream interfaces (later children) | PASS | None after this child (last in order). Archive seam present: `topic` / `finalParagraphText` / `editedByOperator` written in `documentNode()` and mapped (topic keyword / finalParagraphText text index:false / editedByOperator boolean); write-side topic derived from version claims intentKey main segment (c5 op* facts / c2/c4 post-close paragraphs seam per data-class comment). |

### AUTO_FIX (F-ids or N/A)

- **F-1** — I-5 write-side: `UnsupportedAnswerIndexService.liveDocument()` keeps `status = UnsupportedAnswerIndexStatus.ACTIVE` at creation. A fresh live-archived entry was never converted by channel B, so per I-5 「ACTIVE = 已由运营转化为 QA 事实（通道 B 完成）」 it must enter as `CANDIDATE`; with ACTIVE-from-birth it is excluded from channel-A style samples and the channel-B 待转事实 queue (both CANDIDATE-only), hollowing observable outcome 4 for live 历史回答, and the UI mislabels it 「已转化」. Repair (plan-uniquely determined, authorized file): `liveDocument()` status → `CANDIDATE`; only channel-B activation (`activatePendingTopic`) writes ACTIVE. No existing test pins live=ACTIVE (T-6.5 asserts editedByOperator/topic/SAVED only).
- **F-2** — Plan acceptance I-5: 「测试断言 TRAINING + ACTIVE 与 LIVE + CANDIDATE 均合法」 — no test asserts the unbundled cross-combos (validate admits them in code, but nothing exercises TRAINING+ACTIVE / LIVE+CANDIDATE). Repair (authorized file): add to `UnsupportedAnswerIndexServiceTest.kt` acceptance tests asserting `create()` accepts TRAINING+ACTIVE and LIVE+CANDIDATE (and that status×sourceMode binding is gone).

### RECORD_ONLY (O-ids or N/A)

- N/A

### Required Action (COMPLETE_CHILD | AUTO_FIX | PAUSE)

AUTO_FIX — Gate 2 fails on I-5 (F-1 live status contradicts the converted-or-not definition; F-2 required acceptance test absent). All other gates pass; all required commands green; both repairs are small, plan-determined, and fully within authorized files.

## Light Verification: LIGHT_PASS

Child: c6 (16-unsupported-index, A3-amended) — plan `docs/plans/2026-08-28/16-unsupported-index.md` (Plan identity `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`), child brief `children/c6/brief.md`
Boundary: `41d94c9410312f8e35539c5192dfcd3606b30a57..f1d464e6f2c25d6d8318bdf2137c05685c297d82` (fix commit 56d3215 only; prior C6Ver verdict LIGHT_FAIL on F-1/F-2, gates 1/3/4 PASS)
Verifier: C6ReVer

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Changed product/test files inside Authorized Files (A3) | PASS | `git diff --name-status 82410c6..56d3215` = exactly 3 files, all A3-authorized: UnsupportedAnswerIndexService.kt (#1), UnsupportedAnswerIndexServiceTest.kt (#8), UnsupportedAnswerIndexApiTest.kt (#11). No other product/test file touched. Worktree dirty files are docs-only fast-p evidence (brief/execution/fix-log/verify-log), not product. |
| 2. F-1/F-2 closed; I-1..I-6 intact | PASS | F-1 CLOSED — `liveDocument()` writes `status = UnsupportedAnswerIndexStatus.CANDIDATE` (UnsupportedAnswerIndexService.kt:688, F-1 rationale comment); repo-wide sweep (`ACTIVE\|已转化` over service/controller/PendingMailOperationService/AiTrainingEvaluationService/app.js + all of src/main + src/test) shows the ONLY ACTIVE writer is channel-B `activatePendingTopic()` (`_update_by_query` script params, :410); API test re-pinned to expect live archive writes CANDIDATE (UnsupportedAnswerIndexApiTest.kt:248,288). F-2 CLOSED — acceptance test `create accepts TRAINING plus ACTIVE and LIVE plus CANDIDATE` (UnsupportedAnswerIndexServiceTest.kt:271-295) asserts both cross-combos produce CREATED; passes (focused run 10/0/0/0). I-1..I-6 otherwise intact — I-1: no UnsupportedAnswerIndex ref in AiReplyGroundedContentPlanner.kt; style samples in dedicated `## Style samples` prompt section with verbatim instruction 「以下段落仅供参考句式、语气与过渡方式，**不得引用其中任何事实或数字**…」; default-OFF `@Value("\${talent-introduction.llm.style-sample-injection-enabled:false}")`; N=2/topic CANDIDATE-only. I-2/I-3: mapping strict with 26 properties (topic keyword / finalParagraphText text index:false / editedByOperator boolean); 方案 A `patchIndexMapping()` PUT _mapping, failure warn-only; `listQuery` term-filters only topic/sourceMode (bool.filter), no body search. I-4: ALLOWED_HANDLINGS 4-set × ALLOWED_GENERATION_KINDS 2-set; operatorInstruction optional (length-only when non-blank); live archive sets editedByOperator=true. I-5: `validate()` no longer binds status×sourceMode (only sourceMode↔sourceType↔qualificationType consistency remains). I-6: operatorInstruction excluded from blank-drop (OPTIONAL_LIST_FIELDS), renders `—`. documentId inputs unchanged: `sha256("${sourceType}|${sourceId}|${requestKey}|${versionId}")`. |
| 3. Required commands run fresh (zulu-11) vs baseline | PASS | Focused `mvn test -Dtest=UnsupportedAnswerIndexServiceTest,PendingMailOperationServiceTest`: BUILD SUCCESS, 10/0/0/0 + 2/0/0/0 (prior 9 → +1 F-2 test). Full `mvn test`: BUILD SUCCESS, **3005** tests / 0 failures / 0 errors / 5 skipped (baseline 3004 + 1 = 3005 ✓); exec-maven-plugin node record present. Build `mvn clean package`: BUILD SUCCESS, 3005/0/0/5. JS authority `node --test src/test/js/aiTrainingUnsupportedAnswers.test.js`: 7 pass / 0 fail. Full JS `node --test src/test/js/*.test.js`: **765** pass / 0 fail (baseline 765 ✓, no JS change in fix). Hygiene `git diff --check`: exit 0. |
| 4. Downstream interfaces (later children) | PASS | None — c6 is the last child in order (children dir = c1..c6); plan/brief state no downstream consumers; fix commit introduces no interface change. |

### AUTO_FIX (F-ids or N/A)

- **F-1** — CLOSED (Round 1): `liveDocument()` now writes CANDIDATE at creation; `activatePendingTopic()` (channel B) is the only ACTIVE writer; no live=ACTIVE pin remains anywhere (main-source and test sweep).
- **F-2** — CLOSED (Round 1): acceptance test asserting TRAINING+ACTIVE and LIVE+CANDIDATE legal exists and passes; API test re-pinned to CANDIDATE for live archive.

### RECORD_ONLY (O-ids or N/A)

- N/A

### Required Action
- COMPLETE_CHILD

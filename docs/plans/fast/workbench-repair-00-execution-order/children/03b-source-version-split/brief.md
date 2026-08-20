# Fast-P Child Brief — 03b-source-version-split

- Master: docs/plans/2026-08-19/workbench-repair-00-execution-order.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Plan: docs/plans/2026-08-19/workbench-repair-03b-source-version-split.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order
- Branch: fast/workbench-repair-00-execution-order
- Child base (product): e2ad440157017fb6ced066fe63ad2d5e104a8296 (= child 03a terminal Code head)
- Prior children: 01, 02, 03a all LIGHT_PASS_WITH_NOTES. 03a built the per-request evidence framework that 03b extends. NOTE: 03a/02 changed TrustReplyWorkbenchService.kt and trust-reply-workbench.js substantially — plan line numbers were measured against the original base; locate anchors by content and record shifts.
- Downstream: none (03b is the last child). Its must-NOT-change list (hard gates at AiReplyIntentCatalog.kt:567/:705, AiReplyContextBuilder untouched) is the review-critical surface.

## Approved contract

The plan file is the complete approved contract. Read it fully from disk before starting and treat its bytes as authoritative.

- RESEARCH CHECKPOINTS (plan-mandated, before editing):
  1. `grep -rn "sourceVersion" --include=*.kt src/test | wc -l` and `grep -rn "sourceVersion" src/test/js/*.js | wc -l` — record baseline counts (plan's 2026-08-19 baseline: Kotlin 137, JS 124). If post-change failures vastly exceed expectation, STOP and return PLAN_CONFLICT rather than mass-editing tests.
  2. Confirm 03a landed: `grep -n "requestEvidenceVersion" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` must hit. If absent, STOP — 03b must not start without 03a.
- Authorized files (exactly 8):
  1. `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt` — T1 (append `expertProfileText` + `trainingKnowledgeText` fields with defaults; extract buildExpertProfile result to local; profileText construction byte-unchanged — I-5)
  2. `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` — T2–T5
  3. `src/main/resources/static/trust-reply-workbench.js` — T6 (incl. S-1/S-2 verbatim fragments)
  4. `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt`
  5. `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
  6. `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`
  7. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
  8. `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- Invariants: I-1 (sourceVersion = 7 identity components only; evidence components only in research-context per-request evidence; context components only in contextVersion — never in requestKey/versionId/requestEvidenceVersion/aggregateEvidenceVersion), I-2 (profile only mixes into requiresResearchContext==true items; others byte-identical to 03a baseline values), I-3 (content hash + boolean, not boolean alone), I-4 (per-item context-stale prompt + one-click regenerate button, both mandatory), I-5 (profileText prompt content unchanged; AiReplyContextBuilder NOT modified), I-6 (contextVersion never enters any identity hash).
- Style contracts: S-1 verbatim `<span class="muted" data-role="item-context-stale">本条在旧训练知识/对话历史下生成</span>` beside 03a's item-evidence-stale span; S-2 verbatim `<button type="button" class="button small secondary" data-action="regenerate-context-stale">重新生成受影响条目</button>` at end of status area only when >=1 context-stale item. No new classes, no inline style, no styles.css/index.html changes.
- must-NOT-change: inbound identity changes still full reset (requestKey all change); research-match hard gate MISSING preserved (AiReplyIntentCatalog.kt NOT modified); profileText prompt content unchanged; mailHistory rules unchanged (AiReplyContextBuilder NOT modified); auto-reply path unchanged (GroundedAutoReplyDecisionService NOT modified); 03a per-request semantics unchanged.
- Prohibited files (must NOT change): `AiReplyContextBuilder.kt`, `AiTrainingQaService.kt`, `AiReplyIntentCatalog.kt`, `GroundedAutoReplyDecisionService.kt`, `AiTrainingController.kt`, `UnmatchedInboundMailController.kt`, `PendingMailOperationService.kt`, `app.js`, any `db/migration/*.sql`, `styles.css`, `index.html`.
- Guard-test note: the plan's 守门测试 command names `AiReplyIntentCatalogTest+GroundedAutoReplyDecisionServiceTest+AiTrainingQaServiceTest` — first verify each class exists under src/test/kotlin; drop any missing class from the run and record it in the report (plan-sanctioned 修正记录). Use comma-form `-Dtest='A,B,C'` (surefire 2.22.2 rejects '+'-lists).

## Execution rules (fast-p)

- Use `execute-p` against this exact plan. Run the plan identity gate (sha256) and worktree gate; the worktree above is the target.
- Modify only the eight authorized files. Preserve every invariant and the must-NOT-change list (this is the safety-critical child).
- Run every required command from the plan's 验证命令 section freshly (JDK 11 zulu-11), comma-form class lists:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='AiReplyContextServiceTest,TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,PendingMailOperationServiceTrustWorkbenchTest'`
  - guard tests as above (existence-checked)
  - `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js`
  - `node --test src/test/js/*.test.js`
  - `node --check src/main/resources/static/trust-reply-workbench.js`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` (full)
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
  - `git diff --check`
- Commit the implementation locally as a single commit:
  `feat(fast-p): implement 03b-source-version-split`
  Only the eight authorized files in that commit. Do NOT include any file under `docs/plans/fast/`.
- Do not touch `docs/plans/`, the ledger, other child plans, or any file outside the authorized list.
- Do not review later children, repair unrelated behavior, push, merge, amend, or rewrite history.

## Reporting

Write the full `execute-p` result (Execution Result, Task Status, Commands, Changed Files, Deviations, Freshness, Remaining Blocker) by APPENDING to `docs/plans/fast/workbench-repair-00-execution-order/children/03b-source-version-split/execution.md` (file starts empty).

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the implementation commit SHA, a command summary, and the report path.

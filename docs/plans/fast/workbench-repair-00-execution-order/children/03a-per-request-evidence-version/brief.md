# Fast-P Child Brief — 03a-per-request-evidence-version

- Master: docs/plans/2026-08-19/workbench-repair-00-execution-order.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Plan: docs/plans/2026-08-19/workbench-repair-03a-per-request-evidence-version.md (commit b830ec208e9fe51bd693436f92158f1fde76622b)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-workbench-repair-00-execution-order
- Branch: fast/workbench-repair-00-execution-order
- Child base (product): 055d313d250053d7cbd917884745571b9580b9b4 (= child 02 terminal Code head)
- Prior children: 01-tab-focus-selector (LIGHT_PASS_WITH_NOTES), 02-claim-paragraphs (LIGHT_PASS_WITH_NOTES). NOTE: child 02 changed TrustReplyWorkbenchService.kt at :1286/:1331 and added a 2-line I-3 comment above :1153 — line numbers in this plan were measured against the pre-02 base; locate anchors by content, not raw line numbers, and record shifts in the report.
- Downstream: 03b-source-version-split REQUIRES this child's per-request evidence framework (`requestEvidenceVersion`, `aggregateEvidenceVersion`, `TrustReplyRequestCoverage.evidenceSetVersion`, `data-role="item-evidence-stale"` span). These are the 03b interface.

## Approved contract

The plan file is the complete approved contract. Read it fully from disk before starting and treat its bytes as authoritative. Work through the plan's 阶段 A (server) then 阶段 B (frontend) in order.

- RESEARCH CHECKPOINT (plan-mandated, before editing): run
  `grep -rn "evidenceSetVersionWithMapping" --include=*.kt src/main src/test`
  If any caller exists besides `TrustReplyWorkbenchService.kt:1522` (verify by content), STOP and return PLAN_CONFLICT — do not expand scope.
- Authorized files (exactly 9):
  1. `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` — T1–T4
  2. `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` — T5 (schema v4; no new migration file — I-7)
  3. `src/main/resources/static/trust-reply-workbench.js` — T6–T7 (incl. S-1 span, verbatim)
  4. `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt`
  5. `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt`
  6. `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStoreTest.kt`
  7. `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`
  8. `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
  9. `src/test/js/trustReplyWorkbenchSharedMount.test.js`
- Invariants: I-1 (requestEvidenceVersion bound to requestKey + ordered factRuleIds + subset rule snapshot only — no observation time, no other items' ruleIds), I-2 (rebind invalidates exactly both ends), I-3 (aggregate is derived; :464 keeps aggregate compare, :959 per-request, :1051 assemble check deleted), I-4 (partial restore; PARTIALLY_RESTORED/droppedItemCount/STALE), I-5 (frontend resets only affected item via preserveVersions bootstrap), I-6 (schema v4, v3 decoded as old -> whole STALE, no silent per-request mismatch), I-7 (no DB migration files).
- Style contract S-1: only the verbatim `<span class="muted" data-role="item-evidence-stale">事实已变化，本条回答需重新生成</span>`; no new classes, no styles.css/index.html changes, no inline style.
- must-NOT-change: cross-item fact uniqueness, rebind produces different version, evidenceSetVersion still aggregate fingerprint everywhere it appears today, server remains sole authority, fact ORDER participates in version identity, sourceVersion change still full reset (03b scope).
- Prohibited files (must NOT change): `TrustReplyWorkbenchController.kt`, `UnmatchedInboundMailController.kt`, `app.js`, `AiReplyDraftService.kt`, `AiReplyReviewAuditService.kt`, `AiTrainingEvaluationService.kt`, `UnsupportedAnswerIndexService.kt`, `AiReplyHighRiskClaimValidator.kt`, `AiReplyPointByPointComposer.kt`, any `db/migration/*.sql`, `styles.css`, `index.html`. Do NOT modify `buildInitialItemVersions` evidence source (C-6 observation; if you believe it must change, STOP and return PLAN_CONFLICT).

## Execution rules (fast-p)

- Use `execute-p` against this exact plan. Run the plan identity gate (sha256) and worktree gate; the worktree above is the target.
- Modify only the nine authorized files. Preserve every invariant and the must-NOT-change list.
- Run every required command from the plan's 验证命令 section freshly (JDK 11 zulu-11). Note: surefire 2.22.2 rejects the plan's literal `+`-joined class list ('No tests were executed!'); use the comma-separated equivalent `-Dtest='TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchStateStoreTest,TrustReplyWorkbenchControllerTest,PendingMailOperationServiceTrustWorkbenchTest'` (identical class set) and record the syntax deviation:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='TrustReplyWorkbenchServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchStateStoreTest,TrustReplyWorkbenchControllerTest,PendingMailOperationServiceTrustWorkbenchTest'`
  - `node --test src/test/js/trustReplyWorkbenchSharedMount.test.js`
  - `node --test src/test/js/*.test.js`
  - `node --check src/main/resources/static/trust-reply-workbench.js`
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` (full)
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
  - `git diff --check`
- Commit the implementation locally as a single commit:
  `feat(fast-p): implement 03a-per-request-evidence-version`
  Only the nine authorized files in that commit. Do NOT include any file under `docs/plans/fast/`.
- Do not touch `docs/plans/`, the ledger, other child plans, or any file outside the authorized list.
- Do not review later children, repair unrelated behavior, push, merge, amend, or rewrite history.

## Reporting

Write the full `execute-p` result (Execution Result, Task Status, Commands, Changed Files, Deviations, Freshness, Remaining Blocker) by APPENDING to `docs/plans/fast/workbench-repair-00-execution-order/children/03a-per-request-evidence-version/execution.md` (file starts empty).

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the implementation commit SHA, a command summary, and the report path.

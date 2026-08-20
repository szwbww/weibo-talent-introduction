# Fast-P Child Brief — P2b

- Plan (authoritative contract): `docs/plans/2026-08-20/P2b-bound-facts-into-prompt.md` (commit:15dbf44ea93cfab28f24bfb3ab017fa60ad3dbc8)
- Child base SHA: set to P2a terminal Code head (filled at dispatch)
- Depends on: P0, P1, P2a
- Downstream consumers: none (last child in Line B)

## Global constraints (from `docs/plans/2026-08-20/00-execution-order.md`)

1. **Order authority**: `00-execution-order.md` wins on conflict.
2. **Symbol-first relocation**: line numbers stale (07:34 UTC worktree); locate by symbol, lines cross-check only. Symbol not found = real problem.
3. **JDK 11 mandatory**: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
4. **Verification command authority**: shared commands only in `00-execution-order.md` 「验证命令」.
5. **P2b is the only cross-line child** — Line A (`workbench-operator-instruction-authorizes-actions.md`) is merged into this branch (commits `d56383e` + `66e1036`). Line A's operator-directed system message contract exists in the base; P2b revises it (adds the attached-facts channel) while preserving its action constraint.
6. **需求方决策 (hard)**: outbound audit only true evidence → `sendQaRuleIds` never includes bound-but-not-evidence facts. Manual binding never changes status.
7. **Line A constraint preserved verbatim**: `Do not introduce any outbound action that the answer basis does not state.` must remain in the system message (plan I-2 acceptance greps it). The fact channel must NOT become an action-authorization source (00-execution-order "线 A 的动作约束必须原样保留").
8. **No front-end files, no `styles.css`, no storage changes** — plan has no 样式契约 section; changed-file list contains no static resources.

## Authorized files (exactly 5 — nothing else may change)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | `workbenchResult`'s `promptRuleIds` → ordered union |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `generateItem`'s `promptRuleIds` → union; `generateOperatorDirectedAnswer` add param, add fact block, revise system message |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | add 2 cases (C-1) |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | add 3 cases + update 1 existing Line A case (C-2, C-3) |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | add 1 case (C-4) |

## Invariants (exact text in plan §关键不变量; acceptance greps in §验收标准)

- **I-1** `promptRuleIds` source changes from `factRuleIds` to ordered union `(factRuleIds + boundRuleIds).distinct()` — evidence first, bound appended; `sendQaRuleIds` NEVER touched. Two sites: `QaFactSelectionService.workbenchResult`, `AiReplyDraftService.generateItem`'s `ResolvedQaRules.promptRuleIds`.
- **I-2** operator-directed fact channel is PARALLEL to answer basis: fact text only from server-injected bound facts (model must not invent); answer basis remains the skeleton/口径; fact channel never authorizes actions. Exact constraint phrasing in plan (includes `neither the answer basis nor the attached reference facts`).
- **I-3** injected facts fetched per `boundRuleIds` from `qaRuleRepository` CURRENT values, skipping rules with empty `answerBody` — copy the existing knowledge-block style at `AiReplyDraftService.kt:2281-2296` (`buildFreeFormUserContent`). Never reuse cached/snapshotted bodies.
- **I-4** when `boundRuleIds` is empty or equals `factRuleIds`, everything is identity: `promptRuleIds` unchanged and operator-directed prompt text gets NO added paragraph (no empty "Facts the operator attached" section).
- **I-5** injected facts must still pass `findViolations(candidate, allowedActions)` and `rejectNonEnglishItemAnswer` at `generateOperatorDirectedAnswer`'s output validation — do NOT relax either for injected facts.

## Required commands (from this plan's `## 验证命令` + shared authority)

Run plan §验证命令 exactly: C-1 two single tests, C-2 three single tests, C-4 one single test, plus shared suite from `00-execution-order.md`. Pass criteria: Maven exit 0, `Failures: 0, Errors: 0`; node `# fail 0`; checks silent.

## Scope check (plan A-9)

`git diff --name-only <base>..HEAD` must output exactly the 5 authorized paths. `TrustReplyWorkbenchService.kt`, `AiReplyGroundedContentPlanner.kt`, `AutoReplyConfidenceScorer.kt`, `AiReplyReviewAuditService.kt`, `PendingMailOperationService.kt`, `src/main/resources/static/` must NOT appear.

## Downstream interfaces (must be preserved exactly)

- Line A's operator-directed prompt assertions (e.g. `prompt.contains("operator-provided answer basis")`) must still hold after the revision (plan I-4 consequence) — the updated Line A test case proves this.
- `sendQaRuleIds`/`sendIds` remain composed ONLY from `factRuleIds` (outbound audit `mail_record_qa_rule` records evidence only).
- `canonicalizeClaims` (`TrustReplyWorkbenchService.kt:1424-1425`) and `AiReplyGroundedContentPlanner` (`:73-80`) continue reading `factRuleIds` — claims must stay evidence-grounded.
- Status/`allowedHandlings`/`factRuleIds` semantics unchanged from P2a.

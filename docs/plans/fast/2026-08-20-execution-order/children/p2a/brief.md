# Fast-P Child Brief — P2a

- Plan (authoritative contract): `docs/plans/2026-08-20/P2a-bound-vs-evidence-split.md` (commit:15dbf44ea93cfab28f24bfb3ab017fa60ad3dbc8)
- Child base SHA: set to P1 terminal Code head (filled at dispatch)
- Depends on: P0, P1
- Downstream consumers: P2b (consumes `boundRuleIds`)

## Global constraints (from `docs/plans/2026-08-20/00-execution-order.md`)

1. **Order authority**: `00-execution-order.md` wins on conflict.
2. **Symbol-first relocation**: line numbers stale (07:34 UTC worktree); locate by symbol, lines cross-check only. Symbol not found = real problem.
3. **JDK 11 mandatory**: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
4. **Verification command authority**: shared commands only in `00-execution-order.md` 「验证命令」.
5. **需求方决策 (hard)**: manual binding does NOT change item status — bound `UNSUPPORTED` stays `UNSUPPORTED` (else `allowedHandlings` loses 「按回答说明生成」, clashing with Line A). This is plan I-2 and 00-execution-order decision #1.
6. **P1 prerequisite**: P1's `droppedBindingRuleIds`/`droppedFactRuleIds` fields + `data-role="item-facts-dropped"` markup are in the base. P2a keeps field names/chain, rewrites semantics and front-end copy (plan I-6/S-1). Do NOT delete the markup; do NOT keep P1's old wording ("未被采纳" would contradict visible chips).
7. **Status/allowedHandlings untouched**: `buildRequestFact` status computation and `allowedHandlings`/`recommendedHandling` stay byte-identical (plan What must NOT change #1). `factRuleIds` value logic untouched — still "system-recognized evidence" (#2).
8. **No new CSS classes, no inline styles, `styles.css` untouched**; chips use existing front-end path only (S-2: no second chips logic, no front-end `boundRuleIds` field).

## Authorized files (exactly 8 — nothing else may change)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `RequestFactItem` add 1 field (`boundRuleIds`) |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | three `resolve*Selection` assignments + assertion compares `boundRuleIds` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | switch the four projections (B-1..B-4), nothing else |
| 4 | `src/main/resources/static/trust-reply-workbench.js` | change ONE Chinese string only (C-1) |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | add 4 cases (D-1) |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | add 3 cases (D-2) |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` | add 1 case (D-3) |
| 8 | `src/test/js/trustReplyWorkbench.test.js` | add 2 cases (D-4) |

## Invariants (exact text in plan §关键不变量; acceptance greps in §验收标准)

- **I-1** `boundRuleIds: List<Long> = emptyList()` on `RequestFactItem`; default only for source compat — production paths assign explicitly: `resolveMatrixSelection` → `boundRuleIds = explicitIds` (verbatim, ordered); `resolveAutoSelection` → `boundRuleIds = item.factRuleIds`; `resolveLegacySelection` → its own assignment.
- **I-2** switch EXACTLY four "operator view" projections from `item.factRuleIds` to `item.boundRuleIds`: ① `canonicalMatrix`'s `factRuleIds =`; ② `resolveCanonicalSelection`'s `requestEvidenceVersion(key, item.factRuleIds, …)`; ③ `buildInitialItemVersions`'s same-shape call; ④ `toCoverage`'s `factRuleIds =`. No more, no fewer.
- **I-3** `canonicalMatrix` and `toCoverage` switch in the SAME commit and stay byte-equal (front-end `applyBootstrap` equality guard at `trust-reply-workbench.js:585-595` throws `TRUST_REPLY_FACT_SELECTION_INVALID` if only one switches).
- **I-4** equality assertion in `resolveMatrixSelection` now compares `item.boundRuleIds != explicitIds` (恒真 defensive assertion, still throws `TRUST_REPLY_FACT_SELECTION_INVALID`); must NOT compare `item.factRuleIds` again (that is the original defect).
- **I-5** for items with no prior drop (`boundRuleIds == factRuleIds`), `requestEvidenceVersion` input is byte-identical → `evidenceSetVersion`/`versionId` MUST be unchanged; existing locked items stay valid.
- **I-6** `droppedBindingRuleIds`/`droppedFactRuleIds` keep names + chain, value stays `explicitIds - item.factRuleIds`, but front-end copy changes to "已绑定但不会作为本条回答的依据" semantics (verbatim string in plan S-1).

## Required commands (from this plan's `## 验证命令` + shared authority)

Run plan §验证命令 exactly: D-1 four single tests, D-2 three single tests, D-3 one single test, D-4 `node --test src/test/js/trustReplyWorkbench.test.js`, plus shared suite from `00-execution-order.md`. Pass criteria: Maven exit 0, `Failures: 0, Errors: 0`; node `# fail 0`; checks silent.

## Scope check (plan A-8)

`git diff --name-only <base>..HEAD` must output exactly the 8 authorized paths. `AiReplyGroundedContentPlanner.kt`, `AutoReplyConfidenceScorer.kt`, `AiReplyReviewAuditService.kt`, `PendingMailOperationService.kt`, `AiTrainingController.kt`, `UnmatchedInboundMailController.kt`, `styles.css` must NOT appear.

## Downstream interfaces (must be preserved exactly)

- P2b consumes: `RequestFactItem.boundRuleIds` field; three `resolve*Selection` assignments; the four projections now on `boundRuleIds`; assertion comparing `boundRuleIds`. P2b changes `promptRuleIds` sources and adds the operator-directed fact channel; it must NOT re-touch status, `factRuleIds` semantics, or `sendQaRuleIds`.
- P2b requirement from 00-execution-order: outbound audit (`sendQaRuleIds`) stays evidence-only — P2a's `factRuleIds` remains the evidence set.
- HTTP contract: `TrustReplyRequestCoverage.factRuleIds` field name/type unchanged (value semantics migrate to bound set). Front-end chips render from `request.factRuleIds` automatically — no front-end schema change.
- `TRUST_REPLY_FACT_SELECTION_INVALID` still thrown for genuinely illegal input (matrix size mismatch, disabled rule) and now also as the constant-true defensive assertion.

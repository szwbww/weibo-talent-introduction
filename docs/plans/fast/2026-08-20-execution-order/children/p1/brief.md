# Fast-P Child Brief — P1

- Plan (authoritative contract): `docs/plans/2026-08-20/P1-fact-binding-drop-not-fatal.md` (commit:15dbf44ea93cfab28f24bfb3ab017fa60ad3dbc8)
- Child base SHA: set to P0 terminal Code head (filled at dispatch)
- Depends on: P0
- Downstream consumers: P2a (rewrites P1's prompt semantics + wording), P2b

## Global constraints (from `docs/plans/2026-08-20/00-execution-order.md`)

1. **Order authority**: `00-execution-order.md` wins on conflict.
2. **Symbol-first relocation**: plan line numbers are from the 07:34 UTC worktree and may be stale; locate by symbol/function name, line numbers cross-check only. Symbol not found = real problem.
3. **JDK 11 mandatory**: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
4. **Verification command authority**: shared commands only in `00-execution-order.md` 「验证命令」; this plan's section lists this child's specific runs.
5. **需求方决策**: manual binding never changes item status; outbound audit only true evidence.
6. **P0 prerequisite**: P0 is merged in this branch (its code is the base). P1's A-1 reads the 422 `code` and A-5 uses the P0 reset button.
7. **P2a semantics migration**: the prompt introduced here (`data-role="item-facts-dropped"`) is TEMPORARY wording. P2a will change its meaning and text from "未被采纳（已丢弃）" to "已绑定但不作为依据". Do NOT delete the markup, and do NOT design anything that prevents P2a from rewriting the copy. Field names `droppedBindingRuleIds` / `droppedFactRuleIds` and the transfer chain are the P2a contract — keep them stable.
8. **No new CSS classes, no inline styles, `styles.css` untouched**; reuse `class="muted"` exactly per plan S-1.

## Authorized files (exactly 7 — nothing else may change)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | `RequestFactItem` add 1 defaulted field |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | change the one `:199-204`-region degradation point (only place un-throwing) |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | `TrustReplyRequestCoverage` add 1 field; `toCoverage` add 1 line |
| 4 | `src/main/resources/static/trust-reply-workbench.js` | carry new field, render prompt, add name-lookup helper |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | add 4 cases (C-1) |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` | add 2 cases (C-2) |
| 7 | `src/test/js/trustReplyWorkbench.test.js` | add 3 cases (C-3) |

## Invariants (exact text in plan §关键不变量; acceptance greps in §验收标准)

- **I-1** degradation happens at exactly ONE place: `resolveMatrixSelection`'s `if (item.factRuleIds != explicitIds) throw ...`. After change it computes `droppedBindingRuleIds = explicitIds - item.factRuleIds.toSet()` (keeping `explicitIds` order) and continues using `item.factRuleIds` as the item's fact set.
- **I-2** dropped-binding info is per-item: `RequestFactItem.droppedBindingRuleIds: List<Long> = emptyList()`; `TrustReplyRequestCoverage.droppedFactRuleIds: List<Long> = emptyList()`.
- **I-3** new fields are shadow fields: NEVER in `requestEvidenceVersion(...)` inputs (`TrustReplyWorkbenchService.kt`), NEVER in `canonicalMatrix(...)` output, never in any identity hash or outbound text.
- **I-4** the two server projections stay byte-equal: `requestFactSelections` (from `canonicalMatrix`) and `requestCoverage[].factRuleIds` (from `toCoverage`) both take only `item.factRuleIds`; new field is a parallel THIRD projection only.
- **I-5** self-healing: frontend `requestFromCoverage` rebuilds `state.requests[].factRuleIds` from the filtered coverage; next `serializeRequestFactSelections()` matches server's filtered result; same operation never triggers degradation twice.

Style S-1: reuse `class="muted"` exactly as the stale hint at `trust-reply-workbench.js:1851-1855`; prompt text per plan S-1 (verbatim copy in plan).

## Required commands (from this plan's `## 验证命令` + shared authority)

Run plan §验证命令 exactly: C-1 four single tests, C-2 two single tests, C-3 `node --test src/test/js/trustReplyWorkbench.test.js`, plus shared suite from `00-execution-order.md`. Pass criteria: Maven exit 0, `Failures: 0, Errors: 0`; node `# fail 0`; checks silent.

## Scope check (plan A-9)

`git diff --name-only <base>..HEAD` must output exactly the 7 authorized paths. `styles.css`, `AiReplyGroundedContentPlanner.kt`, `PendingMailOperationService.kt`, `AutoReplyConfidenceScorer.kt` must NOT appear.

## Downstream interfaces (must be preserved exactly)

- P2a keeps `droppedBindingRuleIds`/`droppedFactRuleIds` field names + transfer chain; changes only semantics/wording. Keep field types `List<Long>` and the `explicitIds - item.factRuleIds` computation.
- P2a rewrites the `data-role="item-facts-dropped"` prompt copy — the markup element must remain present with identical class and `data-role` so P2a can find it.
- Bootstrap still returns 422 with `TRUST_REPLY_FACT_SELECTION_INVALID` for genuinely illegal input (matrix size mismatch, disabled rule) — plan What must NOT change #1, and P0's error-code UI must keep working on it.
- Truly accepted bindings show as chips exactly as before; no prompt for them (A-6).
- Dropped bindings never enter AI context or outbound audit (A-7).

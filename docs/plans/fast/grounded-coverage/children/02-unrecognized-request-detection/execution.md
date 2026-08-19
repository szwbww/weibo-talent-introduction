# Execution Report — 02-unrecognized-request-detection (P2a)

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/2026-08-19/02-unrecognized-request-detection.md`
- Plan SHA-256: `440b5b5536b1ae1fdef28e3c5541e3fd7660ba39ffcf920c6105d5177a9ebea6`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage/docs/plans/2026-08-19/02-unrecognized-request-detection.md@440b5b5536b1ae1fdef28e3c5541e3fd7660ba39ffcf920c6105d5177a9ebea6`
- Execution epoch: NEW
- Approval basis: current invocation (fast-p child brief + approved child plan, plan identity gate passed at start and rechecked at handoff)
- Executor: `Impl02UnrecognizedAsk`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage`
- Target branch: `fast/grounded-coverage`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage@fast/grounded-coverage@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-grounded-coverage`
- Pre-execution code SHA: `f5c09382744c0da8a537610af6145974ee1fcaf4` (child 01 terminal code head; HEAD also carried controller evidence commit `481db67` — docs only, untouched)
- Post-execution code SHA (implementation commit): `1df49e8e37f24d4040e27e1d78052a8645253e2b`
- Evidence HEAD: N/A (controller commits fast-p evidence separately; docs/plans/fast/** left uncommitted)
- Implementation boundary: `f5c09382744c0da8a537610af6145974ee1fcaf4..1df49e8e37f24d4040e27e1d78052a8645253e2b`

---

## Changes per file

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/InboundAskEnumerator.kt` (NEW) | 阶段 B: `EnumeratedAsk` / `AskEnumeration` data classes, `InboundAskEnumerator` `@Service` (`enumerate`/`parse`, I-1 verbatim quote validation with whitespace folding + ≥8 chars + dedupe + 12-item cap with explicit truncation log, I-4 fail-open on every failure path), `claimed()` overlap helper (I-7), plan-verbatim `ASK_ENUMERATION_SYSTEM_PROMPT`, and pure `buildAskEnumLogLine()` (D-4 fixed `[ASK_ENUM]` format) |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/config/AskEnumeratorProperties.kt` (NEW) | `enabledForAutoReply: Boolean = false` (I-6 default false; key `talent-introduction.llm.ask-enumerator.enabled-for-auto-reply`), self-registered via `@EnableConfigurationProperties` inside the authorized file |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | 阶段 A: `MatchedIntentSpan`, `matchIntentsWithSpans` (same canonicalize→alias→disambiguate→next-stages timing logic, plus canonical→original index-map restoration with word-boundary expansion so `programme`→`program` never truncates a span), `matchIntents` reduced to a thin wrapper, `wordBoundaryContains`→`wordBoundaryRanges` split. Zero behaviour change for existing callers (N4/IP-1) |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | 阶段 C-1/C-3: nullable `InboundAskEnumerator` + `AskEnumeratorProperties` constructor deps (historical single-arg construction sites compile unchanged); `select()` auto path gates enumeration on `enabledForAutoReply` (I-6) and emits the auto-path `[ASK_ENUM]` line; `selectForWorkbench()` always enumerates (I-6) and threads the result through the private resolvers; `buildRequestFact` gained optional `askEnumeration` + `requestRange` and fills `unrecognizedAsks` via span claiming (I-7) with per-request region attribution; `workbenchResult` computes the four shadow fields. `status` when-block, `groundedRequestCount`/`unsupportedRequests` expressions character-identical (N1/N2) |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | 阶段 C-2/C-3: `TrustReplyUnrecognizedAsk` DTO, `TrustReplyRequestCoverage.unrecognizedAsks` (default empty), `toCoverage` fills it, bootstrap emits exactly one `[ASK_ENUM]` line with real `source`/`contactId`/`kind`. `requestKey`/`versionId`/`evidenceSetVersionWithMapping`/`allowedHandlings`/`recommendedHandling` bodies untouched (I-2/N3/N4) |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | **Required location deviation (see Deviations):** only the two data-class definitions — `RequestFactItem.unrecognizedAsks` and `ResolvedQaRules.unrecognizedAskCount/enumeratorAvailable/enumeratorEnumerated/enumeratorClaimed`, all with defaults so every existing constructor compiles unchanged. Nothing else in the file changed |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/InboundAskEnumeratorTest.kt` (NEW) | D-1/D-4: I-1 verbatim-keep + 3 discard cases + dedupe + fenced JSON + 12-cap; I-4 five fail-open cases; fixed `[ASK_ENUM]` line format assertions |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt` | D-2: I-3 shadow equivalence (3 vs 0 unrecognized → identical status/counts/factIds); I-7 double-intent overlap claimed once; 骨科 letter fully claimed (unrecognized=0); visa letter unrecognized≥1; multi-request region attribution; I-6 gating tests (auto path zero `enumerate` calls when flag off; enumerates when on; workbench always enumerates) |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt` | A-2: wrapper equivalence on all fixtures; 骨科 `remuneration`/`intellectual property` span restoration; `programme` spelling span restoration; hyphenated span; `general.answer` empty ranges |

## Commands

All run in the target worktree with JDK 11 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`).

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=… mvn compile -o` | PASS | exit 0 |
| `JAVA_HOME=… mvn test -Dtest='InboundAskEnumeratorTest,QaFactSelectionServiceTest,AiReplyIntentCatalogTest' -o` | PASS | exit 0; `Tests run: 91, Failures: 0, Errors: 0` (InboundAskEnumeratorTest 13, AiReplyIntentCatalogTest 35, QaFactSelectionServiceTest 43) |
| `JAVA_HOME=… mvn test -o` (full regression gate) | PASS | exit 0; `Tests run: 2591, Failures: 0, Errors: 0, Skipped: 4` (incl. 2 passes from an orphaned scratch-test class compiled minutes earlier; see note) |
| `JAVA_HOME=… mvn clean package -o` (plan 验证命令; clean-state rebuild) | PASS | exit 0; `Tests run: 2589, Failures: 0, Errors: 0, Skipped: 4`; node suites 651/651 |
| `git diff --check` | PASS | exit 0, no output |

Note on the 2591 vs 2589 delta: the full `mvn test` ran incrementally and surefire picked up an orphaned `ScratchSpanDebugTest.class` (2 passing debug tests whose source had already been deleted). The subsequent `mvn clean package` removed it; **2589 is the authoritative clean-state full-suite count** (199 test classes, 0 failures, 0 errors, 4 skipped). Both runs show zero failures/errors.

The optional Flyway migration test (`-DmigrationIt=true`) was skipped per the plan ("默认跳过", needs local Docker).

## Verification evidence (invariants / gates)

- **I-1**: 4 discard cases + 1 verbatim-keep case (with `mail.substring(range) == quote`) + dedupe + cap, all green in `InboundAskEnumeratorTest`.
- **I-2**: `git diff` shows zero changed lines in `requestKey` / `versionId` / `evidenceSetVersionWithMapping` bodies (TrustReplyWorkbenchService diff touches only the DTO, bootstrap log, `toCoverage`, and the new `extractorKinds` helper); `grep -n "unrecognized\|askEnumer"` over the three function line regions returns zero hits.
- **I-3**: shadow-equivalence test green; `grep -c "^[+-].*RequestGroundingStatus"` on the QaFactSelectionService diff = 0 (status `when` block character-identical).
- **I-4**: five fail-open cases green (llm disabled / provider null / chat throws / non-JSON / empty array → `available=false, asks=[]`, no exception).
- **I-5**: `grep -rn "unrecognizedAsks\|EnumeratedAsk" src/main/kotlin` hits only InboundAskEnumerator.kt, AiReplyDraftService.kt (data classes), QaFactSelectionService.kt, TrustReplyWorkbenchService.kt — **not** AiReplyPointByPointComposer.kt / AiReplyGroundedContentPlanner.kt / AiReplyGroundedDraftMaterializer.kt.
- **I-6**: `AskEnumeratorProperties.enabledForAutoReply` default `false` (source); Mockito zero-call assertion for the auto path with the flag off; workbench always enumerates test green.
- **I-7**: `remuneration and intellectual property` ask overlaps both `finance.arrangements` and `ip.arrangements` spans and is claimed exactly once (no negative counts).
- **N1/N2/N3/N4/N6**: full suite green; `AiReplyIntentCatalogTest` pre-existing fixtures run verbatim against the `matchIntents` wrapper; `matchIntents == matchIntentsWithSpans().map { definition }` asserted for all P1 fixtures.
- **Prompt**: `ASK_ENUMERATION_SYSTEM_PROMPT` verified line-for-line identical (whitespace-stripped) to the plan's verbatim block (8/8 lines).
- **Log format**: `[ASK_ENUM] source={} contactId={} available={} enumerated={} claimed={} unrecognized={} kind={}` asserted as exact strings in `InboundAskEnumeratorTest` (D-4).
- **Authorized scope**: commit contains 9 files — the 8 authorized files plus `AiReplyDraftService.kt` (data-class-only; see Deviations). `docs/plans/fast/**` untouched (ledger.md remains uncommitted for the controller).

## Deviations

1. **`AiReplyDraftService.kt` edited (data classes only).** The plan's C-1 requires `RequestFactItem.unrecognizedAsks` and `ResolvedQaRules.unrecognizedAskCount` (+ enumerator shadow fields), but both data classes physically live in `AiReplyDraftService.kt`, not in `QaFactSelectionService.kt` as the plan's 变更文件清单 assumes (stale file attribution, same category as the plan's stale line numbers). The brief's own "Downstream interfaces" contract mandates these fields. The edit is confined to the two data-class definitions (12 insertions / 2 deletions) with defaults, so no other construction site changes. This is the single scope addition; it is required for the implementation to exist at all.
2. **`selectForWorkbench` signature unchanged; enumeration happens inside `QaFactSelectionService`.** The plan's C-2 "透传" through a new `selectForWorkbench` parameter is impossible without breaking non-authorized tests that stub `selectForWorkbench("What?", null, listOf(9L), true)` with exact-argument Mockito stubs/verifies (a 5th parameter would make production calls mismatch those stubs). Equivalent wiring: `selectForWorkbench` enumerates internally (workbench path always enabled, I-6), the shadow fields travel on `ResolvedQaRules`, and `TrustReplyWorkbenchService` emits the bootstrap log from those fields — one LLM call per selection, no stub breakage.
3. **Two extra shadow fields on `ResolvedQaRules`** (`enumeratorEnumerated`, `enumeratorClaimed`, defaulted): needed so the workbench can emit the fixed-format `[ASK_ENUM]` line (enumerated/claimed counts) without a second LLM call. The plan specifies `unrecognizedAskCount` + `enumeratorAvailable`; these two are read-only measurement fields in the same shadow family.
4. **Auto-path log emitted from `QaFactSelectionService.select()` with `source=AUTO contactId=0`.** The plan's C-3 wants a line "在自动路"; the auto-reply services are not in the authorized file list, so the only authorized code on the auto path is `select()`, which has no contact identity. The fixed field names/order are preserved; values are `AUTO`/`0` for that path (workbench path logs real `source`/`contactId`). Consequence: `select()` also logs on the other callers it serves (training/unmatched generate, PendingMailOperationService suggest/preflight) — extra lines beyond "每次自动回复决策", documented here; the log shape remains greppable and the flag-off path logs `available=false`.
5. **`AskEnumeratorProperties` registered via `@EnableConfigurationProperties` inside its own file** instead of `RestTemplateConfig.kt` (not authorized); same mechanism as `TaskAuditRetentionScheduler`.

## Freshness

- Plan identity rechecked: YES (SHA unchanged `440b5b55…`)
- Worktree identity rechecked: YES (root/branch/git-dir match, incl. expect-flags before commit)
- Reported commit reachable from target branch: YES (`1df49e8e` is HEAD of `fast/grounded-coverage`)
- Required commands run this invocation: YES (focused, full, `git diff --check`, plus `mvn clean package`)
- Historical evidence used only as baseline: YES (child 01's stale surefire reports ignored; all counts from fresh runs)

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` against this child plan.

---

### Finalization note (2026-08-19, controller)

Evidence re-recorded in the terminal evidence commit to include all four child artifacts (brief/execution/verify-log/fix-log). No content change beyond this note; fix_round=0.

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/2026-08-26/02-unrecognized-asks-and-orphan-keys.md
Plan SHA-256: 0f81dd9033236fca5f616e80bed9597faea9b67fbc10c39f638847804cd3f1e2
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order/docs/plans/2026-08-26/02-unrecognized-asks-and-orphan-keys.md@0f81dd9033236fca5f616e80bed9597faea9b67fbc10c39f638847804cd3f1e2
Execution epoch: NEW
Approval basis: current invocation (child c2 brief `docs/plans/fast/2026-08-26-execution-order/children/c2/brief.md`, bound to plan identity above)
Executor: C2Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order
Target branch: fast/2026-08-26-execution-order
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-26-execution-order@fast/2026-08-26-execution-order@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-08-26-execution-order
Pre-execution code SHA: de5e130a84fba33296ea906734a1c7f071e3383a (c1 terminal Code head; HEAD was beae04c7e8f7064163ee1167f2ed4ba5c4d0b6d6 including c1 evidence/ledger docs commits)
Post-execution code SHA: f6dc048359b0d7f46b335f640d78033fa7747a27
Evidence HEAD: N/A (no separate evidence commit; single implementation commit per brief)
Implementation boundary: de5e130a84fba33296ea906734a1c7f071e3383a..f6dc048359b0d7f46b335f640d78033fa7747a27

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1.1/T1.3 — status cap composed with 01 I-6 lift in ONE status expression (both plan numbers in comments; available=false never caps, I-3) | IMPLEMENTED | `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` | `QaFactSelectionUnrecognizedStatusTest` 3/3 green (I-1 GROUNDED→PARTIAL, I-3 available=false→GROUNDED, PARTIAL not further downgraded); `QaFactSelectionServiceTest` 69/0/0; full suite green |
| T2.1 — two entries appended at END of `QaCoverageKeyCatalog.catalog` `listOf(...)` with V105-style comment (I-4) | IMPLEMENTED | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` | Parity test `normalizeAndValidate keeps existing key order...` asserts `programme.purpose,fees.policy` order unchanged and both new keys sort after `governance.sponsor_level`; `QaRuleManagementServiceTest` 60/0/0 |
| T2.2 — exactly one `alternativeCoverageKeys` line on `application.next_stages` (I-2/I-5) | IMPLEMENTED | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` | git diff = single added line (plus the required trailing comma on the preceding line, matching plan T2.2 literal); `AiReplyIntentCatalogTest` 35/0/0; `TrustReplyWorkbenchItemFlowTest` 52/0/0 |
| T2.3 — `QaCoverageKeyIntentParityTest` (I-5 (a)+(b), exception set, exception-set-proves-itself, reachability) | IMPLEMENTED | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt` | 5/5 green |
| T2.4 — `QaFactSelectionUnrecognizedStatusTest` (I-1/I-3, mock form from `QaFactSelectionServiceTest:20-22`, `ask(...)` fixture from `:1427-1445`) | IMPLEMENTED | `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionUnrecognizedStatusTest.kt` | 3/3 green |

### Commands
All commands ran freshly this invocation after the final implementation state, with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.

| Command | Result | Evidence |
|---|---|---|
| `mvn test -Dtest=QaCoverageKeyIntentParityTest` | PASS | exit 0; Tests run: 5, Failures: 0, Errors: 0 |
| `mvn test -Dtest=QaFactSelectionUnrecognizedStatusTest` | PASS | exit 0; Tests run: 3, Failures: 0, Errors: 0 |
| `mvn test -Dtest=QaFactSelectionServiceTest` | PASS | exit 0; Tests run: 69, Failures: 0, Errors: 0 |
| `mvn test -Dtest=AiReplyIntentCatalogTest` | PASS | exit 0; Tests run: 35, Failures: 0, Errors: 0 |
| `mvn test -Dtest=QaRuleManagementServiceTest` | PASS | exit 0; Tests run: 60, Failures: 0, Errors: 0 |
| `mvn test -Dtest=TrustReplyWorkbenchItemFlowTest` | PASS | exit 0; Tests run: 52, Failures: 0, Errors: 0 |
| `mvn test` (full gate) | PASS | exit 0; Tests run: 2868, Failures: 0, Errors: 0, Skipped: 4; BUILD SUCCESS |
| `git diff --check` | PASS | exit 0, silent |

No test-class substitution was needed: `AiReplyIntentCatalogTest` and `QaRuleManagementServiceTest` both exist in `src/test/kotlin/**/*Test.kt`.

### Changed Files (commit f6dc048359b0d7f46b335f640d78033fa7747a27)
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt` — T1.3: single composed status expression (01 I-6 lift first, 02 I-1/I-3 cap second; both plan numbers in comments; `askEnumeration.available && unrecognizedAsks.isNotEmpty() && naturalStatus == GROUNDED → PARTIAL`; available=false never caps).
- `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt` — T2.1: appended `work.time_commitment` and `work.advisory_duration` Entries at END of `listOf(...)` with 计划 02 (I-4) comment.
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt` — T2.2: one added line `alternativeCoverageKeys = listOf("application.required_materials")` on `application.next_stages`; key/title/requestAliases untouched (requestKey hashes byte-identical).
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt` — T2.3: I-5 parity tests (a)(b), exception set `KNOWN_UNREFERENCED_KEYS` = {general.answer, work.relocation} with per-entry rationale, exception-set-must-matter assertion, I-4 append-order/`normalizeAndValidate` ordering assertion, IP-5 reachability assertion.
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionUnrecognizedStatusTest.kt` — T2.4: I-1/I-3 status cap tests via `selectForWorkbench` with mocked `InboundAskEnumerator`.

### Deviations
1. `scripts/worktree_identity.py` crashes on stale deleted worktree entries (`/sessions/rcw-01pxobmo3wj1bdm5hjgawb7s/...` locked/initializing; `Path.resolve(strict=True)` → FileNotFoundError). The Target Worktree Gate was replicated manually with the script's exact git commands (`rev-parse --show-toplevel`, `branch --show-current`, `rev-parse --absolute-git-dir`, `rev-parse --git-common-dir`, `rev-parse HEAD`) before/after/at commit; all values match the expected worktree identity (see Worktree ID above).
2. `application.timeline` is referenced by no static `definitions` entry; it is referenced only via the runtime `next_stages` timing composition (`AiReplyIntentCatalog.kt:424` — `requiredCoverageKeys = listOf("application.steps", "application.timeline")` when `asksTiming && !hasWorkIntents`). The plan's C-1 audit counts it as referenced (whole-file literal extraction), so the parity test models the same methodology (`intentReferencedKeys()` includes `application.timeline` with a documented comment). `KNOWN_UNREFERENCED_KEYS` remains exactly {general.answer, work.relocation} as specified by the plan.
3. `AiReplyIntentCatalog.kt` diff includes the required trailing comma on the preceding `requiredCoverageKeys = listOf("application.steps")` line (needed to append the new list element; matches plan T2.2 literal exactly). No semantic change to any key/title/alias/required key.
4. `docs/plans/fast/2026-08-26-execution-order/ledger.md` is modified in the working tree by the controller — left untouched, not committed.

### Freshness
- Plan identity rechecked: YES (SHA-256 unchanged `0f81dd…cd3f1e2` at end of execution)
- Worktree identity rechecked: YES (manual replication with expect-root/branch/git-dir, at commit time)
- Reported commits reachable from target branch: YES (`f6dc048` is HEAD of `fast/2026-08-26-execution-order` and contains exactly the 5 authorized files; no fast-p files)
- Required commands run this invocation: YES (all 8, after final state)
- Historical evidence used only as baseline: YES (c1 state inspected via `git show de5e130`; all command evidence collected fresh this invocation)

### Remaining Blocker
- None

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`

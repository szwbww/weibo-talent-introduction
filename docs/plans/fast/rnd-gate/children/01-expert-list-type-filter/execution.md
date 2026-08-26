# Execution Report — 01-expert-list-type-filter

## Execution Result: PLAN_CONFLICT

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate/docs/plans/fast/rnd-gate/children/01-expert-list-type-filter/brief.md`
- Plan SHA-256: `363972e8d6517e96d78eb99b334f8ec96439556eaa8dba0ae238e3ed27b482d6`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate/docs/plans/fast/rnd-gate/children/01-expert-list-type-filter/brief.md@363972e8d6517e96d78eb99b334f8ec96439556eaa8dba0ae238e3ed27b482d6`
- Execution epoch: NEW
- Approval basis: current invocation (brief.md is the approved contract)
- Executor: Impl01ListTypeFilter
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate`
- Target branch: `fast/rnd-gate`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate@fast/rnd-gate@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-rnd-gate`
- Pre-execution code SHA: `f2935072c819a9167e75220a6a959b0769462fde`
- Post-execution code SHA: `N/A` (no commit created — PLAN_CONFLICT)
- Evidence HEAD: `N/A`

> Note: `scripts/worktree_identity.py` cannot run in this environment — the common git dir
> (`/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`) holds stale `worktree /sessions/…`
> registrations (locked, "initializing") whose paths do not exist, and the script's
> `resolve_root()` raises FileNotFoundError on them. Identity was verified manually with the
> exact same git queries (root/branch/git-dir/HEAD all match the plan's expectations).

## Status summary

All six authorized files are fully implemented (Tasks 1–5), all JS verification passes
(737/737), the targeted backend test passes (65/65), and compilation succeeds. The full
`mvn test` / `mvn clean package` gate fails on exactly ONE test —
`OperatorStatusWriteSeamGuardTest` — because the plan's **mandatory** Task 3 changes to
`ExpertIndexController.kt` and Task 1 changes to `ExpertSearchService.kt` shift three
line-number-pinned entries in that test's `EXCLUDED_NOISE_SITES` list (see Blocker).

The repair is mechanical and uniquely determined (three `path`/`line` updates, contexts
unchanged), but the affected file `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
is **not** in the plan's Authorized Files and the assignment forbids modifying files outside
that list. Per execute-p ("Edit an unlisted implementation or test file" is prohibited;
"If completion requires an unlisted file … stop with PLAN_CONFLICT") the execution stops
here for a human amendment.

No commit was created: the plan's commit rule presupposes a complete, passing run, and
committing an implementation whose mandatory verification gate fails would misrepresent state.

## Changed files (all authorized, none committed)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | Companion: `ALLOWED_EXPERT_TYPES` (derived from `ExpertType.values()` + `"UNCLASSIFIED"`), `expertTypesFilter` (trim/filter/distinct → null on empty → single `bool.should` + `minimum_should_match:1` with per-value `require`), private `expertTypePredicate` (`UNCLASSIFIED` → `must_not exists expertClassification.type`; others → `term expertClassification.type`). `searchExperts` and `buildExpertFilters` gain trailing `expertTypes: List<String> = emptyList()`; filter appended after the discipline block; the three aggregation callsites (:949/:994/:1101) untouched (defaults keep them byte-identical). |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | `listExperts` gains trailing `@RequestParam(required = false) expertType: List<String>? = null`, passed as `expertType.orEmpty()`. `ExpertIndexResponse` gains 4 trailing nullable fields (`expertType`, `expertSendable`, `expertProductionScore`, `expertResearchScore`) assembled from `expert.expertClassification` (all null when classification missing). (`ExpertContactView` does not exist in this tree; the plan's :388-400/:438-448 refs are the `ExpertIndexResponse` view — line drift, per plan's own re-grep instruction.) |
| 3 | `src/main/resources/static/index.html` | S-1: `#expertTypeTagSelect` skeleton (span.toolbar-label > span.tag-select + 7 `.tag-chip` buttons, verbatim per plan) inserted after the discipline label, before `H-Index ≥`. S-3: STEM option visible text `理工科` → `理工科（含医学）`; all four `value` attributes verbatim. |
| 4 | `src/main/resources/static/app.js` | New top-level `expertTypeActiveValues()`; chip-click binding `initExpertTypeTags` (toggle `.active` + `reloadContactsFromStart()`) after `initHasFieldTags`; registration point 1 in `loadContacts` (read at top + repeated `params.append("expertType", v)` in the ES branch + mapping of `expertType`/`expertProductionScore`/`expertResearchScore`); registration point 2 in `gateSummaryParams` (same append); registration point 3 in `updateFilterBadge` (`expertTypeActiveValues().length > 0`); NOT added to the change-listener array (registration point 4). S-2: row chip `span.tag-chip` (no `.active`, no inline style) with `title="生产分 X / 科研分 Y"` (escapeHtml'd) rendered only when `contact.expertType` present; Chinese labels map local to `renderContactListItems` (reachabilityMeta vm-sandbox precedent). |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 9 new tests: whitelist derivation (enum + 1), empty→null (incl. blank strings), single term predicate, multi-value single should + `minimum_should_match:1` + no top-level `filter` key, trim/dedup, UNCLASSIFIED `must_not exists`, invalid value throws, `searchExperts` empty-vs-default query bodies equal (filter size 2), `searchExperts` multi-term should filter passes to ES. |
| 6 | `src/test/js/expertTypeFilter.test.js` | New. 4 tests: chip click toggles + `expertTypeActiveValues()` + reload requested; no `expertType` param when nothing selected; two selected chips → `getAll("expertType").length === 2`; badge counts active type chips (group semantics: 0 → 1 → 0, hidden toggles). |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest` | PASS | exit 0; Tests run: 65, Failures: 0, Errors: 0, Skipped: 0 |
| `node --test src/test/js/expertTypeFilter.test.js` | PASS | exit 0; tests 4, pass 4, fail 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; tests 737, pass 737, fail 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `git diff --check` | PASS | no output |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` | FAIL | exit 1; Tests run: 2839, Failures: 1 (only `OperatorStatusWriteSeamGuardTest`), Errors: 0, Skipped: 4 |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn clean package` | FAIL | exit 1; same single failure (Tests run: 2839, Failures: 1, Errors: 0, Skipped: 4) |

## Blocker (exact, minimal)

`OperatorStatusWriteSeamGuardTest.operator_status write sites exactly match whitelist`
(assertion at `OperatorStatusWriteSeamGuardTest.kt:135`) requires the source-set of
`operatorStatus = ` / `operator_status` hits to equal `ALLOWED_WRITE_SITES`. Its
`EXCLUDED_NOISE_SITES` pins `path` + **exact line number** + context. The plan's authorized
edits shift three of those pinned lines, so the hits re-enter the violation set:

| Noise site (registered) | Current line (after authorized edits) |
|---|---|
| `expert/service/ExpertSearchService.kt:498` `operatorStatus = source.nullableText` | **542** (+44: companion additions + searchExperts param line) |
| `expert/controller/ExpertIndexController.kt:90` `operatorStatus = contact?.operatorStatus` | **91** (+1: expertType request param) |
| `expert/controller/ExpertIndexController.kt:431` `operatorStatus = operatorStatus ?: expert.operatorStatus` | **436** (+5: param + 4 view fields) |

Verified cause chain: guard test PASSES on the clean base tree (`Tests run: 1, Failures: 0` —
checked by stashing all authorized changes) and fails only with this child's authorized
changes applied. Contexts are unchanged; the repair is updating the three `line` values in
`EXCLUDED_NOISE_SITES` (498→542, 90→91, 431→436) — no behavioral change. The file is NOT in
the plan's Authorized Files.

## Required amendment (human)

Authorize a 3-line update to
`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`:
`EXCLUDED_NOISE_SITES` lines `ExpertSearchService.kt 498→542`, `ExpertIndexController.kt
90→91`, `ExpertIndexController.kt 431→436` (contexts unchanged), then re-run the two failing
Maven commands. After the amendment the commit
`feat(fast-p): implement 01-expert-list-type-filter` can proceed (implementation-only, no
`docs/plans/fast/` files, no test logs).

## Deviations

- **typeof guards in app.js (2 sites)** — `loadContacts` and `gateSummaryParams` call
  `expertTypeActiveValues` via `typeof … === "function" ? … : []`. Existing vm-sandbox JS
  tests (`loadContactsFilter.test.js`, `gateTemplateFilter.test.js`) extract single functions
  and run them in sandboxes that do not define the new top-level function; unguarded calls
  throw ReferenceError there. Behavior in the browser is unchanged (function always defined).
- **Local label map** — the S-2 Chinese-name map lives inside `renderContactListItems`
  (following the `reachabilityMeta` precedent documented in a code comment) instead of a
  top-level const, for the same vm-sandbox reason. It is display-only; the filter-value
  whitelist authority remains `ExpertSearchService.ALLOWED_EXPERT_TYPES` (I1-1). The S-1
  skeleton's `data-value` attributes are the plan-mandated verbatim HTML.
- **Badge test semantics** — `updateFilterBadge` counts active filter *groups*
  (`expertTypeActiveValues().length > 0` is one condition, same as hasField), so two chips
  still count 1; test asserts group semantics.
- **`ExpertContactView` naming** — the plan's view name does not exist in this tree; the
  list endpoint's view is `ExpertIndexResponse` (plan instructs re-running greps; line refs
  drift).
- **worktree_identity.py** — cannot run due to stale `/sessions/…` worktree registrations in
  the common git dir (environment issue); identity verified manually with identical queries.
- **No commit** — PLAN_CONFLICT; see Status summary.

## Freshness

- Plan identity rechecked: YES (SHA-256 unchanged `363972e8…`)
- Worktree identity rechecked: YES (branch `fast/rnd-gate`, HEAD `2b80a92`, base `f2935072`)
- Required commands run this invocation: YES (all 7 attempted; 5 PASS, 2 FAIL on the single
  guard-test blocker)
- Historical evidence used only as baseline: YES (base-tree guard-test pass was verified by
  stashing this child's changes, then restoring)

## Next Action

- Obtain human authorization for the 3-line `EXCLUDED_NOISE_SITES` amendment, then re-run
  `mvn test` + `mvn clean package`, commit, and hand off to `verify-p`.

---

# Execution Report — 01-expert-list-type-filter (Epoch 2, RESUME after Amendment A1)

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate/docs/plans/fast/rnd-gate/children/01-expert-list-type-filter/brief.md`
- Plan SHA-256: `ea3d7b6fae59aaf90da398817fc02851190a020ddf8196e95444b5bd46f1d72a` (A1-amended brief; epoch-1 hash `363972e8…` superseded)
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate/docs/plans/fast/rnd-gate/children/01-expert-list-type-filter/brief.md@ea3d7b6fae59aaf90da398817fc02851190a020ddf8196e95444b5bd46f1d72a`
- Execution epoch: RESUME (product tree carried the epoch-1 implementation as uncommitted working-tree changes; this epoch audited, completed, and committed it)
- Approval basis: current invocation (approved brief incl. Amendment A1, recorded in ledger)
- Executor: Impl01Epoch2
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate`
- Target branch: `fast/rnd-gate`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate@fast/rnd-gate@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-rnd-gate`
- Pre-execution code SHA (branch HEAD before this epoch): `f7166b8bfd74082ba38333cc6339420cb5eb69e3` (docs-only: plans + pause + A1 evidence; product base f2935072 with epoch-1 uncommitted changes)
- Post-execution code SHA: `7c703e3d5e51c165ee6c75f316de0f018c44e8df`
- Evidence HEAD: `7c703e3d5e51c165ee6c75f316de0f018c44e8df` (single product commit; no separate evidence commit)
- Implementation boundary: `f7166b8..7c703e3` (product files only; `docs/plans/fast/**` excluded)

> Environment note: `scripts/worktree_identity.py` cannot run — stale `worktree /sessions/…`
> registrations in the common git dir fail its `resolve(strict=True)`. Identity was verified
> with the identical git queries (root/branch/git-dir/HEAD), and re-verified with expected
> values before `git add`/`git commit`.

## Status summary

Epoch-1 implementation audited against the A1-amended brief: **zero deviations found** —
all invariants I1-1..I1-5, S-1..S-3, M-2, M-3, and the downstream contract hold in the
working-tree code (details per file below). Amendment A1 applied to
`OperatorStatusWriteSeamGuardTest.kt`: exactly three `EXCLUDED_NOISE_SITES` line-number
updates (90→91, 431→436 in `ExpertIndexController.kt`; 498→542 in `ExpertSearchService.kt`),
context substrings verbatim, nothing else changed (verified via diff — 3 lines only).
All required verification commands pass freshly, pinned to the worktree (see Commands),
including the previously-blocking `OperatorStatusWriteSeamGuardTest` (1 test, 0 failures).
Committed in ONE commit `feat(fast-p): implement 01-expert-list-type-filter` (7 files,
+590/−14), excluding `docs/plans/fast/**`.

## Amendment A1 application (guard test)

`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
— `EXCLUDED_NOISE_SITES` line-only maintenance:

| Noise site | before | after | context (verbatim, unchanged) |
|---|---|---|---|
| `expert/controller/ExpertIndexController.kt` | 90 | 91 | `operatorStatus = contact?.operatorStatus` |
| `expert/controller/ExpertIndexController.kt` | 431 | 436 | `operatorStatus = operatorStatus ?: expert.operatorStatus` |
| `expert/service/ExpertSearchService.kt` | 498 | 542 | `operatorStatus = source.nullableText` |

Pre-check simulated the guard's stale-exclusion assertion over the whole repo: exactly these
three entries were stale, all others still hit. Post-A1, the guard test passes (1/1).

## Per-file changes (committed at 7c703e3)

| # | File | Status | Change |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | M | Companion `ALLOWED_EXPERT_TYPES` (`ExpertType.values().map{it.name}.toSet() + "UNCLASSIFIED"`), `expertTypesFilter` (trim/filter/distinct → null on empty; per-value `require` in whitelist; single `bool.should` + `minimum_should_match:1`), private `expertTypePredicate` (`UNCLASSIFIED` → `must_not exists expertClassification.type`, else `term`). `searchExperts` + `buildExpertFilters` trailing `expertTypes: List<String> = emptyList()`; filter appended after discipline block; three aggregation callsites untouched (defaults → byte-identical) |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | M | `listExperts` trailing `@RequestParam(required=false) expertType: List<String>? = null`, passed `expertType.orEmpty()`; `ExpertIndexResponse` +4 trailing nullable fields (`expertType`, `expertSendable`, `expertProductionScore`, `expertResearchScore`) assembled from `expert.expertClassification` (null when classification missing); no evidence-code exposure |
| 3 | `src/main/resources/static/index.html` | M | S-1 `#expertTypeTagSelect` skeleton inserted after discipline label, before `H-Index ≥` (verbatim: span.toolbar-label > span.tag-select + 7 `.tag-chip` buttons; no new class, no inline style); S-3 STEM option text `理工科`→`理工科（含医学）`, all `value` attrs verbatim |
| 4 | `src/main/resources/static/app.js` | M | `expertTypeActiveValues()`; `initExpertTypeTags` chip-click binding (toggle `.active` + `reloadContactsFromStart()`, after `initHasFieldTags`); registration 1 in `loadContacts` (read + repeated `params.append("expertType", v)` + row mapping); registration 2 in `gateSummaryParams` (same append); registration 3 in `updateFilterBadge` (`expertTypeActiveValues().length > 0`); NOT added to change-listener array (registration 4); S-2 row chip `span.tag-chip` (no `.active`, no inline style, `title="生产分 X / 科研分 Y"` escapeHtml'd, not rendered when type missing) |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | M | +9 tests: whitelist derivation (enum+1), empty→null (incl. blanks), single term, multi-value single should + `minimum_should_match:1` + no top-level `filter` key, trim/dedup, UNCLASSIFIED `must_not exists`, invalid throws, `searchExperts` empty-vs-default query equal (filter size 2), `searchExperts` multi-term should to ES |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | M | **A1 only**: three line numbers (see table above); nothing else |
| 7 | `src/test/js/expertTypeFilter.test.js` | A | New, 4 tests: chip toggle + `expertTypeActiveValues()` + reload requested; no `expertType` param when unselected; two chips → `getAll("expertType").length === 2`; badge count/hide group semantics |

## Commands (all freshly run in this epoch, pinned to the worktree)

> Pinning: each command executed with explicit `cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-rnd-gate` and/or `mvn -f pom.xml` so the project basedir is the fast worktree. (See environment note below.)

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` (full suite) | PASS | exit 0; `Tests run: 2839, Failures: 0, Errors: 0, Skipped: 4`; BUILD SUCCESS; node exec: tests 737, pass 737, fail 0, cancelled 0 — incl. new suite `expert type filter (01-expert-list-type-filter)`; `OperatorStatusWriteSeamGuardTest` 1/1 PASS |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest` | PASS | exit 0; `Tests run: 65, Failures: 0, Errors: 0, Skipped: 0` (all 9 new tests present in surefire XML) |
| `node --test src/test/js/expertTypeFilter.test.js` | PASS | exit 0; tests 4, pass 4, fail 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; tests 737, pass 737, fail 0, cancelled 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0, no output |
| `git diff --check` | PASS | exit 0, no output |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0; BUILD SUCCESS; `Tests run: 2839, Failures: 0, Errors: 0, Skipped: 4`; node 737/737; war: `…-fast-rnd-gate/target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` |

Commit: `7c703e3d5e51c165ee6c75f316de0f018c44e8df` `feat(fast-p): implement 01-expert-list-type-filter`
(7 files, 590 insertions(+), 14 deletions(−)); `docs/plans/fast/**` (ledger.md, header.tmp ×3)
left uncommitted for the controller. Post-commit: HEAD = commit, branch `fast/rnd-gate`,
worktree clean except docs.

## Environment note (tooling, not plan deviation)

- The shell tool's `cwd` parameter was intermittently ignored for backgrounded (`nohup … &`)
  Maven runs: two runs (a `mvn test` and the first `mvn clean package`) executed with basedir
  `/Users/lukai/IdeaProjects/weibo-talent-introduction` (main repo) instead of the fast
  worktree; their results are invalid for this plan and were discarded (that `clean` deleted
  the main repo's `target/` build artifacts — sources untouched, main session can rebuild).
  All accepted evidence above comes from runs pinned via explicit `cd` + `mvn -f pom.xml`,
  verified by the `[WARNING] Source root doesn't exist: …-fast-rnd-gate/src/main/java` line
  in each accepted log (proves worktree basedir). The transient "missing tests" in the two
  discarded logs were the main repo's own state, not a worktree defect.
- `scripts/worktree_identity.py` blocked by stale `/sessions/…` worktree registrations
  (environment); identity verified manually with identical queries (see header).

## Deviations

- None from the A1-amended brief. (Epoch-1 implementation notes — typeof guards, local label
  map, group-semantics badge test, `ExpertIndexResponse` naming — were re-audited and remain
  within the brief's own conventions; the plan's `ExpertContactView` name does not exist in
  this tree, the view is `ExpertIndexResponse`.)

## Freshness

- Plan identity rechecked: YES (SHA-256 `ea3d7b6f…` unchanged throughout this epoch)
- Worktree identity rechecked: YES (root/branch/git-dir matched before stage and commit;
  HEAD advanced only by the authorized commit)
- Reported commit reachable from target branch: YES (HEAD of `fast/rnd-gate`)
- Required commands run this invocation: YES (all 7 above, freshly, after final state)
- Historical evidence used only as baseline: YES (epoch-1 report is history; all acceptance
  evidence is from this epoch's pinned runs)

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` (child 01); epoch-1 ledger amendment A1 already
  recorded; controller may commit `docs/plans/fast/**` separately.

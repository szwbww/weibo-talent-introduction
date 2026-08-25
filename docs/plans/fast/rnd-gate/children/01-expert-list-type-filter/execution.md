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

# Aggregate Machine Verification — 2026-08-20-execution-order

- Master plan: docs/plans/2026-08-20/00-execution-order.md (commit 15dbf44ea93cfab28f24bfb3ab017fa60ad3dbc8)
- Implementation boundary: 66e1036d5e5d9d33f2b59655f20063ed90fa9015..1bf415a9dd79bf582bd009f0361dc4580ffa4fb1
- Reviewer: fresh `general-purpose` subagent (id `a4bfccc739b009bd0`), dispatched by the review-fast-p controller with no inherited implementer/light-verifier conversation, following the `verify-p` procedure applied once to the whole combined boundary (see the review ledger's "Skill-asset deviation" note for why this ran as a manual reconstruction of verify-p rather than a literal nested skill invocation).
- Independently-obtained command evidence quoted in this report (node --test full suite + scoped, node --check ×2, git diff --check, and the baseline-reproduction of the one pre-existing JS failure) was captured by the controller in this same review session, minutes before the reviewer was dispatched, from two ephemeral git worktrees checked out at the exact boundary SHAs (`.worktrees/review-2026-08-20-execution-order` @ 1bf415a9, `.worktrees/review-baseline-66e1036` @ 66e1036) inside the connected repository. These worktrees remain checked out at the time of this commit for anyone who wants to re-verify by hand; they are gitignored (`.worktrees/`) and carry no tracked content.

---

## Verification Result: BLOCKED

Plan: `docs/plans/2026-08-20/00-execution-order.md` (authoritative) + `P0-sse-error-code-and-state-reset.md` + `P1-fact-binding-drop-not-fatal.md` + `P2a-bound-vs-evidence-split.md` + `P2b-bound-facts-into-prompt.md`
Implementation boundary: `66e1036d5e5d9d33f2b59655f20063ed90fa9015..1bf415a9dd79bf582bd009f0361dc4580ffa4fb1` (whole combined boundary; child sub-ranges p0 `66e1036..8ea1e24`, p1 `8ea1e24..6942ce1`, p2a `6942ce1..19a348b`, p2b `19a348b..1bf415a9` — all confirmed present in branch `fast/2026-08-20-execution-order`, `git log` order 8ea1e24→6942ce1→19a348b→1bf415a9, no gaps, no foreign commits merged in)
Convergence: INITIAL (first whole-boundary pass)
Manual acceptance: PENDING (A-1..A-9 per child; none machine-verifiable, not required to block this report)

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=<zulu-11> mvn test` (full suite, final boundary) | BLOCKED | No Maven binary exists in this sandbox or in the remote-devices worktree (`find / -iname mvn` → empty, `command -v mvn` → empty, no brew). Ledger's only cumulative `mvn test` run was at master base `66e1036` *before* any child ran (2630/0/0/4). No verifier ran `mvn test` against the final combined tree as an independent check — confirmed by grep: `ledger.md` has exactly one `mvn` line (the pre-implementation baseline); each child `verify-log.md`'s `mvn test` full-suite run only covers that child's own incremental boundary. The closest data point is p2b's own light-verifier self-report (`child-p2b-verify-log.md:12`): `Tests run: 2656, Failures: 0, Errors: 0, Skipped: 4, BUILD SUCCESS` at pre-rebase SHA `a3ef1cd`, which the ledger claims is tree-identical to final head `1bf415a9` (docs-only rebase, "product commit TREES byte-identical"). Per verify-p Phase 2/3 this is an *unverified claim by an implementation-adjacent agent*, not independently reproduced — and `handoff.md` states explicitly "No whole-system verification was performed." Treated as BLOCKED, not fabricated. |
| `JAVA_HOME=<zulu-11> mvn clean package` | BLOCKED | Same reason — no Maven available anywhere reachable from this review session; no independent full-boundary build evidence exists. |
| `node --test src/test/js/*.test.js` | PASS (one pre-existing, unrelated, environment-specific failure) | Ran independently at `final_code_head`: exit 1, `# tests 678 / # pass 675 / # fail 1 / # cancelled 2`. Failure isolated to `expertTagBatchFix.test.js` ("log execution identity (I-2)" / "interval diff normalization (I-1)", timer-based). Confirmed via diff inspection that file is untouched by this boundary. Reproduced the identical failure (28 pass/1 fail/2 cancelled) at baseline `66e1036` in a separate worktree — proves pre-existing/sandbox-specific, not a regression. All 24 tests in the one file this diff actually touches (`trustReplyWorkbench.test.js`) pass. |
| `node --test src/test/js/trustReplyWorkbench.test.js` | PASS | exit 0, `# tests 24 / # pass 24 / # fail 0 / # cancelled 0`. |
| `node --check src/main/resources/static/app.js` | PASS | exit 0, no output. |
| `node --check src/main/resources/static/trust-reply-workbench.js` | PASS | exit 0, no output. |
| `git diff --check 66e1036..1bf415a9` | PASS | exit 0, no output. |

### Contract Matrix

**File-scope / master invariants**

| ID | Verdict | Evidence |
|---|---|---|
| M-scope: changed files = exact union of 4 plans' authorized files | PASS | `git diff --name-only 66e1036..1bf415a9` → exactly 8 `src/main` files + 6 test files + docs/plan bookkeeping; all forbidden files (`styles.css`, `app.js`, `GlobalExceptionHandler.kt`, `AiReplyActionPolicy.kt`, `AiReplyGroundedContentPlanner.kt`, `AutoReplyConfidenceScorer.kt`, `AiReplyReviewAuditService.kt`, `PendingMailOperationService.kt`, `AiTrainingController.kt`) absent. An unrelated sibling commit on `main` touching `app.js` was confirmed NOT an ancestor of `1bf415a9` and has zero diff effect. |
| M-1: Line A / Line B non-overlap (except P2b's disclosed, required revision) | PASS | `deriveAllowed`, `AiReplyActionPolicy.kt`, and `validateLockedItem`/`operatorAuthorizedActions`/`ANSWER_FROM_OPERATOR_INPUT` region: zero diff hits. P2b's revision of Line A's system message in `generateOperatorDirectedAnswer` is exactly the one disclosed, required crossing point. |
| M-2: sequencing precondition — Line A merged before Line B started | PASS | Verified directly at baseline `66e1036`: Line A's action-policy code and both relevant sentences already present there. |
| M-3: decided-decision (a) — bound facts never change item `status` | PASS | Status-calc block in `QaFactSelectionService.kt` has zero diff hits across the whole boundary; test asserts `status == UNSUPPORTED` survives binding. |
| M-4: decided-decision (b) — `sendQaRuleIds` only real evidence | PASS | Both send-audit construction sites remain sourced from `factRuleIds` only (unchanged source lines); dedicated test asserts audit-visible ids exclude a bound-only id. |
| M-5: P1's dropped-hint semantically migrated by P2a, not deleted/left stale | PASS | Same `data-role="item-facts-dropped"` element; text now reads "已绑定但不会作为本条回答的依据" — old "未被采纳" text has zero remaining occurrences. |
| M-6: P2b gives operator-directed facts a channel AND leaves Line A's action constraint untouched | PASS | New fact block injected only into the user-message; the action-authorization sentence appears only as unchanged context in the diff (never a `+`/`-` line). |
| M-7: Maven never run cumulatively against combined boundary by anyone | CONFIRMED GAP (BLOCKED) | See Commands table. |

**P0 (SSE error code + state reset)** — I-1..I-6, S-1/S-2, IP-4: **all PASS.** Error `code`/`message` payload identical at both SSE-failure sites with proper WARN logging; only 1 pre-existing, non-scoped `catch (_: Exception)` remains per file (verified pre-existing at baseline); `errorFromStream`/`errorFromResponse`/`isStaleError`/`isFrameStaleError` byte-identical; reset entry gated to bootstrap-failure/reset-failure only, no version compare, deletes exactly one row, forces clean re-bootstrap; no new CSS, no `styles.css` diff; `renderShell(` hit-count matches acceptance math (plan-text's own "4 处调用" was an off-by-one in the plan's prose, not an implementation defect).

**P1 (dropped-binding downgrade)** — I-1..I-5, S-1: **all PASS.** Single un-throw site preserves `factRuleIds` and records dropped ids in original order; 4 exact shadow-field hits; drop tracking never enters the identity hash (`requestEvidenceVersion`/`canonicalMatrix`/`versionId`/saved-state payload all zero-hit); wire payload to the server stays `{requestKey, factRuleIds}` only; hint text superseded consistently by P2a (see M-5).

**P2a (bound-vs-evidence split)** — I-1..I-6 + both must-not-change items: **all PASS.** `boundRuleIds` field plus exactly 3 assignment call sites; exactly 4 "operator view" projections switched (`canonicalMatrix`, `resolveCanonicalSelection`, `buildInitialItemVersions`, `toCoverage`) while `adjacentIds` and the evidence-filter site are confirmed still on `factRuleIds`; `canonicalMatrix`/`toCoverage` switch together; assertion becomes a tautological guard on the new field; evidence-version call shape unchanged; suggested-instruction test confirms it never names a bound-but-unsupported fact; none of the 4 forbidden "B-class" files touched.

**P2b (bound facts into prompt, not audit)** — I-1..I-5 + must-not-change items 1-3,8: **all PASS.** Prompt channel = evidence-first ordered union of send-ids and bound-ids; send stays evidence-only (zero `boundRuleIds` on any send-side RHS); new system-message sentence for operator-directed facts added, action-authorization sentence unchanged; empty/no-bound-facts case leaves prompt text byte-identical (dedicated test); output-validation functions (`findViolations`/`INTERNAL_RESPONSE_MARKER`/`rejectNonEnglishItemAnswer`) zero-hit; p2b's own changed-file set is exactly the 2 authorized service files + 3 test files, no frontend files touched.

### Finding Lineage

N/A — first whole-boundary pass; no prior `verify-p`/aggregate report exists for this master slug.

### Findings

#### P1
- None found.

#### P2
- None found. All RECORD_ONLY items across the 4 children (in `handoff.md`'s RECORD_ONLY Index) were individually re-investigated against the actual diff rather than accepted as pre-cleared, and all were confirmed forced/benign: a pre-existing matrix test renamed rather than deleted for P1's new no-throw semantics; a pre-existing prompt-assertion test updated for P2b's deliberate, disclosed contract change; the mail-controller's net-zero-line diff forced by an unrelated test's exact-line pin; and P0's plan-text-vs-actual `renderShell(` call-count off-by-one, which is the plan prose's own arithmetic slip, not an implementation defect.

#### Observations
- A harness-only doc file (`children/p2b/execution.md:62`) has trailing whitespace — outside the reviewed code boundary, no effect on `git diff --check` for the committed range.
- P0's bootstrap-failure shell message still surfaces a raw error string (rather than the mapped Chinese text) for HTTP 4xx failures with no body-message field; this is in-scope-as-written per the plan's own "Applies to" clause (only the item-level retry path was required to map to Chinese), not a violation — flagged only in case the product intent was broader than what P0 actually committed to.
- An unrelated `main`-branch commit briefly appeared adjacent to this boundary in `git log --all` chronological listing; confirmed via `git merge-base --is-ancestor` to have zero relationship to the reviewed head. Flagged only so a future reviewer doesn't need to re-chase this red herring.

### Evidence Boundaries
- Full backend build/test suite (`mvn test`, `mvn clean package`) could not be independently run against the final combined boundary `1bf415a9` — no Maven installation exists in either environment reachable from this review session (the cloud sandbox, or the connected-device bridge's Linux VM), and no network/install path is available in either. The only related data point — p2b's own light-verifier's self-reported full-suite run (2656/0/0/4, BUILD SUCCESS) at the pre-rebase SHA, claimed tree-identical to the final head — is an unverified implementer-adjacent claim, not a reproduced result, and is explicitly disclaimed by the fast-p handoff itself ("No whole-system verification was performed"). This is the sole missing-evidence item driving the BLOCKED verdict; every other mandatory item (contract matrix, file scope, frontend tests/lint/whitespace) was independently reproduced and found clean.

### Next Action
- BLOCKED → obtain the named evidence (a fresh `mvn test` and `mvn clean package` run against `1bf415a9dd79bf582bd009f0361dc4580ffa4fb1` on a machine with JDK 11/zulu-11 and Maven, per the project's own build instructions), then request a fresh review pass. Given the exhaustive, zero-finding agreement found on every other mandatory item, this is very likely to converge to PASS once that evidence exists — but it must be obtained, not assumed, before this implementation is integrated.

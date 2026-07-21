---
name: fix-v
description: Verify code changes against an approved implementation plan. Use after plan execution, after another agent applies a fix plan, or when auditing whether implementation, tests, state transitions, persistence semantics, and cross-plan contracts match the approved design. Enforce evidence-based compliance, bounded mechanical fixes, P1 lineage tracking, three actionable design-fix rounds, human-approved plan amendments, and strict scope control.
---

# Constrained Verification

## Purpose

Verify implementation changes against their approved plan and demonstrated production invariants.

When a blocking compliance defect exists, specify the repair in a fix plan but do not implement the behavioral repair. A separate repair pass applies it; re-run this skill afterward.

Edit code only for mechanical build/test repairs requiring no product, architectural, or behavioral judgment.

At the start, announce:

> I'm using the constrained-verification skill to verify these changes.

## Core Terms

- **Mechanical attempt:** One stated compile/test repair set followed by a rerun. Allow at most 3 attempts per verification pass.
- **Design fix round:** One actionable `fix-N` plan, its implementation, and the subsequent re-verification. Allow `fix-1`, `fix-2`, and `fix-3` only.
- **Terminal verification:** Verification after `fix-3` has been implemented. If a blocking P1 remains, stop without creating `fix-4`.
- **Control-plane artifact:** A fix plan, blocked/terminal report, or knowledge entry produced by verification. Exempt these artifacts from implementation file scope, but never use the exemption to edit product code.
- **Approved promotion:** A knowledge rule added to a shared instruction file only after explicit human approval. Without approval, propose promotion in the outcome artifact but do not edit the shared file.

Generating `fix-3` does not exhaust the third design fix round. Allow its implementation and terminal re-verification.

## Phase 1: Extract the Verification Contract

Read the complete original plan before editing or evaluating code.

### 1a. Extract requirements

Record:

- Design constraints and production invariants.
- Scope boundary and listed files.
- Runtime behavior, tests, and acceptance criteria.
- Rejected approaches.
- Write/read-path audits.
- Cross-plan inputs and outputs.
- Required build/test commands.

Treat files outside approved implementation scope as unavailable for direct repair unless an approved amendment explicitly authorizes them.

### 1b. Read prior decisions

Read every prior fix plan referencing the same original plan. Build a decision log from `修正记录`, `不适用`, `已有决策`, `不做`, `降级`, crossed-out items, and human-approved amendments recorded in the repository or current task.

Let later approved decisions override older text. Do not reopen a closed decision as a finding.

### 1c. Read relevant knowledge

If `docs/knowledge/` exists:

1. Find entries matching the stores, modules, and defect classes under verification.
2. Read every relevant entry completely.
3. Add its `正确做法` rule as an audit probe.
4. Tag it with `(来源: K-<id>)`.
5. Defer `hit_count` and `last_used` updates until Phase 8, and update only entries actually used.

Do not load or update unrelated entries. A knowledge rule is not independent authority for a P1; trace any P1 to an approved requirement, demonstrated production invariant, or semantic completeness defect.

### 1d. Build one checklist

Combine original constraints, approved amendments, closed decisions, relevant knowledge probes, and prior P1 repair requirements. Assign stable IDs and use the checklist throughout the pass and every re-entry.

## Phase 2: Compile and Test

Run commands required by the plan. If absent, use documented project commands.

Record:

```text
Build: PASS | FAIL | BLOCKED
Tests: PASS | FAIL | BLOCKED — N passed, M failed, K skipped
```

Classify each failure before editing:

- **Mechanical:** Syntax, imports, constructor wiring, stale test compilation, or a test assertion that unambiguously contradicts an approved requirement.
- **Behavioral:** Runtime behavior violates the verification contract.
- **Infrastructure:** Environment, dependency, database, network, credentials, or external service prevents execution.

Do not repair behavioral failures directly. Classify them under Phases 4–5.

Diagnose infrastructure failures reasonably within scope. If any required verification remains unexecuted, do not report PASS. Select a blocked outcome unless an approved plan explicitly marks that check optional.

## Phase 3: Mechanical Fix Loop

Allow at most 3 mechanical attempts per verification pass.

Before each edit:

1. Identify the failing file and line.
2. State the one-sentence repair.
3. Check it against every Phase 1 constraint.
4. Confirm the file is inside approved implementation scope.
5. Confirm no product, design, or behavioral decision is required.

Allow only:

- Fix syntax or typographical errors.
- Add missing imports.
- Align constructor calls with approved signatures.
- Remove references to deleted code.
- Update a stale test assertion only when approved behavior is explicit and unambiguous.

Prohibit:

- Adding a class, interface, enum, state, or workflow step.
- Adding retry, recovery, reconciliation, or fallback behavior.
- Changing state-machine semantics or architecture.
- Modifying an already-applied database migration.
- Weakening a test to obtain a pass.
- Editing outside approved implementation scope.
- Any repair requiring product or design judgment.

After every attempt, rerun relevant build/tests and record the result. Mechanical attempts do not consume design fix rounds.

After three unsuccessful attempts, select a blocked outcome. Do not perform a fourth attempt.

## Phase 4: Design Compliance Audit

Run this phase regardless of build/test outcome whenever meaningful source audit remains possible.

### 4a. Audit every constraint

For every checklist item:

1. Open relevant implementation files.
2. Read runtime paths, not only declarations or signatures.
3. Trace inputs, writes, state transitions, persistence, error handling, and outputs.
4. Record a verdict with exact evidence.

Use:

```text
Constraint I-1: [description] — ✅
Evidence: path/File.kt:42 — [runtime evidence]

Constraint I-2: [description] — ❌
Evidence: path/File.kt:88 — [violating behavior]
```

For source verdicts, require file and line. For runtime-only evidence, cite the exact command, test, or log location. If evidence is unavailable, record `BLOCKED`, not a fabricated pass or failure.

Also record:

```text
Deleted code: ✅ | ❌ | N/A
No extras: ✅ | ❌
Scope compliance: ✅ | ❌
```

Exclude control-plane artifacts from `No extras` and implementation scope checks.

### 4b. Check semantic completeness

#### Cross-invocation accumulation

For daily, hourly, cumulative, quota, or rate-limit semantics:

1. Find each counter's initialization.
2. Verify it survives repeated entry-point invocation when required.
3. Verify restart and retry cannot reset a persistent time-window limit.
4. Verify names such as `dailyTotal` match actual lifetime.

Classify a per-run counter implementing a cross-run limit as P1 only when production or acceptance impact is demonstrated.

#### State-machine reachability and liveness

For every state machine:

1. List implemented states and transitions.
2. Identify terminal and non-terminal states.
3. Verify every non-terminal state has an implemented outgoing path.
4. Verify promised recovery from error, paused, degraded, unknown, and retryable states.
5. Verify frontend recovery actions call backend paths accepting the current state.
6. Trace crash and restart behavior.

Classify a non-terminal state with no valid recovery path as P1 unless the approved contract declares it terminal and fail-closed.

#### Cross-plan contracts

When the feature spans multiple plans:

1. Match written fields with downstream read assumptions.
2. Verify null/empty semantics, units, initialization, and reset rules.
3. Verify method preconditions and error handling.
4. Trace happy, error-then-recovery, and restart-after-crash paths.

Classify a broken boundary contract as P1 only when introduced or behaviorally exposed by the current plan and production or acceptance impact is demonstrated.

Report:

```text
Accumulation check: ✅ | ❌ | N/A | BLOCKED
State-machine check: ✅ | ❌ | N/A | BLOCKED
Cross-plan check: ✅ | ❌ | N/A | BLOCKED
```

### 4c. Establish the regression boundary

Determine whether each defect existed before the original plan's first implementation change. Use baseline code, history, tests, or other concrete evidence when available.

Classify:

- Introduced or behaviorally exposed by the plan: eligible for blocking P1.
- Pre-existing and adjacent: observation only; recommend a separate plan if warranted.
- Uncertain after reasonable investigation: state uncertainty; do not claim pre-existing as fact.

Do not let a pre-existing adjacent defect block the current plan.

## Phase 5: Classify Findings and Track Lineage

Use:

- **P1:** Proven production defect; repair regression; violation of an explicit mandatory requirement with runtime or acceptance impact; or semantically incomplete plan requirement with demonstrated impact.
- **P2:** Test-depth improvement, maintainability issue, hygiene, non-mandatory textual mismatch, or defensive hardening without runtime or acceptance impact.
- **Observation:** Pre-existing issue, unrelated out-of-scope concern, unresolved uncertainty, or non-blocking note.

Do not promote test preferences, formatting, or style to P1.

For every P1, record constraint source, runtime evidence, trigger frequency, impact, minimal repair scope, amendment ownership, and regression-boundary evidence.

On re-entry, classify every prior and current P1:

- **RESOLVED:** Prior root cause and violation no longer exist.
- **PERSISTENT:** Same root cause and violation remain.
- **REGRESSION:** Latest repair introduced a defect.
- **NEW_IN_SCOPE:** Newly discovered violation traceable to the original contract.
- **PRE_EXISTING:** Predates the plan; observation only.

Use:

| Previous ID | Current ID | Status | Constraint | Evidence |
|---|---|---|---|---|
| P1-1 | — | RESOLVED | I-2 | `File.kt:42` |
| — | P1-2 | NEW_IN_SCOPE | I-13 | `File.kt:88` |

Audit the complete checklist on every re-entry. Do not use raw P1 count as the sole convergence test.

Allow a newly discovered issue to block when it violates the original plan, an approved amendment, or a demonstrated production invariant within original scope; when the repair behaviorally affects its runtime path; or when it is an affected cross-file/cross-plan contract.

Demote only genuinely pre-existing, unrelated, out-of-scope, or behaviorally unaffected adjacent issues. Never demote an in-scope production defect solely because its file was untouched by the latest repair.

## Phase 6: Resolve Amendment Ownership and Run the Quality Gate

Determine amendment ownership before selecting the outcome:

```text
Does implementation violate a correct plan?
├── YES → Specify implementation repair only.
└── NO
    └── Is a plan requirement missing or incorrect?
        ├── YES → Propose an amendment; do not apply it without approval.
        └── NO → Record an observation only.
```

Append or apply `修正记录` only when explicit human approval is already recorded in the repository or current task. Otherwise mark the proposal `待批准` and leave the original plan unchanged.

Trace every P1 to original plan text, a human-approved amendment, a demonstrated production invariant, or a semantic completeness failure with demonstrated impact. Never invent acceptance criteria and later treat them as approved.

Run the plan quality gate whenever recommending plan correction/decomposition and during terminal verification. Record `N/A` otherwise.

Prove structural defects through actual coupling, not counts alone:

- **Excessive coupling:** More than 10 changed files, 3 or more independent subsystems, or repeated reopening of unrelated modules. Counts are signals; explain actual coupling.
- **Missing invariant:** A shared field, state, identity, or semantic rule lacks one authoritative meaning and code paths interpret it inconsistently.
- **Missing write-path audit:** A shared store changed without identifying relevant writers, allowing an unreviewed writer to violate the contract.
- **Inseparable constraints:** Required repair cannot satisfy two approved constraints simultaneously.

If structural defect is proven, select an early blocked outcome and require human approval for plan correction/decomposition. Otherwise keep any follow-up scope narrow.

## Phase 7: Select the Outcome

Select exactly one provisional outcome. Do not emit it yet; complete knowledge write-back first.

### Pass

Pass only when required build and tests are `PASS`, all required audits are complete, and no blocking P1 remains. An approved optional skipped check must be reported explicitly; never relabel `BLOCKED` as `PASS`.

Do not create a fix plan for a pass.

### Blocking P1 with an available round

- Initial verification produces `fix-1`.
- Re-verification after `fix-1` may produce `fix-2`.
- Re-verification after `fix-2` may produce `fix-3`.
- Allow `fix-3` to be implemented.
- Re-verification after `fix-3` may not produce `fix-4`.

Prepare `docs/plans/fix/<target-plan-name>/fix-<N>.md`. Use the verified sub-plan filename for a sub-plan and standalone filename for a standalone plan.

Include:

1. Original plan/sub-plan reference and actionable round.
2. Constraint excerpts.
3. P1 lineage.
4. Findings with trigger frequency, impact, and evidence.
5. Minimal repair specifications: exact file, required behavior, prohibited changes, and acceptance behavior.
6. Pre-repair build/test status.
7. Full compliance and semantic audits.
8. Proposed plan amendment, if needed, marked `待批准`.
9. Quality-gate result when applicable.

Exclude completed-repair claims, post-repair results, unrelated cleanup, unsupported redesign, and direct behavioral code edits.

### Terminal verification after fix-3

Do not create `fix-4`. Prepare:

```text
docs/plans/fix/<target-plan-name>/verification-blocked-after-fix-3.md
```

Include remaining P1 evidence, lineage, frequency and impact, build/test status, violated constraints, quality-gate result, narrow candidate follow-up scope, and an explicit statement that no `fix-4` was created.

Do not disguise another same-plan repair specification as a stop report. The candidate follow-up is advisory, requires explicit human approval, and must have a distinct objective, invariant set, and bounded scope. Renaming the residual repair does not reset the round limit.

### Early blocked

Select early blocked only when:

- Required repair conflicts with an approved constraint.
- Repair requires unauthorized scope expansion or unapproved architecture.
- The quality gate proves structural plan defect.
- Persistent infrastructure failure prevents required verification.
- Three mechanical attempts are exhausted.

Raw P1 count equality is not an early-stop condition.

Prepare one report:

```text
docs/plans/fix/<target-plan-name>/verification-blocked-initial.md
docs/plans/fix/<target-plan-name>/verification-blocked-after-fix-<N>.md
```

Choose the first path for initial verification and the second after a fix round. Include the round, exact failure, blocking constraint, attempts made when relevant, root cause, options with tradeoffs, and smallest human decision required.

## Repair Proportionality and Scope Discipline

Match repair requirements to trigger frequency and impact. For rare paths, prefer the simplest safe behavior: stable error, bounded logging, clamp/reject, fail closed, or audited skip.

Do not introduce classification hierarchies, DTOs, states, services, or UI branches for near-zero-frequency cases unless an approved invariant requires them.

Keep fix plans free of unrelated refactoring, cleanup, formatting, new features, and files outside the minimal affected runtime path.

If a closed decision appears questionable, record it for human review without reopening it.

## Phase 8: Knowledge Write-Back

Run before emitting every outcome: pass, fix plan, early blocked, or terminal stop.

Create or update entries only for confirmed reusable lessons, including P1 classes, accumulation failures, dead states, contract failures, and repair regressions. Do not write feature-specific task lists as knowledge.

Apply deferred `hit_count` and `last_used` updates only to entries actually used.

Store entries under `docs/knowledge/<domain>/`:

```yaml
---
id: K-<short-slug>
domain: <store-or-module>
created: <yyyy-MM-dd>
last_used: <yyyy-MM-dd>
hit_count: 0
source: fix-v:<plan-name>:<outcome-id>
last_source: fix-v:<plan-name>:<outcome-id>
severity: P1|P2
---
```

For a new entry, set `source` and `last_source` to the current outcome. For an existing entry, preserve its original `source`; update only `last_source`, `last_used`, corrected lesson text when evidence requires it, and `hit_count` when the entry was actually used.

Use one outcome ID:

- `initial-pass`
- `fix-<N>`
- `blocked-initial`
- `blocked-after-fix-<N>`
- `stop-after-fix-3`

Write:

```text
经验：<reusable lesson>
正确做法：<general rule>
反例：<bug pattern with file and line>
```

Use the project's configured promotion threshold, defaulting to 10 when none exists. When an entry reaches it, propose one concise shared rule with a `(K-<id>)` backlink in the outcome artifact. Target only the project's designated shared instruction file. If none is designated, propose the rule without choosing or creating a destination. Modify a shared instruction file only when explicit human approval is already recorded; then treat that edit as an approved control-plane artifact. Do not run global decay.

## Phase 9: Emit the Outcome and Stop

After Phase 8, emit the selected outcome once.

### Pass format

```markdown
## 验证结果：通过

计划：[plan filename]
编译：PASS
测试：PASS — N passed, M failed, K skipped

### P1 收敛

[Lineage table, or "N/A — initial verification had no P1"]

### 合规审计

Constraint X: ✅ Evidence: `File.kt:42`
Constraint Y: ✅ Evidence: `Other.kt:18`
Deleted code: ✅ | N/A
No extras: ✅
Scope compliance: ✅
```

For blocking P1, write the prepared `fix-N` plan. For terminal verification, write the prepared terminal report. For early blocked, emit the prepared blocked report.

Then stop. Do not implement behavioral P1 repairs, apply unapproved amendments, create `fix-4`, or continue into another verification round.

## Red Flags

Stop and reassess when:

- Implementing a behavioral P1 instead of specifying it.
- Creating `fix-4` or resetting the limit by renaming the same repair.
- Treating generation of `fix-3` as terminal before its repair and re-verification.
- Comparing only P1 counts.
- Demoting an in-scope defect because its file was untouched.
- Amending an approved plan without recorded approval.
- Recommending decomposition without the quality gate.
- Adding a class, state, migration, retry path, or recovery service during mechanical repair.
- Weakening tests to obtain a pass.
- Reporting PASS with required checks blocked or unexecuted.
- Skipping knowledge write-back because an outcome was already selected.

## Workflow

```text
Create approved plan
→ Implement plan
→ Run fix-v
→ If needed, prepare fix-1
→ Knowledge write-back
→ Emit fix-1 and stop
→ Separate repair pass
→ Re-run fix-v
→ Repeat through fix-3 at most
→ Re-verify fix-3
→ Pass, or terminal stop without fix-4
```

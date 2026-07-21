---
name: fix-v
description: Verify code changes against an approved implementation plan. Use after plan execution, after another agent applies a fix plan, or when auditing whether implementation, tests, state transitions, persistence semantics, and cross-plan contracts match the approved design. Enforce evidence-based compliance, bounded mechanical fixes, P1 lineage tracking, three actionable design-fix rounds, human-approved plan amendments, and strict scope control.
---

# Constrained Verification

## Purpose

Verify that implementation changes match their approved plan and production invariants.

When a blocking compliance defect exists, write an actionable fix plan, do not implement the P1 repair, let a separate repair agent implement it, and re-run this skill afterward.

Apply code edits only for mechanical build/test repairs that require no product, architectural, or behavioral judgment.

At the start, announce:

> I'm using the constrained-verification skill to verify these changes.

## Core Terms

Keep these limits separate:

- **Mechanical attempt:** One stated compile/test repair set followed by a rerun. Allow at most 3 attempts per verification pass.
- **Design fix round:** One actionable `fix-N` plan, its implementation by another agent, and the subsequent re-verification. Allow `fix-1`, `fix-2`, and `fix-3` only.
- **Terminal verification:** The verification after `fix-3` has been implemented. If a blocking P1 remains, stop without creating `fix-4`.
- **Control-plane artifact:** A fix plan, terminal report, or knowledge entry produced by verification. Treat these artifacts as explicit exceptions to the implementation file scope; never use the exception to edit product code.

Generating `fix-3` does not exhaust the third design fix round. Allow `fix-3` to be implemented and re-verified.

## Phase 1: Extract the Verification Contract

Before editing or evaluating code, read the complete original plan.

### 1a. Extract requirements

Record design constraints and invariants; scope boundary and listed files; runtime behavior; tests and acceptance criteria; rejected approaches; write/read-path audits; cross-plan inputs and outputs; and build/test commands.

Treat files outside the approved implementation scope as unavailable for direct repair unless an approved amendment explicitly authorizes them.

### 1b. Read prior decisions

Read every prior fix plan referencing the same original plan. Build a decision log from `修正记录`, `不适用`, `已有决策`, `不做`, `降级`, crossed-out items, and human-approved amendments recorded in the repository or current task.

Let later approved decisions override older plan text. Do not reopen a closed decision as a finding.

### 1c. Read relevant knowledge

If `docs/knowledge/` exists:

1. Find entries matching the stores, modules, and defect classes under verification.
2. Read each relevant entry completely.
3. Add its `正确做法` rule to the checklist.
4. Tag the check with `(来源: K-<id>)`.
5. Record, but defer until knowledge write-back, the `hit_count` and `last_used` updates for entries actually used.

Do not load or update unrelated knowledge entries.

### 1d. Build one checklist

Combine original constraints, approved amendments, closed decisions, relevant knowledge rules, and prior P1 repair requirements.

Assign stable IDs. Use this checklist for every later phase and re-entry.

## Phase 2: Compile and Test

Run the plan's required commands. If absent, use the project's documented standard commands.

Record:

```text
Build: PASS | FAIL | BLOCKED
Tests: PASS | FAIL | BLOCKED — N passed, M failed, K skipped
```

Classify each failure before editing:

- **Mechanical:** Syntax, imports, constructor wiring, stale test compilation, or an assertion that unambiguously contradicts an approved requirement.
- **Behavioral:** Runtime behavior violates the verification contract.
- **Infrastructure:** Environment, dependency, database, network, credentials, or unavailable external services prevent verification.

Treat a behavioral failure as P1 evidence only after applying the severity and regression rules below. Do not repair it directly.

Treat infrastructure failure as a blocker only after reasonable in-scope diagnostics show that meaningful verification cannot continue.

## Phase 3: Mechanical Fix Loop

Allow at most 3 mechanical attempts per verification pass.

Before each edit:

1. Identify the failing file and line.
2. State the one-sentence repair.
3. Check it against every Phase 1 constraint.
4. Confirm the file is inside the approved implementation scope.
5. Confirm the repair introduces no design or behavioral decision.

Allow only:

- Fix syntax or typographical errors.
- Add missing imports.
- Align constructor calls with already-approved signatures.
- Remove references to deleted code.
- Update a stale test assertion only when approved behavior is explicit and unambiguous.

Prohibit:

- Adding a class, interface, enum, state, or workflow step.
- Adding retry, recovery, reconciliation, or fallback behavior.
- Changing state-machine semantics or architecture.
- Modifying an already-applied database migration.
- Weakening a test to make broken behavior pass.
- Editing outside approved implementation scope.
- Making any repair that requires product or design judgment.

After every attempt, rerun the relevant build/tests and record the result. The mechanical-attempt limit does not consume a design fix round.

After three unsuccessful attempts, output a blocked report containing:

- Remaining failure with file and line.
- Attempts made.
- Root cause.
- Whether the original plan is defective.
- Smallest required human decision.

Do not perform a fourth attempt.

## Phase 4: Design Compliance Audit

Run this phase even when all tests pass.

### 4a. Audit every constraint

For every checklist item:

1. Open the relevant implementation.
2. Read the runtime path, not only declarations or signatures.
3. Trace inputs, writes, state transitions, persistence, error handling, and outputs.
4. Record a verdict with exact evidence.

Use:

```text
Constraint I-1: [description] — ✅
Evidence: path/File.kt:42 — [runtime evidence]

Constraint I-2: [description] — ❌
Evidence: path/File.kt:88 — [violating behavior]
```

Require a file and line for source-based verdicts. For runtime-only evidence, cite the exact command, test, or log location. Do not mark a constraint compliant without inspectable evidence.

Also record:

```text
Deleted code: ✅ | ❌ | N/A
No extras: ✅ | ❌
Scope compliance: ✅ | ❌
```

### 4b. Check semantic completeness

Verify whether the plan itself fully defines the required behavior.

#### Cross-invocation accumulation

For daily, hourly, cumulative, quota, or rate-limit semantics:

1. Find where each counter is initialized.
2. Verify it survives repeated entry-point invocation when required.
3. Verify restart and retry cannot reset a persistent time-window limit.
4. Verify names such as `dailyTotal` match actual lifetime.

Classify a per-run counter implementing a cross-run limit as P1 when production impact is demonstrated.

#### State-machine reachability and liveness

For every state machine:

1. List all implemented states and transitions.
2. Identify terminal and non-terminal states.
3. Verify every non-terminal state has an implemented outgoing path.
4. Verify error, paused, degraded, unknown, and retryable states have promised recovery behavior.
5. Verify frontend recovery actions call backend paths accepting the current state.
6. Trace crash and restart behavior.

Classify a non-terminal state with no valid recovery path as P1 unless the approved contract declares it terminal and fail-closed.

#### Cross-plan contracts

When the feature spans multiple plans:

1. Match every written field with downstream read assumptions.
2. Verify null/empty semantics, units, initialization, and reset rules.
3. Verify method preconditions and error handling.
4. Trace the happy path, error-then-recovery path, and restart-after-crash path.

Classify a broken boundary contract as P1 when introduced or exposed by the current plan and production impact is demonstrated.

Report:

```text
Accumulation check: ✅ | ❌ | N/A
State-machine check: ✅ | ❌ | N/A
Cross-plan check: ✅ | ❌ | N/A
```

### 4c. Establish the regression boundary

Ask whether the defect existed before the original plan's first implementation change. Verify with baseline code, history, tests, or other concrete evidence when available.

Classify:

- Introduced or behaviorally exposed by the plan: eligible for blocking P1.
- Pre-existing and adjacent: observation only; recommend a separate plan if warranted.
- Uncertain after reasonable investigation: state uncertainty; do not claim pre-existing as fact.

Do not let a pre-existing adjacent defect block the current plan.

## Phase 5: Classify Findings

Use:

- **P1:** Proven production defect; repair regression; violation of an explicit mandatory requirement with runtime or acceptance impact; or semantically incomplete plan requirement with demonstrated production impact.
- **P2:** Test-depth improvement, maintainability issue, hygiene, non-mandatory textual mismatch, or defensive hardening without runtime or acceptance impact.
- **Observation:** Pre-existing issue, unrelated out-of-scope concern, unresolved uncertainty, or non-blocking note.

Do not promote test preferences, formatting, or style concerns to P1.

For every P1, record:

- Constraint source.
- Runtime evidence.
- Trigger frequency.
- Production or acceptance impact.
- Minimal repair scope.
- Whether it corrects implementation or requires a plan amendment.
- Regression-boundary evidence.

## Phase 6: Track P1 Lineage and Re-entry Scope

On re-entry, classify each prior and current P1:

- **RESOLVED:** Prior root cause and violation no longer exist.
- **PERSISTENT:** Same root cause and violation remain after repair.
- **REGRESSION:** The repair introduced a new defect.
- **NEW_IN_SCOPE:** A newly discovered violation directly traceable to the original contract.
- **PRE_EXISTING:** The issue predates the plan; observation only.

Use:

| Previous ID | Current ID | Status | Constraint | Evidence |
|---|---|---|---|---|
| P1-1 | — | RESOLVED | I-2 | `File.kt:42` |
| — | P1-2 | NEW_IN_SCOPE | I-13 | `File.kt:88` |

Do not use raw P1 count as the sole convergence test. Equal counts may still show progress; lower counts do not prove completion.

Audit the complete original checklist on every re-entry.

Allow a newly discovered issue to block when any condition holds:

- It directly violates the original plan, an approved amendment, or an established production invariant within the original implementation scope, even if its file was untouched by the latest repair.
- The latest repair changed or behaviorally affected its runtime path.
- It is a cross-file or cross-plan contract affected by the repair.

Demote only genuinely pre-existing, unrelated, out-of-scope, or behaviorally unaffected adjacent issues to observations.

Never hide or demote a known in-scope production defect solely because its file was unchanged in the latest repair. Use the three-round terminal rule—not scope demotion—to bound late discovery.

## Phase 7: Decide the Outcome

### Pass

Pass only when build/tests are complete enough for meaningful verification and no blocking P1 remains.

Output:

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

Do not create a fix plan.

### Blocking P1 with an available round

Select the round:

- Initial verification produces `fix-1`.
- Re-verification after `fix-1` may produce `fix-2`.
- Re-verification after `fix-2` may produce `fix-3`.
- Allow `fix-3` to be implemented.
- Re-verification after `fix-3` may not produce `fix-4`.

Write:

```text
docs/plans/fix/<target-plan-name>/fix-<N>.md
```

Use the verified sub-plan filename for a sub-plan and the standalone filename for a standalone plan. Do not use the master-plan directory for a defect belonging to one sub-plan.

Include:

1. Original plan/sub-plan reference.
2. Actionable round number.
3. Constraint excerpts.
4. P1 lineage.
5. Findings with trigger frequency, impact, and evidence.
6. Minimal repair specifications: exact file, required behavior, prohibited changes, and acceptance behavior.
7. Pre-repair build/test status.
8. Full compliance audit.
9. Semantic completeness audit.
10. Proposed plan amendment, if required, clearly marked unapproved.

Exclude claims of completed repair, post-repair results, unrelated cleanup, and unsupported redesign.

After writing the fix plan, stop and await a separate repair agent or human dispatch.

### Terminal verification after fix-3

Do not create `fix-4`.

Write:

```text
docs/plans/fix/<target-plan-name>/verification-blocked-after-fix-3.md
```

Include:

1. Remaining P1s with exact evidence.
2. P1 lineage.
3. Trigger frequency and impact.
4. Build/test status.
5. Violated constraints.
6. Plan quality-gate result.
7. Smallest candidate follow-up scope.
8. Explicit statement that no `fix-4` was created.

Do not include another same-plan repair specification disguised as a stop report.

Treat the candidate follow-up as advisory only:

- Do not create or execute it automatically.
- Require explicit human approval.
- Require a distinct objective, invariant set, and bounded scope.
- Do not reset the round limit by merely renaming the same residual repair.
- Keep a persistent same-root-cause defect terminal until a human authorizes a genuinely separate plan.

### Early blocked condition

Stop early only when:

- Necessary repair conflicts with an approved constraint.
- Repair requires unauthorized scope expansion.
- Repair requires a new architecture not permitted by the plan.
- The quality gate proves a structural plan defect.
- Persistent infrastructure failure prevents meaningful verification.
- Three mechanical attempts are exhausted.

Raw P1 count equality is not an early-stop condition.

Output the round, exact failure, blocking constraint, root cause, options with tradeoffs, and smallest recommended human decision. Await direction.

## Phase 8: Handle Plan Amendments

Determine ownership:

```text
Does implementation violate a correct plan?
├── YES → Write the fix plan only.
└── NO
    └── Is a plan requirement missing or incorrect?
        ├── YES → Propose an amendment in the fix plan and stop for approval.
        └── NO → Record an observation only.
```

Never amend an approved original plan solely on verifier authority.

Append or apply a `修正记录` only when explicit human approval is already recorded in the repository or current task. Otherwise, label the amendment `待批准` and leave the original plan unchanged.

Never invent acceptance criteria and later treat them as approved requirements. Trace every P1 to:

- Original plan text.
- Human-approved amendment.
- Established production invariant with evidence.
- Semantic completeness failure with demonstrated production impact.

## Phase 9: Run the Plan Quality Gate

Run this gate before recommending correction or decomposition of the original plan.

Prove a structural defect through one or more conditions:

- **Excessive coupling:** The plan changes more than 10 files, spans 3 or more independent subsystems, or cannot be verified without repeatedly reopening unrelated modules. Treat counts as signals; explain actual coupling.
- **Missing invariant:** The plan introduces a shared field, state, identity, or semantic rule without one authoritative meaning, and evidence shows inconsistent interpretation.
- **Missing write-path audit:** The plan changes a shared store without identifying relevant writers, allowing an unreviewed writer to violate the new contract.
- **Inseparable constraints:** The required repair cannot satisfy two approved constraints simultaneously.

If proven, recommend plan correction or decomposition and stop for human approval.

If not proven, do not recommend broad decomposition. At terminal verification, provide only a narrow candidate follow-up scope under the safeguards in Phase 7.

## Repair Proportionality and Scope Discipline

Match every repair requirement to defect frequency and impact. For rare paths, prefer the simplest safe behavior: stable error, bounded logging, clamp/reject, fail closed, or audited skip.

Do not introduce classification hierarchies, DTOs, states, services, or UI branches for near-zero-frequency cases unless the approved invariant requires them.

Keep fix plans free of unrelated refactoring, cleanup, formatting, new features, and files outside the minimal affected runtime path.

If a closed decision appears questionable, record it for human review. Do not reopen it unilaterally.

## Knowledge Write-Back

Run after every outcome: pass, fix plan, blocked report, or terminal stop.

Create or update entries only for confirmed reusable lessons, including P1 classes, accumulation failures, dead states, contract failures, and repair regressions. Do not write feature-specific task lists as knowledge.

Apply deferred `hit_count` and `last_used` updates only to entries actually used.

Store under `docs/knowledge/<domain>/`:

```yaml
---
id: K-<short-slug>
domain: <store-or-module>
created: <yyyy-MM-dd>
last_used: <yyyy-MM-dd>
hit_count: 0
source: fix-v:<plan-name>:fix-<N>
severity: P1|P2
---
```

Use `source: fix-v:<plan-name>:stop-after-fix-3` for terminal verification.

Write:

```text
经验：<reusable lesson>
正确做法：<general rule>
反例：<bug pattern with file and line>
```

When an entry reaches the project's promotion threshold, add one concise rule with a `(K-<id>)` backlink to the shared instruction file. Keep full detail in `docs/knowledge/`. Do not run global decay from this skill.

## Red Flags

Stop and reassess when:

- Implementing a behavioral P1 instead of writing a fix plan.
- Creating `fix-4` or resetting the round limit by renaming the same repair.
- Treating generation of `fix-3` as terminal before repair and re-verification.
- Comparing only P1 counts.
- Demoting an in-scope defect merely because its file was untouched in the latest repair.
- Amending the approved plan without recorded human approval.
- Omitting exact evidence from a stop report.
- Recommending decomposition without passing the quality gate.
- Adding a class, state, migration, retry path, or recovery service during mechanical repair.
- Weakening tests to obtain a pass.
- Expanding scope because a broader redesign looks cleaner.

## Workflow

```text
Create approved plan
→ Implement plan
→ Run fix-v
→ If needed, create actionable fix-1
→ Separate agent repairs fix-1
→ Re-run fix-v
→ If needed, create actionable fix-2
→ Separate agent repairs fix-2
→ Re-run fix-v
→ If needed, create actionable fix-3
→ Separate agent repairs fix-3
→ Re-run fix-v
→ Pass, or terminal stop without fix-4
```

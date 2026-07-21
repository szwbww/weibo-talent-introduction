---
name: fix-v
description: Verify code changes against an approved implementation plan. Use after plan execution, after a separate repair pass applies a fix plan, or when auditing whether implementation, automated tests, state transitions, persistence semantics, and cross-plan contracts match approved design. Enforce evidence-based compliance, bounded mechanical repairs, P1 lineage, three actionable design-fix rounds, human-approved plan amendments, and strict scope control.
---

# Constrained Verification

## Purpose

Verify implementation changes against an approved plan and demonstrated production invariants.

When a blocking behavioral defect exists, specify its repair in a fix plan but do not implement the repair. Require a separate repair pass, then re-run this skill.

Edit code only for mechanical build/test repairs that require no product, architectural, or behavioral judgment.

At the start, announce:

> I'm using the constrained-verification skill to verify these changes.

## Core Terms

- **Approved plan:** The plan explicitly selected by the user/current task as the verification baseline, or recorded as approved in the repository. Baseline approval does not approve later amendments.
- **Demonstrated production invariant:** A runtime rule within the approved objective and affected runtime paths, normatively established by an explicit user statement, approved documentation, or an acceptance criterion tied to approved behavior. Logs, incidents, and reproducible scenarios may prove impact but cannot define a normative rule by themselves.
- **Mechanical attempt:** One stated mechanical repair set followed by a build/test rerun. Allow at most 3 attempts per verification pass.
- **Design fix round:** One actionable `fix-N` plan, its implementation by a separate repair pass, and the following re-verification. Allow `fix-1`, `fix-2`, and `fix-3` only.
- **Terminal verification:** Verification after `fix-3` has been implemented. If a blocking P1 remains, stop without creating `fix-4`.
- **Control-plane artifact:** A fix plan, blocked/terminal report, or knowledge entry created by verification. Exempt these artifacts from implementation scope checks; never use this exemption to edit product code.
- **Machine acceptance:** Automated plan items, normally `I-n` invariants and `S-n` style contracts.
- **Manual acceptance:** Human checklist items, normally `A-n`. Do not execute or block on them during fix-v.

Generating `fix-3` does not exhaust the third design fix round. Allow its separate implementation and terminal re-verification.

## Phase 1: Extract the Verification Contract

Read the complete original plan before editing or evaluating code.

### 1a. Establish the baseline

Confirm the exact approved plan or sub-plan selected by the task. If no approved baseline can be established, select an early blocked outcome.

Record:

- Design constraints and demonstrated production invariants.
- Approved implementation file scope.
- Runtime behavior and machine-verifiable acceptance criteria.
- Required automated tests and build commands.
- Rejected approaches and must-not-change requirements.
- Write/read-path audits.
- Cross-plan inputs, outputs, and ownership boundaries.
- Manual acceptance items.

Treat manual `A-n` items as pending human work. Do not mark them `PASS`, `FAIL`, or `BLOCKED` during machine verification. Report them separately in the final outcome.

Treat implementation files outside approved scope as unavailable for direct repair unless an approved amendment explicitly authorizes them.

### 1b. Read prior decisions and determine the round

Read every prior fix plan and blocked/terminal report referencing the same original plan.

Build a decision log from:

- `修正记录`.
- `不适用`, `已有决策`, `不做`, and `降级` sections.
- Crossed-out requirements.
- Human-approved amendments recorded in the repository or current task.

Let later approved decisions override older text. Do not reopen a closed approved decision as a finding.

Determine the current round from prior artifacts and repair state:

- No prior actionable fix plan: initial verification; next available plan is `fix-1`.
- `fix-1` implemented: re-verification may create `fix-2`.
- `fix-2` implemented: re-verification may create `fix-3`.
- `fix-3` implemented: terminal verification; never create `fix-4`.
- Latest `fix-N` not yet implemented: report that repair is pending; do not create `fix-(N+1)`.

Never overwrite an existing round or reset numbering by renaming the same repair.

### 1c. Read relevant knowledge

If `docs/knowledge/` exists:

1. Find entries matching the stores, modules, and defect classes under verification.
2. Read each relevant entry completely.
3. Add its `正确做法` rule as an audit probe.
4. Tag the probe with `(来源: K-<id>)`.
5. Defer `hit_count` and `last_used` updates until Phase 6.

Use knowledge as a research/audit probe, not independent authority for a P1. Trace every P1 to the approved contract, a demonstrated production invariant, or a semantic completeness failure with demonstrated impact.

Do not load or update unrelated knowledge entries.

### 1d. Build one checklist

Combine the original contract, approved amendments, closed decisions, prior P1 repair requirements, and relevant knowledge probes into one checklist.

Assign stable IDs. Preserve plan IDs such as `I-n` and `S-n` when present. Use the same checklist for the complete initial audit and every re-entry.

## Phase 2: Compile and Test

Run commands required by the plan. If absent, use documented project commands.

Record:

```text
Build: PASS | FAIL | BLOCKED
Tests: PASS | FAIL | BLOCKED — N passed, M failed, K skipped
```

If the runner does not expose counts, write `count unavailable`; never invent counts.

Classify every failure before editing:

- **Mechanical:** Syntax, imports, constructor wiring, stale test compilation, deleted-code references, or an assertion that unambiguously contradicts approved behavior.
- **Behavioral:** Runtime behavior violates the verification contract.
- **Infrastructure:** Environment, dependency, database, network, credentials, or an external service prevents execution.

Do not repair behavioral failures directly. Audit and classify them in Phases 4–5.

Diagnose infrastructure failures reasonably within scope. If required verification remains unexecuted, do not report PASS. Select an early blocked outcome unless the approved plan explicitly marks that check optional.

## Phase 3: Mechanical Fix Loop

Allow at most 3 mechanical attempts per verification pass. Mechanical attempts do not consume design fix rounds.

Before each edit:

1. Identify the failing file and line.
2. State the one-sentence repair.
3. Check the repair against every Phase 1 constraint.
4. Confirm the file is inside approved implementation scope.
5. Confirm no product, design, or behavioral judgment is required.

Allow only:

- Fix syntax or typographical errors.
- Add missing imports.
- Align constructor calls with approved signatures.
- Remove references to deleted code.
- Update a stale assertion only when approved behavior is explicit and unambiguous.

Prohibit:

- Adding a class, interface, enum, state, or workflow step.
- Adding retry, recovery, reconciliation, fallback, or new error behavior.
- Changing state-machine semantics or architecture.
- Modifying an already-applied database migration.
- Weakening a test to obtain a pass.
- Editing outside approved implementation scope.
- Any repair requiring product or design judgment.

After each attempt, rerun the relevant build/tests and record the result.

After 3 unsuccessful attempts, select an early blocked outcome. Do not perform a fourth attempt.

## Phase 4: Design Compliance Audit

Run this phase regardless of build/test outcome whenever meaningful source audit remains possible.

### 4a. Audit every machine-verifiable constraint

For every checklist item:

1. Open relevant implementation files.
2. Read runtime paths, not only declarations or signatures.
3. Trace inputs, writes, state transitions, persistence, errors, and outputs.
4. Record a verdict with exact evidence.

Use:

```text
Constraint I-1: [description] — ✅
Evidence: path/File.kt:42 — [runtime evidence]

Constraint I-2: [description] — ❌
Evidence: path/File.kt:88 — [violating behavior]
```

Require file and line for source verdicts. For runtime-only verdicts, cite the exact command, test, log, or reproducible scenario. If required evidence is unavailable, record `BLOCKED`, not a fabricated pass or failure.

Also record:

```text
Deleted code: ✅ | ❌ | N/A
No extras: ✅ | ❌
Scope compliance: ✅ | ❌
Manual acceptance: PENDING | N/A
```

Exclude control-plane artifacts from `No extras` and implementation scope checks.

### 4b. Check semantic completeness

#### Cross-invocation accumulation

For daily, hourly, cumulative, quota, or rate-limit semantics:

1. Find each counter's initialization.
2. Verify it survives repeated entry-point invocation when required.
3. Verify restart/retry cannot reset a persistent time-window limit.
4. Verify names such as `dailyTotal` match actual lifetime.

Classify a per-run counter implementing a cross-run limit as P1 when runtime/acceptance impact is directly evidenced or necessarily follows from the approved time-window semantics. Do not require a production incident when the bypass follows deterministically from the code and approved contract.

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

Classify a broken boundary contract as P1 only when it was introduced or behaviorally exposed by the current plan and its runtime/acceptance impact is evidenced or necessarily follows from approved semantics.

Report:

```text
Accumulation check: ✅ | ❌ | N/A | BLOCKED
State-machine check: ✅ | ❌ | N/A | BLOCKED
Cross-plan check: ✅ | ❌ | N/A | BLOCKED
```

### 4c. Establish the regression boundary

Determine whether each defect existed before the original plan's first implementation change. Use baseline code, history, tests, or other concrete evidence when available.

Classify:

- **Introduced:** Created by the plan's implementation; eligible for blocking P1.
- **Behaviorally exposed:** The plan made a pre-existing latent defect reachable or relevant; eligible for blocking P1 when within the affected contract.
- **Pre-existing adjacent:** Existed and remains behaviorally unaffected; observation only.
- **Uncertain:** Evidence cannot establish the boundary; report uncertainty and do not claim the defect is pre-existing.

Do not let a genuinely pre-existing, behaviorally unaffected adjacent defect block the current plan.

Do not demote an in-scope defect solely because its file was untouched by the latest repair.

## Phase 5: Classify Findings and Select an Outcome

### 5a. Classify findings

Use:

- **P1:** Proven production defect; repair regression; violation of an explicit mandatory requirement whose runtime/acceptance impact is directly evidenced or necessarily implied by the approved contract; or a semantically incomplete requirement with demonstrated/necessary impact.
- **P2:** Test-depth improvement, maintainability issue, hygiene, non-mandatory textual mismatch, or defensive hardening without proven contract impact.
- **Observation:** Pre-existing adjacent issue, unrelated/out-of-scope concern, unresolved uncertainty, or non-blocking note.
- **BLOCKED:** A required verdict cannot be reached because evidence or infrastructure is unavailable. `BLOCKED` is an outcome condition, not a fabricated P1.

Do not promote test preferences, formatting, style preference, or verifier inference to P1.

For every P1, record:

- Constraint source.
- Runtime/acceptance evidence or necessary semantic implication.
- Trigger frequency.
- Impact.
- Minimal repair scope.
- Amendment ownership.
- Regression-boundary evidence.

### 5b. Track P1 lineage

On re-entry, classify every prior and current P1:

- **RESOLVED:** Prior root cause and violation no longer exist.
- **PERSISTENT:** Same root cause and violation remain.
- **REGRESSION:** Latest repair introduced a defect.
- **NEW_IN_SCOPE:** Newly discovered violation traceable to the original contract.
- **PRE_EXISTING:** Predates and is behaviorally unaffected by the plan; observation only.

Use:

| Previous ID | Current ID | Status | Constraint | Evidence |
|---|---|---|---|---|
| P1-1 | — | RESOLVED | I-2 | `File.kt:42` |
| — | P1-2 | NEW_IN_SCOPE | I-13 | `File.kt:88` |

Audit the complete checklist on every re-entry. Do not use raw P1 count as the sole convergence test or as an automatic stop condition.

If a `PERSISTENT` P1 remains after its repair was implemented and the next repair specification would be materially identical, select early blocked instead of consuming another design fix round. Create a later `fix-N` only when new evidence identifies a materially different root cause, repair location, or contract-compliant repair action.

Allow a newly discovered issue to block when it violates the original plan, an approved amendment, or a demonstrated production invariant within original scope; when the repair affects its runtime path; or when it is an affected cross-file/cross-plan contract.

### 5c. Resolve amendment ownership

Use:

```text
Does implementation violate a correct plan?
├── YES → Specify implementation repair only.
└── NO
    └── Is a plan requirement missing or incorrect?
        ├── YES → Propose an amendment; do not apply it without approval.
        └── NO → Record an observation only.
```

Apply or append `修正记录` only when explicit human approval is already recorded in the repository or current task. Otherwise mark the proposal `待批准` and leave the original plan unchanged.

Never invent an acceptance criterion and later use it as authority for a P1.

### 5d. Run the plan quality gate when required

Run the quality gate when recommending plan correction/decomposition and during terminal verification. Record `N/A` otherwise.

Prove structural defects through actual coupling, not counts alone:

- **Excessive coupling:** More than 10 changed files, 3 or more independent subsystems, or repeated reopening of unrelated modules. Treat counts as signals; explain actual coupling.
- **Missing invariant:** A shared field, state, identity, or semantic rule lacks one authoritative meaning and code paths interpret it inconsistently.
- **Missing write-path audit:** A shared store changed without identifying relevant writers, allowing an unreviewed writer to violate the contract.
- **Inseparable constraints:** Required repair cannot satisfy two approved constraints simultaneously.

If a structural defect is proven, select an early blocked outcome and require human approval for plan correction/decomposition.

### 5e. Select exactly one provisional outcome

#### Pass

Pass only when required build/tests are `PASS`, all machine-verifiable audits are complete, and no blocking P1 or `BLOCKED` item remains.

Manual `A-n` items remain pending and do not block machine-verification pass. State clearly that fix-v pass is not final human acceptance.

Do not create a fix plan for a pass.

#### Blocking P1 with an available round

- Initial verification may produce `fix-1`.
- Re-verification after implemented `fix-1` may produce `fix-2`.
- Re-verification after implemented `fix-2` may produce `fix-3`.
- Allow `fix-3` to be implemented.
- Re-verification after implemented `fix-3` may not produce `fix-4`.

Prepare:

```text
docs/plans/fix/<target-plan-name>/fix-<N>.md
```

Use the verified sub-plan filename for a sub-plan and the standalone filename for a standalone plan.

Include:

1. Original plan/sub-plan reference and actionable round.
2. Constraint excerpts.
3. P1 lineage.
4. Findings with frequency, impact, boundary, and evidence.
5. Minimal repair specification: exact file, required behavior, prohibited changes, and machine-verifiable acceptance behavior.
6. Pre-repair build/test status.
7. Full compliance and semantic audits.
8. Proposed plan amendment, if needed, marked `待批准`.
9. Quality-gate result when applicable.
10. Pending manual acceptance items, without claiming completion.

Exclude behavioral code edits, completed-repair claims, post-repair results, unrelated cleanup, unsupported redesign, and new manual requirements. Keep repairs proportional to frequency/impact; do not add DTOs, states, services, or UI branches for rare paths unless an approved invariant requires them.

#### Terminal verification after fix-3

Do not create `fix-4`. Prepare:

```text
docs/plans/fix/<target-plan-name>/verification-blocked-after-fix-3.md
```

Include remaining P1 evidence, lineage, frequency/impact, build/test status, violated constraints, quality-gate result, narrow candidate follow-up scope, and an explicit statement that no `fix-4` was created.

Do not disguise another same-plan repair specification as a stop report. Any candidate follow-up is advisory, requires explicit human approval, and must have a distinct objective, invariant set, and bounded scope.

#### Early blocked

Select early blocked only when:

- No approved baseline can be established.
- Latest `fix-N` has not been implemented.
- A persistent P1 would require repeating a materially identical repair specification.
- Required repair conflicts with an approved constraint.
- Repair requires unauthorized scope expansion or unapproved architecture.
- The quality gate proves a structural plan defect.
- Persistent infrastructure failure prevents required verification.
- Three mechanical attempts are exhausted.
- Required build/test remains `FAIL` or `BLOCKED`, no actionable P1 repair applies, and permitted mechanical repair cannot resolve it.

Prepare:

```text
docs/plans/fix/<target-plan-name>/verification-blocked-initial.md
docs/plans/fix/<target-plan-name>/verification-blocked-after-fix-<N>.md
```

Use the first path for initial verification and the second after a fix round. If no target plan can be identified, emit the blocked report inline and do not invent a directory.

Include the round, exact failure, blocking constraint, attempts made, root cause, options/tradeoffs, and the smallest human decision required.

## Phase 6: Knowledge Write-Back

Run before emitting every outcome: pass, fix plan, early blocked, or terminal stop.

Create/update entries only for confirmed reusable lessons, including P1 classes, accumulation failures, dead states, contract failures, and repair regressions. Do not write feature-specific task lists as knowledge.

For each relevant existing entry actually used as an audit probe:

- Increment `hit_count` once per verification pass, not once per checklist occurrence.
- Set `last_used` to today.

Accept the common existing fields `id`, `domain`, `created`, `last_used`, `hit_count`, and `source`. Accept older/create-p entries without `last_source` or `severity`; do not reject them.

For new fix-v entries, include:

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

When updating an older entry, preserve its original `source`, set `last_source`, and add/correct `severity` only when current evidence supports it.

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

Use the project's configured promotion threshold, defaulting to 10. When an entry reaches it, propose one concise shared rule with a `(K-<id>)` backlink in the outcome artifact.

Modify a shared instruction file only when explicit human approval is already recorded and the project designates the target. Otherwise propose promotion without choosing or creating a destination.

This approval rule applies to plan amendments and shared-instruction promotions only. Conflicting instructions in another skill do not constitute approval. Do not invoke another skill to bypass an unapproved amendment or promotion.

Do not run global decay, archive pruning, or unrelated knowledge cleanup.

## Phase 7: Emit the Outcome and Stop

After Phase 6, emit the selected outcome once.

### Pass format

```markdown
## 验证结果：通过（机器验证）

计划：[plan filename]
编译：PASS
测试：PASS — N passed, M failed, K skipped
人工验收：PENDING — A-n 由人工执行 | N/A

### P1 收敛

[Lineage table, or "N/A — initial verification had no P1"]

### 合规审计

Constraint I-1: ✅ Evidence: `File.kt:42`
Constraint S-1: ✅ Evidence: `Other.css:18`
Accumulation check: ✅ | N/A
State-machine check: ✅ | N/A
Cross-plan check: ✅ | N/A
Deleted code: ✅ | N/A
No extras: ✅
Scope compliance: ✅

### 非阻塞项

[P2/observations, or N/A]
```

For blocking P1, write and report the prepared `fix-N` plan. For terminal verification, write/report the terminal artifact. For early blocked, write/report the blocked artifact or inline report.

Then stop. Do not implement behavioral P1 repairs, apply unapproved amendments, create `fix-4`, perform manual acceptance, or continue into another verification round.

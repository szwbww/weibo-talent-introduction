# Child Brief — c1 · 11-fact-supply（V109 事实供给）

- Plan: `docs/plans/2026-08-28/11-fact-supply.md` (Plan identity: `commit:5a90e3e53e5fe8b40059b3090f086d6b36a09a01`)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-08-28-reply-orchestration-order`
- Branch: `fast/2026-08-28-reply-orchestration-order`
- Child base (product boundary): `de228e17cc0134a7c11dea7cbf82054e8d249f99`
- Master plan: `docs/plans/2026-08-28/10-reply-orchestration-order.md` (same commit)

## Contract

1. The plan file above is the complete approved contract. Read it in full from disk before any edit. Follow `execute-p`: bind to plan identity and worktree identity, then implement.
2. Modify ONLY the 3 authorized files listed in the plan's `## 变更文件清单`: `src/main/resources/db/migration/V109__qa_fact_supply_and_controlled_key_repair.sql` (new), `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaFactSupplyMigrationTest.kt` (new), `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyIntentParityTest.kt` (extend). Nothing else. Do not touch `docs/plans/fast/**` (fast-p evidence; the controller commits it separately). Do not touch any other product/test file. **Zero production Kotlin changes.**
3. The SQL in task T-1 is final code: copy it verbatim, do not add/remove/reword statements or bodies. The five INSERTs and two UPDATEs must appear exactly as written.
4. Preserve every invariant I-1..I-6 and every `What must NOT change` item exactly as written.
5. Phase 6 knowledge writes are NOT part of this child's authorized files (the plan assigns them to the plan author / fix-v, which fast-p defers to human review). Do not create or edit `docs/knowledge/**`.

## Global constraints (master plan 10, apply to every statement in V109)

- **G-1 (frozen rules)**: `qa_rule` id ∈ {1, 3, 21, 24} were hand-adjusted by the requester; this run must not modify any column of those rows. Every `UPDATE qa_rule` MUST carry `AND id NOT IN (1, 3, 21, 24)` as a second guard independent of `reply_subject` location.
- **G-2 (id↔subject evidence boundary)**: only id=24 (`Program overview`) has migration-level mapping evidence; do not assume id 1/3/21 subjects. Demand-side baseline 2026-08-28 (provided): id1=`About the talent program`, id3=`Application criteria`, id21=`Meeting arrangement`, id24=`Program overview`. Conflict check already done: this plan's targets (`Project sensitivity concerns`, `Pre-contract IP boundary`, 5 new rules) do not intersect the frozen four.
- **G-3 (no overwrite of operator runtime edits)**: INSERTs guarded `WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = '<subject>')`; keyword appends use `CONCAT` + `NOT LIKE` de-dup; full-body rewrites require a verbatim online baseline guard (this plan has none); every `UPDATE` carries `updated_at = updated_at`.
- **G-4 (controlled groups are exact-set triggers)**: G1 `{confidentiality.materials}`, G2 `{fees.policy}`, G3 `{contract.party, contract.terms}`, G4 `{ip.arrangements}` — `QaCoverageKeyCatalog.validateControlledBody` fires only when the coverage set equals a group exactly.
- **G-5 (coverage keys must be paired with intents)**: a non-empty coverage set with no intent referencing any key is structurally unreachable. New catalog keys and new intents must ship in the same plan (this plan adds neither).
- **G-6 (catalog append-only)**: `QaCoverageKeyCatalog` entries may only be appended at list end. This plan does not touch the catalog.
- **G-7 (requestKey hash purity)**: `requestKey = sha256(sourceVersion, index, requestText, intentKeys)`; nothing else may enter the hash. Not applicable to this plan but binding for the run.

## Required commands (run fresh, after final state)

JDK 11 is mandatory: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

```bash
# this plan's tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaFactSupplyMigrationTest,QaCoverageKeyIntentParityTest
# full gate
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
# build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
# hygiene
git diff --check
```

Pass criteria: every `mvn` exit 0 with `Tests run: N, Failures: 0, Errors: 0`; `git diff --check` exit 0. (Docker-gated `FlywayMigrationIntegrationTest` is opt-in and NOT required in this environment.)

## Downstream interfaces (for later children)

- c2 (12-letter-closer) assumes the 5 new facts + deadlock repair + IP keywords exist as migration-level data (V109). No code interface.
- Migration version allocation: V109 belongs to this child; V110 is reserved for plan 17 (NOT part of this run).

## Commit

Single local implementation commit (no fast-p files, no evidence):

```text
feat(fast-p): implement c1
```

Write the full execution report to `docs/plans/fast/2026-08-28-reply-orchestration-order/children/c1/execution.md` (overwrite the empty placeholder) using the execute-p output contract.

## Return

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

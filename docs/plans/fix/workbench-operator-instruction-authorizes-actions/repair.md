# Repair Plan: workbench-operator-instruction-authorizes-actions

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-08-20/workbench-operator-instruction-authorizes-actions.md`
Verification report: `verify-p` run 2026-08-20, Verification Result **FAIL**, Convergence **INITIAL** (first pass; no prior repair round exists for this plan)
Implementation boundary: uncommitted working-tree diff vs `main@08a25fe`

## Objective

Bring the working tree back into I-9 file-scope compliance so that `git diff --name-only` outputs exactly the 7 files listed in the baseline plan's `## 变更文件清单` — no more, no less — without touching the content of any of those 7 files, which verify-p already confirmed are correctly implemented.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P2 (blocks PASS — violates mandatory I-9 / A-13) | Invariant I-9: "本计划只允许修改 `## 变更文件清单` 中列出的 7 个文件"; machine test: `git diff --name-only` must equal the 7-path allow-list exactly | 9 tracked files outside the allow-list carry uncommitted modifications: `docs/releases.json` (an external "multi-ai-kit" deploy-log entry recording a production publish of the base commit `08a25fe` itself — not authored by this plan's implementation) and 7 `docs/knowledge/**/K-*.md` files (6 are pure `hit_count`/`last_used` usage-tracking bumps from this project's knowledge-loading convention; one, `K-manual-send-safety-gate-first-hit-only.md`, additionally carries an appended, unauthorized "修订" section and a trailing-blank-line whitespace defect that fails `git diff --check`) |

## Findings Excluded

| Finding | Reason |
|---|---|
| 20 pre-existing `node --test` failures (aiReplyReviewConfirmation, batchEntryRelocation, contactHeadLayout, taskRecordsPaging, etc.) | Observation in the verify-p report — all failing suites are frontend files this plan never touches (confirmed zero frontend diff); unrelated pre-existing defect, excluded per repair-p's "unrelated pre-existing defects" exclusion rule |
| Two new untracked knowledge docs (`docs/knowledge/llm/K-operator-directed-authorization-seam.md`, `docs/knowledge/llm/K-workbench-state-lazy-expiry.md`) and the plan file itself | Observation in the verify-p report, not a confirmed mandatory violation — `git diff --name-only` (I-9's exact-match machine check) does not list untracked files, so they do not cause the I-9 failure; excluded per repair-p's "BLOCKED items and observations" exclusion rule |
| IP-1 through IP-7, and E-1/E-2/E-3 test-execution evidence | BLOCKED in the verify-p report (no JDK 11 + Maven + Maven-Central-reachable environment was available to the verifier) — excluded per repair-p's "BLOCKED items" exclusion rule. Not a confirmed violation; requires the `## 验证命令` section to be run in an environment with that toolchain before PASS, not a repair task |

## Unchanged Contract

- The 7 plan-authorized files (`AiReplyActionPolicy.kt`, `AiReplyDraftService.kt`, `TrustReplyWorkbenchService.kt`, `PendingMailOperationService.kt`, `AiReplyDraftServiceTest.kt`, `TrustReplyWorkbenchItemFlowTest.kt`, `PendingMailOperationServiceTrustWorkbenchTest.kt`) keep their current content unchanged — verify-p already confirmed I-1 through I-8 pass on them.
- All must-NOT-change items from the baseline plan (§`## 需求描述` → `What must NOT change`) remain in force, in particular item 8 (no frontend changes) and item 9 (no HTTP contract changes) — this repair does not touch `src/main/resources/static/` or any HTTP DTO.
- No behavioral code change of any kind. This repair only discards uncommitted modifications to out-of-scope tracked files; it does not add, edit, or rewrite any file content.
- The knowledge base's own genuine staleness (e.g. `K-manual-send-safety-gate-first-hit-only.md`'s outdated "现状" section, which the baseline plan's own Phase 0 audit already flagged as due for revision) is not resolved by this repair — reverting it merely removes an unauthorized edit from this plan's diff; a correct revision of that knowledge doc is a separate, explicitly scoped task outside this baseline plan.

## Authorized Files

| File | Purpose |
|---|---|
| `docs/releases.json` | Revert to its `main@08a25fe` content — discard the externally-appended deploy-log entry that is not part of this plan's implementation |
| `docs/knowledge/build/K-js-tests-run-via-exec-plugin.md` | Revert to its `main@08a25fe` content |
| `docs/knowledge/llm/K-action-sanitizer-inclusive-offset.md` | Revert to its `main@08a25fe` content |
| `docs/knowledge/llm/K-ai-reply-action-cta-variant-coverage.md` | Revert to its `main@08a25fe` content |
| `docs/knowledge/llm/K-grounded-proposed-action-body-parity.md` | Revert to its `main@08a25fe` content |
| `docs/knowledge/llm/K-sensitive-action-span-granularity.md` | Revert to its `main@08a25fe` content |
| `docs/knowledge/llm/K-sensitive-cta-compound-material-coverage.md` | Revert to its `main@08a25fe` content |
| `docs/knowledge/llm/K-sensitive-material-cta-not-mention.md` | Revert to its `main@08a25fe` content |
| `docs/knowledge/mail/K-manual-send-safety-gate-first-hit-only.md` | Revert to its `main@08a25fe` content — discard both the usage-tracking bump and the unauthorized "修订" section |

No other file may be created, edited, or deleted by this repair.

## Repair Tasks

### R-1: Discard out-of-scope tracked-file modifications

- Resolves: V-1
- Root cause: 9 tracked files outside the baseline plan's 7-file allow-list carry uncommitted modifications — 1 external deploy-log append (`docs/releases.json`) and 7 knowledge-doc usage-tracking/content edits (`docs/knowledge/**/K-*.md`) — none of which the baseline plan authorized touching.
- Files: the 9 files listed in `## Authorized Files` above.
- Change: for each of the 9 files, discard the uncommitted working-tree modification and restore the exact blob committed at `main@08a25fe` (e.g. `git checkout 08a25fe -- <path>` or equivalent). No file content is authored or rewritten by this repair — only reverted to its last-committed state.
- Regression test: none required — this is a pure revert with no behavioral surface. The discriminating check is the completion-criteria command below.
- Existing verification: N/A (no code path is touched; the 7 authorized files' existing test coverage, per verify-p, is unaffected by this repair).
- Must not change: the 7 plan-authorized files' content; any file not in the 9-file list above; do not re-edit or "fix" the knowledge docs' stale content as part of this repair.
- Prohibited: committing the reverted state under a message that implies new authorship of the knowledge-doc content; re-running any tooling that would immediately re-append the same `docs/releases.json` entry or re-bump the same `hit_count` fields before this repair's completion check is verified.

## Verification Commands

1. `git diff --name-only` — must output exactly the 7 paths in the baseline plan's `## 变更文件清单`, no more, no less.
2. `git diff --check` — must exit 0 with no output (the whitespace defect was solely in the now-reverted `K-manual-send-safety-gate-first-hit-only.md`).
3. Full baseline-plan suite before PASS, per the baseline plan's `## 验证命令` (requires JDK 11 `zulu-11` + Maven with Maven Central reachable — unavailable in the verify-p sandbox that produced the FAIL report; must be run in an environment with that toolchain, e.g. the developer's own machine, before this repair — and the underlying plan — can be marked PASS):
   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
     -Dtest=AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchServiceTest,AiReplyActionPolicyTest,PendingMailOperationServiceTrustWorkbenchTest
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
   JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
   ```

## Completion Criteria

- `git diff --name-only` (vs `main@08a25fe`) equals exactly the baseline plan's 7-file allow-list.
- `git diff --check` exits 0.
- The 7 plan-authorized files are byte-for-byte unchanged by this repair (diff of R-1 touches only the 9 listed files).
- Changed files remain inside the authorized list in `## Authorized Files`.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

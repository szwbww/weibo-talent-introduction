# Repair Plan: ai-reply-grounded-server-owned-envelope-plan

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-07-27/ai-reply-grounded-server-owned-envelope-plan.md
Verification report: current `verify-p` (2026-07-27, FAIL / INITIAL)
Implementation boundary: `HEAD` `2d3fd75f` through current working tree (10 baseline-authorized product/test files; no controller or client changes)

## Objective

Restore the mandated `STRUCTURE/bind → compose → CLAIM → TRUST → ACTION/final parity` candidate pipeline and give every emitted stable diagnostic a code-specific, body-free repair instruction.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-6, I-5: ACTION/final parity runs only after bind, compose, CLAIM and TRUST | `AiReplyGroundedDraftMaterializer.parseUnifiedJson()` returns an ACTION verdict for invalid `actionText` before claim binding/composition; `AiReplyDraftService` cannot run the required later stages. |
| V-2 | P1 | I-8, T5: each stable diagnostic receives a corresponding repair instruction | `repairInstruction()` branches mostly on stage, so distinct STRUCTURE/CLAIM/TRUST codes receive the same generic text instead of the required code-specific correction. |

## Findings Excluded

| Finding | Reason |
|---|---|
| O-1 — `UnmatchedInboundAiReplyTurnKnowledgeTest#real endpoint send failure cancels pending progress flush` | Reproduced failure is in an unchanged streaming-controller test outside baseline and repair scope. It races cleanup against its first progress send; no controller/test edit is authorized here. It must be resolved independently before the baseline can receive PASS. |
| Prior repair items for strict non-text `actionText` rejection and bounded audit diagnostics | Current implementation and focused tests already prove them: materializer rejects number/object/blank values; audit projects 20 distinct items with correct total/truncated and legacy keys. |

## Unchanged Contract

- Do not modify controllers, `HttpLlmDraftClient`, provider model/seam, TTL, cancellation, SSE/progress behavior, schema/data, fallback text, adoption/send gates, or automatic-send authority.
- Do not loosen exact claim-key binding, answerBody-only authority, high-risk/modality/trust checks, action checks, or the single-repair limit.
- Diagnostics and repair messages must not contain raw response, claim/action text, email body, prompt, source text, or `answerBody`.
- Do not add a second repair, a partial-claim success path, or another audit top-level field.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt` | Keep protocol parsing/binding/composition inputs separate from final ACTION verdicts. |
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt` | Run ACTION exactly after CLAIM/TRUST and map stable codes to repair instructions. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt` | Lock structural binding and deferred ACTION metadata behavior. |
| `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt` | Lock stage order, single source read/validation, and code-specific body-free repair messages. |

## Repair Tasks

### R-1: Defer all action semantics to the final candidate stage

- Resolves: V-1
- Root cause: `AiReplyGroundedDraftMaterializer.kt:91-96` emits `ACTION/AI_REPLY_ACTION_TEXT_INVALID` before `AiReplyDraftService.kt:950-1021` executes CLAIM, TRUST, and final ACTION/parity checks.
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializer.kt`; `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- Change: Preserve enough non-body action protocol metadata for final validation without accepting an invalid action. Run exact JSON STRUCTURE/bind, deterministic compose, one CLAIM validation, one TRUST validation, then all actionText type/blank/singleton/authorization, claim-action, and final-body parity checks. Any failure still yields no candidate body and at most one repair.
- Regression test: A candidate simultaneously violating CLAIM, TRUST, and ACTION, including invalid `actionText`, yields diagnostics in `CLAIM`, `TRUST`, then `ACTION` order; source resolution/claim validation occurs once; valid `null` and an authorized singleton action retain existing behavior.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest=AiReplyGroundedDraftMaterializerTest,AiReplyHighRiskClaimValidatorTest,AiReplyDraftServiceTest`
- Must not change: exact set/order binding, sourceIds/answerBody authority, aggregate warnings, repair temperature, provider call cap, fallback state, and final sanitizer as defense-in-depth.
- Prohibited: early ACTION short-circuit, repeated claim/source validation, partially materialized output, action text in diagnostics, and relaxed null/blank/non-text handling.

### R-2: Make repair guidance specific to each emitted stable code

- Resolves: V-2
- Root cause: `AiReplyDraftService.kt:1201-1217` maps STRUCTURE, CLAIM, and TRUST only by stage; diagnostics such as unknown key, missing set, invalid field, source unavailable, modality, trust rhetoric, and enterprise uncertainty lose their distinct correction guidance.
- Files: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`; `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`
- Change: Map every stable code emitted by the materializer, claim/trust validators, and final action checks to a concrete protocol/safety correction. Keep a safe stage fallback only for unexpected legacy codes. The repair message remains last, carries stage/code/claimKey plus the exact current-plan JSON skeleton, and never echoes candidate or source content.
- Regression test: Table-drive emitted STRUCTURE, CLAIM, TRUST, and ACTION codes through a first failed grounded call. For each, assert the final repair message includes its code and its distinct prescribed instruction, the legal skeleton, and no raw candidate/mail/source marker; assert no more than two provider calls.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest=AiReplyDraftServiceTest`
- Must not change: INITIAL/REPAIR lineage, 0.3/0.0/0.6 temperatures, exact plan skeleton, aggregate warnings, and fail-closed fallback.
- Prohibited: generic code-only guidance, raw-response echoing, source/fact text injection, deterministic-envelope duplication, client/controller changes, or additional repair attempts.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest=AiReplyGroundedDraftMaterializerTest,AiReplyHighRiskClaimValidatorTest,AiReplyDraftServiceTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test -Dtest=AiReplyReviewAuditServiceTest,GroundedAutoReplyDecisionServiceTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test` — rerun before baseline PASS; separately track excluded O-1 if it persists.
4. `git diff --check`

## Completion Criteria

- All action violations, including non-text/blank `actionText`, are diagnosed only in the final ACTION stage after bind, compose, exactly one CLAIM pass, and exactly one TRUST pass.
- Every emitted stable STRUCTURE/CLAIM/TRUST/ACTION code has a distinct, body-free repair instruction; all repair prompts retain exact diagnostics and the legal skeleton.
- No candidate with any failure yields partial LLM text; repair remains capped at one attempt.
- Changed files are limited to the authorized files.
- Focused commands pass. Full-suite O-1 is not repaired by this plan and remains an independent prerequisite for a baseline PASS.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

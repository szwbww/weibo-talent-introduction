# Repair Plan: trusted-reply-shared-workbench-02-item-lock-assembly

Status: DRAFT — HUMAN APPROVAL REQUIRED  
Baseline plan: docs/plans/2026-07-27/trusted-reply-shared-workbench-02-item-lock-assembly.md  
Verification report: verify-p phase2, 2026-07-28 (current working tree)  
Implementation boundary: working tree against `e12c8069`; phase2's implementation/test diff plus untracked `TrustReplyWorkbenchItemFlowTest.kt`

## Objective

无依据确认语不得接受任何带期限的核实/回复承诺，不论期限使用阿拉伯数字、英文数字词或中文数字词。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-7 | P1 | I-6 — 无依据确认语禁止时间/期限承诺 | `validateNoEvidenceAcknowledgement()` 仅经 `containsHallucinatedNumberOrUrl()` 识别阿拉伯数字（`AiReplyHighRiskClaimValidator.kt:35`），而该数字/时间规则只匹配 `\\d` 与英文单位（`:365`, `:394-397`）。`我会在三天内核实后再回复。` 命中 pending 语义，却没有任何 reject 分支。 |

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1～V-5 | 当前实现和定向测试已满足：SSE coordinator、CTA authority、运行路径覆盖、claim action 复验、OMIT 版本化。 |
| V-6 | 已解决：assemble 用已复算的 `TrustReplyItemVersion.answerText` compose；`TrustReplyWorkbenchItemFlowTest` 覆盖带前后空白 ACK 输入。 |
| `TrustReplyWorkbenchServiceTest.kt` | 仅为构造函数 mock 注入新增依赖；非行为 scope mismatch。 |
| 人工验收 A-1～A-8 | 仍须人工执行。 |

## Unchanged Contract

- 安全中英文 fallback 原文继续通过；不含期限的“核实后再回复 / check and follow up”继续允许。
- ACK 仍为单段、1～600 字符；仍拒绝 URL、事实、金额、身份/合同声明、CTA、列表、内部 token。
- 不改变 handling matrix、版本 ID、claim 绑定、assemble、SSE、前端、持久化或发送链路。

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidator.kt | 令无依据确认语对中英文、数字词和阿拉伯数字形式的期限/时间承诺 fail-closed。 |
| src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyHighRiskClaimValidatorTest.kt | 固定跨语言期限绕过的回归证据，并保留安全 fallback 通过断言。 |

## Repair Tasks

### R-1: 阻断无依据确认语的期限承诺

- Resolves: V-7
- Root cause: 无依据 gate 的数字/时间检测只覆盖阿拉伯数字和英文时间单位；中文数字词及英文拼写数字词可绕过。
- Files: `AiReplyHighRiskClaimValidator.kt`, `AiReplyHighRiskClaimValidatorTest.kt`.
- Change: 无依据确认语一旦包含期限、截止时间或承诺性时间表达即拒绝；检测不得依赖某一种数字书写形式。保留无期限的通用核实/跟进语及计划固定 fallback。
- Regression test: 至少拒绝 `目前没有已核验的信息可以确认，我会在三天内核实后再回复。` 与 `I do not have verified information yet, but I will check and follow up within three days.`；中英文固定 fallback 仍通过。
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyHighRiskClaimValidatorTest,AiReplyDraftServiceTest test`.
- Must not change: ACK 的非期限 pending 语义、固定 fallback 字符串、现有 action/fact/list/internal-token 拒绝语义及全部有依据 claim 校验。
- Prohibited: 放宽任何 ACK gate；新增事实/CTA；修改前端、API、版本、compose、发送或持久化路径。

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyHighRiskClaimValidatorTest,AiReplyDraftServiceTest test`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyDraftServiceTest,AiReplyHighRiskClaimValidatorTest,AiReplyPointByPointComposerTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchControllerTest,AiReplyGroundedDraftMaterializerTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,GroundedAutoReplyDecisionServiceTest,PendingMailOperationServiceTest test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `rg -n "sendManualRichReply|MailDeliveryService|mailRecordRepository.save|mailRecordQaRuleRepository.save" src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`

## Completion Criteria

- 中英文、数字词及阿拉伯数字形式的 ACK 期限承诺均被 `validateNoEvidenceAcknowledgement()` 拒绝。
- 计划指定中英文 fallback 逐字保留且通过 gate。
- 定向、基线定向和全量 Maven 命令通过；send-boundary scan 无命中。
- 改动仅限 Authorized Files。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

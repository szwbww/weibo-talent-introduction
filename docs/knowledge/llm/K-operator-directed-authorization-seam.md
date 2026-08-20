---
id: K-operator-directed-authorization-seam
domain: llm
created: 2026-08-20
last_used: 2026-08-20
hit_count: 0
source: create-p:workbench-operator-instruction-authorizes-actions
severity: P1
---

# `ANSWER_FROM_OPERATOR_INPUT` 的动作授权分散在五处，且 `deriveAllowed` 的后两个形参是死参数

改「按回答说明生成」的动作行为时，必须同时看这五处，漏一处就表现为"改了没用"或"能生成不能锁定"：

| # | 位置 | 判据 | 特点 |
|---|---|---|---|
| 1 | `AiReplyDraftService.kt:660` | `deriveAllowed(inboundText, null, emptyList())` | 生成侧授权来源 |
| 2 | `AiReplyDraftService.kt` `withActionBoundary`（`:1724-1744`） | 把 `formatAllowedLabel(allowed)` 写进 system prompt | `NONE` 会与"只许复述 basis"直接冲突，模型服从边界 |
| 3 | `AiReplyDraftService.kt:708` | `findViolations(candidate, allowedActions)` | 命中即 `FALLBACK_NO_RESPONSE`，运营看到"生成失败" |
| 4 | `TrustReplyWorkbenchService.kt:1352` | `detectActions(locked.answerText).isNotEmpty()` | **不看 `allowed`**，最硬的一道；只要含任何动作就判废 |
| 5 | `PendingMailOperationService.kt:788-792` | `restrictForTrustState(deriveAllowed(...), hasBlockingTrust)` + `findViolations` | 按整封信算，属人工发送取证 |

## `deriveAllowed` 的形参在逐条链路上恒为 null

`deriveAllowed(inboundText, operatorInstruction, operatorTurns)` 支持从运营输入解析意图，但
`grep -rn "deriveAllowed" --include=*.kt src/main` 的 6 个调用点里，**只有整封 `generate()`
（`AiReplyDraftService.kt:863`）真的传值**，其余 5 处一律 `null, emptyList()`。
看到这个签名不要假设运营输入已经参与授权。

## 不要指望扩正则来让运营的中文说明生效

`MATERIAL_INTENT` / `MEETING_INTENT` 是为解析**来信英文**设计的。实测「希望专家先提供一下简历
做一个简单的了解 然后再安排 zoom 视频会议」：MEETING 命中（`zoom` / `视频会议`），MATERIAL **不命中**
（中文分支只有 `请对方提供(简历|材料)|我的简历|索要(简历|材料)|附件.{0,10}(简历|履历|材料)`）。
半个生效比全不生效更难排查。授权应由**处理方式**决定，不去读自由文本。

## G1 与 G2 是两件事，别一起放开

- **G1 授权**：谁有资格提出对外动作。可以按 handling 放开。
- **G2 合规**：敏感材料 CTA（`detectSensitiveMaterial`，**不读 `allowed`**）、CV 目的缺失、CV 自愿缺失。
  永不放开——要让文案通过，改 prompt 让模型产出合规句式，不要放松正则。
  实测可通过的范式句：`If you would like to proceed, you are welcome to share your CV at your
  convenience so that we can carry out an initial eligibility review.`（`eligibility review` 命中
  `CV_PURPOSE`，`at your convenience` / `you are welcome to` 命中 `CV_OPTIONALITY`，且必须落在**同一句**
  内——切分单位是 `SENTENCE_SPLIT`）。

关联：[[K-sensitive-material-cta-not-mention]]、[[K-ai-reply-action-cta-variant-coverage]]、[[K-grounded-proposed-action-body-parity]]、[[K-manual-send-safety-gate-first-hit-only]]

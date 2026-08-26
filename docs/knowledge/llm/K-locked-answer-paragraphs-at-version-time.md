---
id: K-locked-answer-paragraphs-at-version-time
domain: llm
created: 2026-08-19
last_used: 2026-08-21
hit_count: 1
source: create-p:workbench-repair-02-claim-paragraphs
severity: P1
---

经验：逐项工作台的正文要分段时，直觉是「在 composer 里把 claim 用 `\n\n` 连起来」——这会违反 [[K-locked-item-assembly-list-not-set]] 的「每个非省略 `answerText` 必须逐字出现且恰好一次」「锁定后 composer 不得再次 trim 或格式化」。

正确做法：段落结构属于 `answerText` **本身**，在**版本创建时**一次性规范化完成；composer 保持「frame 块 + 逐字答案，`joinToString("\n\n")`」不变。

落点是三个必须同源的拼接位置（本仓 2026-08-19 实测）：
- 生成侧 `AiReplyDraftService.kt:1552`（`section.answers.joinToString(...)`）
- 校验侧 `TrustReplyWorkbenchService.kt:1286`（`answerText != canonical.joinToString(...)` 否则抛 `TRUST_REPLY_ANSWER_CLAIMS_MISMATCH`）
- 物化侧 `TrustReplyWorkbenchService.kt:1331`（`normalizedAnswer`，该值进 `versionId()` 哈希）
- 外加测试镜像 `TrustReplyWorkbenchItemFlowTest.kt:1204`

三处任一漂移 → 整合按钮永远 422。**必须抽成单一常量**，禁止各写字面量。

两个刻意的例外，不要顺手改：
- `TrustReplyWorkbenchService.kt:1153` 的 `finalBody`（信任门禁入参）保持**单空格**——高风险短语族匹配以连续文本为前提（[[K-high-risk-phrase-family-symmetric-match]]）。
- `OMIT` / `ACKNOWLEDGE_PENDING` / `ANSWER_FROM_OPERATOR_INPUT` 三分支 `claims` 恒空，走 `answerText.trim()`，不参与 join。

副作用与降级：改 `answerText` 会让存量 locked item 在 `canonicalizeClaims` 相等性检查失败，由 `restoreSavedStateWithFrame` 的 catch（:575-587）接住判 STALE——这是**既有的优雅降级路径**，不需要升 `STATE_SCHEMA_VERSION`。

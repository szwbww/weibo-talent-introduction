# Phase 1 URL-safe request extraction：修复计划 1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-12/ai-reply-safety-model-plan-index.md`
- 子计划：`docs/plans/2026-07-12/ai-reply-url-safe-request-extraction.md`
- 复验对象：Phase 1 URL-safe request extraction

## 约束摘录

| ID | 约束 |
|---|---|
| I-1 | 问号定位前掩码 http(s) URL；URL 整行不得成为 request item。 |
| I-2 | 掩码仅用于定位；返回项从原文 offset 还原。 |
| I-3 | `extractGapTexts` 与 `extractRequestItems` 共用同一 URL-safe tokenizer。 |
| 边界 | 普通问句、项目符号、无问句/无项目符号 fallback，以及 `match()` 的自动回复语义不变。 |

## 修正记录表

| P1 | 触发频率 | 问题 |
|---|---|---|
| P1-1 | 低频；专家只给 Scholar/Scopus 链接，或以 bullet 列出链接时稳定触发 | URL-only 项绕过 `extractQuestionSentences()`：bullet 直接进入 `combined`，无 bullet/问句时 fallback 直接返回整个正文。违反 I-1 的“URL 整行不得成为 request item”。 |

## 修复规格

### P1-1：所有 request-item 入口过滤 URL-only 文本

- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt`
- 修改：将已有 `isUrlOnlyRequestFragment` 作为 `extractRequestItems` 的统一过滤条件；在 bullet 参与 normalized dedup 前排除 URL-only bullet，并在 whole-body fallback 前判断正文是否 URL-only。
- 预期：
  - `https://scholar.google.com/citations?user=...` 单独作为正文时返回空 request item；
  - `- https://www.scopus.com/authid/detail.uri?authorId=...` 不产生 request item；
  - 含自然语言的 bullet（即使带 URL）仍原样保留一次；
  - 普通无问句/无 bullet 的自然语言 fallback 仍返回原文 trim。
- 复杂度：复用现有 helper，不新增状态、DTO、regex 或抽取分支体系。

- 文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`
- 修改：补充 URL-only 正文和 URL-only bullet 的回归断言；保留当前“文字 + URL bullet 保留一次”断言。

## 当前状态（修复前）

- 定向：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaMatchServiceTest test` — PASS，27 passed。
- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` — PASS，Surefire 1406 passed，0 failed，0 errors，2 skipped。
- 前端回归：`node --test src/test/js/*.test.js` — PASS，215 passed。
- 说明：当前测试未覆盖 URL-only fallback/bullet；因此全绿不代表 I-1 完整。

## 合规审计

- I-1 — ❌ `QaMatchService.kt:150-163` 未过滤 URL-only bullet，且 fallback 直接返回 `messageBody.trim()`；`isUrlOnlyRequestFragment` 仅在 `extractQuestionSentences` 的 183-187 行生效。
- I-2 — ✅ `QaMatchService.kt:177-185` 在等长空格掩码上定位，按原始 `match.range` 截取并 trim；`QaMatchServiceTest.kt:832-838` 断言研究问题原文和顺序。
- I-3 — ✅ `QaMatchService.kt:130-139` 与 149-160 都调用 `extractQuestionSentences`；无第二套问号 regex。
- 不变项 — ✅ `QaMatchService.kt:77-93` 的 `match()` supersede/handoff/autoReplyEnabled 未改；`QaMatchServiceTest.kt:845-859` 覆盖普通两问与含文字 URL bullet。
- Deleted code — ✅ 旧的两处直接 `QUESTION_SENTENCE_PATTERN.findAll(messageBody)` 已删除。
- No extras — ✅ 实现提交 `2211c300` 只改计划列出的两个文件；本次复验另更新 QA knowledge 命中元数据。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 本子计划不定义状态机。
- Cross-plan check：✅ Phase 1 的 `gapItems.text` 保持原文、`candidateRuleIds` 保持现有契约；Phase 4 计划把其作为 `unsupportedRequests` 的输入，P1-1 修复后不会传递 URL-only 项。

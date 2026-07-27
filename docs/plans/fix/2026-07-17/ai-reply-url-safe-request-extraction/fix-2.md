# Phase 1 URL-safe request extraction：修复计划 2

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-12/ai-reply-safety-model-plan-index.md`
- 子计划：`docs/plans/2026-07-12/ai-reply-url-safe-request-extraction.md`
- 前序复验：`docs/plans/fix/ai-reply-url-safe-request-extraction/fix-1.md`
- 复验对象：Phase 1 URL-safe request extraction

## 约束摘录

| ID | 约束 |
|---|---|
| I-1 | 问号定位前掩码 http(s) URL；URL 整行不得成为 request item。该规则同时适用于 `extractGapTexts/countQuestionUnits`。 |
| I-2 | 掩码只用于定位；返回项必须从原始 offset 还原。 |
| I-3 | `extractGapTexts` 与 `extractRequestItems` 共用同一 URL-safe question extractor，不得复制问号 regex。 |
| 边界 | 普通问句、项目符号、无问句/无项目符号 fallback，以及 `match()` 自动回复语义不变。 |

## 修正记录表

| P1 | 触发频率 | 问题 |
|---|---|---|
| P1-1（遗留） | 低频；一封已命中 QA 的来信另以至少两个 URL-only bullet 附研究主页时稳定触发 | fix-1 仅在 `extractRequestItems` 过滤 URL-only bullet；自动路径的 `extractGapTexts` 仍把它们计入 `countQuestionUnits`。当 URL bullet 数超过已命中分类数时，`detectGap()` 会误判缺口，改变 `match()` 的 `gapDetected` 并可能触发人工 handoff。 |

## 修复规格

### P1-1：自动 gap 计数排除 URL-only bullet

- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt`
- 修改：在 `extractGapTexts` 的 bullet 流中、比较 `questions.size` 前，复用已有 `isUrlOnlyRequestFragment` 过滤 URL-only bullet。不得新增 regex、DTO、状态或第二套 tokenizer。
- 预期：
  - `- https://scholar.google.com/citations?user=...` 与 `- https://www.scopus.com/authid/detail.uri?authorId=...` 不增加自动 `countQuestionUnits`；
  - 同一来信中带自然语言的 bullet（即使含 URL）仍保留并参与计数；
  - `extractGapTexts` 和 `extractRequestItems` 都复用同一 URL-only 判定，普通问句与 fallback 语义不变。
- 触发频率：低频，仅专家把多个纯 URL 作为项目符号附在已匹配邮件时；采用现有 helper 的单行过滤即可。

- 文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`
- 修改：增加自动 `match()` 回归：一条可命中规则的正文带两个 URL-only bullet 时 `gapDetected == false`；含文字 URL bullet 仍按现有规则计数。保持既有 suggestion URL-only body/bullet 回归。

## 当前状态（修复前）

- 定向：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaMatchServiceTest test` — PASS，28 passed。
- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` — PASS，见 Surefire XML，无 failures/errors。
- 前端回归：`node --test src/test/js/*.test.js` — PASS，215 passed，0 failed，0 skipped。
- 说明：现有新增测试只覆盖 suggestion 入口；未覆盖自动 `match()` 的 URL-only bullet gap 计数。

## 合规审计

- I-1 — ❌ `QaMatchService.kt:133-140` 的 `extractGapTexts` 收集所有 bullet，未调用 `isUrlOnlyRequestFragment`；`QaMatchService.kt:125-127` 直接以其大小判断 gap。`QaMatchService.kt:150-167` 虽已修复 suggestion 入口，但不能覆盖自动路径。
- I-2 — ✅ `QaMatchService.kt:179-193` 在等长空格掩码上定位，并按原始 `match.range` 截取、trim 返回。
- I-3 — ✅ `QaMatchService.kt:130-139` 与 `158-159` 都调用 `extractQuestionSentences`；仅 `extractGapTexts` 的 bullet 过滤遗留。
- 不变项 — ✅ `QaMatchService.kt:68-93` 的 `match()` supersede/handoff/autoReplyEnabled 实现未被 fix-1 改动；`QaMatchServiceTest.kt:842-859` 覆盖普通两问和含文字 URL bullet。
- Deleted code — ✅ 未见旧 `QUESTION_SENTENCE_PATTERN.findAll(messageBody)` 双实现。
- No extras — ✅ 本轮审计范围仅原计划的两个文件与命中知识元数据。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 本子计划不定义状态机。
- Cross-plan check：❌ Phase 1 自动路径把 `gapDetected` 交给后续 handoff 决策；URL-only bullet 可在该边界制造伪缺口。修复 P1-1 后恢复既有“URL 不算请求”的契约。

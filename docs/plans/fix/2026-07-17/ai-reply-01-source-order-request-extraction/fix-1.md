# Phase 1 复验修复计划（fix-1）

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-13/ai-reply-evidence-safe-point-reply-plan-index.md`
- 子计划：`docs/plans/2026-07-13/ai-reply-01-source-order-request-extraction.md`

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 | 每个单元携带**原邮件** `startOffset/endOffset`；候选合并后按该 offset 升序。 |
| I-2 | 段落内单换行可跨行定位并折叠；空白段落不得跨越。 |
| I-3 | URL 定位阶段等长掩码，候选从原文 range 恢复。 |
| I-4 | bullet 与 question 重叠保留 bullet；归一化文本首次出现去重。 |
| I-5 | `suggestComposition()` 与 `detectGap()` 共用 extractor；自动路径只消费数量。 |
| I-6 | 普通正文 fallback 一项；空白/URL-only 为零项。 |

## 修正记录表

| 编号 | 级别 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | extractor 先把 CRLF/CR 改写为 LF，再以改写后字符串计算并返回 offset；`ExtractedRequest` 的 offset 不再指向传入的原邮件。违反 I-1 和 T1 的“保留 offset 映射”。 | 含 CRLF 的邮件均触发；标准 MIME 文本通常使用 CRLF。 |

## 修复规格

### P1-1：保持原邮件 offset 映射

- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractor.kt`
- 改动：保留 `messageBody` 作为唯一 offset 坐标系。若为统一换行创建 searchable/normalized view，必须同时维护 normalized index 到原文 index 的映射；`ExtractedRequest.startOffset/endOffset` 及所有 `substring` 恢复范围必须使用原文坐标。
- 预期：对含 `\r\n` 和单独 `\r` 的输入，`messageBody.substring(startOffset, endOffset)` 覆盖原始候选范围；排序、overlap、URL 掩码和软换行折叠结果与 LF 输入等价。
- 测试：新增 CRLF 与 CR 测试，断言首个/后续候选 offset 对应原文切片，且跨行问题文本折叠、URL 伪问句过滤、source order 均保持。
- 约束：不修改 `QaMatchService` 的关键词匹配、`applySupersede`、内容变体、`GapItem` API、自动回复正文或审计 rule id；不新增 controller DTO、Spring 依赖、状态或迁移。

## 当前状态

- Build：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests package -q`
- 定向测试：PASS — `mvn -Dtest=QaRequestExtractorTest,QaMatchServiceTest test`（42 passed）
- 全量测试：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -q`（743 passed，0 failed，1 skipped）

## 合规审计

- I-1：❌ `QaRequestExtractor.kt:20-21` 以 `normalizeNewlines()` 改写输入，`QaRequestExtractor.kt:95-99`、`158-163` 返回的是改写后坐标；`QaRequestExtractor.kt:259-260` 删除 CR 字符，未保留到原文的映射。排序本身见 `QaRequestExtractor.kt:32-33`。
- I-2：✅ `QaRequestExtractor.kt:107-169` 按段落建立 searchable view，并在 `124-132` 折叠单换行；`218-246` 以空白段落分隔；`159` 返回折叠文本。
- I-3：✅ `QaRequestExtractor.kt:249-256` 等长掩码 URL，`156` 从 body range 恢复候选；`268-270` 过滤 URL-only。
- I-4：✅ `QaRequestExtractor.kt:28-36` 删除与 bullet 重叠的问句并按 offset/归一化去重；范围判断见 `265-266`。
- I-5：✅ `QaMatchService.kt:23` 由 extractor 建议项；`QaMatchService.kt:130-131` 自动 gap 只读取同一 extractor 的数量；未读取 `GapItem` 或 AI matrix。（来源: K-gap-items-compose-only）
- I-6：✅ `QaRequestExtractor.kt:42-54` 覆盖 fallback、blank、URL-only。
- URL 历史风险：✅ `QaRequestExtractor.kt:249-256`、`268-270` 继续等长掩码且过滤 URL-only。（来源: K-url-query-question-tokenizer）
- 来源顺序历史风险：❌ 同 P1-1。（来源: K-request-extractor-offset-order）
- 删除旧 extractor：✅ `QaMatchService.kt:131-132` 已无旧 question/bullet tokenizer 调用。
- No extras：✅ 业务代码和测试变更限于子计划列出的四个文件；知识命中计数为 fix-v 元数据更新。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 无状态机变更。
- Cross-plan check：✅ Phase 1 对下游只维持既有 `GapItem(text, candidateRuleIds)` 顺序契约；建议路径与自动 gap 路径均调用同一 extractor。CRLF offset 缺陷已作为 P1-1 单列。

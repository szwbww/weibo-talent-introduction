# AI 回复复杂问询覆盖与双入口语义对齐（后端）— 修复计划 1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-12/ai-reply-grounded-parity-backend.md`
- 本轮：fix-1/3

## 约束摘录

- I-4：AI 回复仅读取既有 ES 画像；APPLICATION 缺失只回退 CANDIDATE；读取失败或画像为空时，返回 `EXPERT_PROFILE_NOT_FOUND`；研究资料不足时返回 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`；不得触发 enrichment。
- I-6：两个入口经同一 `AiReplyContextService` 构造上下文。
- I-7：覆盖元数据必须基于可靠上下文，不得把资料缺失伪装为已覆盖。

## 修正记录

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | ES 查询正常返回 `null`（包括 contact 无 ORCID）时未加入 `EXPERT_PROFILE_NOT_FOUND`。调用方只能看到研究资料不足，无法区分“画像不存在”与“画像字段不足”。 | 有联系人尚未建 ES 文档、CANDIDATE/APPLICATION 尚未同步，或 ORCID 缺失时触发；日常可见。 |

## 修复规格

### P1-1：缺失画像必须显式 warning

修改 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextService.kt`：

1. `loadProfile` 在 ORCID 为空、当前层查询为空且（若适用）CANDIDATE 回退仍为空时，向 `warnings` 加入一次 `EXPERT_PROFILE_NOT_FOUND`，并返回 `null`。
2. 保留现有异常路径和只读依赖：仅调用 `ExpertSearchService.findByOrcidId`；不得注入或调用 enrichment 服务。
3. `build` 继续在研究请求且画像不充分时添加 `EXPERT_RESEARCH_CONTEXT_INSUFFICIENT`；两类 warning 可同时出现。
4. 修改/新增 `AiReplyContextServiceTest`：覆盖无 ORCID、查询无结果、APPLICATION + CANDIDATE 均无结果；断言 warning 存在且无 enrichment 交互。同步删除把该缺陷固化为正确行为的测试断言（当前 `AiReplyContextServiceTest.kt:227-234`）。

## 当前状态（修复前）

- 编译：PASS
- 测试：PASS — 121 passed, 0 failed, 0 skipped
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaMatchServiceTest,AiReplyDraftServiceTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest,AutoMailReplyServiceTest`

## 合规审计

- I-1 自动/草稿 supersede 隔离：✅ `QaMatchService.kt:34` 多请求保留 raw matches；`QaMatchService.kt:77` 自动路径仍调用 `applySupersede`。
- I-2 模式唯一且续轮稳定：✅ `AiReplyDraftService.kt:69-76` 每次先解析原始来信结构，再按请求数、unsupported、研究需求判定模式。
- I-3 prompt/send QA ids 分离：✅ `AiReplyDraftService.kt:180-189` 分别维护 `sendQaRuleIds` 与 `promptRuleIds`；结果仅返回 send ids（`143-153`）。
- I-4 只读画像与缺失可见：❌ `AiReplyContextService.kt:68-78` 的空 ORCID、当前层/CANDIDATE 查询均为空均直接返回 `null`，只在异常时于 `79-82` 返回 `EXPERT_PROFILE_NOT_FOUND`。
- I-5 精确邮件身份：✅ `AiTrainingController.kt:179-197` 优先 `mailRecordId`、校验 INBOUND/contact，只有为空才 latest fallback。
- I-6 两入口同源：✅ `AiTrainingController.kt:201-212`、`UnmatchedInboundMailController.kt:292-304` 都调用 `AiReplyContextService.build` 后传入同一 `generate`。
- I-7 覆盖元数据：✅ `AiReplyDraftService.kt:191-218` 按请求项计算 grounded/unsupported；研究资料不足进入 unsupported。
- I-8 frame/事实来源：✅ `AiReplyDraftService.kt:322-335` 限定 grounded facts；`356-409` 复用 manual frame；`105-116` 未为 `QA_GROUNDED` 注入 few-shot。
- 删除代码：✅ 无计划要求删除的存活代码。
- No extras：✅ 限定实现范围内无额外生产代码文件。

### 语义完整性检查

- Accumulation check：✅ 无时间窗计数器。
- State machine check：✅ 无状态机变更。
- Cross-plan check：✅ 此子计划的跨入口合同一致；P1-1 为本计划内画像缺失语义遗漏。

# AI 训练对话方式治理 — 修复计划 1

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-12/ai-training-dialogue-style-curation.md`
- 本轮：fix-1/3

## 约束摘录

- I-2：`QA_MATCHED` 与 fallback 不注入对话范例；fallback 的 `fewShotDialogRefs` 必须为空。
- I-1：few-shot 仅为结构、语气、沟通策略，不能在 LLM 未实际使用消息时被表示为已注入。
- I-4：数据治理后不能经 fallback 旁路影响草稿或诊断元数据。
- 来源：K-ai-generate-single-freeform-seam、K-dialogue-seed-idempotent-skip。

## 修正记录

| P1 | 问题 | 触发频率 |
|---|---|---|
| P1-1 | 当 `llm.enabled=true` 但 `LlmDraftClientProvider.getIfAvailable()` 返回 null 时，`generate()` 已先为 `FREE_FORM/QA_GROUNDED` 查询 few-shot，并将其 refs 传给 deterministic fallback。响应 `usedLlm=false` 却宣称已注入 style 示例，违背 fallback 零注入契约。 | LLM 配置开启但 client 不可用、初始化失败或运行期降级时触发；故障期间每次生成都会发生。 |

## 修复规格

### P1-1：未调用 LLM 时不得选择或回传 few-shot

修改 `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`：

1. 在构造 `QA_GROUNDED/FREE_FORM` messages 前先取得可用 client。若 client 为 null，直接进入 deterministic `fallback(...)`，保持 `fewShotDialogRefs=emptyList()`，不得调用 `AiTrainingDialogueService.selectRelevantDialogues`。
2. LLM 调用返回空值或抛异常时，fallback 结果也必须强制 `fewShotDialogRefs=emptyList()`；不得把仅构建但未成功生成的示例 refs 暴露给 API/UI。
3. 不改 `QA_MATCHED` verbatim 构建、正常 LLM 成功时的 grounded max=1 / free-form max=2、或 fallback 正文来源。
4. 扩充 `AiReplyDraftServiceTest`：
   - enabled=true + provider returns null：`verifyNoInteractions(aiTrainingDialogueService)`，`usedLlm=false`，refs 为空；
   - client chat 抛异常或返回 blank：refs 为空、fallback 正文不含 style 示例文本。

## 当前状态（修复前）

- 编译：PASS
- 测试：PASS — 60 passed, 0 failed, 0 skipped
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=AiTrainingDialogueSeederTest,AiTrainingDialogueCurationTest,AiReplyDraftServiceTest,AiTrainingSimulateTest`

## 合规审计

- I-1 facts/style boundary：✅ `AiReplyDraftService.kt:300-308` 明确 style-only 且事实只能来自 QA/training/profile；`236-266` 将 grounded 示例限制为 max=1。
- I-2 mode isolation / fallback：❌ `AiReplyDraftService.kt:105-129` 在调用 client 前构建 grounded/free-form messages；`136-168` client null/异常后进入 fallback 仍传递 `fewShotDialogRefs`；`472-504` 将 refs 原样返回。
- I-3 curated enabled set：✅ `dialogue-seed.json` 仅六个 STYLE refs；`V69__curate_ai_training_dialogue_styles.sql:4-58` 停用 DIALOG 并幂等 upsert 六条 STYLE。
- I-4 content safety：✅ `AiTrainingDialogueCurationTest.kt:44-59` 锁定两轮、禁词、长度和无占位符；`63-83` 锁定 SQL/JSON 同步。
- I-5 new/existing DB parity：✅ `AiTrainingDialogueSeederTest.kt:36-54` 新库 save=6；`V69__curate_ai_training_dialogue_styles.sql:6-58` 更新存量。
- I-6 honest UI：✅ `index.html:810-828` 使用 style-only 说明与五列只读表；`app.js:2535-2546` 仍只读渲染。
- 删除代码：✅ legacy seed 已从 JSON 删除；V69 保留 legacy DB 行但统一停用，符合审计/回滚要求。
- No extras：✅ 生产变更在计划范围内；未改 CSS、未新增编辑/标签页。

### 语义完整性检查

- Accumulation check：✅ 无时间窗口计数器。
- State machine check：✅ 无状态机变更。
- Cross-plan check：❌ 计划 1 的 `usedLlm/injectedDialogRefs` 诊断合同与计划 3 不一致：provider 不可用时将未实际使用的 refs 返回给计划 2 UI。

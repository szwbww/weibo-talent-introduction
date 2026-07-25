# Fix-1：流式双 TTL、取消与进度契约闭环

- 基线计划：`docs/plans/2026-07-23/ai-reply-streaming-dual-ttl-cancel-plan.md`
- 验证轮次：初始复验；下一可用修正轮次 `fix-1`
- 模式：`WORKFLOW_ARTIFACTS`
- 结论：`FIX-1`

## 修正边界

只修改基线计划列出的 10 个实现/测试文件。不得修改 Prompt、事实/claim/action 规则、持久化 schema、`LlmProperties.timeoutMs`、旧 JSON 调用协议、训练模拟或自动回复入口。不得把取消改成 fallback，或新增重试/恢复策略。

## P1

### P1-1：取消未主动关闭正在阻塞的 DeepSeek HTTP 流

- 约束：I-7；`K-llm-attempt-total-budget-cancel`。
- 证据：`src/main/kotlin/com/weibo/talentintroduction/llm/service/HttpLlmDraftClient.kt:232-242` 只为 attempt deadline 注册 `responseFuture.cancel(true)` 与 body close；`AiReplyCancellationToken` 没有订阅/回调机制。`HttpLlmDraftClient.kt:276-281` 仅在 `BufferedReader.readLine()` 前检查 token，read 阻塞时 controller 的 `token.cancel()` / worker interrupt 无法保证取消 `responseFuture` 或关闭 stream。
- 影响/频率：每次用户在 provider 静默、read 阻塞期间停止生成时均可能继续占用连接和额度，直到单次 TTL；违反“服务端真实取消”。
- 边界：`INTRODUCED`（本计划新增 streaming seam）。
- 最小修正：为 `AiReplyCancellationToken` 增加一次性、可移除的取消监听；stream seam 在创建 future/body 后注册监听，监听中取消 future 并关闭已取得的 body，finally 移除监听。保留 attempt-deadline 逻辑和 failure 分类；取消必须返回 `CANCELLED` 且不保留部分 content。
- 禁止：轮询线程、扩大 TTL、将取消映射为 timeout/fallback、修改旧 RestTemplate seam。

### P1-2：跨 provider 调用的活动计数未累计

- 约束：I-13、Stage 3.6。
- 证据：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:173-185` 从每次 stream seam 的局部 `events/chars` 取 `max`；而 `HttpLlmDraftClient.kt:268-269` 在每次调用重置这两个计数。因此 retry/可信修复/动作修复后的 `providerEventCount` 与 `contentChars` 不是 generation 累计值。
- 影响/频率：任意发生第二次 provider 调用的生成都会少报或覆盖已观测活动，页面“已接收”指标不可验证。
- 边界：`INTRODUCED`（本计划新增进度 tracker）。
- 最小修正：tracker 在 `beginProviderCall` 记录本次 stream 的上次局部计数；sink 仅累加非负增量，两个 generation 总计数饱和到 `Int.MAX_VALUE`。新调用重置局部基线，不重置 generation 总计。保留 1 秒聚合限频与 payload 白名单。
- 禁止：传输正文/delta 到 tracker、以累计值推断完成率、改变 phase/activity 枚举。

### P1-3：前端请求快照与 S-1 精确样式契约不完整

- 约束：I-11、S-1。
- 证据：`src/main/resources/static/app.js:9910-9915` 的 `activeGeneration` 仅保存 generationId/recordId/requestSeq/controller；`app.js:9943-9947`、`9981-9985`、`9991-9994` 结果/异常/finally guard 未比较本次 attempt TTL 与 total TTL。`src/main/resources/static/styles.css:5901` 使用 `.ai-reply-\\67 eneration-controls`，未逐字复制要求的 `.ai-reply-generation-controls`。
- 影响/频率：状态重建或后续扩展可使 timeout 快照不再构成结果门禁；并且当前 CSS 不符合基线的逐字样式契约。
- 边界：`INTRODUCED`（本计划新增前端双 TTL/流状态）。
- 最小修正：active request snapshot 增加 model、attemptTTL、totalTTL，并在 result/error/finally 的 current guard 一并比较；保持控件禁用、generationId/progressSeq 防陈旧规则不变。将 S-1 selector 恢复为计划指定的纯文本 selector；不改现有 class 规则。
- 禁止：放宽 generationId 检查、将 TTL 写入 localStorage、增加未声明 CSS class 或 inline style。

### P1-4：Stage 6 必需测试未实现，当前通过结果不能覆盖新契约

- 约束：Stage 6、验收 I-1～I-14、S-1～S-3。
- 证据：四个基线指定测试文件均未出现在工作树 diff；`rg` 在 `HttpLlmDraftClientTest.kt`、`AiReplyDraftServiceTest.kt`、`UnmatchedInboundAiReplyTurnKnowledgeTest.kt`、`aiReplyLoadingFeedback.test.js` 中没有新 stream/TTL/generation/progress 测试。专项命令通过的 156 个既有测试未覆盖本计划的 provider cancel、SSE registry、计数累计、POST parser 或双 TTL UI。
- 影响/频率：P1-1～P1-3 及 I-1～I-14 的回归均无法由计划要求的自动化契约阻止。
- 边界：`INTRODUCED`（本计划规定的测试变更缺失）。
- 最小修正：按 Stage 6 补齐四个文件的指定测试，至少覆盖：
  - stream 取消主动 cancel future/close body、DONE/finishReason/64K、活动 callback 与饱和；
  - policy 边界、总预算截断、retry 上限、cancel 不进入 fallback、跨调用累计指标；
  - ready/progress/heartbeat 白名单与限频、五类 cleanup、cancel/COMMITTING 审核边界；
  - split/multi-frame parser、请求 TTL/generation snapshot guard、stop/reset、ticker、S-1/S-2/S-3 精确 DOM/CSS。
- 禁止：只加源码字符串断言替代行为测试、弱化旧回归断言、mock 掉取消/stream close 行为。

## 修正后机器验收

1. 取消处于 `readLine()` 阻塞时，测试断言 token 立即取消 future、关闭 body、service 抛 `AiReplyGenerationCancelledException`，controller 仅发送 `cancelled`，且 `recordInitialDraft` 为 0。
2. 两次 provider 调用（transport retry 或 repair）后 progress 的 eventCount/contentChars 等于两次局部增量之和并饱和；progress 仍最多每秒一个，phase 变化即时。
3. result/error/finally 仅在 `recordId/requestSeq/model/attemptTTL/totalTTL/generationId` 全匹配时更新；S-1 selector 与计划文本一致。
4. 执行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
mvn test -Dtest=HttpLlmDraftClientTest,AiReplyDraftServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest
node --test src/test/js/aiReplyLoadingFeedback.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

## 修正前验证

- 编译：PASS
- 专项 Maven：PASS — 156 passed, 0 failed, 0 skipped
- JS：PASS — 41 passed, 0 failed, 0 skipped
- 全量 Maven：PASS — 1872 passed, 0 failed, 4 skipped
- 人工验收：PENDING — A-1～A-21 由人工执行

## 合规审计

| 项目 | 结论 | 证据 |
|---|---|---|
| I-1 双 TTL 解析/回显 | ✅ | `AiReplyDraftService.kt:42-49`; `UnmatchedInboundMailController.kt:437-444,476-480` |
| I-2 独立 attempt / total 剩余截断 | ✅ | `AiReplyDraftService.kt:64-68,796-800`; `HttpLlmDraftClient.kt:223-249` |
| I-3 单一 total deadline / warning | ✅ | `AiReplyDraftService.kt:53-56,826-829,1668-1691` |
| I-4 重试上限/取消不重试 | ✅ | `AiReplyDraftService.kt:763-777,660-670,1098-1108` |
| I-5 回复专用窄 seam | ✅ | `HttpLlmDraftClient.kt:87-136`; `AiReplyDraftService.kt:808-822` |
| I-6 DeepSeek 完成条件 | ✅ | `HttpLlmDraftClient.kt:254-334` |
| I-7 真实取消 | ❌ | P1-1 |
| I-8 COMMITTING 线性化 | ✅ | `UnmatchedInboundMailController.kt:380-393,612-665` |
| I-9 有界 registry/cleanup | ✅ | `UnmatchedInboundMailController.kt:483-500,675-688` |
| I-10 POST SSE / 心跳 / headers | ✅ | `UnmatchedInboundMailController.kt:462-547`; `app.js:437-484` |
| I-11 前端快照隔离 | ❌ | P1-3 |
| I-12 fallback/隐私门禁 | ✅ | `AiReplyDraftService.kt:536-560,1682-1692`; `HttpLlmDraftClient.kt:335-352` |
| I-13 真实进度 | ❌ | P1-2 |
| I-14 限频/陈旧/白名单 | ✅ | `AiReplyDraftService.kt:173-205`; `UnmatchedInboundMailController.kt:618-705`; `app.js:330-388` |
| S-1 | ❌ | P1-3 |
| S-2/S-3 | ✅ | `styles.css:5985-6041`; `app.js:4133-4149` |

Accumulation check: ❌ — P1-2。  
State-machine check: ✅ — REGISTERED/RUNNING/COMMITTING/CANCEL_REQUESTED/FINISHED。  
Cross-plan check: ✅ — 训练与自动回复仍走旧 seam。  
Deleted code: N/A — 基线未要求删除。  
No extras: ✅ — 产品实现位于基线 10 文件范围；既有控制面文档变更不计入。  
Scope compliance: ✅。  
Manual acceptance: PENDING。

## P1 谱系

| P1 | 谱系 |
|---|---|
| P1-1～P1-4 | NEW_IN_SCOPE / INTRODUCED |

无已实现 fix 轮次；未创建或修改 `fix-2`。

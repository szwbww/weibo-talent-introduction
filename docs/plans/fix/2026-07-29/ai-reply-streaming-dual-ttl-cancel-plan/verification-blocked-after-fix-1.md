# 流式双 TTL、取消与进度：fix-1 后复验阻塞报告

- 基线计划：`docs/plans/2026-07-23/ai-reply-streaming-dual-ttl-cancel-plan.md`
- 前序修复：`docs/plans/fix/ai-reply-streaming-dual-ttl-cancel-plan/fix-1.md`
- 模式：`WORKFLOW_ARTIFACTS`
- 结论：`EARLY_BLOCKED`

## 为什么没有生成 fix-2

`fix-1` 已有实现痕迹，但其 P1-3 与 P1-4 仍以原根因、原修复规格存在。`fix-v` 不允许用新的 `fix-2` 重复同一修复；须先按现有 `fix-1` 的边界完成遗漏实现及测试，再重新复验。

## 验证状态

- 编译：PASS
- 专项 Maven：PASS — 162 passed, 0 failed, 0 skipped
- JS：PASS — 43 passed, 0 failed, 0 skipped
- 全量 Maven：PASS — 1878 passed, 0 failed, 4 skipped
- 人工验收：PENDING — A-1～A-21

## P1 谱系

| 项目 | 状态 | 证据 |
|---|---|---|
| fix-1 P1-1 取消关闭阻塞流 | RESOLVED | `AiReplyDraftService.kt:73-99` 提供可移除监听；`HttpLlmDraftClient.kt:237-240` 取消 future/stream；`HttpLlmDraftClientTest.kt` 阻塞流测试通过。 |
| fix-1 P1-2 跨调用活动累计 | RESOLVED | `AiReplyDraftService.kt:194-215` 按局部增量累计并饱和；`AiReplyDraftServiceTest.kt` 覆盖两次调用与饱和。 |
| fix-1 P1-3 请求快照 guard | PERSISTENT | 见 P1-1。CSS selector 已修正，但 `finally` 仍漏 generationId/record 边界。 |
| fix-1 P1-4 Stage 6 测试 | PERSISTENT | 见 P1-2。新增测试仅覆盖少量 seam，未覆盖原指定的 controller、SSE/registry、UI 停止/进度契约。 |
| 总 TTL 后验证 | NEW_IN_SCOPE | 见 P1-3。 |
| progress 反压 | NEW_IN_SCOPE | 见 P1-4。 |
| total-timeout 前端可观察性 | NEW_IN_SCOPE | 见 P1-5。 |

## 阻塞 P1

### P1-1：旧 generation 的 finally 可清除新 generation 的 loading

- 约束：I-11、Stage 5.9；fix-1 P1-3。
- 证据：`src/main/resources/static/app.js:10000-10006` 的 finally guard 未比较 `detailId`、`state.mailbox.detailContext.id` 或 `generationId`；`app.js:10008-10015` 仍会移除 overlay、停止 ticker、清空 latestProgress。停止 A 后 `app.js:420-434` 清除 activeGeneration；用户随即为同一邮件、同模型、同 TTL 启动 B 时，A 的 aborted fetch 进入 finally，旧 guard 可与 B 匹配并清除 B 的 loading/progress。
- 影响/频率：每次“停止后立即用相同模型与 TTL 重试”均可触发；B 仍运行而页面恢复可操作状态，违反陈旧请求隔离。
- 边界：`PERSISTENT`；fix-1 已要求 result/error/finally 全部比较完整快照，当前只修复了 result/error。
- 最小既有修复：落实 fix-1 P1-3：将 finally guard 与 result/error 统一为同一完整 predicate，比较 `recordId/requestSeq/model/attemptTTL/totalTTL/generationId`，再清理 loading/ticker/activeGeneration。
- 禁止：放宽 generationId 检查、把 TTL 持久化、改变已有草稿状态。

### P1-2：Stage 6 必需契约测试仍未完成

- 约束：Stage 6；fix-1 P1-4。
- 证据：`HttpLlmDraftClientTest.kt` 仅新增成功流与取消阻塞流测试；`AiReplyDraftServiceTest.kt` 仅新增 policy/累计计数测试；`UnmatchedInboundAiReplyTurnKnowledgeTest.kt` 仅新增 UUID/TTL 参数校验；`aiReplyLoadingFeedback.test.js` 仅新增 parser 与 TTL 解析。未覆盖 fix-1 指定的 ready/progress/heartbeat 白名单与限频、五种 registry cleanup、cancel/COMMITTING 审核边界、split/multi-frame 以外的 stop/reset/ticker/S-1～S-3 DOM/CSS、total deadline 及前端 stale-finally。
- 影响/频率：当前 1878 个 Maven 和 43 个专项 JS 测试均不能阻止上述运行时回归，不能构成基线 Stage 6 的机器验收。
- 边界：`PERSISTENT`；仍是 fix-1 P1-4 的原测试缺口。
- 最小既有修复：按 fix-1 P1-4 的四个既定测试文件补齐行为测试；不得以源码字符串断言替代 cancel、SSE、registry 和 DOM 状态行为。
- 禁止：弱化旧断言、mock 掉 stream close/cancel、删除现有回归测试。

### P1-3：总 TTL 未覆盖 provider 成功后的校验与最终结果

- 约束：I-3、I-12；A-7。
- 证据：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt:828-860` 仅在下一次 provider 调用前，或 provider **失败**且 deadline 已过时返回 `TOTAL_TIMEOUT`；成功结果直接进入 `:545-565`、`:659-683`、`:730-751` 的校验/materialize/finalize。`buildGroundedResult` 的 budget/token/reporter 参数在 `:953-955` 未使用。
- 影响/频率：provider 在 deadline 前刚完成、而 JSON materialize/事实或动作校验跨过 deadline 时，仍能写审核并发送成功 result；违反生成链总 TTL 硬上限。
- 边界：`NEW_IN_SCOPE`。
- 后续最小范围：在 provider 成功返回后的验证、修复及 commit 前使用同一 budget 检查；到期统一映射 `AI_REPLY_LLM_TOTAL_TIMEOUT`、`FALLBACK_NO_RESPONSE`、`BLOCKED`、`usedLlm=false`，不得启动新调用。

### P1-4：同步 SSE progress send 可反压阻塞 provider 读取

- 约束：I-14、Stage 4.8/10。
- 证据：`AiReplyProgressTracker` 的 sink 在 `UnmatchedInboundMailController.kt:491-495` 直接调用 `GenerationControl.publishProgress`；`GenerationControl.publishProgress` 于 `:618-622` 同步进入 `sendLocked`，后者于 `:680-688` 同步 `emitter.send`。HTTP parser 在 `HttpLlmDraftClient.kt:279-332` 的读循环内同步调用该 sink。因此慢浏览器或阻塞 emitter send 能暂停 provider stream 的读取。
- 影响/频率：任意慢/背压 SSE 客户端可把进度发送变成 provider 读取瓶颈，违反“进度发送失败不得阻塞 provider 读取”，并可放大单次 TTL 超时。
- 边界：`NEW_IN_SCOPE`。
- 后续最小范围：仅将最新快照写入 control；以独立、可取消的限频 flush 在 send lock 内发送，phase 切换可立即调度，不得让 provider callback 直接执行 emitter.send。

### P1-5：总 TTL 超时未映射为前端可观察 warning

- 约束：I-3、I-12；A-7。
- 证据：后端已在 `AiReplyDraftService.kt:1700,1713-1722` 定义并映射 `AI_REPLY_LLM_TOTAL_TIMEOUT`；前端 `src/main/resources/static/app.js:4022-4029`、`:4036-4052`、`:4055-4064` 未包含该 code。因此 A-7 的“DeepSeek 生成总时限已用尽”不会显示，回退为泛化失败文案。
- 影响/频率：每次总 TTL 到期均缺失原因区分，运营无法判断是单次超时还是整链预算耗尽。
- 边界：`NEW_IN_SCOPE`。
- 后续最小范围：在现有 warning label、failure set 和优先级中加入 total-timeout 的固定中文文案，并补 JS 行为测试；不改发送/采用门禁。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 双 TTL 解析/回显 | ✅ | `AiReplyDraftService.kt:44-51`; `UnmatchedInboundMailController.kt:437-444,476-480` |
| I-2 attempt/total 截断 | ✅ | `AiReplyDraftService.kt:66-70,828-847`; `HttpLlmDraftClient.kt:223-246` |
| I-3 总 TTL 硬上限 | ❌ | P1-3 |
| I-4 有界重试/取消不重试 | ✅ | `AiReplyDraftService.kt:787-813,854-855` |
| I-5 回复专用 stream seam | ✅ | `HttpLlmDraftClient.kt:121-138,197-364`; `AiReplyDraftService.kt:839-852` |
| I-6 SSE 完成条件 | ✅ | `HttpLlmDraftClient.kt:258-338` |
| I-7 服务端真实取消 | ✅ | `AiReplyDraftService.kt:73-105`; `HttpLlmDraftClient.kt:237-240`; `UnmatchedInboundMailController.kt:646-672` |
| I-8 COMMITTING 线性化 | ✅ | `UnmatchedInboundMailController.kt:380-393,612-665` |
| I-9 registry 有界清理 | ✅ | `UnmatchedInboundMailController.kt:483-500,675-688` |
| I-10 POST SSE/heartbeat/headers | ✅ | `UnmatchedInboundMailController.kt:462-547`; `app.js:437-484` |
| I-11 前端陈旧隔离 | ❌ | P1-1 |
| I-12 timeout fallback/可观察性 | ❌ | P1-3、P1-5 |
| I-13 可验证进度与累计指标 | ✅ | `AiReplyDraftService.kt:186-263`; `app.js:366-388` |
| I-14 限频、不反压与陈旧隔离 | ❌ | P1-1、P1-4；另 `app.js:401-405` 在 IDLE 时未外推最近活动秒数。 |
| S-1 | ✅ | `styles.css:5901-5934`; `app.js:9160-9182` |
| S-2/S-3 | ✅ | `styles.css:5985-6041`; `app.js:4109-4153` |

Accumulation check: ✅ — generation 累计增量与饱和实现已存在。  
State-machine check: ✅ — `REGISTERED → RUNNING → COMMITTING → FINISHED` 与取消分支可达。  
Cross-plan check: ✅ — 训练和自动回复仍走非 streaming seam。  
Deleted code: N/A。  
No extras: ✅ — 产品改动仍处于基线 10 个文件。  
Scope compliance: ✅。  
Manual acceptance: PENDING。

## 所需人工决定

确认继续实施既有 `fix-1` 的 P1-3/P1-4，并在同一修复通道一并处理 P1-3～P1-5 的新根因；完成后重新运行 `fix-v`。本次未创建 `fix-2`。

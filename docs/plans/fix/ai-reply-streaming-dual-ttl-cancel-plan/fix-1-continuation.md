# Fix-1 continuation：补齐 SSE/前端行为验收矩阵

## Execution directive

- Execution state: `READY_TO_EXECUTE`
- Intent: `CONTINUE_PARENT_REPAIR`
- Authorized repair IDs: `R-1`
- Production-code edits: `PROHIBITED`
- Allowed files:
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
  - `src/test/js/aiReplyLoadingFeedback.test.js`
- Revision delta: 已补 endpoint completion/timeout、D-3/D-4/D-8/D-9 endpoint 路径及部分 JS handler/DOM 测试；仍缺真实 heartbeat、完整 phase/activity/白名单、JSON/SSE 全字段一致性、终态资源取消，以及前端完整生成终态与精确 CSS 契约；授权与 deferred 范围不变。
- Known deferred test failures:
  - D-1 → `stream endpoint validates canonical generation id and TTL bounds before work`
  - D-2 → `ending provider call freezes attempt elapsed time`
  - D-3 → `slow emitter keeps pending same phase progress at one hertz`
  - D-4 → `send failure cleans pending progress and prevents a second send`; `real endpoint send failure cancels pending progress flush`
  - D-5 → JS `extrapolates recent activity while attempt elapsed stays idle`
  - D-6 → `stream log contains only the approved metadata allowlist`
  - D-7 → `stream rejects lookalike event stream content type`
  - D-8 → `phase change preempts delayed same phase progress flush`
  - D-9 → `disconnect is terminal for concurrent progress callbacks`
- Completion rule: R-1 下列 Controller 与 JS 残余均由可执行、行为级测试覆盖；改动只在两个 allowed files；失败只能映射 D-1～D-9 或由新增测试暴露出的同一 deferred 根因。
- Stop rule: Stop only if a concrete blocker prevents R-1; otherwise finish all R-1 work even when deferred findings keep verification red.
- Next action: Rerun `fix-v` against the same baseline and parent fix.

执行 agent 必须直接完成 R-1。Deferred findings 只允许保留或补强红测，不授权产品修复、弱化、skip、删除或放大 sleep。

- Baseline: `docs/plans/2026-07-23/ai-reply-streaming-dual-ttl-cancel-plan.md`
- Parent fix: `docs/plans/fix/ai-reply-streaming-dual-ttl-cancel-plan/fix-1.md`
- Verification round: after `fix-1`
- Mode: `WORKFLOW_ARTIFACTS`
- Outcome: `EARLY_BLOCKED`
- This artifact consumes no design fix round.

## Why continuation is required

父 `fix-1` 的 P1-1～P1-3 已收敛。P1-4 要求 Baseline Stage 6 的完整行为矩阵，当前只完成部分测试：

- `UnmatchedInboundAiReplyTurnKnowledgeTest.kt:912-966` 只断言出现过 ready/progress/result，未证明 ready 首发、完整 phase/activity、heartbeat、I-13 精确白名单与 JSON/SSE 全字段一致。
- `UnmatchedInboundAiReplyTurnKnowledgeTest.kt:974-1045` 证明 registry 移除，但未证明 heartbeat/progress flush/worker 均停止且 cleanup 后无发送。
- `aiReplyLoadingFeedback.test.js:391-788` 主要直接测试 helper 或局部 action；未执行 `trust-generate-draft` 的 result/cancelled/error/abort/finally 完整状态机，也未证明邮件切换/详情关闭实际 cancel+abort 与旧 generation cleanup 隔离。
- `aiReplyLoadingFeedback.test.js:904-921` 只做局部正则检查，没有逐字比较 S-1～S-3 CSS 块。

这些仍是 fix-1 P1-4 的同一测试缺口、同一修复位置与同一修复规格。创建 `fix-2` 会错误消耗新设计轮次。

## Revision delta

- Resolved since previous continuation:
  - 已新增真实 endpoint completion/timeout cleanup 测试。
  - 已新增 D-3/D-4/D-8/D-9 的 endpoint→tracker→control→emitter 路径测试。
  - 已新增 TTL DOM、停止 TOO_LATE、详情关闭、SSE 三终态 reader 与部分 takeover 测试。
- Still executable from parent fix: `R-1` 的真实 heartbeat/完整 payload/parity/资源终止，以及前端完整生成终态/切换取消/精确 CSS 行为矩阵。
- Newly deferred: `NONE`。
- Authorization change: `NONE`。

## Current verification status

- Build: `PASS`
- Targeted Maven tests: `FAIL` — 212 passed, 9 failed, 0 skipped（221 total）
- JS tests: `FAIL` — 61 passed, 1 failed, 0 skipped（62 total）
- Full Maven tests: `FAIL` — 1924 passed, 9 failed, 4 skipped（1937 total）
- `git diff --check`: `PASS`
- Manual acceptance: `PENDING` — Stage 0 真实 provider/部署探测及 A-1～A-21 未执行

## P1 lineage

| Finding | Lineage | Current evidence | Executable now |
|---|---|---|---|
| fix-1 P1-1：取消关闭阻塞 provider stream | `RESOLVED` | `AiReplyDraftService.kt:73-105`; `HttpLlmDraftClient.kt:237-246,358-363`；阻塞取消测试通过 | N/A |
| fix-1 P1-2：跨 provider activity 累计 | `RESOLVED` | `AiReplyDraftService.kt:186-216` 按局部增量累计并饱和；对应测试通过 | N/A |
| fix-1 P1-3：完整请求快照与 S-1 selector | `RESOLVED` | `app.js:9915-9923,9951-9958,9992-9999,10006-10023`; `styles.css:5901` | N/A |
| fix-1 P1-4：Stage 6 行为测试矩阵 | `PERSISTENT` | 上述 Controller/JS 残余；同一父修复规格未完成 | **YES — R-1** |
| D-1～D-9 | `PERSISTENT`（前次 deferred） | 10 个红测与下表生产证据 | NO — deferred |

## Authorized remaining repair

### R-1：完成 Baseline Stage 6 的 Controller SSE 与前端终态行为测试

- Parent authority: Baseline Stage 6；Parent fix P1-4。
- Allowed files:
  - `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt`
  - `src/test/js/aiReplyLoadingFeedback.test.js`
- Production-code edits: `PROHIBITED`。

#### Exact remaining implementation

1. Controller endpoint 行为：
   - 捕获并按顺序断言首个事件为 `ready`；ready 含 generationId、applied attempt/total 与精确 QUEUED 白名单快照。
   - 用测试侧可控 executor/scheduler 驱动 PREPARING/CALLING/VALIDATING/REPAIRING/FINALIZING、WAITING/REASONING/WRITING、静默期 `heartbeat.progress`；断言 elapsed/activity 推进、progressSeq 严格递增及 I-13 精确键集合，不含 prompt/content/delta/reasoning/正文。
   - 同一 fixture 分别调用 JSON 与 SSE 入口，逐字段比较 `AiReplyTurnResponse`；断言正常路径恰好一个 result，终态后无 progress/heartbeat。
   - completion、timeout、send failure、cancel、error、reject 后，通过公开可观察行为证明 registry 移除、worker/heartbeat/progress flush 停止且后续 reporter callback 不发送。
   - 保留现有 D-3/D-4/D-8/D-9 endpoint 红测；若测试当前误绿，应增强确定性触发，不得放大 sleep 或弱化断言。
2. 前端行为：
   - 执行真实 attempt/total change/input handler，验证 auto 文案；非法 custom 阻止生成网络请求并显示精确错误。
   - 执行 reset、邮件切换、详情关闭入口，验证模块级 cancel endpoint 与 fetch abort；旧 generation 的 result/error/finally 不清理新 generation。
   - 执行停止按钮的 CANCEL_REQUESTED、NOT_ACTIVE、TOO_LATE，验证 `正在停止…`、清理/保留 stream 语义及已有 draft/adoptContext 深相等。
   - 执行 `trust-generate-draft` 完整分支，分别覆盖 result/cancelled/error/abort/finally；每个终态验证 ticker、latestProgress、loading、activeGeneration，且旧请求 cleanup 不触碰新请求。
   - 使用实际渲染 DOM/属性验证 S-1/S-2/S-3，并逐字比较三组计划指定 CSS；断言源码和 DOM 无“完成百分比”，事件数/字符数/TTL 不标为完成率。

#### Prohibited changes

- 不得修改生产代码、配置、计划基线、知识文档、父 `fix-1.md` 或其他测试文件。
- 不得修复 D-1～D-9 产品根因；不得弱化、skip、删除红测、扩大 sleep，或仅以源码搜索/私有反射替代 endpoint/DOM 行为。
- 不得新增需求、架构、状态、恢复策略或人工验收项。

### Authorized-work completion

1. 上述 Controller ready/heartbeat/phase/activity/白名单/parity/终态资源矩阵均存在且可独立失败。
2. 上述 JS handler、生成完整终态、切换取消、陈旧 cleanup、精确 DOM/CSS 矩阵均存在且可独立失败。
3. 本 continuation 后新增改动只在两个 allowed files；D-1～D-9 红测保留。

### Verification checks

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn test -Dtest=HttpLlmDraftClientTest,AiReplyDraftServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest

node --test src/test/js/aiReplyLoadingFeedback.test.js

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH \
mvn test

git diff --check
```

- Expected deferred failures: 上述 D-1～D-9 已命名红测；R-1 新增测试若仅暴露相同根因，必须映射到对应 D-n。
- 若授权工作完整且剩余失败只映射 D-1～D-9，报告 `CONTINUATION_COMPLETE_WITH_DEFERRED_FAILURES`。

## Deferred findings

以下仅为证据，不授权本 continuation 修改产品代码。R-1 完成后重新运行 `fix-v`，再决定是否生成 `fix-2`。

| ID | 状态 | 违反 | 生产证据 | 当前红测 |
|---|---|---|---|---|
| D-1 canonical UUID 大小写绕过 | `PERSISTENT` | I-9 | `UnmatchedInboundMailController.kt:470-475` 使用 lowercase 比较，接受大写 UUID | `stream endpoint validates canonical generation id and TTL bounds before work` |
| D-2 attempt elapsed 未冻结 | `PERSISTENT` | I-13 | `AiReplyDraftService.kt:221-225,238-252` end 后保留 start time | `ending provider call freezes attempt elapsed time` |
| D-3 pending flush 绕过 1 Hz | `PERSISTENT` | I-14 | `UnmatchedInboundMailController.kt:659-667` 对 pending 立即 0-delay 重排 | `slow emitter keeps pending same phase progress at one hertz` |
| D-4 send failure 未完整 cleanup | `PERSISTENT` | I-9/I-14 | `UnmatchedInboundMailController.kt:732-741` 未置终态、未清 pending/flush | `send failure cleans pending progress and prevents a second send`; `real endpoint send failure cancels pending progress flush` |
| D-5 IDLE recent activity 冻结 | `PERSISTENT` | I-14 | `app.js:401-405` 只在非 IDLE 时外推最近活动 | JS `extrapolates recent activity while attempt elapsed stays idle` |
| D-6 stream 日志超 allowlist | `PERSISTENT` | I-12 | `HttpLlmDraftClient.kt:339-341,376` 输出 messageCount/异常 type | `stream log contains only the approved metadata allowlist` |
| D-7 相似 Content-Type 被接受 | `PERSISTENT` | I-6 | `HttpLlmDraftClient.kt:258,266-267` 用 startsWith 接受 `text/event-streaming` | `stream rejects lookalike event stream content type` |
| D-8 phase 不抢占延迟 flush | `PERSISTENT` | I-14 | `UnmatchedInboundMailController.kt:624-640` 已有 future 时 phase 变化不重排 | `phase change preempts delayed same phase progress flush` |
| D-9 disconnect 后可重建 flush | `PERSISTENT` | I-9/I-14 | `UnmatchedInboundMailController.kt:715-730` disconnect 不置终态 | `disconnect is terminal for concurrent progress callbacks` |

## Complete audit

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 双 TTL 请求契约 | ✅ | `AiReplyDraftService.kt:36-58`; `UnmatchedInboundMailController.kt:437-444,476-480`; TTL 边界测试通过 |
| I-2 单次 TTL 独立 | ✅ | `AiReplyDraftService.kt:61-71,849-897`; `HttpLlmDraftClient.kt:223-246` |
| I-3 总 TTL 硬上限 | ✅ | `AiReplyDraftService.kt:510-527,619-627`; 总预算跨校验回退测试通过 |
| I-4 总 TTL 不扩大重试 | ✅ | `AiReplyDraftService.kt:809-847`; retry 上限测试通过 |
| I-5 回复专用窄 seam | ✅ | `HttpLlmDraftClient.kt:87-138,197-205`; `AiReplyDraftService.kt:347-389,849-897` |
| I-6 DeepSeek SSE 完成条件 | ❌ | D-7；DONE/stop/content/64K/partial 其余条件测试通过 |
| I-7 服务端真实取消 | ✅ | `AiReplyDraftService.kt:73-105`; `HttpLlmDraftClient.kt:237-246,343-363` |
| I-8 取消与审核线性化 | ✅ | `UnmatchedInboundMailController.kt:380-393,618-622,693-713`; cancel-first/commit-first endpoint 测试通过 |
| I-9 有界 registry 与清理 | ❌ | D-1、D-4、D-9 |
| I-10 有界 SSE 与原子结果 | ✅ | `UnmatchedInboundMailController.kt:462-547,672-690`; `app.js:307-328,437-484`; 完整 Stage 6 测试仍属 R-1 |
| I-11 前端状态隔离 | ✅ | `app.js:9915-9923,9951-9958,9992-9999,10006-10023` |
| I-12 失败/可信/隐私边界 | ❌ | D-6；total-timeout fallback/前端 warning 已通过 |
| I-13 可验证实时进度 | ❌ | D-2 |
| I-14 有界、保活、陈旧隔离 | ❌ | D-3、D-4、D-5、D-8、D-9 |
| S-1 | ✅ | `styles.css:5901-5934`; `app.js:9165-9188`; 精确测试仍属 R-1 |
| S-2 | ✅ | `styles.css:5985-5994`; `app.js:4114-4149`; 精确测试仍属 R-1 |
| S-3 | ✅ | `styles.css:5996-6041`; `app.js:366-388,4114-4149`; 精确测试仍属 R-1 |
| Stage 6 自动测试 | ❌ | fix-1 P1-4；R-1 仍缺上述行为矩阵 |

- Accumulation check: ✅ — `AiReplyDraftService.kt:186-216` 跨调用 delta 累计与饱和。
- State-machine check: ❌ — D-4/D-9 的 send failure/disconnect 未稳定进入终态。
- Cross-plan check: ✅ — 旧入口通过无 runtime 参数重载走 legacy seam：`AiReplyDraftService.kt:347-389`。
- Deleted code: `N/A` — baseline 未要求删除代码。
- No extras: ✅ — 产品实现仍只在 baseline 的 10 个文件；既有无关发布/知识/控制面改动不纳入本修复范围。
- Scope compliance: ✅ — continuation 仍只授权上述两个测试文件。
- Manual acceptance: `PENDING` — Stage 0 与 A-1～A-21。
- Plan quality gate: `N/A` — 未提出分解或结构性修正。

## Execution handoff

实现 R-1 全部项目，执行检查，只报告：

- `CONTINUATION_COMPLETE`
- `CONTINUATION_COMPLETE_WITH_DEFERRED_FAILURES`
- `CONTINUATION_BLOCKED`

授权测试完整、剩余失败仅映射 D-1～D-9 时，报告 `CONTINUATION_COMPLETE_WITH_DEFERRED_FAILURES`；随后对同一 baseline/parent 运行 `fix-v`。不得在本 pass 实现 Deferred findings。

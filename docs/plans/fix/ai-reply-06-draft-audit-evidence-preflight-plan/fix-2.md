# 修复计划：AI 回复第 6 步草稿审计、证据展示与人工编辑复验（fix-2）

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-19/ai-reply-06-draft-audit-evidence-preflight-plan.md`
- 前序修复：`docs/plans/fix/ai-reply-06-draft-audit-evidence-preflight-plan/fix-1.md`
- 复验轮次：fix-2/3
- 范围：仅 fix-1 修复过的 `src/main/resources/static/app.js` 与原计划列出的 `src/test/js/trustReplyWorkbench.test.js`。

## 约束摘录

- I-5 / S-1 / S-3：运营端展示证据集时必须使用短 hash，不显示内部 ID、intent、coverage key 或 warning code。
- I-10：preflight 成功和异常响应都必须同时匹配 recordId、seq、adopted draftId 与 exact text snapshot；重新采用、切换、清空、发送立即失效旧请求。
- I-9 / I-12：preflight 仅作只读提示，不得改变人工发送按钮、确认或 raw/rendered 边界。

## 修正记录表

| P1 | 触发频率 | 问题 |
|---|---|---|
| P1-7 | 每次显示草稿反馈或审计日志 | `evidenceSetVersion` 已改为纯 64 位 SHA-256，但 UI 仍以 `split("-")[0]` 取“短值”，结果渲染完整 hash，违反短 hash 展示契约并可挤压窄屏布局。 |
| P1-8 | 同一草稿在一次请求未完成时继续编辑，且旧请求失败 | `catch` 仅比较 seq/record，输入后的 500ms debounce 尚未递增 seq；旧失败响应会把“复验暂不可用”显示到已变更的正文，违反异常响应 exact text/draftId stale guard。 |

## 修复规格

### P1-7：统一截断证据集 hash

- 文件：`src/main/resources/static/app.js`、`src/test/js/trustReplyWorkbench.test.js`。
- 变更：为 evidenceSetVersion 使用固定前 8 或 12 位的截断 helper；`renderAiReplyFeedback()` 与 `renderLogDetail()` 均使用该 helper，不再依赖 `-` 分隔格式。
- 预期：纯 SHA-256 和未来带分隔的 token 都只显示短 hash；完整 token 仍只随 preflight 请求发送，不进入 DOM。
- 触发频率：所有带 evidenceSetVersion 的草稿及其操作日志。

### P1-8：异常响应也执行完整 stale guard

- 文件：`src/main/resources/static/app.js`、`src/test/js/trustReplyWorkbench.test.js`。
- 变更：请求快照保存 draftId 与 exact text；`catch` 渲染错误前使用与成功路径等价的 recordId、seq、draftId、exact text 和当前 detail 校验。输入、重采用、切换、清空、发送必须使旧响应无法渲染。
- 预期：快速输入后旧失败响应不显示；当前文本的失败仍显示中文“复验暂不可用，请人工核对”；发送 handler 不读取 preflight state。
- 触发频率：网络失败、超时或 500 与运营快速编辑并发时。

## 当前状态

- 定向 Maven：PASS — 134 passed, 0 failed, 0 skipped。
- `node --test src/test/js/trustReplyWorkbench.test.js`：PASS — 9 passed, 0 failed。
- `git diff --check`：PASS。
- 未运行全量 Maven/npm：本轮在 P1 审计后按 skill 停止；`npm test` 在 fix-1 已确认无 test script。

## 合规审计

- I-1：✅ `AiReplyDraftService.kt:1373-1408` 版本只由有序 ruleId/available/updatedAt/body SHA-256 构成；`AiReplyReviewAuditService.kt:43-106` 独立记录 observedAt。
- I-2：✅ `AiReplyDraftService.kt:156,1414-1429` 每次生成只构造一次 immutable prompt snapshot，并传入 FREE_FORM 消息构造。
- I-3 / I-4：✅ `AiReplyReviewAuditService.kt:43-163` 仅存有界结构字段，日志失败不阻断；`AiReplyDraftService.kt:1377-1393` 读取异常有 unavailable source 和稳定 warning。
- I-5 / S-1 / S-3：❌ `app.js:3933-3937,7030-7035` 用 `split("-")[0]` 显示完整纯 SHA-256，不是短 hash（P1-7）。
- I-6：✅ `app.js:8812-8829,9645-9688` 每个草稿保存自己的 QA/evidence 元数据，采用读取 entry。
- I-7 / I-8：✅ `PendingMailOperationService.kt:537-693` preflight 从当前 canonical facts 重算，不写入状态。
- I-9 / I-12：✅ `app.js:9710-9750` 发送分支未读取 preflight state；`app.js:9730-9745` 保留 raw/rendered baseline 判断。
- I-10：❌ `app.js:8920-8928` 异常路径缺 draftId/exact text 校验，旧失败响应可覆盖新文本（P1-8）；成功路径 `8902-8919` 已完整校验。
- I-11：✅ `PendingMailOperationService.kt:543-554` 限制正文、facts、长度及版本字符集；非法输入经全局 handler 返回 4xx。
- Deleted code：✅ 未恢复 identity、review confirmation 或人工发送 gate。
- No extras：✅ 生产代码仍仅原计划 5 个文件；无 schema、Flyway、CSS、OperatorActionType 变更。

### 语义完整性

- Accumulation check：✅ 无时间窗口累加器。
- State machine check：✅ N/A；PASS/WARNING 是只读结果，无新增状态机。
- Cross-plan check：✅ 第 4/5/6 步共享的发送 authority 未变化；成功、失败恢复、重开详情均不把 preflight 变成发送条件。

## 观察

- 原计划指定的三份 Kotlin preflight/audit/controller 测试文件存在，但当前定向执行的 16 个用例未覆盖本轮新增的 endpoint、边界和无写入断言；属测试覆盖缺口（P2），未单列 P1。

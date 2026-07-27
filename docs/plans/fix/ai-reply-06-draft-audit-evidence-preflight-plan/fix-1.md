# 修复计划：AI 回复第 6 步草稿审计、证据展示与人工编辑复验

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-19/ai-reply-06-draft-audit-evidence-preflight-plan.md`
- 复验轮次：fix-1/3
- 范围：仅原计划 10 文件清单内的 `AiReplyDraftService.kt`、`PendingMailOperationService.kt`、`app.js` 及对应既有测试。

## 约束摘录

- I-1：生成结果的 Prompt/证据版本必须来自本次服务端结果；同一事实状态的有序 evidenceSetVersion 稳定，观测时间不冒充事实版本。
- I-2：FREE_FORM 的 Prompt 内容和 version 必须来自同一次 effective prompt snapshot。
- I-4：证据读取/审计写失败不能阻断草稿；读取失败须标 unavailable 并有稳定观测 warning。
- I-5：运营 UI 仅展示 displayName 等可观察内容，不得回退 rule ID、intent、coverage key 或 warning code。
- I-10：异步 preflight 必须同时验证 recordId、seq、采用草稿和 exact text；重采用立即失效旧响应。
- I-11：preflight 的 expectedEvidenceSetVersion 限长且只允许版本字符集，非法输入稳定 4xx。
- I-9/I-12：preflight 只读；不成为人工发送 gate，不改变 raw/rendered 发送边界。

## 修正记录表

| P1 | 触发频率 | 问题 |
|---|---|---|
| P1-1 | 每次采用后 preflight | evidenceSetVersion 拼入 `Instant.now()`，同一事实必定被判来源变化；审计没有独立 observedAt。 |
| P1-2 | 配置更新与生成并发时 | FREE_FORM 分两次读取 Prompt，内容和审计 version 可错配。 |
| P1-3 | 来源元数据丢失/旧响应时 | UI 回退展示 rule ID，泄露内部标识。 |
| P1-4 | 同邮件快速重采用共用 QA IDs 的草稿时 | 旧 preflight 响应可覆盖新采用草稿。 |
| P1-5 | QA 读取异常时 | source 仅标 unavailable，未增加稳定观测 warning。 |
| P1-6 | 任意直接 API 调用 | expectedEvidenceSetVersion 未校验字符集。 |

## 修复规格

### P1-1：确定性事实版本与独立观测时间

- 文件：`AiReplyDraftService.kt`、`AiReplyReviewAuditService.kt`、对应审计/草稿/预检测试。
- 变更：evidenceSetVersion 只由有序 source 状态（至少 ruleId、available、updatedAt、answerBodySha256）计算；将 observedAt 独立加入 evidence/audit snapshot，不拼入 version。
- 预期：未变更 QA 下生成、续轮和 preflight 的版本完全相等；任一事实状态变化才改变版本；日志仍可看到本次观测时间。

### P1-2：FREE_FORM 单次 Prompt snapshot

- 文件：`AiReplyDraftService.kt`、`AiReplyDraftServiceTest.kt`。
- 变更：在 `generate()` 仅一次读取 effective Prompt，生成 immutable `{systemPrompt, version}`；向 `buildFreeFormMessages()` 传入该内容，所有生成/回退结果复用其 version。
- 预期：模拟两次读取间配置变更时，发送给 LLM 的 system prompt 哈希必等于 response/audit version；默认和自定义均只读取一次。

### P1-3：移除 UI 内部 ID 回退

- 文件：`app.js`、`trustReplyWorkbench.test.js`。
- 变更：事实 ID 未命中 evidence snapshot 时，不渲染 `ID:`；改为既定中文无来源/未命名提示。
- 预期：HTML 中无 rule ID、intent key、coverage key 或 warning code；正常 evidence 仍显示 displayName、日期和短 hash。

### P1-4：以 draftId 作 preflight 身份与即时失效

- 文件：`app.js`、`trustReplyWorkbench.test.js`。
- 变更：将 draftId 写入 adoptContext 和请求快照；重新采用时同步递增 preflight seq、取消 timer、清空容器；成功/异常响应均比对 recordId、seq、draftId、exact text。
- 预期：旧响应不能渲染到任何新采用草稿，即使 QA IDs、版本或文本相同；不改变发送 handler。

### P1-5：QA 读取异常的稳定可观察 warning

- 文件：`AiReplyDraftService.kt`、`AiReplyReviewAuditServiceTest.kt`、`AiReplyDraftServiceTest.kt`。
- 变更：证据重读异常时，保留 unavailable source，并向本次 result/snapshot 追加固定、无正文 warning code；不改变 readiness 或阻断草稿。
- 预期：读取异常可由 UI/日志区分，日志仍不包含正文、Prompt 或 raw JSON。

### P1-6：版本 token 字符集

- 文件：`PendingMailOperationService.kt`、`PendingMailOperationServiceTrustWorkbenchTest.kt`、`UnmatchedInboundTrustWorkbenchTest.kt`。
- 变更：在长度检查外，以计划定义的版本字符集校验 expectedEvidenceSetVersion；非法输入抛 `IllegalArgumentException` 并沿全局 handler 返回 400。
- 预期：合法当前版本正常预检；空版本允许；空白、控制字符及非法标点稳定 BAD_REQUEST。

## 当前状态

- 定向 Maven：PASS — 128 passed, 0 failed, 0 skipped。
- `node --test src/test/js/trustReplyWorkbench.test.js`：PASS — 9 passed。
- 全量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test` PASS；Node 集成 336 passed。
- `npm test`：FAIL — 项目无 `test` script；仅观察，不属本轮 P1。
- `git diff --check`：PASS。

## 合规审计

- I-1：❌ `AiReplyDraftService.kt:1352-1383` 将 `Instant.now()` 拼入 evidenceSetVersion；同事实不能稳定比较，且无独立 observedAt。
- I-2：❌ `AiReplyDraftService.kt:156,227-235,969-993,1389-1403` 分别读取 version 与 systemPrompt。
- I-3：✅ `AiReplyReviewAuditService.kt:41-103,121-143` 限制 source/coverage/warning/few-shot，after 仅存结构字段。
- I-4：❌ `AiReplyDraftService.kt:1356-1368` 读取异常仅写 unavailable，未写稳定观测 warning；`AiReplyReviewAuditService.kt:105-148` 的日志写失败不阻断已符合。
- I-5：❌ `app.js:8462-8471` 缺 evidence 时输出 `ID: ${ruleId}`。
- I-6：✅ `app.js:8812-8826,9646-9671` 每条 draft 存 own metadata，采用读取 entry。
- I-7：✅ `PendingMailOperationService.kt:537-680` 以完整 textBody 和当前 selection 重算，expected version 仅作变化提示。
- I-8：✅ `PendingMailOperationService.kt:662-665` 仅从 current inbound 派生动作权限，再按 trust 收紧。
- I-9：✅ `PendingMailOperationService.kt:537-680` 无 save/log/delivery；`app.js:9688-9725` 发送路径未读 preflight。
- I-10：❌ `app.js:8850-8925,9652-9679` 只比较 qaRuleIds，重采用未立即递增 seq，也无 draftId 比较。
- I-11：❌ `PendingMailOperationService.kt:543-551` 仅检查长度，不限制 expectedEvidenceSetVersion 字符集；全局 400 映射存在于 `GlobalExceptionHandler.kt:14-17`。
- I-12：✅ `app.js:9652-9679,9688-9725` adoption 保存 raw/rendered baseline，preflight 不参与发送决定。
- Deleted code：✅ 无本计划禁止恢复的 identity/review/send gate。
- No extras：✅ 仅原计划范围内 8 个已修改文件；无 schema、Flyway、CSS、OperatorActionType 变更。

### 语义完整性

- Accumulation check：✅ 无时间窗口累加器。
- State machine check：✅ N/A；本计划未新增状态机，PASS/WARNING 仅只读结果。
- Cross-plan check：✅ 本子计划与第 4/5 步接口读取链已抽查；P1-1/P1-2 是本计划新增版本契约内部断裂，不改变跨计划发送 authority。

## 观察

- 原计划列出的 `npm test` 不可执行，因为仓库没有 npm `test` script；Maven 已执行 Node 集成检查。属于测试命令维护问题，非生产 P1。

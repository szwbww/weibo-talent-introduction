# 开发计划：Phase 3 — 人工组装台 + 审计闭环 + LLM

> 使用 create-p 技能编写。依赖 Phase 1 / 2a / 2b（分类、`QaReplyComposer`、`section_title`、`mail_record_qa_rule` 关联表、`operator_action_log`）。
> 跨 mail + 前端 + （可选）外部 LLM，**超出单计划上限**，拆为 4 个按序子计划。审计以增量引用前序。

公共审计基线：人工回复链路已存在——`PendingMailOperationService.sendQaReply(inboundProcessingId, qaRuleId,…)`（选**单**条规则发，写 `matched_qa_rule_id`，记 `operatorActionLog`）与 `sendManualRichReply(…htmlBody,textBody…)`（自由正文发，`matched_qa_rule_id=null`）；端点 `POST /api/.../unmatched-inbound/{id}/qa-reply` 与 `.../manual-rich-reply`（`UnmatchedInboundMailController:170,182`）；前端 `app.js:4797` 现以单 `qaRuleId` 调用。`operator_action_log` 已记录 before/after（含 qaRuleId、bodyPreview），是审计闭环的现成落点。

---

## 子计划 3-1：多规则组装回复后端（mail 子系统）

### 需求描述
- **可观察结果**：运营可一次选**多条** QA 规则（±自由文本），后端用 `QaReplyComposer` 组装成一封并发送，且记录实际选用的规则集。
- **必须不变**：现有单规则 `sendQaReply` 与自由 `sendManualRichReply` 端点行为不变（新增端点，不改旧的）；`QaReplyComposer` 复用、不分叉逻辑；状态仍经 `ConversationStateService.transition`。
- **超出范围**：UI（3-2）、审计报表（3-3）、LLM（3-4）。

### 关键不变量
- **I-1**：新方法 `sendManualComposedReply(inboundProcessingId, qaRuleIds:List<Long>, overrideTextBody:String?, senderAccountCode?, operatorName?)`：用 `QaReplyComposer` 对 `qaRuleIds` 对应（enabled）规则组装；`overrideTextBody` 非空时以人工文本为准（两层草稿的"已编辑"态）。
- **I-2**：外发 `mail_record` 写**主规则**于 `matched_qa_rule_id`，并向 `mail_record_qa_rule` 落入选用规则全集（复用 2b-4 表，ordinal=运营排序）。`triggeredBy=OPERATOR`、`mailType="MANUAL_COMPOSED_REPLY"`。
- **I-3**：禁用规则进入列表 → 显式报错（同现有 `require(rule.enabled)`），不静默丢弃。
- **I-4**：经 `operatorActionLog` 记录 `qaRuleIds` 全集 + 是否人工改写（`edited:Boolean`）——审计闭环数据源。

### 现状审计（增量）
- 写路径新增：`PendingMailOperationService.sendManualComposedReply`（仿 `sendManualRichReply` 结构，行 151-202）→ `mailRecordRepository.save` + `mailRecordQaRuleRepository.saveAll` + `operatorActionLogService.record`。
- 控制器新增端点 `POST /api/.../unmatched-inbound/{id}/composed-reply`。
- `QaReplyComposer`（2a）需可被 mail 模块调用：确认其为无状态 `@Service` 可注入（是）。

### 实现方案
1. `PendingMailOperationService`：加 `sendManualComposedReply`。组装走 `QaReplyComposer`；落库 mail_record + 关联表；记日志。
2. `UnmatchedInboundMailController`：加端点 + 请求体 `ComposedReplyRequest(qaRuleIds, overrideTextBody?, senderAccountCode?, operatorName?)`。
3. 测试：`PendingMailOperationServiceTest` 补——多规则组装正文、关联表全集、禁用规则报错、edited 标志。

### 变更文件清单（4）
`PendingMailOperationService.kt`(改)/`UnmatchedInboundMailController.kt`(改)/`MailRecordQaRuleRepository.kt`(复用/必要时改)/`PendingMailOperationServiceTest.kt`(改)。
文件 ≤10 ✅　子系统 1（mail，复用 QA composer）✅　新存储列/表 0（复用 2b-4）✅

### 验收标准
- I-1/I-2：选 2 规则 → 外发 body=composer 两段；mail_record_qa_rule 2 行；matched_qa_rule_id=主规则。
- I-3：含禁用规则 → 400/异常，不发送。
- I-4：operator_action_log.after 含 qaRuleIds 列表与 edited。

---

## 子计划 3-2：前端组装台 UI（前端子系统）

### 需求描述
- **可观察结果**：邮件详情人工回复区出现"组装台"——左侧按 6 主题折叠的**片段面板**（QA 规则 + 常用片段），引擎命中项**默认勾选并置顶标"建议"**；中间**草稿预览**（两层：已选规则段 + 自由文本块，可拖动排序）；右侧**缺口清单**（来信问题点，随勾选实时打勾）。点"发送"调 3-1 端点。
- **必须不变**：现有单规则下拉回复、自由富文本回复入口保留；不改后端。
- **超出范围**：审计报表（3-3）、LLM（3-4）。

### 关键不变量
- **I-1**：勾选/取消规则只改"已选规则层"，不清空运营已敲的自由文本层（两层模型）。
- **I-2**：默认勾选项 = 后端返回的引擎命中规则集；运营可增删。
- **I-3**：发送 payload = `{qaRuleIds:[有序], overrideTextBody: 当运营手改过预览则填，否则 null, operatorName}`，命中 3-1 的 `edited` 语义。
- **I-4**：纯前端改动，不动 `/api/qa/*` 与后端 DTO 之外的契约。

### 现状审计（增量）
- `app.js`：现有 QA 规则数据已在 `state.qaRules`/`state.categories`（行 9-10、1389-1416）；人工回复区与 `qa-reply` 调用在 `:4797`。新增组装台视图与 `composed-reply` 调用。
- `index.html`：新增组装台 DOM 容器与样式（`styles.css`）。
- 需要后端提供"该来信的引擎命中规则集 + 缺口清单"：可在打开详情时调一个只读预览端点（3-1 可附带 `GET .../composed-reply/suggest`），或前端用已加载规则本地匹配。**决策：加只读 `suggest` 端点**复用 `QaMatchService`，保证与自动逻辑一致（此端点归入 3-1 范围的小附加，或本子计划后端 1 文件）。

### 实现方案
1. （后端小附加）`UnmatchedInboundMailController` + service：`GET .../{id}/composed-reply/suggest` → 返回命中规则集、按主题分组的全部可选规则、缺口数。
2. `app.js`：组装台组件（片段面板/两层草稿/缺口清单/发送）。
3. `index.html` + `styles.css`：DOM 与样式。
4. 验证：前端交互测试（或手动脚本截图核对，按 CLAUDE 前端约定）。

### 变更文件清单（≤5）
`app.js`(改)/`index.html`(改)/`styles.css`(改)/`UnmatchedInboundMailController.kt`(改, suggest)/`PendingMailOperationService.kt`(改, suggest 只读)。
文件 ≤10 ✅　子系统 2（前端 + mail 只读端点）✅　新存储 0 ✅

### 验收标准
- I-1：敲文本后勾/取消规则，文本不丢。
- I-2：打开详情默认勾选=引擎命中集。
- I-3：发送 payload 结构正确、命中 3-1。
- 缺口清单随勾选实时更新。

---

## 子计划 3-3：审计闭环报表（mail/运营子系统，只读）

### 需求描述
- **可观察结果**：一张"引擎命中 vs 人工选用"对比视图——按时间段统计：被运营**移除**的建议规则（疑似误命中）、被运营**新增**的规则（疑似缺失规则/盲区）、高频自由文本主题。指导规则优化。
- **必须不变**：纯只读聚合，不改发送链路与既有数据。
- **超出范围**：自动改规则（仍人工决策）。

### 关键不变量
- **I-1**：数据源 = `operator_action_log`（人工选用 qaRuleIds、edited）+ `mail_record_qa_rule`（实际外发规则集）+ `QaMatchService` 建议集（由 3-2 suggest 落日志或重算）。
- **I-2**：报表只 SELECT，不写任何业务表。
- **I-3**：差异定义固定——`removed = suggested − selected`，`added = selected − suggested`，按 rule 聚合计数。

### 现状审计（增量）
- 读路径新增：报表 service 读 `operator_action_log`、`mail_record_qa_rule`。无写路径。
- 端点 `GET /api/qa/audit/rule-usage?from&to`。

### 实现方案
1. 新 `QaRuleAuditService` + 只读 repository 查询（JDBC `@Query` 聚合）。
2. 控制器端点 + 响应 DTO。
3. （可选）前端一个只读报表页。
4. 测试：service 聚合用例（removed/added 计数）。

### 变更文件清单（≤5）
`QaRuleAuditService.kt`(新)/控制器(改或新)/repository 查询(改)/DTO/测试。
文件 ≤10 ✅　子系统 1 ✅　新存储 0 ✅

### 验收标准
- I-3：构造 suggested={1,2,3}、selected={2,3,4} → removed={1}、added={4}。
- I-2：无任何业务表写入。

---

## 子计划 3-4（可选）：LLM 缝合 / 翻译（隔离子系统）

### 需求描述
- **可观察结果**：组装台"润色"按钮，把已选规则段 + 自由文本经 LLM 缝合为顺滑、去重、且匹配专家语言的草稿，**回填预览供人工再编辑**（绝不自动发送）。
- **必须不变**：仅作用于人工路径的草稿生成；自动回复链路不接 LLM；无 LLM 或调用失败时回退到 2b-1 确定性拼接。
- **超出范围**：自动外发用 LLM。

### 关键不变量
- **I-1**：LLM 仅产出**草稿**，写回预览层，需人工点发送；失败/超时/未配置 → 回退确定性组装，不阻断。
- **I-2**：输入限定为"已选规则正文 + 来信问题 + 自由文本"，不注入其他敏感数据；输出仅文本。
- **I-3**：开关 `talent-introduction.llm.enabled`（默认 false）；关闭时按钮隐藏，行为同 3-2。

### 现状审计（增量）
- 新增隔离 `LlmStitchService`（HTTP 客户端，配置类 `@ConfigurationProperties`，遵循仓内 opt-in bean 模式如 mail-queue）。端点 `POST .../composed-reply/polish`（只返回草稿，不发送）。
- 不触碰发送/落库路径。

### 实现方案
1. 配置类 + opt-in bean（参照 `RabbitMailQueueConfig` 的 `enabled` 网关与 `ObjectProvider` 回退）。
2. `LlmStitchService` + 端点（返回草稿文本）。
3. 前端"润色"按钮（gated by enabled）。
4. 测试：enabled=false 回退确定性；失败回退；输入边界。

### 变更文件清单（≤6）
配置类/`LlmStitchService.kt`/控制器(改)/`app.js`(改)/`application.yml`(改)/测试。
文件 ≤10 ✅　子系统 ≤2（LLM 服务 + 前端按钮）✅　新存储 0 ✅

### 验收标准
- I-1：失败/未配置 → 返回确定性拼接草稿；无自动发送。
- I-3：enabled=false 时按钮不出现、链路无 LLM 依赖。

---

> 全程总览：Phase 1 分类重构 → 2a 多命中聚合 → 2b 标题/覆盖/缺口/落库 → 3 人工组装台 + 审计 + LLM。每个子计划独立可部署、独立验收，单计划均守 ≤10 文件 / ≤2 子系统 / ≤1 新列。

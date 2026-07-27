# AI 回复整体复验缺陷修复总索引

## 需求描述

十个子任务的自动测试已通过，但整体复验仍发现三类会直接影响真实回信或发送安全的 P1 缺陷：

1. 专家原始问法中的英式拼写、复合短语和连字符未被 intent catalog 稳定识别，真实邮件可能漏掉 programme purpose/structure 或 IP 子意图。
2. QA 来源使用 `may/can/depends` 时，模型回答 `will receive` 仍可能通过高风险声明校验，把可能性写成确定承诺。
3. AI 初稿审计写失败、同秒生成两稿或 authority JSON 损坏时，当前发送闸门存在泄露可采用草稿、读取旧 authority 或 fail-open 的路径；确认指标也可能被客户端 `replySource` 污染。

本文件只负责范围、顺序和发布门编排，不直接交给开发执行。三个执行计划分别满足 create-p 的文件数和子系统边界。

## 关键不变量

- 专家原始七组问题必须保持原顺序，并精确解析为后端定义的原子 intent；不得用 `general.answer` 掩盖已知业务问题。（K-ai-reply-intent-alias-fixture-fidelity、K-compound-request-coverage-intent-atomic）
- intent 只决定“专家问了什么”；QA `coverage_keys` 只决定“现有审核依据能回答什么”。不得改写线上 QA 正文、关键词或 coverage 数据来规避解析缺陷。（K-request-facts-not-flat-pool）
- 研究匹配仍必须同时具有专家画像和 programme scope 依据；本轮禁止触发 Scholar/Scopus、CV 或任何外部资料抓取。（K-research-fit-dual-evidence）
- 模型输出必须在严格结构 materialize 后执行声明策略；任一高风险回答失败，整次 LLM 结果废弃并走确定性 fallback。（K-grounded-json-materialize-before-policy）
- AI 草稿发送 authority 只来自服务端当前审计记录；浏览器提交的 `replySource`、readiness、identity 只能作为请求声明，不能创造或降级服务端 authority。（K-ai-review-server-authoritative-snapshot）
- `mail_record`、`mail_record_qa_rule` 与既有外发审计语义不变；真实选用 QA 仍以关联表为准。（K-rich-reply-qa-audit-reuse、K-audit-selected-source）
- 训练模拟保持零写入；只有收发邮件首轮生成建立发送 authority。
- 不新增外部 API、数据库表或迁移，不修改模型下拉框枚举、加载遮罩和现有邮件样式。

## 现状审计

| 缺陷域 | 直接证据 | 风险 | 历史修复状态 |
|---|---|---|---|
| Intent 真实 fixture | `AiReplyIntentCatalog.kt` 只 lowercase + URL mask；aliases 缺少 `purpose and structure of the programme`，`intellectual-property` 也无法匹配带空格 alias | 真实七问可静默退化或缺 intent，标题与 readiness 随之错误 | Phase 6 `fix-3.md` 已输出 `Verification Blocked`，禁止再建 fix-4 |
| Modality | `detectsModalityStrengthening()` 只识别 `guaranteed/will definitely/...`；测试名写“will”但样例实际为 `will definitely` | `may receive` 可被写成 `will receive` | Phase 7 已有 fix-3，本轮按根因重做而非追加补丁 |
| Review authority | 初稿审计失败返回 null 但 controller 仍返回草稿；无审计记录直接放行；latest 仅按秒级 `created_at`；缺 readiness 时 return；确认日志由客户端字段触发 | 可采用未授权草稿、旧 identity 误判、损坏记录 fail-open、质量指标失真 | Phase 8 `fix-3.md` 仍有 3 个 P1 |

### 数据与写路径边界

- `qa_rule`：V1 建表，V76 增加 `coverage_keys VARCHAR(2000) NOT NULL DEFAULT ''`。运行时写入只经 `QaRuleManagementService.createRule/updateRule/setRuleEnabled`；本轮三个计划均只读，不新增迁移、不写 QA 数据。
- `operator_action_log`：V19 建表，`id` 自增，`created_at DATETIME` 秒级。通用写入口为 `OperatorActionLogService.record()`；AI 草稿、发送拦截、审核确认由 `AiReplyReviewAuditService` 调用。其他专家状态、索引等级、绑定与外发日志写入路径不得改变。
- `mail_record`：V1 建表；发送路径包括自动回复、人工外联、会议邀请和 pending/manual rich reply。本轮只调整 `PendingMailOperationService` 的发送前 authority 校验与发送后确认事件条件，不改变投递和持久化顺序。
- `mail_record_qa_rule`：V42 建表，`(mail_record_id, qa_rule_id)` 唯一；人工、自动和 pending 回复均有写入路径。本轮不改表与写入逻辑。

## 实现方案

按以下顺序逐个执行并单独调用 fix-v；前一计划未通过时不得进入下一计划：

1. [真实邮件 intent fixture 与匹配归一化](./ai-reply-intent-fixture-normalization.md)
2. [条件来源到确定回答的语气强化拦截](./ai-reply-modality-strengthening.md)
3. [AI 草稿审核 authority fail-closed](./ai-reply-review-authority-fail-closed.md)

顺序理由：先保证 request/intents 正确，再验证模型对这些 intents 的事实表达，最后以稳定的 readiness/snapshot 建立发送 authority。

## 变更文件清单

本索引不拥有业务代码文件。三个执行计划分别拥有 3、3、9 个变更文件；禁止跨计划顺手修改。若执行时发现必须新增文件或跨入第三个子系统，应停止并重新调用 create-p 拆分。

## 验收标准

- 三个子计划的自动测试和人工验收全部通过。
- 原始 Pracheta/Janmeda 七问 fixture 精确得到七组、固定顺序和完整 intent 列表。
- `may receive` 来源不能产出可采用的 `will receive` LLM 草稿；明确写 `will receive` 的来源仍可通过。
- 任意首轮 AI audit 持久化失败时，浏览器收不到可采用正文；重试可恢复。
- 同一秒两条初稿记录只认更大 `id`；损坏、矛盾或缺字段 authority 一律在投递前拒绝。
- READY 草稿不产生 `AI_REPLY_REVIEW_CONFIRMED`；只有服务端确认过的 NEEDS_REVIEW/BLOCKED 草稿产生该事件。
- `mvn test`、`node --check src/main/resources/static/app.js`、`node --test src/test/js/*.test.js` 全部通过。
- 无新 migration；训练模拟不写 `operator_action_log`、`mail_record` 或 `mail_record_qa_rule`。

## 人工验收清单

本轮不创建 acceptance 勾选文件。开发与 fix-v 全部通过后，再从三个子计划的 `## 人工验收清单` 合并导出。总体验收至少覆盖：

- 真实七问邮件在 AI 训练模拟与收发邮件入口的逐点结构一致。
- 条件语气、明确语气各一组受控模型返回。
- audit 写失败恢复、同秒双稿、损坏记录、纯人工无记录、READY 直发、非 READY 确认六条发送路径。
- 浏览器确认错误态无草稿气泡、输入不清空、首轮状态未锁定，恢复后可原地重试。


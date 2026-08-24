# 00 人工事实权威改造总控计划

> 类型：Master governance plan。本文只约束 4 份子计划的顺序、跨计划契约、发布单元和验收门禁；禁止把本文当成单个 implementation plan 直接执行。

## 需求描述

把以下 4 份子计划约束为一个可追踪的改造项目，确保“摘抄正确 → 人工事实成为工作台最终结果 → 线上发送与关联落库保持同一事实 → 最终事件留下诊断记录”按顺序闭环：

1. `01-mail-request-extraction-correctness.md`
2. `02-manual-fact-authority-workbench.md`
3. `03-manual-fact-authority-live-send.md`
4. `04-trust-reply-diagnostics-persistence.md`

必须保持不变：自动回复、legacy flat selection、普通人工 QA 发送的严格匹配；规则存在/enabled/policy/answerBody、source/evidence/version、locked item、placeholder、suppression、高风险事实、动作和发送确认等机械/安全硬门禁；既有发送幂等和事务语义。

明确不在范围：直接实现业务代码；新增诊断筛选 UI/API/索引；修改 4 个子计划未列出的邻接功能；把人工事实权威扩展到自动回复或无 workbench assembly 的旧路径；在机器验证前生成 acceptance 衍生文件。

## 关键不变量

### Invariant I-1：执行顺序不可交换

- Rule：必须严格执行并验证 `01 → 02 → 03 → 04`；后计划使用前计划落地后的代码，不得从原始基线并行实现共享文件。
- Applies to：全部 4 个子计划，尤其共享的 `QaFactSelectionService.kt`、`TrustReplyWorkbenchService.kt`、`PendingMailOperationService.kt` 和复用测试文件。
- Violation consequence：requestKey、事实字段语义、verified assembly 或诊断 DTO 会基于不同代码版本实现，造成编译通过但跨链路漂移。
- 来源：original；[[K-request-key-includes-intent-keys]]、[[K-materialize-version-five-write-sites]]。

### Invariant I-2：人工事实身份端到端恒等

- Rule：显式 workbench 矩阵逐 request 的最终事实，必须按顺序贯通 `RequestFactItem.factRuleIds/boundRuleIds → requestFactSelections → selection.sendQaRuleIds → assemble.canonicalFactIds → SendPayload.canonicalQaRuleIds → mail_record_qa_rule(ordinal)`；允许跨 request 重复，但 canonical union 只保留首次出现。
- Applies to：子计划 02 的 selection/generation/assembly；子计划 03 的发送、安全和关联落库；子计划 04 的诊断快照。
- Violation consequence：用户选择与生成正文、发送安全输入、数据库事实关联或审计记录不一致。
- 来源：[[K-fact-matrix-two-semantics-in-one-field]]、[[K-ai-reply-prompt-vs-send-rule-ids]]、[[K-rich-reply-qa-audit-reuse]]。

### Invariant I-3：语义诊断不得重新成为硬门禁

- Rule：关键词/intent mismatch、unrecognized ask、UNSUPPORTED、跨摘要重复事实只用于建议或诊断；不得删除人工事实、过滤 handling、阻断 assembly/发送。硬门禁仅限规则可用性、必要事实/说明、结构、版本、source、safety、suppression、placeholder、确认和幂等。
- Applies to：子计划 02 的 allowed handling/前置条件；子计划 03 的 assembly 与 safety 分流；子计划 04 的 flags。
- Violation consequence：恢复“用户已选择事实但没有按事实原文输出”的原始问题，或让记录字段反向影响业务结果。
- 来源：[[K-explicit-fact-selection-must-match-request]]（2026-08-24 修订）、[[K-manual-send-fact-gate-is-the-only-seam]]（2026-08-24 修订）。

### Invariant I-4：子计划 02 与 03 是同一原子发布单元

- Rule：工作台放开人工权威（02）不得在发送端权威重算（03）之前单独上线；两者必须进入同一 release artifact、同一回滚单元。02 可先完成机器验证，但在 03 通过前状态只能是 `IMPLEMENTED_NOT_RELEASABLE`。
- Applies to：release R2、部署和回滚。
- Violation consequence：UI/assembly 接受 mismatch facts，发送端却用 legacy strict selection 二次删除或拒绝，形成预览与外发不一致。
- 来源：original；[[K-manual-send-fact-gate-is-the-only-seam]]。

### Invariant I-5：诊断只能依附最终事件

- Rule：子计划 04 只能把服务端生成的有界 diagnostics 附加到发送成功或训练评估既有 action row；bootstrap、添加/删除/移动事实、生成、锁定、预览均不得写诊断 action。
- Applies to：`TrustReplyAssembleResponse`、`AiTrainingEvaluationService`、`ManualReplySendAttemptService.recordSendAudit`、`operator_action_log`。
- Violation consequence：审计日志被临时操作污染、出现敏感正文或后续统计把试操作当成最终决定。
- 来源：[[K-training-evaluation-bounded-action-log]]。

### Invariant I-6：自动/legacy/无 assembly 路径必须回归兼容

- Rule：只允许显式 workbench matrix 和 verified assembly 采用人工权威；auto、null selection、legacy flat selection、`trustReplyAssembly == null` 继续执行当前严格匹配和既有 degraded-warning 逻辑。
- Applies to：子计划 02 `QaFactSelectionService`；子计划 03 `canonicalizeFactRuleIds` 分流；子计划 04 无 assembly action payload。
- Violation consequence：人工功能改造外溢到自动发送、普通 QA 审计或旧人工发送。
- 来源：[[K-explicit-fact-selection-must-match-request]]、[[K-manual-send-fact-gate-is-the-only-seam]]。

### Invariant I-7：子计划范围冻结

- Rule：每个子计划只能修改其 `## 变更文件清单` 中的文件；需要新增文件、字段、store、action type、API 或 CSS 时立即停止，先修订对应子计划；若影响跨计划契约，同时修订本 master 并由用户重新批准。
- Applies to：执行、repair、verification 全阶段。
- Violation consequence：突破 create-p 的 10 文件/2 子系统边界，验证范围失真，后续 fix 轮次不可控。
- 来源：original。

### Invariant I-8：每个门禁必须留下机器证据

- Rule：每个子计划先跑自身定向测试并通过独立验证，再进入下一计划；最终必须跑合并定向测试、完整 `mvn test`、前端 Node 测试和 `git diff --check`。任何基线既有失败需在阶段 0 记录完整命令和输出，不得口头忽略。
- Applies to：G0-G5 全部质量门禁。
- Violation consequence：后计划掩盖前计划回归，或把既有失败误算为本次成功/失败。
- 来源：original。

### Invariant I-9：失败只回退当前发布单元，不跳过依赖

- Rule：01 失败则停止全部后续；02/03 任一失败则 R2 整体不可发布；04 失败不得宣告 master 完成。修复只能在失败子计划范围内进行，除非按 I-7 完成计划修订。
- Applies to：repair、release、rollback。
- Violation consequence：以“先上线一部分”制造事实语义断层，或在未留痕时误报完整交付。
- 来源：original。

## 现状审计

### 受控子计划与发布单元

| 子计划 | 文件数 / 子系统 | 依赖 | 发布单元 | 独立完成条件 |
|---|---:|---|---|---|
| 01 摘抄与坐标 | 6 / 2 | 无 | R1 | G1 通过，可独立发布 |
| 02 工作台人工权威 | 10 / 2 | 01 | R2 | G2 通过但不可单独发布 |
| 03 LIVE 发送闭环 | 4 / 2 | 01、02 | R2 | G3 通过后 02+03 才可一起发布 |
| 04 诊断留痕 | 7 / 2 | 01、02、03 | R3 | G4/G5 通过后 master 才可完成 |

### 共享文件与顺序交互

- `QaFactSelectionService.kt`：01 修 ask/span claiming；02 再改矩阵事实语义。02 必须基于 01 结果，禁止各自从旧版本实现后覆盖。
- `QaFactSelectionServiceTest.kt`、`TrustReplyWorkbenchServiceTest.kt`：01 与 02 共用；02 不得删除 01 新增的线上回归 fixture。
- `TrustReplyWorkbenchService.kt`：02 改 selection/handling/assembly canonical facts；03 在其上抽 verified assembly；04 再在 verified result 上增加 diagnostics。
- `PendingMailOperationService.kt`、`PendingMailOperationServiceTrustWorkbenchTest.kt`：03 建立发送权威分流；04 只附加最终诊断，不得重写 03 的事实来源。
- `ManualReplySendAttemptServiceTest.kt`：03 钉住关联 ordinal；04 在同一测试基础上增加 action payload，不得把关联断言替换掉。

### `trust_reply_workbench_state`

- Schema：`V83__create_trust_reply_workbench_state.sql:13-23`，每个 source 一行，payload LONGTEXT；`TrustReplyWorkbenchStateStore:194-204` 限 256 KiB、30 天。
- Write paths：`TrustReplyWorkbenchStateStore.save/delete/deleteBySource/pruneExpired`；service 调用关系已在子计划 01 审计。
- Read paths：`load/decodePayload` 被 bootstrap、restore、预检授权读取。
- Interaction point：01 改 request 集合/requestKey，旧 payload 必须走 `STALE`；02 继续使用 canonical matrix，不新增推测迁移。（来源：[[K-request-key-includes-intent-keys]]）

### `mail_record_qa_rule`

- Schema：`V42__mail_record_qa_rule.sql:3-12`，`(mail_record_id,qa_rule_id)` 唯一，ordinal 必填。
- Write paths重新 grep 结果恰为 3 个：`ManualExpertMailService.kt:92`、`ManualReplySendAttemptService.kt:253`、`AutoMailReplyService.kt:637`。
- Read paths：既有 repository/audit 按 mail record 和 ordinal 读取；本 master 不新增 reader。
- Interaction point：02 的 assembly canonical union 经 03 SendPayload 进入 `ManualReplySendAttemptService`；另外两条写路径必须不变。（来源：[[K-rich-reply-qa-audit-reuse]]）

### `operator_action_log`

- Schema：`V19__add_operator_status_and_action_log.sql:32-52`，`after_value TEXT`，已有 inbound/action/time 索引，无 JSON flag 索引。
- 本项目相关写路径重新 grep：`AiTrainingEvaluationService.kt:75`、`ManualReplySendAttemptService.kt:383` 均调用统一 `OperatorActionLogService.record`。
- Read paths：`OperatorActionLogService.search` / `/api/operator-action-logs` 按现有字段查询并返回 afterValue。
- Interaction point：04 只能扩展这两条最终 action 的 after map；不得新增 row、action type、查询承诺或正文。（来源：[[K-training-evaluation-bounded-action-log]]）

### 无新增 master 写路径

本文不直接授权任何业务文件或 store 写入。各 store 的具体实现和测试只能由对应子计划授权；master 只验证跨计划输入输出是否恒等。

## 实现方案

### 阶段 0：冻结基线与执行上下文（I-7、I-8）

1. 记录执行起点 commit、`git status --short`、当前未提交用户改动；不得清理或覆盖非本项目修改。
2. 运行 4 份子计划涉及的现有定向测试和 `git diff --check`，把既有失败的完整命令/输出写入执行记录。
3. 将 4 份子计划和本 master 标记为批准版本；执行中若需改计划，按 I-7 停止并请求批准。

门禁 G0：基线可复现；无未解释的测试失败；执行器确认不会直接把 master 当作单一大计划修改超过 10 个文件。

### 阶段 1：执行并验证子计划 01（I-1、I-6、I-8、I-9）

只执行 `01-mail-request-extraction-correctness.md`。完成其全部机器验收和人工清单准备；不得提前引入 02 的事实字段。

门禁 G1：子计划 01 的全部 invariants 和测试通过；脱敏线上 fixture 只剩 1 条 QUESTION；非零 offset claiming 正确；旧 state 为 STALE。G1 失败立即停止。

### 阶段 2：执行并验证子计划 02（I-1、I-2、I-3、I-4、I-6、I-7、I-8）

基于 G1 的代码执行 `02-manual-fact-authority-workbench.md`。严格遵守其 S-1，不新增 CSS/class/inline style。保留 01 测试。

门禁 G2：7 handling、manual fact equality、residual general claims、跨摘要重复、verbatim、auto/legacy 回归和前端 Node 测试全部通过。状态仅为 `IMPLEMENTED_NOT_RELEASABLE`；禁止部署 02。

### 阶段 3：执行并验证子计划 03，形成原子 R2（I-2、I-3、I-4、I-6、I-8、I-9）

基于 G2 的 `TrustReplyWorkbenchService` 执行 `03-manual-fact-authority-live-send.md`。verified assembly 必须复用 02 的 canonical selection，不得复制第三套语义 resolver。

门禁 G3：assembly 在 claim 前重算；客户端 ids 必须与 verified canonical 严格相等；safety/编辑正文/legacy 分流/关联 ordinal/幂等全部通过。随后联合重跑 01+02+03 定向测试。只有 G3 通过，R2 才可作为一个 artifact 发布或回滚。

### 阶段 4：执行并验证子计划 04（I-2、I-3、I-5、I-6、I-7、I-8）

基于 G3 的 verified assembly 执行 `04-trust-reply-diagnostics-persistence.md`。diagnostics 只读 selection/versions，不得回写业务状态或进入 safety/hash。

门禁 G4：LIVE/TRAINING 最终 action 含有界 diagnostics；非最终操作、无 assembly 发送无伪诊断；51/21/51 截断和隐私 canary 测试通过。

### 阶段 5：全项目联合机器验证（I-1-I-9）

1. 运行 4 份子计划所有定向 Kotlin 测试的并集。
2. 运行 `node --test src/test/js/trustReplyWorkbench.test.js`。
3. 运行完整 `mvn test`，不得用定向测试替代全量回归。
4. 运行 `git diff --check`；逐文件核对实际改动均属于某个子计划文件清单。
5. 机器核对端到端等式：matrix → assembly → SendPayload → association → diagnostics；核对 auto/legacy/null assembly 负面分支。

门禁 G5：全部命令 exit 0；无超范围文件；无被删除/弱化的前序测试；独立 verification 通过。失败回到对应子计划 repair，不允许跳过。

### 阶段 6：人工验收与发布（I-4、I-8、I-9）

G5 后才从本 master 和 4 份子计划的 `## 人工验收清单` 导出各自 acceptance 文件。按 R1、R2、R3 的发布边界验收；R2 必须证明 02/03 在同一 artifact。全部 A-n 完成前不得宣告 master 完成。

## 变更文件清单

本 master **不直接授权生产/测试代码修改**。以下 4 个文件是受控执行输入；各自 `## 变更文件清单` 是唯一代码授权边界：

| 文件 | 作用 |
|---|---|
| `docs/plans/2026-08-24/01-mail-request-extraction-correctness.md` | G1 与 R1 权威子计划 |
| `docs/plans/2026-08-24/02-manual-fact-authority-workbench.md` | G2、R2 前半权威子计划 |
| `docs/plans/2026-08-24/03-manual-fact-authority-live-send.md` | G3、R2 后半权威子计划 |
| `docs/plans/2026-08-24/04-trust-reply-diagnostics-persistence.md` | G4、R3 权威子计划 |

Master 文件计数：4 个受控计划文档、0 个直接授权代码文件；子计划分别保持 ≤10 文件、≤2 子系统。禁止把四份代码文件清单合并成一个大执行计划。

## 验收标准

- I-1：执行记录显示 01/02/03/04 的开始与 verification 时间严格递增；共享文件 diff 基于前一阶段产物，无并行覆盖。
- I-2：集成测试断言 `matrix ordered union == assemble.canonicalFactIds == SendPayload.canonicalQaRuleIds == mail_record_qa_rule ordered ids`；diagnostics 中逐 request manual ids 与 matrix 相同。
- I-3：mismatch/unrecognized/UNSUPPORTED/duplicate fixture 均只产生提示或 flags；不触发 handling 过滤、事实删除或发送拒绝；规则 unavailable/version/safety fixture仍硬拦。
- I-4：release manifest/构建记录证明 02 与 03 属于同一 artifact；不存在只含 02 的部署。
- I-5：只有发送成功和训练评估 action 含 diagnostics；bootstrap/generate/lock/preview/发送失败不新增诊断 action。
- I-6：auto、legacy flat、null assembly 既有严格匹配/degraded warning/action type 测试通过。
- I-7：`git diff --name-only` 每个代码文件都能映射到恰当子计划清单；出现额外文件即失败。
- I-8：G0-G5 命令、exit code、输出均归档；最终 `mvn test`、Node test、`git diff --check` exit 0。
- I-9：任一 gate 失败时状态未被标记完成/可发布；repair 记录指向失败子计划及其不变量。

联合验证命令最低集合：

```bash
mvn -q -Dtest=QaRequestExtractorTest,QaFactSelectionServiceTest,AiReplyDraftServiceTest,TrustReplyWorkbenchItemFlowTest,TrustReplyWorkbenchServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest,AiTrainingEvaluationServiceTest test
node --test src/test/js/trustReplyWorkbench.test.js
mvn test
git diff --check
```

## 人工验收清单

### A-1：线上样本摘抄恢复

- 前置条件：存在 `LIVE_INBOUND:124` 对应邮件；部署包含 R1。
- 操作步骤：1）打开该邮件工作台；2）展开全部 request；3）刷新一次。
- 预期结果：只出现 1 条真实问题；姓名、职位、机构、电话、地址 5 条签名均不出现；刷新不报 bootstrap 422，旧快照若存在显示 `STALE`。
- 覆盖：I-1、I-6；子计划 01。

### A-2：七种 handling 与 mismatch 事实原文

- 前置条件：构造一条自然状态 `UNSUPPORTED` 的 request；存在 1 条 enabled、policy 非 NEVER、answerBody 非空但意图不匹配的事实。
- 操作步骤：1）添加该事实；2）打开处理方式下拉；3）选择“按事实原文回答”；4）生成并整合。
- 预期结果：下拉显示 7 项；事实未被删除；出现“人工选择已生效；系统未匹配到对应意图，已记录供后续优化。”；正文逐字等于该事实 answerBody。
- 覆盖：I-2、I-3；子计划 02 S-1。

### A-3：跨摘要重复事实

- 前置条件：同一邮件至少 2 条 request，存在 1 条可用事实。
- 操作步骤：1）分别给两条 request 添加同一事实；2）生成并锁定；3）刷新；4）整合。
- 预期结果：两个 picker 均允许选择；刷新后两个绑定都保留；整合不出现 `TRUST_REPLY_FACT_ALREADY_ASSIGNED` 或 `TRUST_REPLY_DUPLICATE_CLAIM`；canonical facts 中该 id 只出现一次。
- 覆盖：I-2、I-3；子计划 02。

### A-4：LIVE 发送与数据库事实恒等

- 前置条件：使用 A-2/A-3 的 LIVE assembly；具备测试收件箱和数据库只读权限。
- 操作步骤：1）采用 assembly；2）可编辑正文措辞；3）完成预检与所需确认；4）发送；5）按 outbound mail_record id 查询 `mail_record_qa_rule order by ordinal`。
- 预期结果：邮件发送成功；数据库事实 id 顺序严格等于工作台 canonical facts；没有自动推荐额外 id、没有漏项；编辑正文不改变事实关联。
- 覆盖：I-2、I-4；子计划 03。

### A-5：篡改、版本与安全硬门禁

- 前置条件：准备一份可发送 assembly 和一封 suppressed 测试联系人邮件。
- 操作步骤：1）分别修改 qaRuleIds 顺序、sourceVersion、locked item；2）提交发送；3）再对 suppressed 联系人提交原始合法请求。
- 预期结果：前三种均在 SMTP attempt 创建前失败；suppressed 联系人仍禁止外发；任何失败均无 SENT mail_record。
- 覆盖：I-3、I-8、I-9；子计划 03。

### A-6：最终诊断留痕

- 前置条件：一封包含 mismatch、unrecognized、UNSUPPORTED+manual、duplicate assignment 的 LIVE 邮件和一封训练邮件；部署 R3。
- 操作步骤：1）成功发送 LIVE 邮件；2）提交训练评分；3）按 inbound/action 查询 operator action logs；4）在另一封邮件只做添加/删除/预览但不发送。
- 预期结果：前两条最终 action 的 afterValue 含 `trust-reply-diagnostics-v1` 和对应 flags；不含邮件/回答/事实正文；临时操作不新增诊断 action。
- 覆盖：I-5；子计划 04。

### A-7：自动、legacy 与纯人工发送回归

- 前置条件：各准备一封自动回复可匹配邮件、legacy flat QA 人工邮件、无 workbench assembly 的纯人工富文本邮件。
- 操作步骤：依次执行原有预览/发送流程并查看 action log。
- 预期结果：自动与 legacy 继续按关键词/intent 严格筛选；legacy mismatch 走既有 degraded warning；纯人工富文本使用原 action type且无伪 diagnostics。
- 覆盖：I-6；需求中的 must-not-change。

### A-8：原子发布证明

- 前置条件：准备 R2 release manifest、构建号或部署 commit。
- 操作步骤：1）核对 artifact 同时包含子计划 02 与 03 的提交；2）模拟回滚该 artifact。
- 预期结果：不存在仅含 02 的可部署版本；回滚时工作台人工权威与发送权威同时回退，不出现一侧新一侧旧。
- 覆盖：I-4、I-9。


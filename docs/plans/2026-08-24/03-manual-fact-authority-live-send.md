# 03 人工事实权威贯通线上发送与关联落库

> 执行顺序：第 3 份；依赖计划 01、02。与计划 02 同一发布窗口交付。

## 需求描述

让线上 `sendManualRichReply` 在 SMTP claim 前服务端重算并验证 `TrustReplyAssembleRequest`，以重算结果作为事实、授权和安全检查输入。对可信 workbench assembly，不再调用 legacy strict selection 重新做关键词/intent 语义筛选；最终 `mail_record_qa_rule` 必须逐元素等于服务端 assembly 的 canonical facts。没有 assembly 的普通人工富文本/历史 QA 发送继续沿用现有 `canonicalizeFactRuleIds` 降级逻辑。

允许运营采用工作台后编辑最终正文；服务端仍对实际发送的 subject/text/html 做 placeholder、抑制、高风险 claim、动作和确认校验。正文编辑不能改变 canonical fact ids，也不能绕过 assembly source/version/locked-item 校验。

关联知识：[[K-manual-send-fact-gate-is-the-only-seam]]、[[K-rich-reply-qa-audit-reuse]]、[[K-ai-reply-prompt-vs-send-rule-ids]]、[[K-manual-rich-render-before-send]]、[[K-manual-send-safety-gate-first-hit-only]]、[[K-suppression-check-call-sites]]、[[K-operator-directed-authorization-seam]]。

## 关键不变量

### Invariant I-1：assembly 必须在任何发送副作用前重算

`LIVE_INBOUND` source type/id、sourceVersion、canonical matrix、每条 locked item、evidence/context version、claims、versionId、frame 必须通过服务端 `TrustReplyWorkbenchService` 验证；失败发生在 suppression/`prepareAndClaim`/SMTP/DB 成功落库之前。

### Invariant I-2：可信 assembly 事实只来自服务端重算

有 assembly 时：

`canonicalFactIds = verifiedAssembly.response.canonicalFactIds`

客户端 `qaRuleIds` 必须按顺序与该列表完全相等，否则返回稳定 422/409，不得静默采纳客户端 ids、不得回退自动推荐、不得部分删减。

### Invariant I-3：无 assembly 路径逐字兼容

`trustReplyAssembly == null` 时继续使用 `canonicalizeFactRuleIds`：仅捕获 `IllegalArgumentException`，不可用/不匹配变为可确认 warning，可用子集进入关联；`carriesQa` 仍由用户是否提交 `qaRuleIds` 判定。

### Invariant I-4：发送安全边界不放宽

实际渲染后的 subject/text/html 继续执行长度、placeholder、suppression、高风险事实、trust rhetoric、敏感动作、普通/强确认。可信 assembly 只替换事实 selection 数据源，不跳过任何 safety finding。

### Invariant I-5：operator action 授权来自已验证 locked items

不得再像当前代码仅检查 source identity 后直接读取客户端 `lockedItems`。授权动作必须由通过 `verifyAssembly` 的 locked versions 推导；assembly 无效时授权集合为空且发送失败。

### Invariant I-6：最终关联精确且有序

`ManualReplySendAttemptService.SendPayload.canonicalQaRuleIds` 等于 verified canonical facts；`finalizeSuccess` 按 ordinal 写入 `mail_record_qa_rule`。同一事实跨 request 重复时 canonical union 首次出现一次，满足表唯一键。

### Invariant I-7：幂等和事务语义不变

`prepareAndClaim`、SMTP、`finalizeSuccess`、失败恢复、`mail_send_attempt`/`mail_record` 唯一约束均不改。assembly 验证失败不能烧掉 attempt。

## 现状审计（代码证据）

### 当前发送顺序

- `PendingMailOperationService.sendManualRichReply():153-179` 先加载来信/联系人，然后在 `:167-173` 直接对客户端 `qaRuleIds` 调 `canonicalizeFactRuleIds`。
- `:218-226` 只检查 assembly source type/id，随后直接从客户端 lockedItems 推导 operator actions；没有调用 `assemble()`。
- `:228-257` safety 和 SendPayload 均使用上述 legacy canonical ids。
- suppression 在 `:268-274`，`prepareAndClaim` 在 `:276`；因此新验证必须插入 `:165` 后且早于 `:268`。
- 当前唯一 `assemble(candidateAssembly)` 在 `archiveLiveUnsupportedAnswers():610-648`，发生在发送成功之后，只影响归档，无法保护 SMTP/事实关联。

### legacy fact gate

- `canonicalizeFactRuleIds():564-598` 先调用 strict `select`，失败后 partition 为 selectable/unmatched/unavailable，并只保留 selectable 子集。
- `collectSafetyFindings():804-808` 又按 canonical ids 调一次 strict `select`；即使计划 02 的工作台接受 mismatch，这里仍会二次重筛或抛错。
- 该 legacy seam 对无 assembly 路径仍有价值，不能全局修改 `select()`。

### 工作台重算

- `TrustReplyWorkbenchService.assemble():1214-1313` 已完成 source、matrix、locked item、claim、version、frame 的权威重算；计划 02 将 `canonicalFactIds` 改为 `selection.sendQaRuleIds`。
- 现方法只返回 response，没有把内部 `ResolvedQaRules selection` 暴露给发送期 safety，因此需要窄的 internal verified result，不能让发送服务自行重写第三套 resolver。

### 落库写路径全集

命令 `rg -n "mailRecordQaRuleRepository\.save" src/main/kotlin` 当前恰有 3 个生产命中：

- `ManualExpertMailService.kt:92`；
- `ManualReplySendAttemptService.kt:253`；
- `AutoMailReplyService.kt:637`。

本计划只改变第二条的输入，不改其他两条。`V42__mail_record_qa_rule.sql:3-12` 定义 `(mail_record_id,qa_rule_id)` 唯一键和 ordinal；`ManualReplySendAttemptService.finalizeSuccess():202-270` 在新事务内先保存 `mail_record`，再按 payload 顺序写关联，最后将 attempt 标为 SENT。

### `mail_send_attempt` / `mail_record`

`V23:1-17` 创建 attempt 和 message-id/orcid 唯一约束；`V24:18-40` 补发送快照并让 `mail_record.mail_send_attempt_id` 唯一外键。计划不改表、不改事务，只把验证前移到 claim 之前。

## 实现方案

### 阶段 1：抽取一次性 verified assembly

在 `TrustReplyWorkbenchService` 增加 internal `VerifiedTrustReplyAssembly(response, selection)`；把现 `assemble` 主体移入 `verifyAssembly(request)`，公开 `assemble(request)` 仅返回 `.response`。两者共用同一执行，不允许先 assemble 再另行 resolve selection。

verified result 只在 JVM 内部使用，不新增 HTTP 字段；`selection` 供发送 safety 读取自然 intents/trust gap，response 提供 canonical fact ids 和已验证 item versions。

### 阶段 2：在发送入口分流事实来源

在取得 `inboundText/researchProfileSufficient` 后：

1. assembly 非空：先校验 source 为本次 `LIVE_INBOUND:id`，调用 `verifyAssembly`；校验客户端 `qaRuleIds.orEmpty()` 与 verified canonical ids 按顺序完全相等；构造无 degraded codes 的 fact resolution。
2. assembly 为空：执行现 `canonicalizeFactRuleIds`，代码与 warning 语义不变。
3. `carriesQa`：assembly 路径按 verified canonical 是否非空；legacy 路径保留“客户端提交过 qaRuleIds”的既有判据。
4. `serverSuggestedFactIds` 仍只作审计对照，不参与 canonical facts。

不要求 adopted body 与 assembly rendered body相等：运营可以编辑正文；但编辑后的最终文本必须走完整 safety。

### 阶段 3：safety 复用 verified selection

给 `collectSafetyFindings` 增加可空 `verifiedSelection`：

- assembly 路径直接使用它计算 blocking trust、intent evidence 和角色/企业提示；禁止再次调用 `qaFactSelectionService.select(canonicalFactIds)`。
- legacy 路径保持当前 strict select。
- 高风险正文校验继续以 canonical ids 调 `validatePlainText`；动作集合使用已验证 versions 推导的 operator authorization。

### 阶段 4：归档复用发送前验证结果

`archiveLiveUnsupportedAnswers` 接收已验证 response/result；仅在实际发送正文仍等于 assembly 产物时归档 operator-directed 样本。编辑正文可正常发送但不归档。删除发送成功后再次 `assemble` 的逻辑，避免前后两次解析漂移。

### 阶段 5：精确关联测试

增加从工作台 matrix 含 mismatch fact 到 `SendPayload`、`finalizeSuccess`、`mail_record_qa_rule` 的闭环测试；同时钉住 stale/tampered assembly 在 `prepareAndClaim` 前失败、无 assembly legacy 行为不变。

## 变更文件清单

| 文件 | 修改 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` | internal verified result，公开 assemble 复用 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | SMTP 前验证、事实来源分流、safety/归档复用 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt` | assembly 发送闭环、tamper/stale/safety/legacy 回归 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt` | payload canonical ids 与关联表 ordinal 精确性 |

范围：4 个文件；2 个子系统（LLM assembly、mail send）。无 DB migration、controller、前端变更。

## 验收标准

- assembly 含 intent mismatch 人工事实时，发送入口不调用 legacy semantic `select(explicitIds)`；canonical ids 原样进入 safety 和 SendPayload。
- 客户端 qaRuleIds 与 verified canonical ids 任一缺失、增加、顺序改变时，在 `prepareAndClaim` 前失败。
- source id/type、sourceVersion、matrix、locked item、evidence/version 任一 stale/tamper 时，在发送副作用前失败。
- 运营编辑 adopted body 后仍可通过正常 safety/确认发送；canonical facts 不随正文编辑改变；编辑版本不进入 unsupported-answer archive。
- `mail_record_qa_rule` 查询结果按 ordinal 严格等于 verified canonical ids；无额外自动推荐事实、无漏项、无重复键。
- 无 assembly 的手工 QA mismatch/unavailable 路径继续产生既有 degraded warning；纯人工富文本仍记录为 rich reply。
- suppression、placeholder、普通确认、强确认、action safety、attempt 幂等测试全部通过。
- 测试：
  - `mvn -q -Dtest=PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest test`
  - `git diff --check`

## 人工验收清单

1. 在 LIVE 工作台给 UNSUPPORTED request 绑定 mismatch fact，选择“按事实原文回答”，整合并采用。
2. 可选：编辑采用后的正文；执行预检和发送。
3. 确认安全告警仍正常出现并需要原有确认；发送成功。
4. 查询 outbound `mail_record` 及 `mail_record_qa_rule`，按 ordinal 对比工作台 canonical ids，必须完全一致。
5. 修改浏览器请求中的 qaRuleIds 或 assembly sourceVersion，确认请求在 SMTP attempt 创建前失败。
6. 用无 workbench assembly 的旧人工 QA 发送做回归，确认行为未变。


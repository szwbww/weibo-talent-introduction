# 人工拼装回复前缀片段（尊语/致谢/开场白/结束语）— Plan A：后端与数据

> 子计划 A（先行）。子计划 B（前端）依赖本计划交付的 API/响应字段。
> 范围口径：仅作用于「人工拼装回复」链路（`composeInOperatorOrder` 的调用方）。自动回复管线不变。

## 需求描述

- 可观察结果：运营在「人工拼装回复」发送的邮件，正文自动带上可配置的**尊语**（如 `Dear Professor,`）、可选的**致谢语**（如 `Thank you for sharing your CV.`）、**开场白**、**结束语**；这些文案可通过 API（B 计划接页面）增删改查。
- 必须不变（NOT change）：
  - 自动回复管线（`AutoMailReplyService`、`QaReplyComposer.compose`、`/auto-reply-preview`）的正文与行为完全不变。
  - 人工拼装时所选 QA 规则段落的**文本与顺序**不变（仍按 `qaRuleIds` 顺序）。
  - 外发仍走 `ComposedMail(html=true, text=plain)`（HTML 保段落）。
  - `mail_record.body` 仍持久化纯文本；`mail_record_qa_rule` ordinal 语义不变。
- 不在范围（out of scope）：
  - 前端 UI（配置页、致谢语勾选、预览取数）——见 Plan B。
  - 尊语个性化姓名解析（已定：统一 `Dear Professor,`，无占位符、无姓名）。
  - 自动回复也使用 DB frame（明确不做，避免改动自动外发）。
  - 致谢语多选（已定：单选可不选）。

## 关键不变量

### Invariant I-1: frame 单一数据源，预览==外发
- Rule: 人工拼装路径的尊语/开场白/结束语/致谢文案，唯一来源是 `reply_snippet` 表；后端发送与（B 计划的）前端预览必须取同一份，不得在人工路径出现第二处硬编码 frame。
- Applies to: `ReplySnippetService.resolveManualFrame()`、`PendingMailOperationService.sendManualComposedReply`、`UnmatchedInboundMailController.suggestComposedReply` 响应、`LlmStitchService.composeDeterministic`。
- Violation consequence: 运营按预览调内容，实际外发与预览漂移。
- 来源: K-preview-mirrors-pipeline, K-composed-reply-order-contract

### Invariant I-2: 运营规则顺序即组装契约
- Rule: 插入尊语/致谢/开场白/结束语**不得重排**所选 QA 规则段落；段落顺序恒等于 `qaRuleIds` 入参顺序。
- Applies to: `QaReplyComposer.composeInOperatorOrder`（新增 frame 参数后，sections 仍 `matches.joinToString` 原序）。
- Violation consequence: 运营调序失效，回到系统默认排序。
- 来源: K-composed-reply-order-contract

### Invariant I-3: 仅人工路径，自动路径零改动
- Rule: 尊语/致谢与 DB 来源的开场白/结束语，**只**作用于 `composeInOperatorOrder` 的调用方（人工拼装 + 其润色草稿）。`QaReplyComposer.compose`（自动管线）保留现有 `GREETING/CLOSING` 常量，行为不变。
- Applies to: `QaReplyComposer.compose`（不改签名/不读 DB）、`AutoMailReplyService`、`AutoReplyPreviewService`。
- Violation consequence: 自动外发邮件正文被意外改写，超出本需求范围。
- 来源: K-gap-items-compose-only

### Invariant I-4: 致谢语可选、单条、服务端按 id 解析
- Rule: 运营最多选 1 条致谢语；后端按 `ackSnippetId` 从 `reply_snippet` 取文本（不信任客户端传来的致谢正文），位置在尊语之后、开场白之前；未选 = 不出现致谢行。
- Applies to: `sendManualComposedReply`、`polishDraft`、frame 拼装逻辑。
- Violation consequence: 配置失真 / 注入风险 / 预览与外发不一致。
- 来源: 原创

### Invariant I-5: frame 各部分独立可选，缺省即省略且永不空占位
- Rule: SALUTATION/GREETING/CLOSING 各取「enabled 且 is_default=true」的唯一一行作为 frame；某类型无可用默认行 → 该行省略（不输出空行/占位符）。ACK 无 default 概念（多行片段库，不自动套用）。seed 须为 SALUTATION/GREETING/CLOSING 各插入一条默认启用行。
- Applies to: `ReplySnippetService.resolveManualFrame()`、`V47` seed、`ReplySnippetService` 的 default 唯一性约束（同类型设默认时清掉旧默认）。
- Violation consequence: 多默认歧义 / 空 frame 产生空段落。
- 来源: 原创

### Invariant I-6: HTML 段落保留
- Rule: 拼装最终正文各 frame 段与 sections 之间用 `\n\n` 分隔；外发仍 `mailContentService.plainTextToHtml(finalBody)` + `html=true, text=finalBody`。
- Applies to: `sendManualComposedReply`（mail 构造保持现状）、frame 拼接。
- Violation consequence: Gmail/Outlook 纯文本重排塌段。
- 来源: K-plaintext-reply-client-reflow

### Invariant I-7: 组装顺序契约
- Rule: 人工拼装最终正文顺序固定为：
  `尊语` → `致谢(可选)` → `开场白` → `sections(运营序)` → `结束语` → `自由文本(可选)`。
  单规则与多规则**采用同一 frame 顺序**（本计划有意统一：单规则也带开场白/结束语，属"新增框架"而非"改写答案文本"）。
- Applies to: `QaReplyComposer.composeInOperatorOrder`、`buildDeterministicComposedPreview`(B)、`LlmStitchService.composeDeterministic`。
- Violation consequence: 预览/外发/润色三处顺序漂移。
- 来源: 原创（注意：这会改变"单规则原本只有 replyBody"的旧行为——属本需求预期内的框架增强，已在"必须不变"中排除"答案文本/顺序"，未排除"加框架"）。

## 现状审计

### reply_snippet 表（新建）
- 当前不存在。最大迁移版本 `V46`，新迁移用 `V47`。
- 计划字段：`id`(PK)、`snippet_type`(SALUTATION/ACK/GREETING/CLOSING)、`content`(TEXT)、`display_order`(INT)、`is_default`(BOOL)、`enabled`(BOOL)、`created_at`、`updated_at`。
- 写路径（本计划新增，全部经 `ReplySnippetService`）：create / update / setEnabled / setDefault / delete。
- 读路径：`resolveManualFrame()`（取各类型默认 + ACK 列表）、admin list。

### QaReplyComposer（`qa/service/QaReplyComposer.kt`）
- `const val GREETING/CLOSING`（:6-8）：被 `compose`（自动序，:33）与 `composeInOperatorOrder`（运营序，:55）共用。
- 写/读：纯函数对象，无状态。
- 交互点：`compose`（自动，I-3 不动）与 `composeInOperatorOrder`（人工，本计划改）共享常量 → **不能删常量**；`compose` 继续用常量，`composeInOperatorOrder` 改为接收 frame 参数。
- `composeInOperatorOrder` 当前：单规则返回裸 `replyBody`；多规则 = `GREETING + sections + CLOSING`。

### composeInOperatorOrder 的调用方（人工 frame 消费者）
1. `PendingMailOperationService.sendManualComposedReply`（`mail/service/...:280`）— 外发真身。`appendFreeText`(:370) 追加自由文本；mail 构造 :295-301 `plainTextToHtml + html=true`。
2. `LlmStitchService.composeDeterministic`（`llm/service/LlmStitchService.kt:42-57`）— 润色的确定性回退；另有 `buildRuleSegments`(:59-65) 仅供 LLM prompt（**不**含 frame，保持）。
3. （B 计划）前端 `buildDeterministicComposedPreview`（`app.js:4652`）+ 常量 `QA_COMPOSE_GREETING/CLOSING`(`app.js:56-57`)。

### suggest / 请求 DTO（`mail/controller/UnmatchedInboundMailController.kt`）
- `suggestComposedReply`(:208) → `CompositionSuggestResult.toResponse`(:520) → `ComposedReplySuggestResponse`(:408)。本计划在响应加 `salutation/greeting/closing/ackOptions`。
- `ComposedReplyRequest`(`PendingMailOperationService.kt:461-467`) 加 `ackSnippetId: Long?`。
- `PolishDraftRequest`(`LlmStitchService.kt:73-76`) 加 `ackSnippetId: Long?`；polish 端点(:219-236) 透传。
- `sendManualComposedReply` 的 `operatorActionLog.after`(:346-355) 增记 `ackSnippetId`（JSON map，非表字段）。

### 交互点
- IP-1：`reply_snippet`(写=admin/seed) × (读=send/suggest/polish) —— frame 单源（I-1/I-5）。
- IP-2：`composeInOperatorOrder` 被 send 与 polish 两路调用 —— frame 参数须两路一致（I-1/I-7）。
- IP-3：自动管线共享 `QaReplyComposer.GREETING/CLOSING` —— 保留常量、不改 `compose`（I-3）。

## 实现方案

### 阶段 1：数据与 snippet 服务（子系统①）

**Task 1.1 — `V47__create_reply_snippet.sql`**（I-5）
- 建表（字段见审计）。
- seed：SALUTATION 默认 `Dear Professor,`；GREETING 默认 = 现 `QaReplyComposer.GREETING` 原文；CLOSING 默认 = 现 `CLOSING` 原文（保持人工外发与现状一致）；ACK 三条样例（CV/materials/prompt reply），`is_default=false`。
- 默认行 `is_default=true, enabled=true`。注意中文/特殊字符编码（参考 V44/V45 修复经验，文件存 UTF-8）。

**Task 1.2 — `ReplySnippet.kt`（domain）**
- Spring Data JDBC：immutable `data class`，`@Table("reply_snippet")`、`@Id id: Long?`。

**Task 1.3 — `ReplySnippetRepository.kt`**
- `CrudRepository<ReplySnippet, Long>`；显式查询：`findBySnippetTypeAndEnabledTrueOrderByDisplayOrderAsc`、`findBySnippetTypeAndEnabledTrueAndIsDefaultTrue`（取默认；多条时取 display_order 最小，防御性）。

**Task 1.4 — `ReplySnippetService.kt`**（I-1/I-4/I-5）
- `resolveManualFrame(): ManualReplyFrame`（salutation?/greeting?/closing? 文本 + ackOptions: List<(id,content)>）。
- `resolveAck(ackSnippetId: Long?): String?`（按 id 取 ACK 且 enabled，否则 null/报错择一——选 null 容错）。
- CRUD：create/update/setEnabled/delete + `setDefault` 时同类型清旧默认（I-5 唯一性，事务内）。

**Task 1.5 — `ReplySnippetController.kt`（admin REST，供 B 计划）**
- `/api/reply-snippets`：`GET`（按 type 分组或全量）、`POST`、`PUT/{id}`、`POST/{id}/enable`、`POST/{id}/disable`、`POST/{id}/default`、`DELETE/{id}`。仿 `QaRuleManagementController` 风格（请求/响应 DTO + toResponse）。

### 阶段 2：人工拼装链路接入 frame（子系统②）

**Task 2.1 — `QaReplyComposer.composeInOperatorOrder` 增 frame 参数**（I-2/I-3/I-7）
- 新签名（保留旧 `compose` 不动）：
  `composeInOperatorOrder(matches, salutation: String?, ack: String?, greeting: String?, closing: String?)`。
- 拼装：`[salutation, ack, greeting, sections, closing]` 过滤 null/空后 `joinToString("\n\n")`；sections 恒为 `matches.joinToString("\n\n"){ formatSection }`（原序，I-2）。单/多规则同 frame（I-7）。
- `replySubject` 选取逻辑不变。

**Task 2.2 — `PendingMailOperationService.sendManualComposedReply` 接 frame**（I-1/I-4/I-6/I-7）
- 注入 `ReplySnippetService`；`ackSnippetId` 进方法签名与 `ComposedReplyRequest`。
- 取 `frame = resolveManualFrame()`、`ack = resolveAck(ackSnippetId)`，传入新 composer 签名。
- `composedWithFreeText`/`finalBody`/`edited`/mail 构造保持现状（I-6）。
- `operatorActionLog.after` 增记 `ackSnippetId`。

**Task 2.3 — `LlmStitchService` 接 frame**（I-1/I-7）
- 注入 `ReplySnippetService`；`composeDeterministic` 用与 2.1 相同 frame 顺序（salutation/ack/greeting/sections/closing + freeText）。
- `PolishDraftRequest` 加 `ackSnippetId`；`polishDraft` 透传到确定性回退。`buildRuleSegments`（LLM prompt 段落）**不**加 frame（保持，LLM 自行成文，结果作为 draft 仍可被运营在预览里编辑）。

**Task 2.4 — `UnmatchedInboundMailController` DTO/映射**（I-1/I-4）
- `ComposedReplySuggestResponse` 增 `salutation/greeting/closing: String?` 与 `ackOptions: List<AckOptionResponse(id, content)>`；`toResponse` 从 `ReplySnippetService.resolveManualFrame()` 填充（controller 注入该服务）。
- `ComposedReplyRequest`、`PolishDraftRequest` 增 `ackSnippetId`；`sendComposedReply`/`polishComposedReply` 透传。

## 变更文件清单（9 ≤ 10）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V47__create_reply_snippet.sql` | 新建表 + seed |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/.../reply/domain/ReplySnippet.kt` | 新建 domain |
| 3 | `.../reply/repository/ReplySnippetRepository.kt` | 新建 repo |
| 4 | `.../reply/service/ReplySnippetService.kt` | 新建 service + frame provider |
| 5 | `.../reply/controller/ReplySnippetController.kt` | 新建 admin REST |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaReplyComposer.kt` | `composeInOperatorOrder` 增 frame 参数（`compose` 不动）|
| 7 | `.../mail/service/PendingMailOperationService.kt` | send 接 frame + `ackSnippetId` + `ComposedReplyRequest` |
| 8 | `.../llm/service/LlmStitchService.kt` | 确定性回退接 frame + `PolishDraftRequest.ackSnippetId` |
| 9 | `.../mail/controller/UnmatchedInboundMailController.kt` | suggest 响应增 frame/ackOptions；请求 DTO 透传 `ackSnippetId` |

> 包路径 `reply` 模块名最终落地时与现有 `com.weibo.talentintroduction.<module>` 风格一致即可。

## 验收标准

- I-1：单测——同一组 snippet 默认下，`composeInOperatorOrder`(send 取的 frame) 与 suggest 响应返回的 `salutation/greeting/closing` 文本一致。
- I-2：`QaReplyComposerTest` 新增——逆序 `qaRuleIds`（[B,A]）拼出的 sections 中 Body B 在 Body A 前，且 frame 不影响该顺序。
- I-3：`QaReplyComposer.compose`（自动序）测试保持原断言通过；grep 确认 `AutoMailReplyService`/`AutoReplyPreviewService` 未引用新 frame。
- I-4：`PendingMailOperationServiceTest`——传 `ackSnippetId` → 正文尊语后紧跟该 ACK 文本；传 null → 无 ACK 行；传非法 id → 容错（无 ACK，不抛）。
- I-5：`ReplySnippetServiceTest`——同类型 `setDefault` 后旧默认被清；某类型无默认时 `resolveManualFrame` 该字段为 null；seed 后三类各有 1 默认。
- I-6：`PendingMailOperationServiceTest`——发送 mail `html=true`、`text==finalBody`、frame 段间含 `\n\n`。
- I-7：单测断言最终正文顺序 = 尊语→致谢→开场白→sections→结束语→自由文本（单规则也含开场白/结束语）。
- 集成：`mvn test` 全绿；`mvn clean package`（zulu-11）通过。

## 自检清单
- [x] 关键不变量含每个新字段/状态 ≥1 条
- [x] 现状审计列全 `composeInOperatorOrder` 写/读路径（grep 验证）
- [x] 无 task 引入未被不变量覆盖的写路径
- [x] 文件数 9 ≤ 10
- [x] 子系统 2（数据/snippet + mail-compose 接线）≤ 2
- [x] 每个 task 标注治理不变量编号
- [x] 验收每条不变量 ≥1 检查
- [x] 文件清单无"等/相关文件"
- [x] out of scope 明确（前端、自动管线、姓名占位）
- [x] Phase 0 知识全部被用或显式取舍（K-composed-reply-order-contract/K-preview-mirrors-pipeline/K-plaintext-reply-client-reflow/K-gap-items-compose-only 均映射到 I-1..I-7）
- [x] 保存至 docs/plans/2026-06-28/

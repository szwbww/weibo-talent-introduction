# 计划 CV-2：变体 CRUD 贯通与 CUSTOM 片段类型（cv-2-variant-crud-and-custom）

> 系列：变体机制二次重构 2/6。**依赖 CV-1 已合入**（content_variant 表 + ContentVariantService）。
> 决策：变体随 QA 规则/回复片段一起编辑保存；回复片段保留四类型并**新增 CUSTOM（自定义内容）**，CUSTOM 不参与人工回复框架与默认片段逻辑。

## 需求描述

可观测结果：
1. QA 规则、回复片段的创建/更新 API 携带 `variants: List<String>`，保存时全量替换该 owner 的变体；详情/列表 API 返回变体列表。
2. 删除 QA 规则/回复片段时级联删除其变体。
3. 片段类型新增 `CUSTOM`；CUSTOM 片段可被模板内容块和人工组装引用，但不可设默认、不进入人工回复框架（尊语/开场白/结束语）与 ACK 选项。

不得改变：
- 四个既有片段类型的一切行为（resolveManualFrame、resolveAck、isDefault 每类型唯一、ACK 不可默认）。
- QA 规则既有字段的校验与保存行为（replySubject、keyword、priority 等）。
- 变体解析语义（CV-1 I-1，本计划只写不读）。

超出范围（明确不做）：
- 回复路径消费变体（CV-3）；前端编辑 UI（CV-4）；旧 variantGroup 字段删除（CV-6）；变体独立 CRUD 端点（变体无独立生命周期，随 owner 走）。

## 关键不变量

### Invariant I-1: 变体全量替换写入
- Rule: 保存 owner 时 `ContentVariantService.replaceForOwner(ownerType, ownerId, variants)`：先 `deleteByOwnerTypeAndOwnerId` 再按输入顺序落库（variant_order = index*10+10，enabled=true）；与 owner 保存同一事务。删除 owner 时同法级联删。
- Applies to: QaRuleManagementService.create/update/delete、ReplySnippetService.create/update/delete。
- Violation consequence: 孤儿变体被 resolveBody 捞出，发出已删内容。
- 来源: original

### Invariant I-2: 变体内容校验
- Rule: 每条变体 trim 后非空、通过 `mailVariableService.requireValidPlaceholders`、与主体及其他变体 trim 后互不重复；违反抛 IllegalArgumentException 且整体不落库。
- Applies to: replaceForOwner（校验收口在 ContentVariantService，两类 owner 共用）。
- Violation consequence: 非法占位符/重复内容发给专家。
- 来源: original（对齐旧 validateSubjectVariants 规则）

### Invariant I-3: DTO 七层贯通
- Rule: `variants` 字段贯通 Request → toCommand → Command → create/update → replaceForOwner → Detail/Response ← listByOwner；update 时传空数组 = 清空变体（不 fallback 旧值）。
- Applies to: QaRuleManagementController/Service、ReplySnippetController/Service 全链。
- Violation consequence: create 路径静默丢失或清空失效。
- 来源: K-variant-pool-dto-chain

### Invariant I-4: CUSTOM 类型隔离
- Rule: `SnippetType` 增 `CUSTOM`；CUSTOM 片段 `isDefault` 恒 false（保存时 require 拒绝，同 ACK 现有模式 ReplySnippetService:54/:82/:108）；`resolveManualFrame`/`resolveAck` 按类型名查询，天然不含 CUSTOM——不得修改这两个方法。
- Applies to: ReplySnippetService.create/update/setDefault。
- Violation consequence: CUSTOM 内容混入人工回复骨架。
- 来源: original（决策 6）

## 现状审计

### qa_rule（MySQL）
- 写路径: `QaRuleManagementService`（create/update/delete/enable，Controller DTO :233 QaRuleCreateRequest / :260 QaRuleUpdateRequest，均含 replyBody）；迁移种子 V63/V65。
- 读路径（replyBody）: QaMatchService:67 组装、PendingMailOperationService:336、LlmStitchService:61、AutoMailReplyService、AutoReplyPreviewService、MailComposeTemplateService.resolveBlocks、UnmatchedInboundMailController、QaRuleManagementService（CRUD 回显）。变体消费在 CV-1/CV-3，本计划只管写。
- Interaction points: 本计划写入变体 × CV-1 resolveBody 读（模板块路径当即生效——CV-2 合入后模板内 QA 块立刻开始轮换，验收含此场景）。

### reply_snippet（MySQL）
- 写路径: ReplySnippetService.create (:47) / update (:76) / setEnabled / setDefault (:106) / delete (:118)；Controller DTO 含 snippetType/variantGroup（variantGroup 留死，CV-6 删）。
- 读路径: resolveManualFrame (:23，按 SALUTATION/GREETING/CLOSING+ACK 类型查)、resolveAck (:36)、listAll/listByType、MailComposeTemplateService 片段块。
- `SnippetType` enum (:145)：SALUTATION/ACK/GREETING/CLOSING；`isDefault` 约束：ACK 禁默认（:54/:82/:108）、每类型唯一（clearOtherDefaults :129）。
- Interaction points: CUSTOM 入 enum × 前端类型面板（CV-4 注册，本计划后端先行，前端未注册前 CUSTOM 仅 API 可建——可接受中间态）。

### content_variant
- 写路径: 本计划新增 replaceForOwner（唯一写入口）。CV-1 时写路径为 0，本计划为 1。
- 读路径: CV-1 resolveBody/listByOwner。

## 实现方案

### T1 — replaceForOwner + 校验（I-1, I-2）
`ContentVariantService.kt`：新增 `@Transactional fun replaceForOwner(ownerType: String, ownerId: Long, mainBody: String, variants: List<String>)`（含 I-2 校验）与 `fun deleteForOwner(ownerType, ownerId)`；注入 MailVariableService。

### T2 — QA 规则贯通（I-1, I-3）
`QaRuleManagementController.kt`：Create/Update Request 增 `variants: List<String> = emptyList()`，Response 增 `variants`；`QaRuleManagementService.kt`：create/update 保存后 replaceForOwner(QA_RULE, id, replyBody, variants)；delete 前 deleteForOwner；详情组装 listByOwner。

### T3 — 片段贯通 + CUSTOM（I-1, I-3, I-4）
`ReplySnippetService.kt`：SnippetType 增 CUSTOM；create/update require CUSTOM 非默认；命令增 variants 并接 replaceForOwner/deleteForOwner；`ReplySnippetController.kt`：Request/Response 增 variants。

### T4 — 测试（全不变量）
`QaRuleManagementServiceTest.kt`、`ReplySnippetServiceTest.kt`（不存在则新建）：I-1 全量替换与级联删、I-2 五类非法拒绝、I-3 create 带变体/update 清空、I-4 CUSTOM 禁默认 + resolveManualFrame 不含 CUSTOM。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt | T1 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt | T2 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt | T2 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt | T3 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt | T3 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt | T4 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt | T4 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantServiceTest.kt | T4（replaceForOwner 用例） |

文件数 8 ≤ 10；子系统 2（qa、reply，共用 variant service）；新增既有表字段 0。

## 验收标准

- I-1: 用例断言替换后旧变体消失、顺序=输入序；delete owner 后 content_variant 零残留（count 断言）。
- I-2: 空串/非法占位符/与主体重复/变体互重 → 拒绝且 owner 与变体均未落库（事务回滚断言）。
- I-3: create 带 2 变体 → 详情返回 2；update 传 [] → 返回 0；grep 七层每层有 variants 字段。
- I-4: CUSTOM isDefault=true 保存抛错；resolveManualFrame 结果无 CUSTOM 内容（造 CUSTOM 数据断言）；`git diff` 确认 resolveManualFrame/resolveAck 函数体零改动。
- `mvn test` 全绿。

## 人工验收清单

### A-1: QA 规则变体保存与回显（覆盖需求第 1 条、I-1/I-3）
- 前置条件: 任一启用 QA 规则；本阶段无 UI，用 curl/Postman。
- 操作步骤: 1) PUT `/api/qa-rules/{id}`（现行管理端点）body 在原字段基础上加 `"variants": ["Alt body one ${senderName}", "Alt body two"]`；2) GET 详情；3) 再 PUT `"variants": []`；4) 再 GET。
- 预期结果: 步骤 2 返回 variants 恰为两条且顺序一致；步骤 4 返回 variants 为空数组；`SELECT count(*) FROM content_variant WHERE owner_type='QA_RULE' AND owner_id={id}` 依次为 2、0。

### A-2: 非法变体拒绝（覆盖 I-2）
- 前置条件: 同 A-1。
- 操作步骤: PUT variants 含 `"${不存在变量}"`；再 PUT variants 含与 replyBody 完全相同的文本。
- 预期结果: 两次均返回 4xx 与明确中文错误信息；GET 详情变体保持上一次合法值（未部分写入）。

### A-3: CUSTOM 类型隔离（覆盖需求第 3 条、I-4）
- 前置条件: POST `/api/reply-snippets` 建 `{"snippetType":"CUSTOM","content":"Custom paragraph.","isDefault":false,...}`。
- 操作步骤: 1) 再 POST 同 body 但 `"isDefault": true`；2) 打开人工回复界面（未匹配来信 → 人工回复），查看骨架与致谢语选项。
- 预期结果: 步骤 1 返回 4xx（CUSTOM 不可默认）；步骤 2 骨架的尊语/开场白/结束语与 ACK 下拉均不含 "Custom paragraph."。

### A-4: 片段变体进模板块即刻生效（覆盖 interaction point、CV-1 I-1）
- 前置条件: 某片段被模板内容块引用；PUT 该片段带 1 条变体。
- 操作步骤: 对 3 名不同专家手工发送该模板。
- 预期结果: 该段落出现主体与变体两种文案中的至少两种；同一专家重发恒同。

### A-5: 既有四类型回归（覆盖 must-NOT-change 第 1 条）
- 前置条件: 既有默认尊语/开场白/结束语片段不动。
- 操作步骤: 打开人工回复界面走一次完整组装（选 QA 规则 + ACK）并发送。
- 预期结果: 骨架取值、ACK 选项、发送成功与改动前一致；默认片段仍每类型唯一（设另一条为默认后旧默认自动取消）。

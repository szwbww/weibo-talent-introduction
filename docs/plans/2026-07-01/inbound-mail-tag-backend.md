# 来信标签系统 —— 后端（Plan A / 2）

> 本功能拆成两个可独立验收的计划：
> - **Plan A（本文件）**：标签数据模型、自动打标、人工增删、置灰读取、全量统计 API。
> - **Plan B**：`inbound-mail-summary-frontend.md`，来信汇总前端页面（依赖 Plan A 的接口）。
>
> 拆分原因：两者合计变更 11 个文件，超过单计划 ≤10 的硬上限，且前端可在后端 API 就绪后独立开发/验收。

## 需求描述

**可观察结果**：
- 每封「专家来信」（`inbound_mail_processing` 行）可携带一组标签。标签有两类：QA 标签（引用某条 `qa_rule`）与自定义标签（自由文本）。
- 系统处理新来信时，自动为该来信打上「全部能命中的 QA」标签（决策#1：raw 命中集，未经 supersede 压缩）。
- 运营可对任意来信（含历史来信）手动**新增/删除** QA 标签和自定义标签，并有一个「自动添加 QA 标签」按钮，一键补齐所有能命中的 QA 标签。
- 若某 QA 规则后期被停用（`qa_rule.enabled=false`，决策#2）或物理删除，其对应标签在读取时标记为「失效（置灰）」，但**不删除、不丢失文字**。
- 提供全量（决策#3：跨所有来信，不受列表过滤影响）标签统计：数量排行 + 占比。

**不可改变**：
- `AutoMailReplyService` 现有邮件处理主流程的行为、状态流转、自动回复结果。打标签必须是旁路副作用，失败不得影响主流程（I-5）。
- `mail_record_qa_rule` 表及其审计语义（外发回复用了哪些 QA）——与本功能无关，不复用、不修改。
- `qa_rule` 只有软删（`enabled` 开关），本计划不引入物理删除。

**范围外（本计划显式不做）**：
- 前端页面（Plan B）。
- 历史来信的批量回填打标（不做全库 backfill；历史来信靠「自动添加 QA 标签」按钮逐封补齐）。
- 标签增删写入 `operator_action_log`（仅在标签行上记录 `created_by`，不接 `OperatorActionType` 枚举，避免扩散改动）。
- QA 规则删除时联动清理/迁移标签（置灰在读取时计算，无需联动写）。

## 关键不变量

### Invariant I-1: 标签双存储（引用 + 快照）
- Rule: QA 标签行必须**同时**写 `qa_rule_id`（引用，用于置灰判定）和 `label`（快照，写入时取 `qa_rule.displayName`，为空则回退规则关键词首段/`规则#<id>`）。自定义标签行 `tag_type='CUSTOM'` 且 `qa_rule_id` 必为 `NULL`，`label` 为运营输入文本。
- Applies to: `InboundMailTagService.autoApplyQaTags` / `addQaTag` / `addCustomTag` 所有写入路径。
- Violation consequence: 只存引用 → 规则物理删后标签无法显示文字；只存快照 → 无法判定置灰。
- 来源: original

### Invariant I-2: 置灰只读时计算，不落库、不删标签
- Rule: 标签 `active`（是否置灰）在**读取时**计算：`active = (tag_type='CUSTOM') || (对应 qa_rule 存在 且 enabled=true)`。`qa_rule.enabled=false` 即 `active=false`（决策#2）。规则被删/停用**不得**触发删除或修改 `inbound_mail_tag` 行。
- Applies to: `InboundMailTagService.listTags` / `listTagsBatch` / `stats`；QA 规则停用/删除路径（`QaRuleManagementService.setRuleEnabled` 等）**不得**改动标签表。
- Violation consequence: 历史标签丢失、统计失真。
- 来源: original

### Invariant I-3: 「可命中 QA」= 全部 raw 命中
- Rule: 自动打标与「自动添加」按钮所用的命中集，是 `QaMatchService` 对来信正文的**全部 raw 命中规则集**（即 `matchRule` 逐条命中，**不经** `applySupersede` 压缩），去重。不使用 `match()` 返回的 `matchedRuleIds`（那是 supersede 后的发送集）。
- Applies to: 新增 `QaMatchService.matchAllRuleIds(body)`；`InboundMailTagService.autoApplyQaTags` 调用它。
- Violation consequence: 概览型复合规则会吞掉子规则命中，标签不全（对照 K-overview-gap-supersede：supersede 只应用于发送，不应用于标签全集）。
- 来源: original（关联 K-overview-gap-supersede）

### Invariant I-4: autoApply 幂等
- Rule: `autoApplyQaTags` 对同一 `(inbound_processing_id, qa_rule_id)` 已存在标签时**跳过**，不重复插入（DB 唯一键 + 插入前查重双保险）。已被运营删除的 AUTO 标签，再次点「自动添加」按钮时会被重新加回（按钮语义即「补齐全部命中」）——这是预期行为，不引入「已删除」墓碑标记。
- Applies to: `InboundMailTagService.autoApplyQaTags`；DB 唯一索引 `uk_inbound_qa (inbound_processing_id, qa_rule_id)`。
- Violation consequence: 重复标签、UI 重复 chip。
- 来源: original

### Invariant I-5: 自动打标为 best-effort，不阻断主流程
- Rule: 在 `AutoMailReplyService` 内触发 `autoApplyQaTags` 必须用 `runCatching` 包裹，异常只记日志，绝不向上抛、绝不回滚邮件处理。仅在来信正文（`cleanedBody ?: body`）非空时触发。
- Applies to: `AutoMailReplyService.confirmProcessed`、`confirmManualReviewWithBody` 两个 `inbound_mail_processing` 落库点。
- Violation consequence: 打标异常导致收信/自动回复失败，属严重回归。
- 来源: original（关联 K-process-single-all-callers）

### Invariant I-6: 统计全量
- Rule: `/tags/stats` 聚合覆盖 `inbound_mail_tag` 全表（跨所有来信、所有账号、所有时间），不受来信列表的标签/时间过滤影响（决策#3）。QA 标签按 `qa_rule_id` 归并（展示名取当前 `qa_rule.displayName`，失效仍计入并标记）；自定义标签按 `label` 归并。
- Applies to: `InboundMailTagRepository` 聚合查询、`InboundMailTagService.stats`。
- Violation consequence: 排行/占比与「全量」语义不符。
- 来源: original

## 现状审计

### `inbound_mail_processing`（MySQL，Spring Data JDBC）
- Schema: 见 `mail/domain/InboundMailProcessing.kt`。含 `id, expertContactId(nullable), messageId, subject, body, cleanedBody(nullable), receivedAt, processStatus, reasonType, senderAccountCode`。
- Write paths（grep `inboundMailProcessingRepository.save` / `InboundMailProcessing(`）：
  1. `AutoMailReplyService.confirmProcessed()` (:1045) —— 新建行，所有 PROCESSED/MANUAL_REVIEW（经 `confirmManualReview` 委派）来信的主落库点。QA 自动回复路径 (:618) 调用它，**未传 `cleanedBody`（存 null）**。
  2. `AutoMailReplyService.confirmManualReviewWithBody()` (:1010) —— 新建行，带 `cleanedBody`，用于 QA_NO_MATCH / QA_GAP / 退订等人工复核。
  3. `UnmatchedInboundMailService.bindToContact()` (:177)、`markResolved()` (:217) —— `record.copy(...)` 仅改状态，**非新建**。
  4. `PendingMailOperationService.markResolved()` (:459) —— `record.copy(...)` 仅改状态，**非新建**。
- Read paths: `UnmatchedInboundMailService.listManualReviewQueue`、`getDetail`；`InboundMailProcessingRepository.listInboundActivity/countInboundActivity`（按 `received_at` 区间分页，已存在，Plan B 汇总列表将复用/扩展）。
- Interaction points: 新建行的两个 sink（1、2）即自动打标挂载点（I-5）。状态更新（3、4）不触发打标（行已存在，打标已在创建时完成或由按钮补齐）。

### `qa_rule`（MySQL）
- Schema: `qa/domain/QaRule.kt`，含 `id, displayName(nullable), keywords, enabled`。只软删（`QaRuleManagementService.setRuleEnabled` 置 `enabled`）。无物理删除路径。
- Read paths（本计划用）：`QaRuleRepository.findById`（取快照 label、置灰判定）、`findAllEnabledOrdered`（匹配）。
- Interaction point: 置灰依赖 `qa_rule.enabled`（I-2）。停用规则的写路径 `setRuleEnabled` 不需改动。

### `QaMatchService`
- `match(body)`：返回 `matchedRuleIds`（**supersede 后**）——不可用于标签全集（I-3）。
- `suggestComposition(body)`：返回 `suggestedRuleIds`（supersede 后）、`matchedCategoryIds`（raw 分类）——无 raw 规则 id 全集。
- 私有 `matchRule/ruleMatches/normalize/parseKeywords` 可复用。需**新增**公开 `matchAllRuleIds(body): List<Long>` 暴露 raw 命中全集（I-3）。

### `mail_record`（关联，仅 Plan B 用到，本计划只读认知）
- 来信同时写一条 `MailRecord(direction="INBOUND")`（`AutoMailReplyService` :266 等），与 `inbound_mail_processing` 通过 `messageId` 对应。Plan B 详情线程用它。

## 实现方案

### 阶段 1：数据模型
**Task 1.1 迁移 `V53__inbound_mail_tag.sql`**（I-1、I-2、I-4）
```sql
CREATE TABLE inbound_mail_tag (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    inbound_processing_id BIGINT       NOT NULL,
    tag_type              VARCHAR(16)  NOT NULL,          -- QA | CUSTOM
    qa_rule_id            BIGINT       NULL,              -- 仅 QA
    label                 VARCHAR(255) NOT NULL,          -- 快照
    source                VARCHAR(16)  NOT NULL,          -- AUTO | MANUAL
    created_by            VARCHAR(64)  NULL,
    created_at            DATETIME     NOT NULL,
    UNIQUE KEY uk_inbound_qa (inbound_processing_id, qa_rule_id),
    KEY idx_inbound (inbound_processing_id),
    KEY idx_qa_rule (qa_rule_id),
    KEY idx_label (label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
- `uk_inbound_qa`：MySQL 唯一索引允许多个 NULL，故不影响 CUSTOM（其 `qa_rule_id=NULL`）；CUSTOM 去重在 Service 层按 `(inbound_processing_id, label)` 校验。
- 迁移版本确认：现有最大为 `V52`（`git status` 中 `V52__qa_trust_and_gap_rule_optimization.sql`），执行前 grep `db/migration` 复核实际最大版本号，若已占用则顺延。

**Task 1.2 领域类 `InboundMailTag.kt`**（`mail/domain/`）
```kotlin
@Table("inbound_mail_tag")
data class InboundMailTag(
    @Id val id: Long? = null,
    val inboundProcessingId: Long,
    val tagType: String,           // QA | CUSTOM
    val qaRuleId: Long? = null,
    val label: String,
    val source: String,            // AUTO | MANUAL
    val createdBy: String? = null,
    val createdAt: LocalDateTime? = null
)
```

**Task 1.3 仓库 `InboundMailTagRepository.kt`**（`mail/repository/`，I-4、I-6）
- `findAllByInboundProcessingIdOrderByIdAsc(id): List<InboundMailTag>`
- `findAllByInboundProcessingIdIn(ids): List<InboundMailTag>`（列表批量渲染）
- `existsByInboundProcessingIdAndQaRuleId(id, qaRuleId): Boolean`（I-4 查重）
- `existsByInboundProcessingIdAndTagTypeAndLabel(id, "CUSTOM", label): Boolean`（自定义去重）
- 全量统计（I-6），`@Query`：
  - QA 排行：`SELECT qa_rule_id, COUNT(*) FROM inbound_mail_tag WHERE tag_type='QA' GROUP BY qa_rule_id`
  - CUSTOM 排行：`SELECT label, COUNT(*) FROM inbound_mail_tag WHERE tag_type='CUSTOM' GROUP BY label`
  - 用轻量投影 `data class TagCount(...)`。

### 阶段 2：匹配能力
**Task 2.1 `QaMatchService.matchAllRuleIds`**（修改 `QaMatchService.kt`，I-3）
```kotlin
fun matchAllRuleIds(messageBody: String): List<Long> {
    val normalizedBody = normalize(messageBody)
    return qaRuleRepository.findAllEnabledOrdered()
        .mapNotNull { rule -> matchRule(rule, normalizedBody)?.let { rule.id } }
        .distinct()
}
```
- 复用现有私有 `matchRule`/`normalize`。**不**调用 `applySupersede`（I-3）。仅命中 `enabled=true` 规则（`findAllEnabledOrdered`），符合语义。

### 阶段 3：标签服务
**Task 3.1 `InboundMailTagService.kt`**（`mail/service/`，I-1/I-2/I-4/I-6）
- 依赖：`InboundMailTagRepository`、`InboundMailProcessingRepository`、`QaRuleRepository`、`QaMatchService`。
- `autoApplyQaTags(inboundProcessingId: Long, body: String?, createdBy: String? = null): Int`（I-3/I-4）：
  - `body` 空白 → 返回 0。
  - `matchAllRuleIds(body)`；逐条：若 `!existsByInboundProcessingIdAndQaRuleId` 则取 `qaRuleRepository.findById` 生成 label 快照（`displayName ?: keywords.substringBefore(",").trim().ifBlank{"规则#$id"}`），插入 `tag_type=QA, source=AUTO/或传入 source`。返回新增数。
  - 供 pipeline（source=AUTO）与「自动添加」按钮复用（按钮也用 AUTO——都是命中标签）。
- `addQaTag(inboundProcessingId, qaRuleId, operator): InboundMailTag`（I-1）：校验 rule 存在；查重；label 快照；`source=MANUAL`。
- `addCustomTag(inboundProcessingId, label, operator): InboundMailTag`（I-1）：`label` trim 非空；按 `(id,label)` 去重；`tag_type=CUSTOM, qa_rule_id=null, source=MANUAL`。
- `deleteTag(tagId)`：直接删。
- `listTags(inboundProcessingId): List<TagView>`（I-2）：读标签 + 批量取相关 `qa_rule` 的 `id→enabled` 映射；`active` 按 I-2 计算；QA 标签的展示名优先用**当前** `qa_rule.displayName`（存在时），失效则用快照 `label`。
- `listTagsBatch(ids): Map<Long, List<TagView>>`（Plan B 列表用）。
- `stats(): TagStatsResult`（I-6）：合并 QA/CUSTOM 排行；QA 项展示名取当前 displayName（失效标 `active=false`）；按 count 降序；附总数用于占比。

`TagView(tagId, tagType, qaRuleId, label, source, active)`；`TagStatsResult(items: List<TagStatItem>, total)`；`TagStatItem(tagKey, label, tagType, count, active)`，`tagKey`：QA=`qa:<ruleId>`，CUSTOM=`custom:<label>`。

### 阶段 4：pipeline 自动打标挂载（I-5）
**Task 4.1 修改 `AutoMailReplyService.kt`**
- 构造注入 `InboundMailTagService`（旁路依赖，无环）。
- 在 `confirmProcessed()` 落库 `saved = repository.save(...)` 后（需将该方法内 save 结果接住）：
  ```kotlin
  runCatching {
      val tagBody = cleanedBody ?: body ?: received.body
      if (!tagBody.isNullOrBlank()) saved.id?.let { inboundMailTagService.autoApplyQaTags(it, tagBody) }
  }.onFailure { log.warn("auto tag failed for inbound ${saved.id}", it) }
  ```
- 在 `confirmManualReviewWithBody()` 同样处理（已有 `saved` 与 `cleanedBody`）。
- 说明：QA 自动回复路径 `confirmProcessed` 未传 `cleanedBody`，此处回退 `received.body`（全文）匹配，可能略多命中；运营可用按钮或删除修正，可接受（I-4/I-5）。**不**改动现有 `confirmProcessed` 各调用点入参，控制影响面。

### 阶段 5：接口（供 Plan B）
**Task 5.1 `InboundMailProcessingRepository.kt` 增查询**（I-6 列表过滤，Plan B 用）
- 汇总列表（按标签过滤 + 时间区间 + 分页）：
```sql
SELECT p.* FROM inbound_mail_processing p
WHERE p.received_at >= :from AND p.received_at < :to
  AND (:qaRuleId IS NULL OR EXISTS (SELECT 1 FROM inbound_mail_tag t
        WHERE t.inbound_processing_id = p.id AND t.qa_rule_id = :qaRuleId))
  AND (:label IS NULL OR EXISTS (SELECT 1 FROM inbound_mail_tag t2
        WHERE t2.inbound_processing_id = p.id AND t2.tag_type='CUSTOM' AND t2.label = :label))
ORDER BY p.received_at DESC LIMIT :limit OFFSET :offset
```
+ 对应 `COUNT(*)` 变体。（`tagKey` 由 Controller 解析为 `qaRuleId` 或 `label` 之一传入）

**Task 5.2 `InboundMailSummaryController.kt`**（`mail/controller/`，`/api/inbound-summary`）
- `GET /mails?tagKey=&from=&to=&pageSize=&pageOffset=`：解析 `tagKey`→(qaRuleId|label)；查列表 + 总数；批量 `listTagsBatch`；带专家名（`expertContactRepository.findAllById`）。返回行（含 `inboundId, fromEmail, subject, receivedAt, messageId, expertContactId, expertName, processStatus, tags[]`）。
- `GET /mails/{inboundId}/thread`：取来信记录；若有 `expertContactId`，`mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc` 组线程（direction/subject/body(cleanedBody 回退)/时间/messageId），标出 `currentMessageId`=本来信 messageId（前端高亮）；附本来信 `tags`。无 contact 时线程仅本封。
- `GET /tags/stats`：`InboundMailTagService.stats()`（I-6，全量）。
- `GET /tags/options`：过滤下拉项 = stats 的 items（含失效，供筛选历史标签）。
- `POST /mails/{inboundId}/tags/auto`：`autoApplyQaTags(inboundId, 该来信 cleanedBody?:body, operator)`（历史来信一键补齐）。返回最新 `listTags`。
- `POST /mails/{inboundId}/tags`（body: `{qaRuleId?}` 或 `{label?}`）：分派 `addQaTag`/`addCustomTag`。返回最新 `listTags`。
- `DELETE /tags/{tagId}`：删除。

## 变更文件清单

| # | 文件 | 类型 |
|---|------|------|
| 1 | `src/main/resources/db/migration/V53__inbound_mail_tag.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailTag.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailTagRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/InboundMailTagService.kt` | 新增 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/InboundMailSummaryController.kt` | 新增 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt` | 修改（+`matchAllRuleIds`） |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` | 修改（2 sink 挂载 + 注入） |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt` | 修改（+汇总列表/计数查询） |

计 8 文件（≤10 ✓）。子系统：mail + qa（2 ✓）。新表 1、新字段 0（不改 `inbound_mail_processing` 结构）。

## 验收标准

- I-1: 插入 QA 标签后行内 `qa_rule_id` 与 `label` 均非空；CUSTOM 行 `qa_rule_id IS NULL`。单测：`addQaTag` 后查库两字段齐全；改 `qa_rule.displayName` 不影响已存 `label`（快照）。
- I-2: 单测：某来信打 QA 标签→`setRuleEnabled(false)`→`listTags` 返回该标签 `active=false` 且行仍在；`enabled=true` 后 `active=true`。CUSTOM 恒 `active=true`。
- I-3: 单测：构造被 `supersedesChildren` 复合规则覆盖的多命中来信，`matchAllRuleIds` 返回全部 raw 命中（含被覆盖子规则），数量 > `match().matchedRuleIds`。
- I-4: 单测：连续两次 `autoApplyQaTags` 同一来信，第二次返回 0、无重复行；DB 唯一键拦截重复。删除一个 AUTO 标签后再 `autoApplyQaTags` 会重新加回。
- I-5: 单测：mock `InboundMailTagService.autoApplyQaTags` 抛异常，`processSingle` 仍正常完成、inbound 行仍落库。仅正文非空才调用。
- I-6: 单测：跨多封来信打标后 `stats()` 计数=全表聚合，不随任何列表过滤变化；失效 QA 仍计入并 `active=false`。
- 集成：`POST /mails/{id}/tags/auto` 对历史来信补齐命中标签；`POST /mails/{id}/tags`（qaRuleId / label）与 `DELETE /tags/{id}` 生效；`GET /mails?tagKey=` 按标签过滤正确。

## 自检清单
- [x] 关键不变量 ≥1/新字段（新表/新命中集/置灰/幂等/best-effort/全量 均有 I-x）
- [x] 现状审计列全 `inbound_mail_processing` 写路径（grep 确认 4 处，2 新建 sink 为挂载点）
- [x] 无未被不变量覆盖的新写路径（标签写入受 I-1/I-4，pipeline 受 I-5）
- [x] 文件数 8 ≤10；子系统 2 ≤2；新表字段仅本表
- [x] 每 Task 标注治理不变量编号
- [x] 验收每不变量 ≥1 检查
- [x] 文件清单无「相关文件」等模糊项
- [x] 范围外显式列出（前端、回填、审计日志、枚举扩展）
- [x] Phase 0 知识已用/显式取舍（K-overview-gap-supersede 用于 I-3；K-process-single-all-callers 用于 I-5 挂载点判断；K-cleanedbody-inbound-only 用于正文回退；K-audit-selected-source 明确不复用 mail_record_qa_rule）
- [x] 存于 docs/plans/2026-07-01/

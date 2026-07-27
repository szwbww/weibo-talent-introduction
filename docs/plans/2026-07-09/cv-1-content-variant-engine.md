# 计划 CV-1：content_variant 数据模型与渲染引擎切换（cv-1-content-variant-engine）

> 系列：变体机制二次重构 1/6（CV-1 引擎 → CV-2 变体 CRUD → CV-3 回复路径 → CV-4 前端变体编辑 → CV-5 前端预览分屏 → CV-6 旧字段清理）。
> 决策记录（用户 2026-07-09 拍板）：模板主题变体下线、旧 variant_group/subjectVariants 数据直接丢弃、变体挂内容本体（QA 规则/回复片段）、QA 变体只变 replyBody、预览切换器保留、自动路径默认启用变体、人工回复 useVariants 勾选默认主体、片段类型保留并新增 CUSTOM（CV-2）。
> 策略：expand-contract——本计划只建新轨并让引擎停读旧字段，旧列/旧 DTO 到 CV-6 才删，中间态前端旧变体 UI 静默失效（保存字段被忽略），由 CV-4 替换。

## 需求描述

可观测结果：
1. 新表 `content_variant`，变体挂在 QA 规则 / 回复片段本体上；渲染时池 = 主体 + 变体，按专家 seed 确定性选择；无变体恒用主体。
2. 模板主题不再轮换：恒用 `template.subject`；`subjectVariants` 自本计划起为死字段（引擎禁读，保存忽略）。
3. `previewDraft` 的 `variantIndex` 语义变为「预览 seed」（驱动各内容块在各自池上滚动），响应新增 `variantPoolSize`（供 CV-5 切换器定上限）。

不得改变：
- 无 content_variant 记录时，QA/片段内容块渲染输出与改动前逐字节一致（主题变体行为除外——其变更是本需求本身）。
- `variantSeedFor` 与 5 个调用点的 seed 派生（K-variant-seed-call-sites）不动。
- `renderText` 占位符行为（K-renderText-all-callers 5 入口）。
- `qaRuleIds` 顺序契约（CLAUDE.md K-composed-reply-order-contract）。
- `Math.floorMod` 防负（K-positive-hash-index）。

超出范围（明确不做）：
- 变体 CRUD API（CV-2）；QA 自动/人工回复路径接变体（CV-3）；一切前端（CV-4/5）；旧列与旧 DTO 删除（CV-6）。
- 旧 variant_group / subjectVariants 数据迁移（决策：丢弃）。

## 关键不变量

### Invariant I-1: 变体池语义
- Rule: pool = `[主体文本] + content_variant(owner_type, owner_id, enabled=true, ORDER BY variant_order ASC, id ASC)`；选中 index = `Math.floorMod(seed + ownerId, pool.size)`（ownerId 做池间解耦，替代旧 variantGroup.hashCode()）；池空不可能（主体恒在）。`useVariants=false` 的调用（CV-3 人工路径）恒取 index 0（主体）。
- Applies to: `ContentVariantService.resolveBody`（唯一解析实现，禁止第二处实现）。
- Violation consequence: 各路径变体不一致、预览与实发漂移。
- 来源: original（决策 2）+ K-positive-hash-index

### Invariant I-2: 模板主题恒定
- Rule: render/renderByCode/preview/previewDraft 的 subject 一律 `template.subject` 渲染；`selectSubjectVariant`/`buildSubjectPool`/`validateSubjectVariants`/`selectDraftSubject`/`parseSubjectVariants` 全部删除；`command.subjectVariants` 字段保留但**任何代码路径禁读**（CV-6 删字段）。
- Applies to: `MailComposeTemplateService` 全部渲染与保存路径。
- Violation consequence: 死字段复活，CV-6 清理时踩雷。
- 来源: original（决策 1）

### Invariant I-3: owner_type 白名单
- Rule: `owner_type ∈ {QA_RULE, REPLY_SNIPPET}`（常量对象 `ContentVariantOwnerType`）；`resolveBody` 收到未知 ownerType 或 ownerId=null → 返回主体（容错不抛错）。
- Applies to: ContentVariantService 全部方法。
- Violation consequence: 脏数据导致渲染崩溃。
- 来源: original

### Invariant I-4: previewDraft 预览语义
- Rule: `request.variantIndex ?: 0` 作为 seed 传入 `resolveBlocks`；结果新增 `variantPoolSize: Int` = 所有内容块解析池大小的最大值（无块或全无变体 = 1）。preview 与 render 共用 `resolveBody`（同一实现，K-preview-mirrors-pipeline 同源原则）。
- Applies to: `previewDraft`、`resolveBlocks`。
- Violation consequence: CV-5 切换器无上限依据；预览与实发用不同解析。
- 来源: original + K-preview-mirrors-pipeline

### Invariant I-5: 无变体回归
- Rule: content_variant 表无该 owner 记录时，`resolveBody` 返回主体原文（不 trim、不改写）。
- Applies to: resolveBody。
- Violation consequence: 全网既有邮件正文抖动。
- 来源: original

## 现状审计

### content_variant（新表，MySQL）
- Schema: V67 建表——`id BIGINT PK AUTO_INCREMENT, owner_type VARCHAR(32) NOT NULL, owner_id BIGINT NOT NULL, variant_order INT NOT NULL DEFAULT 100, content TEXT NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME, updated_at DATETIME, KEY idx_owner(owner_type, owner_id)`。当前迁移最大号 V66（V66__create_ai_training_dialogue.sql），本计划用 V67。
- Write paths: 本计划 0 个（CRUD 在 CV-2）；表建好即为空 → I-5 保证全量回归。
- Read paths: `ContentVariantService.resolveBody` / `listByOwner`（本计划新增，唯一）。

### 渲染引擎现状（MailComposeTemplateService.kt，631 行版）
- 主题池：`selectSubjectVariant` (:539) + `buildSubjectPool` (:544) + `parseSubjectVariants`；保存校验 `validateSubjectVariants` (:364)；previewDraft `selectDraftSubject` (:253)——全部删除（I-2）。
- snippet 变体：`resolveSnippetVariant` (:530) 查 `findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc`（ReplySnippetRepository:17）——删除，调用点 `resolveBlocks` REPLY_SNIPPET 分支改走 `resolveBody(REPLY_SNIPPET, refId, snippet.content, seed)`；仓库该查询方法一并删除（无其他调用方，grep 确认唯一）。
- QA 块：`resolveBlocks` QA_RULE 分支直接 `rule.replyBody` → 改 `resolveBody(QA_RULE, refId, rule.replyBody, seed)`。
- seed 供给（不动）：6 个调用点已全部接 `variantSeedFor`（K-variant-seed-call-sites，2026-07-08 已完成）。
- Interaction points: ① resolveBody（本计划写）× CV-3 各回复路径（未来读）——签名含 `useVariants: Boolean = true` 预留；② variantPoolSize（本计划写）× CV-5 切换器（读）；③ 引擎停读 subjectVariants × 前端仍发送该字段（Jackson 忽略未知/多余字段，Spring Boot 默认不报错——已验证 Request DTO 字段仍在，仅后端不消费）。

### reply_snippet / mail_compose_template（旧字段，本计划只停读不删）
- `reply_snippet.variant_group`：写路径 ReplySnippetService.create/update（保留，CV-6 删）；读路径仅 resolveSnippetVariant（本计划删）→ 本计划后成死字段。
- `mail_compose_template.subject_variants`：写路径 create/update（保留，忽略语义）；读路径 selectSubjectVariant/selectDraftSubject/toDetail（前两者删；toDetail 保留字段透传避免 DTO 断裂，CV-6 删）。

## 实现方案

### T1 — V67 迁移（I-3）
新文件 `V67__create_content_variant.sql`，DDL 如现状审计所列。不动任何已应用迁移。

### T2 — domain + repository（I-1, I-3）
新文件 `variant/domain/ContentVariant.kt`（immutable data class + `ContentVariantOwnerType` 常量对象）；`variant/repository/ContentVariantRepository.kt`：`findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc`、`findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc`、`deleteByOwnerTypeAndOwnerId`（CV-2 用）。

### T3 — ContentVariantService（I-1, I-3, I-5）
新文件 `variant/service/ContentVariantService.kt`：
`fun resolveBody(ownerType: String, ownerId: Long?, mainBody: String, seed: Int, useVariants: Boolean = true): String`——按 I-1/I-3/I-5；`fun listByOwner(ownerType, ownerId): List<ContentVariant>`。

### T4 — 引擎切换（I-1, I-2, I-4）
`MailComposeTemplateService.kt`：注入 ContentVariantService；删主题池 5 函数与校验调用；resolveBlocks QA_RULE/REPLY_SNIPPET 分支接 resolveBody；previewDraft 按 I-4（variantIndex→seed、variantPoolSize 计算）；`ComposeTemplatePreviewDraftResult` 增 `variantPoolSize: Int = 1`。`ReplySnippetRepository.kt` 删 variantGroup 查询方法。

### T5 — 测试（全不变量）
`ContentVariantServiceTest.kt`（新）：I-1 选择与解耦（两 owner 同 seed 不同 index）、I-3 未知类型容错、I-5 空池回归、useVariants=false 恒主体。
`MailComposeTemplateServiceTest.kt`：删主题变体旧用例；加 I-2（有 subjectVariants 数据的模板渲染仍用主 subject）、I-4（variantIndex 滚动块内容、variantPoolSize 计算）、QA/片段块变体端到端（mock 变体仓库）、无变体逐字节回归。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/resources/db/migration/V67__create_content_variant.sql | T1 新建 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/variant/domain/ContentVariant.kt | T2 新建 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/variant/repository/ContentVariantRepository.kt | T2 新建 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt | T3 新建 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt | T4 |
| 6 | src/main/kotlin/com/weibo/talentintroduction/reply/repository/ReplySnippetRepository.kt | T4 删组查询 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantServiceTest.kt | T5 新建 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt | T5 |

文件数 8 ≤ 10；子系统 2（variant 新模块、template 引擎）；新增共享存储 1 张新表（无既有表新字段）。

## 验收标准

- I-1: 单测断言 pool 顺序（variant_order asc, id asc）、index 公式、两 owner 解耦；grep 全仓 `floorMod` 之外无变体下标计算。
- I-2: grep `subjectVariants` 在 main 源码中仅剩 DTO 字段声明与 toDetail 透传（无逻辑消费）；`selectSubjectVariant|buildSubjectPool|validateSubjectVariants|selectDraftSubject|parseSubjectVariants` grep 零命中。
- I-3: 未知 ownerType 用例返回主体不抛错。
- I-4: previewDraft 用例断言 variantIndex=0/1/2 时块文本滚动、variantPoolSize=max(池)；resolveBody 为 render 与 preview 共用（grep 无第二实现）。
- I-5: 空表全量回归用例（既有测试语料输出逐字节一致）。
- `mvn test` 全绿；逐条打开 resolveBlocks/resolveBody/previewDraft 对照不变量核对（K-template-feature-coverage）。

## 人工验收清单

### A-1: 无变体全量回归（覆盖 must-NOT-change 第 1 条、I-5）
- 前置条件: V67 已执行、content_variant 为空表；存在启用模板「项目介绍邮件」（含 QA 块+片段块+自定义文本块）。
- 操作步骤: 1) 打开模板编辑器查看服务端预览；2) 对一名 L2 专家手工发送该模板邮件；3) 查发件箱该邮件正文。
- 预期结果: 预览与发出的正文与改造前完全一致（可对比改造前同专家的历史邮件正文结构），无多余空行、无内容缺失。

### A-2: 主题恒定（覆盖需求第 2 条、I-2）
- 前置条件: 选一个 `subject_variants` 列仍有历史 JSON 数据的模板（SQL: `SELECT id FROM mail_compose_template WHERE subject_variants IS NOT NULL LIMIT 1`；无则手工 UPDATE 造一条）。
- 操作步骤: 1) 对两名不同专家分别手工发送该模板；2) 查两封邮件主题。
- 预期结果: 两封主题完全相同，均为主 subject 渲染结果；历史变体 JSON 不出现在任何主题中。

### A-3: 变体池生效与确定性（覆盖需求第 1 条、I-1）
- 前置条件: SQL 造 2 条变体 `INSERT INTO content_variant(owner_type, owner_id, variant_order, content, enabled) VALUES ('QA_RULE', <某启用规则id>, 1, 'VARIANT-A ...', 1), ('QA_RULE', <同id>, 2, 'VARIANT-B ...', 1)`；该规则挂在某模板 QA 块上。
- 操作步骤: 1) 对专家甲发送该模板两次；2) 对不同 ORCID 的专家乙、丙、丁各发一次；3) 对比正文该段落。
- 预期结果: 甲两封该段完全相同（确定性）；乙丙丁中至少出现两种不同文案（主体 / VARIANT-A / VARIANT-B 之一）。

### A-4: 预览 seed 滚动（覆盖需求第 3 条、I-4）
- 前置条件: 同 A-3 数据。
- 操作步骤: 1) 编辑该模板，打开服务端预览；2) F12 Network 手工重放 preview-draft 请求，body 中 variantIndex 依次改 0、1、2。
- 预期结果: QA 块段落文本在主体 / VARIANT-A / VARIANT-B 间滚动；响应 JSON 含 `"variantPoolSize": 3`。

### A-5: seed 链路回归（覆盖 must-NOT-change 第 2 条）
- 前置条件: A-3 数据存在。
- 操作步骤: 对同一专家先手工发送模板邮件、再触发会议邀请（切状态或走自动流程）。
- 预期结果: 两封邮件中同一 QA 规则/片段选中的变体一致（同专家同 seed），无一封报错。

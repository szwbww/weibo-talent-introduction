# 批量介绍邮件个性化与反垃圾优化

## 需求描述

**可观测结果**：批量发送 INTRODUCTION 邮件时，每封邮件的 subject、body 段落措辞、Message-ID 各不相同，且发送间隔随机化，降低 Google 判定为 bulk spam 的概率。

**数据现状约束**：线上专家 `researchFields` 和 `keyword` 字段均为空（采集管道从未填充），可靠有值的个性化字段仅有 `familyNames`、`institution`（≈employment/affiliation）、`country`。模板和变体设计必须基于这三个字段，不得依赖 researchFields / keyword。

**不得改变**：
- `ManualInitialOutreachService` 现有的发送节奏逻辑（已有 `perMailIntervalMs` + `AccountRateLimiter`）
- QA 自动回复流程（一对一回复，不需要变体）
- 模板的 block 组装顺序契约（K-composed-reply-order-contract）
- 现有 sender 轮转、warmup、bounce 监控、Postmaster 集成、unsubscribe 机制

**不在范围**：
- 前端管理界面对新字段（subjectVariants、variantGroup）的 UI 支持（后续单独做）
- IP 轮转（SMTP 基础设施层面，非应用层）
- 邮件打开率追踪（tracking pixel）
- HTML 模板美化

## 关键不变量

### Invariant I-1: 变体选择必须基于专家 orcidId 确定性计算
- Rule: 同一专家无论重试多少次，选到的 subject 变体和 body snippet 变体必须一致。选择算法为 `abs(orcidId.hashCode()) % variantCount`。
- Applies to: `MailComposeTemplateService.renderTemplate()`, `MailComposeTemplateService.resolveBlocks()`
- Violation consequence: 同一专家重发时正文变化，显得更像 spam；或审计时无法复现发送内容。
- 来源: original

### Invariant I-2: subjectVariants 为空时退化为原 subject 字段
- Rule: `MailComposeTemplate.subjectVariants` 为 null 或空 JSON 数组时，渲染逻辑必须回退到 `template.subject`。不得因缺少变体而报错。
- Applies to: `MailComposeTemplateService.renderTemplate()`
- Violation consequence: 所有现有模板（未配置变体）全部渲染失败。
- 来源: original

### Invariant I-3: variantGroup 为空的 snippet 保持现有行为
- Rule: `ReplySnippet.variantGroup` 为 null 时，`REPLY_SNIPPET` block 直接使用该 snippet，不触发变体选择。仅当 variantGroup 非空时，从同 group 的 enabled snippet 中按 I-1 规则选一个。
- Applies to: `MailComposeTemplateService.resolveBlocks()`
- Violation consequence: 所有现有 reply snippet 引用行为被破坏。
- 来源: original

### Invariant I-4: InitialOutreachService 发送间隔不影响 ManualInitialOutreachService
- Rule: P1 的 jitter delay 仅加在 `InitialOutreachService.sendInitialBatch()` 循环内，不修改 `ManualInitialOutreachService`（它已有独立的节奏控制）。
- Applies to: `InitialOutreachService.sendInitialBatch()`
- Violation consequence: ManualOutreach 的精细节奏控制被覆盖或冲突。
- 来源: original

### Invariant I-5: 每封外发邮件必须有唯一 UUID-based Message-ID
- Rule: `IntroductionMailComposer.compose()` 必须生成 `<intro-{orcidId}-{UUID}@{senderDomain}>` 格式的 messageId 并设置到 ComposedMail 上，而非依赖 JavaMail 的默认生成。
- Applies to: `IntroductionMailComposer.compose()`
- Violation consequence: JavaMail 默认 Message-ID 模式可被 Google 识别为批量工具指纹。
- 来源: original

## 现状审计

### mail_compose_template (MySQL)
- Schema: `id, template_code, template_name, subject(VARCHAR 255), description, mail_type, enabled, created_at, updated_at`。无 subject_variants 字段。
- Write paths:
  1. `MailComposeTemplateService.create()` — 创建模板
  2. `MailComposeTemplateService.update()` — 更新模板
  3. `MailComposeTemplateService.setEnabled()` — 启禁用
  4. `V61__create_mail_compose_template.sql` — 建表
  5. `V62__unify_mail_templates.sql` — 迁移旧模板数据
- Read paths:
  1. `MailComposeTemplateService.render()` — 按 id 渲染，读 subject + blocks
  2. `MailComposeTemplateService.renderByCode()` — 按 templateCode 渲染，读 subject + blocks
  3. `MailComposeTemplateService.preview()` — 预览，读 subject + blocks
  4. `MailComposeTemplateService.listAll()` / `listEnabled()` — 列表
  5. `MailComposeTemplateController` — REST API 透传
- Interaction points: `render/renderByCode` 被 `IntroductionMailComposer.compose()` 调用，compose 结果被 `InitialOutreachService` 和 `ManualInitialOutreachService` 消费

### reply_snippet (MySQL)
- Schema: `id, snippet_type, content, display_order, is_default, enabled, created_at, updated_at`。无 variant_group 字段。
- Write paths:
  1. `V47__create_reply_snippet.sql` — 建表 + seed 数据
  2. ReplySnippet CRUD（通过 repository，UI 管理）
- Read paths:
  1. `MailComposeTemplateService.resolveBlocks()` — REPLY_SNIPPET block 按 refId 查找单条
  2. `ReplySnippetRepository.findBySnippetTypeAndEnabledTrue*` — 按 type 查找（用于 QA 回复组装）
- Interaction points: resolveBlocks 被 render/renderByCode 调用 → 被 IntroductionMailComposer 消费

### InitialOutreachService 发送循环
- 当前: `experts.forEach { ... compose → send ... }` 无任何 delay
- 与 `ManualInitialOutreachService` 是并行的两条发送路径，互不影响
- Scheduler 通过 `MailAutomationScheduler.scheduleInitialOutreach()` 调用 `sendInitialBatch()`

### SmtpMailDeliveryService / ComposedMail
- `ComposedMail.messageId` 为 null 时，JavaMail 自动生成 Message-ID（格式 `<hash.JavaMail.user@hostname>`，可被指纹识别）
- `ManualInitialOutreachService` 已经生成 `<manual-outreach-{orcid}-{uuid}@weibo.com>`
- `InitialOutreachService` 路径下 `IntroductionMailComposer.compose()` 不设置 messageId → JavaMail 默认

## 实现方案

### Phase A: 发送节奏 (P1 + P3 + P4)

#### Task A-1: InitialOutreachService 加入发送间隔 jitter (I-4)
- 文件: `InitialOutreachService.kt`
- 在 `sendInitialBatch()` 的 forEach 循环中，每封成功发送后加 `Thread.sleep(baseMs + random.nextLong(jitterMs))`
- 注入 `MailSchedulingProperties`，读取 `initialOutreachSendIntervalMs`（默认 30000）和 `initialOutreachSendJitterMs`（默认 60000）
- 间隔仅在还有下一封时生效（最后一封不 sleep）

#### Task A-2: 添加发送间隔配置 (I-4)
- 文件: `MailSchedulingProperties.kt`
- 新增字段: `initialOutreachSendIntervalMs: Long = 30000`, `initialOutreachSendJitterMs: Long = 60000`

#### Task A-3: application.yml 添加默认值 + P3 说明
- 文件: `application.yml`
- 在 `talent-introduction.scheduling` 下新增:
  ```yaml
  initial-outreach-send-interval-ms: ${MAIL_SCHEDULING_INITIAL_OUTREACH_SEND_INTERVAL_MS:30000}
  initial-outreach-send-jitter-ms: ${MAIL_SCHEDULING_INITIAL_OUTREACH_SEND_JITTER_MS:60000}
  ```
- P3 不需要改代码：将 `initial-outreach-cron` 从每日一次改为每 2-3 小时一次（如 `0 0 8,10,12,14,16 * * *`），同时将 `initial-outreach-batch-size` 按比例缩小。这是部署配置调整，不是代码变更。

#### Task A-4: IntroductionMailComposer 生成 UUID Message-ID (I-5)
- 文件: `IntroductionMailComposer.kt`
- 在 `compose()` 返回的 `ComposedMail` 上设置 `messageId`:
  ```kotlin
  val domain = account.senderEmail.substringAfter("@")
  val messageId = "<intro-${expert.orcidId}-${UUID.randomUUID()}@$domain>"
  ```
- 确保 `SmtpMailDeliveryService` 已有的 custom messageId 逻辑被触发

### Phase B: 内容变体池 (P2)

#### Task B-1: 数据库迁移 (I-2, I-3)
- 文件: `V64__add_subject_variants_and_snippet_variant_group.sql`
- 内容:
  ```sql
  ALTER TABLE mail_compose_template ADD COLUMN subject_variants TEXT NULL COMMENT 'JSON 数组: subject 变体列表，为空时使用 subject 字段';
  ALTER TABLE reply_snippet ADD COLUMN variant_group VARCHAR(64) NULL COMMENT '变体组标识，同组 snippet 按确定性规则选一个';
  ```

#### Task B-2: Domain 类添加字段 (I-2, I-3)
- 文件: `MailComposeTemplate.kt` — 添加 `val subjectVariants: String? = null`
- 文件: `ReplySnippet.kt` — 添加 `val variantGroup: String? = null`

#### Task B-3: ReplySnippetRepository 添加变体组查询 (I-3)
- 文件: `ReplySnippetRepository.kt`
- 新增: `fun findByVariantGroupAndEnabledTrueOrderByDisplayOrderAsc(variantGroup: String): List<ReplySnippet>`

#### Task B-4: MailComposeTemplateService 变体选择逻辑 (I-1, I-2, I-3)
- 文件: `MailComposeTemplateService.kt`
- 修改 `render()` 和 `renderByCode()` 签名：新增 `variantSeed: Int = 0` 参数
- 修改 `renderTemplate()`:
  ```kotlin
  // Subject 变体选择 (I-2: 空时退化)
  val subjectText = selectSubjectVariant(template, variantSeed)
  // ... 渲染 subject
  ```
  ```kotlin
  private fun selectSubjectVariant(template: MailComposeTemplate, seed: Int): String {
      val variants = parseSubjectVariants(template.subjectVariants)
      if (variants.isNullOrEmpty()) return template.subject
      return variants[abs(seed) % variants.size]
  }
  ```
- 修改 `resolveBlocks()`: 新增 `variantSeed: Int = 0` 参数
  - 在 `REPLY_SNIPPET` 分支: 如果加载的 snippet 有非空 `variantGroup`，调用 `replySnippetRepository.findByVariantGroupAndEnabledTrueOrderByDisplayOrderAsc(variantGroup)` 取同组列表，按 `abs(seed) % list.size` 选一个替代
  - `variantGroup` 为 null 时走原逻辑不变 (I-3)
- 新增 `parseSubjectVariants(json: String?): List<String>?` 私有方法，用 objectMapper 解析 JSON 数组

#### Task B-5: IntroductionMailComposer 传递 variantSeed (I-1)
- 文件: `IntroductionMailComposer.kt`（与 Task A-4 同文件）
- 计算 `val variantSeed = abs(expert.orcidId.hashCode())`
- 传递给 `mailComposeTemplateService.render(templateId, variables, variantSeed)` 或 `renderByCode("INTRODUCTION", variables, variantSeed)`

## 变更文件清单

| # | 文件路径 | 改动类型 | 所属 Phase |
|---|---------|---------|-----------|
| 1 | `campaign/service/InitialOutreachService.kt` | 修改 | A |
| 2 | `config/MailSchedulingProperties.kt` | 修改 | A |
| 3 | `src/main/resources/application.yml` | 修改 | A |
| 4 | `src/main/resources/db/migration/V64__add_subject_variants_and_snippet_variant_group.sql` | 新增 | B |
| 5 | `template/domain/MailComposeTemplate.kt` | 修改 | B |
| 6 | `template/service/MailComposeTemplateService.kt` | 修改 | B |
| 7 | `reply/domain/ReplySnippet.kt` | 修改 | B |
| 8 | `reply/repository/ReplySnippetRepository.kt` | 修改 | B |
| 9 | `mail/service/IntroductionMailComposer.kt` | 修改 | A+B |

共 9 个文件，2 个子系统（发送基础设施: 1-3, 模板/组装: 4-8），IntroductionMailComposer 是桥接点。

## 验收标准

- **I-1**: 用同一个 orcidId 调用 `IntroductionMailComposer.compose()` 多次（模拟重试），断言 subject 和 body 内容完全一致。用不同 orcidId 调用，断言至少部分 subject 或 body 段落不同（前提是配置了 ≥2 个变体）。
- **I-2**: 创建一个 `subjectVariants` 为 null 的模板，调用 `renderByCode()`，断言返回的 subject 等于 `template.subject`。创建一个 `subjectVariants = '["A","B","C"]'` 的模板，断言返回值为其中之一。
- **I-3**: 创建一个 `variantGroup = null` 的 snippet 并引用为 block，断言渲染结果使用该 snippet 原文。创建 3 个 `variantGroup = "greeting"` 的 snippet，引用其中一个为 block，断言渲染结果为该 group 中的某一个（取决于 seed）。
- **I-4**: 在 `InitialOutreachServiceTest` 中验证发送两封邮件之间经过了 ≥ `initialOutreachSendIntervalMs` 的延迟。验证 `ManualInitialOutreachService` 不受影响（无新依赖注入）。
- **I-5**: 调用 `IntroductionMailComposer.compose()`，断言返回的 `ComposedMail.messageId` 匹配 `<intro-.*-[0-9a-f-]+@.+>` 且非 null。
- **集成场景**: 配置 2 个 subject 变体 + 2 组 body snippet 变体 + 发送间隔，对 10 个不同 orcidId 调用完整发送流程，断言:
  - 10 封邮件的 subject 不全相同
  - 10 封邮件的 body 不全相同
  - 10 封邮件的 Message-ID 各不相同
  - 发送总耗时 ≥ 9 × initialOutreachSendIntervalMs

## P3 部署建议（无代码变更）

将 `initial-outreach-cron` 从每日一次改为每 2-3 小时一次，同时按比例缩小 `initial-outreach-batch-size`：

```yaml
# 之前: 每天 8 点跑一次，batch=100
initial-outreach-cron: "0 0 8 * * *"
initial-outreach-batch-size: 100

# 之后: 工作时间每 2 小时跑一次，batch=20
initial-outreach-cron: "0 0 8,10,12,14,16 * * *"
initial-outreach-batch-size: 20
```

效果：同样 100 封/天，但分散在 5 个时段，配合 P1 的 30-90s 间隔，每个时段内也不会连续快速发送。

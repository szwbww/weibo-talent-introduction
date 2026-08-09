# P1 · 发送侧硬闸门

- master: `docs/plans/2026-08-09/personalization-gate-master.md`
- 子计划序号: 1 / 2
- 子系统: 后端（模板配置、邮件组装、批量发送）
- 前端改动: 无

## 需求描述

### Observable outcome

1. 手动发送与 MATERIAL_REMINDER 批量发出的邮件，正文中的 `${unsubscribeUrl}`、`${researchFields}` 等占位符被真实值替换，不再原样外发。
2. compose 模板可配置「必填个性化变量」；任一必填变量取不到非空值时，该专家被跳过而非用默认值发出。
3. 批量执行详情的「跳过原因」中出现「个性化字段缺失」。
4. 新增 `${primaryResearchField}` 变量，取 `researchFields` 的第一个 topic。

### What must NOT change

1. `IntroductionMailComposer.compose()` 的变量来源仍是 `MailVariableService.buildVariables`，其两个调用方（`InitialOutreachService`、`ManualInitialOutreachService`）无需改签名。(来源: K-introduction-compose-callers)
2. `researchFields` 的 ES 存储格式与 `ExpertDiscoveryService.updateExpertAcademicFields()` 的 doc map 不变。
3. `requireValidPlaceholders` 现有五处调用方的语义与签名不变。
4. `MailComposeTemplateService.renderText()` 的替换算法不变。(来源: K-renderText-all-callers)
5. 未配置 `required_keys` 的模板发送行为不变。
6. `ContentVariantService` 的变体解析与 `variantSeed` 行为不变。

### Out of scope

1. 块级条件渲染（见 master「Out of scope」第 1 项）。
2. ES 预筛与列表筛选——属 P2。
3. 门禁命中后自动触发 enrichment。
4. `IntroductionMailComposer` 纯文本单部分 vs `ManualExpertMailService` multipart 的差异统一。

## 关键不变量

### I-1: 变量注入必须覆盖全集
- Rule: `ManualExpertMailService.composeComposeTemplate()` 传给 `MailComposeTemplateService.render()` 的 variables map，必须由 `MailVariableService.buildVariables(account, expert, contact.expertEmail, previewFallbacks = false, contact = contact)` 产出，其中 `expert` 由 contact 的 `orcidId` + `currentIndexLevel` 解析。禁止保留任何只含 sender 键的本地 map。
- Applies to: `ManualExpertMailService.composeComposeTemplate`
- Violation consequence: `unsubscribeUrl` 与全部专家变量缺失，占位符原样外发（当前线上故障）。
- 来源: original（现状为 `ManualExpertMailService.kt:231-239` 只有 6 个 sender 键）

### I-2: 发送前占位符残留一律拒发
- Rule: 在调用 `mailDeliveryService.send(...)` 之前，对最终 subject、plain text body、HTML body 逐一检查；任一匹配 `\$\{[^}]*\}` 即中止本次发送并抛出 `PlaceholderResidueException`。该检查不得被任何配置开关关闭。
- Applies to: `ManualExpertMailService.sendManualMail`、`IntroductionMailComposer.compose`
- Violation consequence: 任何未来新增的未注入变量都会静默外发。
- 来源: original
- 备注: 该检查**不是**复用 `requireValidPlaceholders`。后者只校验 key 是否在白名单内、nullable key 是否带默认值（`MailPlaceholderService.kt:41-57`），**不检查是否被填充**，拦不住本场景。

### I-3: 门禁必填集的判定输入
- Rule: 门禁判定必须基于「渲染前的原始文本（subject + 各内容块）」与「当前 variables map」，通过 `MailPlaceholderService.detectFallbackKeys(text, variables)` 得出实际走了兜底的 key 集合，再与该模板的 `effectiveRequiredKeys()` 求交集。交集非空即拒发。
- Applies to: `PersonalizationGateService`
- Violation consequence: 若改用渲染后文本判定，兜底值已被填入，无法区分「真实值」与「默认值」。
- 来源: original

### I-4: 必填集为空时门禁不生效
- Rule: `effectiveRequiredKeys(templateId)` 在 `required_keys` 为 NULL、空字符串、空 JSON 数组或解析失败时，一律返回空列表；空列表表示门禁不生效，发送照常。解析失败必须记 WARN 日志但不得抛出。
- Applies to: `MailComposeTemplateService.effectiveRequiredKeys`
- Violation consequence: 存量模板全部被拦截，发送归零。
- 来源: original

### I-5: 两条外发路径都过闸门
- Rule: `ManualExpertMailService.sendManualMail` 与 `IntroductionMailComposer.compose` 都必须调用 `PersonalizationGateService`。
- Applies to: 上述两处
- Violation consequence: INTRODUCTION 批量（主发送路径，`ManualInitialOutreachService.kt:589`）完全绕过门禁。
- 来源: master I-M5

### I-6: 门禁拒发在批量中表现为「跳过」而非「失败」
- Rule: `ManualInitialOutreachService` 的两个发送循环必须分别捕获门禁异常，调用 `accumulator.recordSkipped(BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE, ...)`，**不得**落入通用 `recordFailure` 分支；且不得因此中止整批。
- Applies to: `ManualInitialOutreachService` MATERIAL_REMINDER 循环（`:290` 起 try 块）与 INTRODUCTION 循环（`:588` 起 try 块）
- Violation consequence: 面板显示成片「发送异常」，运营无法区分数据缺失与真实故障。
- 来源: original

### I-7: `primaryResearchField` 是派生变量
- Rule: `primaryResearchField` 的值由 `expert.researchFields` 按 `", "` 切分取首段并 trim 得到；`ES_FIELD_BY_KEY["primaryResearchField"]` 必须为 `"researchFields"`；不得新增 ES 字段或改写入路径。
- Applies to: `MailPlaceholderService`、`MailVariableService.buildVariables`
- Violation consequence: 破坏 enrichment 单一写入点；P2 的筛选会指向不存在的字段。
- 来源: master I-M4 / K-enrichment-write-three-layers

### I-8: 新变量必须进入全部元数据集合
- Rule: 新增 `primaryResearchField` 必须同时出现在 `EXPERT_KEYS`、`VARIABLE_LABELS`、`ES_FIELD_BY_KEY`、`VARIABLE_EXAMPLES` 四个集合中。
- Applies to: `MailPlaceholderService` companion object
- Violation consequence: `variableMetadata()` 由 `VARIABLE_LABELS` 驱动（`MailPlaceholderService.kt:7-16`），漏任一项会导致该变量在校验中被判为未知 token 或 esField 为 null。
- 来源: original

## 现状审计

### `mail_compose_template`（MySQL）
- Schema: 建表见 `V61__create_mail_compose_template.sql`；后续迁移追加了 `template_code`、`mail_type`、`subject_variants`。领域类 `template/domain/MailComposeTemplate.kt:7-19`，Spring Data JDBC 不可变 data class。当前最新迁移为 **V83**，本计划使用 **V84**。
- Write paths:
  1. `MailComposeTemplateService`（新建/更新模板，经管理接口）
  2. Flyway 种子迁移 `V61` / `V71` / `V78`
- Read paths:
  1. `MailComposeTemplateService.getById` / `listEnabled` — 供 `ManualExpertMailService.listSendOptions`（`:32-45`）与 `composeComposeTemplate`（`:163`）
  2. `MailComposeTemplateService.renderByCode` — 供 `IntroductionMailComposer`、`MeetingInvitationMailComposer`、`AutoMailReplyService`、`AutoReplyPreviewService`、`MeetingScheduleService`
- Interaction points: 新增 `required_keys` 列被 `effectiveRequiredKeys()` 读取，供 `PersonalizationGateService`（P1）与只读接口（P2）消费。

### 邮件变量（`MailPlaceholderService` / `MailVariableService`）
- 现有键集: `MailPlaceholderService.kt:101-115` 的 13 个 `EXPERT_KEYS` + 5 个 sender 键 + `unsubscribeUrl`。
- 关键事实: `validatePlaceholders`（`:41-57`）对 nullable key 强制要求非空默认值；但 `MailComposeTemplateService` **不在** `requireValidPlaceholders` 的调用方之列（调用方仅 `ContentVariantService:86`、`QaFactBodyPolicy:24`、`ReplySnippetService:187,225`、`PendingMailOperationService:181-214`），故 compose 模板正文的占位符在保存时未被校验。
- `detectFallbackKeys(text, variables)`（`:82-96`）已能精确返回走了兜底的 key，当前仅被 `renderPreview` 用于展示。

### 外发路径
- 路径 A `ManualExpertMailService.sendManualMail`（`:47-116`）→ `composeComposeTemplate`（`:158-205`）。变量来自 `mailTemplateVariables(account)`（`:231-239`，仅 6 个 sender 键）。消费方：专家详情手动发送、`ManualInitialOutreachService.kt:304` 的 MATERIAL_REMINDER 批量。
- 路径 B `IntroductionMailComposer.compose`（`:15-34`）→ `MailVariableService.buildVariables`（变量完整）。消费方：`InitialOutreachService`、`ManualInitialOutreachService.kt:589` 的 INTRODUCTION 批量。(来源: K-introduction-compose-callers)
- Interaction points: 两条路径共用 `MailComposeTemplateService.renderText`；门禁必须同时挂在两处（I-5）。

### 批量跳过统计
- `OutcomeAccumulator.recordSkipped(code, sample)`（`BatchExecutionModels.kt:131-135`）累加到 `skippedReasons`；`toBreakdown()`（`:156-169`）经 `BatchOutcomeReasonCodes.label(code)` 转成 `ReasonCount(label, count)`。
- 前端 `app.js:14042-14053` 的 `renderReasons` 直接读 `r.label`。**因此新增原因码只需改后端 `LABELS`，前端零改动。**

### ES 字段
- `orcid_info_candidate.json` mapping: `researchFields` / `recentWorkTitles` / `patentTitles` / `degree` / `country` 为 `keyword`；`institution` / `employment` / `keyword` / `familyNames` 为 `text`。
- `researchFields` 由 `ExpertDiscoveryService.updateExpertAcademicFields():1093` 写入，值为 OpenAlex 前 5 个 topic 的 `display_name` 经 `joinToString(", ")`；topic 取值见 `OpenAlexDataSource.kt:279-282`。
- `recentWorkTitles` 为 `fetchRecentWorks(worksUrl, limit = 3)` 的原始论文标题列表（`OpenAlexDataSource.kt:252`、`:285`）。
- `institution` 在专家发现阶段即写入（`ExpertDiscoveryService.kt:619`、`:744`），不依赖 OpenAlex enrichment，填充率高于上述两者。

## 实现方案

### 阶段 1 · 数据与变量基础

**任务 1.1** 新增迁移 `V84__add_required_keys_to_compose_template.sql`（遵守 I-4）

```sql
ALTER TABLE mail_compose_template
    ADD COLUMN required_keys VARCHAR(500) NULL
    COMMENT '必填个性化变量 key 的 JSON 数组，NULL 或空数组表示不启用门禁';
```

不回填任何存量行——存量模板保持 NULL，即门禁不生效（I-4）。

**任务 1.2** `MailComposeTemplate.kt` 增加字段（I-4）

```kotlin
val requiredKeys: String? = null,
```

置于 `mailType` 之后、`enabled` 之前，保持与列顺序一致。

**任务 1.3** `MailPlaceholderService.kt` 新增 `primaryResearchField`（I-7、I-8）

四个集合各加一项：
- `EXPERT_KEYS` += `"primaryResearchField"`
- `VARIABLE_LABELS` += `"primaryResearchField" to "主要研究方向"`
- `ES_FIELD_BY_KEY` += `"primaryResearchField" to "researchFields"`
- `VARIABLE_EXAMPLES` += `"primaryResearchField" to "Machine Learning and Data Classification"`

**任务 1.4** `MailVariableService.kt` 提供取值（I-7）

在 `buildVariables` 的 `expertVars` map 中新增：

```kotlin
"primaryResearchField" to (expert.researchFields
    ?.split(", ")
    ?.firstOrNull()
    ?.trim())
    .orEmpty(),
```

`expert == null` 分支由既有的 `EXPERT_KEYS.associateWith { "" }` 自动覆盖，无需另写。

### 阶段 2 · 门禁服务

**任务 2.1** `MailComposeTemplateService.kt` 新增两个只读方法（I-3、I-4，master 跨子计划接口）

```kotlin
fun effectiveRequiredKeys(templateId: Long): List<String>
fun requiredEsFields(templateId: Long): List<String>
```

- `effectiveRequiredKeys`：读 `requiredKeys` JSON 数组文本；NULL / 空串 / 空数组 / 解析异常一律返回 `emptyList()`，解析异常记 WARN；过滤掉不在 `VARIABLE_LABELS` 中的未知 key。
- `requiredEsFields`：把上者经 `MailPlaceholderService.ES_FIELD_BY_KEY` 映射，去 null、去重，保持稳定顺序。

**任务 2.2** 新建 `mail/service/PersonalizationGateService.kt`（I-2、I-3、I-4）

```kotlin
class PersonalizationGateResult(val blocked: Boolean, val missingKeys: List<String>)

class PlaceholderResidueException(message: String) : RuntimeException(message)
class PersonalizationGateException(val missingKeys: List<String>) : RuntimeException(...)
```

两个职责，分开的方法：

1. `evaluate(rawTexts: List<String>, variables: Map<String, String>, requiredKeys: List<String>): PersonalizationGateResult`
   —— 对每段原始文本调用 `mailPlaceholderService.detectFallbackKeys(text, variables)`，并入一个集合，与 `requiredKeys` 求交集（I-3）。`requiredKeys` 为空时直接返回 `blocked = false`（I-4）。
2. `requireNoPlaceholderResidue(vararg renderedTexts: String?)`
   —— 逐段匹配 `\$\{[^}]*\}`，命中即抛 `PlaceholderResidueException`，异常消息包含首个残留 token（I-2）。

### 阶段 3 · 挂上两条外发路径

**任务 3.1** `ManualExpertMailService.kt`（I-1、I-2、I-3、I-5）

在 `composeComposeTemplate` 中：

1. 删除 `mailTemplateVariables(account)`（`:231-239`），改用：
   ```kotlin
   val expert = mailVariableService.resolveExpertProfileFor(contact)   // 见备注
   val variables = mailVariableService.buildVariables(
       account, expert, contact.expertEmail, previewFallbacks = false, contact = contact
   )
   ```
   备注：`MailVariableService.resolveExpertProfile` 当前是 private（`:260-277`）。本任务允许把它提升为 public 只读方法 `resolveExpertProfileFor(contact)`，**不改其内部逻辑**。这是本计划对 `MailVariableService` 的第二处改动，已计入文件清单。
2. 渲染前取模板原始文本用于门禁判定：调用 `mailComposeTemplateService` 的既有渲染入口时同时拿到 `rawTextsByOrder`（`ResolvedBlocks` 已持有，`MailComposeTemplateService.kt:534`），连同 `template.subject` 一起传入 `PersonalizationGateService.evaluate`。若 `blocked` 为真，抛 `PersonalizationGateException`。
3. HTML 正文改用 `mailVariableService.renderHtmlForContact(...)`（`:180-185`，已对变量值做 `HtmlUtils.htmlEscape`），替代现在的「先渲染纯文本再 `plainTextToHtml`」。
4. 在 `mailDeliveryService.send(account, composed.mail)`（`:58`）之前调用 `requireNoPlaceholderResidue(mail.subject, mail.text, mail.body)`。

**任务 3.2** `IntroductionMailComposer.kt`（I-2、I-5）

在 `compose()` 返回前：

1. 用 `mailComposeTemplateService.effectiveRequiredKeys(...)` 取该模板必填集（`templateId` 为 null 时按 `INTRODUCTION` code 解析），调 `PersonalizationGateService.evaluate`，`blocked` 则抛 `PersonalizationGateException`。
2. 调 `requireNoPlaceholderResidue(rendered.subject, rendered.body)`。

不改 `compose()` 的签名与返回类型，两个调用方不受影响（must-NOT-change 第 1 项）。

### 阶段 4 · 批量跳过统计

**任务 4.1** `BatchExecutionModels.kt`（I-6）

```kotlin
const val PERSONALIZATION_INCOMPLETE = "PERSONALIZATION_INCOMPLETE"
```
并在 `LABELS` 中加 `PERSONALIZATION_INCOMPLETE to "个性化字段缺失"`。

**任务 4.2** `ManualInitialOutreachService.kt`（I-6）

两处循环各加一个**先于**通用 `catch (e: Exception)` 的分支：

- MATERIAL_REMINDER 循环（`:290` 起的 try）：
  ```kotlin
  } catch (e: PersonalizationGateException) {
      accumulator.recordSkipped(
          BatchOutcomeReasonCodes.PERSONALIZATION_INCOMPLETE,
          "个性化字段缺失（${e.missingKeys.joinToString(",")}）：$email"
      )
      roundSent++; processedTotal++; roundProcessed++; roundRejected++
  }
  ```
- INTRODUCTION 循环（`:588` 起的 try）：同样先捕获 `PersonalizationGateException` 记 skipped，**不得**落入现有的 `TEMPLATE_RENDER_FAILED` 分支（`:593`）。

两处均不设置 `stopReason` / `midRoundStop`，不中止整批。

### 阶段 5 · 模板文案（数据变更，非代码）

本阶段产出交由运营在后台执行，**不写入迁移**（存量模板由运营在 UI 维护，仓库中无其块结构）。

推荐的 INTRODUCTION 模板正文：

```
Dear Professor ${expertFamilyName|Colleague},

I came across your recent paper "${recentWorkTitle}" and wanted to reach out.

Your work on ${primaryResearchField} at ${institution|your institution} appears
relevant to technical needs we are currently evaluating with industrial partners
in China.

We support a government-backed program that connects experienced international
experts with Chinese enterprises for research and technology collaboration.

The cooperation is flexible. Most experts remain in their current positions,
provide technical guidance remotely, and visit China only when necessary.
No relocation or commitment is required at this introductory stage.

Would you be open to learning more about the program and the possible
cooperation format?

Website: https://www.qingfeitalent.com/

If you prefer not to receive further emails, click here: ${unsubscribeUrl}
```

改动依据：

| 改动 | 依据 |
|---|---|
| 称呼加 `${expertFamilyName}` | 原文为 `Dear Professor,` 无姓名。`ExpertRecipientNamePolicy.resolveFamilyName`（`MailVariableService.kt:80-96`）已内置 ORCID/邮箱/esDocId 误入姓名的防护 |
| `${researchFields}` → `${primaryResearchField}` | `researchFields` 实际值是 5 个 OpenAlex topic 用 `", "` 拼接（`ExpertDiscoveryService.kt:1093` + `OpenAlexDataSource.kt:279-282`），直接嵌入会产出 60+ 词的关键词堆砌句 |
| 论文标题加英文引号并独立成句 | `recentWorkTitle` 取 `recentWorkTitles.firstOrNull()`（`MailVariableService.kt:144`），是原始论文标题，常含冒号且很长，裸嵌无法辨识边界 |
| 用 "came across" 而非 "after reading" | 系统只持有标题，不持有全文，声称读过属过度声明 |
| 新增 `${institution}` | 在专家发现阶段即写入（`ExpertDiscoveryService.kt:619`、`:744`），不依赖 enrichment，填充率高 |
| 个性化分散到三处 | 原文两个占位符挤在同一句 |
| 不使用 `${hIndex}` / `${worksCount}` / `${lastPublicationYear}` | 在陌生商务邮件中报出对方文献计量指标会显著提高投诉率 |

对应的 `required_keys` 建议值：

```json
["recentWorkTitle","primaryResearchField"]
```

`expertFamilyName` 与 `institution` 不列入必填——它们的默认值（`Colleague` / `your institution`）不构成虚假陈述。

## 变更文件清单

生产文件 **10** 个（达上限，不得再增；若执行中发现需要第 11 个，属计划缺陷，须停止并申请修订）：

| # | 文件 | 改动性质 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V84__add_required_keys_to_compose_template.sql` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt` | 加字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` | 加两个只读方法 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt` | 加变量元数据 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt` | 加变量取值 + 提升 `resolveExpertProfile` 可见性 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt` | 新增 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 修变量注入 + 挂闸门 |
| 8 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | 挂闸门 |
| 9 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 加原因码与标签 |
| 10 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 两处捕获记 skipped |

测试文件（不计入上限）：

| 文件 | 改动性质 |
|---|---|
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateServiceTest.kt` | 新增 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceGateTest.kt` | 新增 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt` | 补 `primaryResearchField` 用例 |
| `src/test/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposerTest.kt` | 补闸门用例 |
| `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt` | 补 `effectiveRequiredKeys` 用例 |

## 验证命令

见 master 计划 `## 验证命令` 节。P1 相关的单类命令为该节的「P1 新增/受影响测试类」一条。本节不重复命令文本。

## 验收标准

- **I-1**: grep `ManualExpertMailService.kt` 确认 `mailTemplateVariables` 已删除且无残留调用；单测断言 `composeComposeTemplate` 产出的正文含真实 unsubscribe URL 与专家研究方向。
- **I-2**: `PersonalizationGateServiceTest` 断言 `requireNoPlaceholderResidue("a ${x} b")` 抛 `PlaceholderResidueException` 且消息含 `${x}`；`ManualExpertMailServiceGateTest` 断言残留时 `mailDeliveryService.send` 从未被调用。
- **I-3**: 单测构造「变量值为空但模板带默认值」的场景，断言 `evaluate` 返回 `blocked = true` 且 `missingKeys` 精确等于必填集与兜底集的交集；再构造「渲染后文本」输入，断言不会被误判为通过。
- **I-4**: 参数化测试覆盖 `null` / `""` / `"[]"` / `"{不是数组}"` 四种 `requiredKeys` 值，断言均返回 `emptyList()`，且第四种记 WARN 不抛异常。
- **I-5**: grep 确认 `PersonalizationGateService` 在 `ManualExpertMailService` 与 `IntroductionMailComposer` 两处均被调用；`IntroductionMailComposerTest` 断言必填缺失时抛 `PersonalizationGateException`。
- **I-6**: grep 确认两处循环的 `catch (e: PersonalizationGateException)` 位于通用 `catch (e: Exception)` 之前；单测断言命中后 `accumulator.skippedReasonsMap()` 含 `PERSONALIZATION_INCOMPLETE`，且 `failure` 计数未增加。
- **I-7**: 断言 `ES_FIELD_BY_KEY["primaryResearchField"] == "researchFields"`；断言 `researchFields = "A, B, C"` 时 `primaryResearchField == "A"`；grep 确认 `ExpertDiscoveryService.kt` 未被本计划修改。
- **I-8**: 断言 `variableMetadata()` 中存在 key 为 `primaryResearchField` 的项，且其 `esField == "researchFields"`、`nullable == true`、`example` 非空。
- 回归：执行 master `## 验证命令` 节的全量测试命令通过。

## 人工验收清单

### A1-1: 手动发送的占位符全部替换
- 前置条件: 一个启用的 compose 模板，正文含 `${unsubscribeUrl}` 与 `${primaryResearchField}`；一个 ES 中 `researchFields` 非空的专家联系人；`UNSUBSCRIBE_BASE_URL`、`UNSUBSCRIBE_SECRET` 均非空。
- 操作步骤: 专家详情页 → 选择该模板 → 手动发送 → 查看收件箱原始邮件源码。
- 预期结果: text/plain 与 text/html 两部分均不含 `${`；unsubscribe 处是含 `/u/unsubscribe?token=` 的完整 URL；研究方向处是一个**单一**短语（不含逗号分隔的多个 topic）。
- 覆盖: I-1、I-2、I-7、需求 observable outcome 1 与 4

### A1-2: 门禁跳过并记入统计
- 前置条件: 模板必填变量配置为 `["recentWorkTitle","primaryResearchField"]`；专家甲两项俱全，专家乙 `recentWorkTitles` 缺失。
- 操作步骤: 对甲乙执行批量发送 → 打开执行详情。
- 预期结果: 成功 1；「跳过原因」出现一行「个性化字段缺失」数量 `1`；「失败原因」中**不含**该条；乙未收到邮件。
- 覆盖: I-3、I-5、I-6、需求 observable outcome 2 与 3

### A1-3: 未配置 required_keys 的模板照常发送（回归）
- 前置条件: 模板 B 的必填变量未配置（NULL）；专家丙 `researchFields` 为空。
- 操作步骤: 用模板 B 对丙手动发送。
- 预期结果: 发送成功；正文中研究方向处显示模板写的默认值文案；未出现在跳过统计中。
- 覆盖: I-4、must-NOT-change 第 5 项

### A1-4: INTRODUCTION 批量同样受门禁约束
- 前置条件: INTRODUCTION 模板配置必填变量；候选池中同时存在数据完整与缺失的专家。
- 操作步骤: 发起 INTRODUCTION 批量发送 → 打开执行详情。
- 预期结果: 「跳过原因」中出现「个性化字段缺失」；数据缺失的专家未收到邮件。**若此项通过而 A1-2 不通过，或反之，说明只挂了一条路径。**
- 覆盖: I-5、master I-M5

### A1-5: 首轮外发的既有调用方未被破坏（回归）
- 前置条件: 一个未配置 `required_keys` 的 INTRODUCTION 模板。
- 操作步骤: 触发定时首轮外发（或手动 INTRODUCTION 批量）。
- 预期结果: 与改动前行为一致，邮件正常发出，联系人状态流转到 `INTRO_SENT`。
- 覆盖: must-NOT-change 第 1 项

### A1-6: 重写后的模板文案目测
- 前置条件: 运营已按「阶段 5」把模板文案更新到后台。
- 操作步骤: 对一个数据完整的真实专家发送一封，通读收到的邮件。
- 预期结果: 称呼为 `Dear Professor <姓氏>,`；第二段出现带英文引号的完整论文标题；研究方向为单一短语；全文无一句同时出现「reviewed your profile」与泛指措辞；正文只有一个外链（官网）。
- 覆盖: 需求 observable outcome 4、阶段 5

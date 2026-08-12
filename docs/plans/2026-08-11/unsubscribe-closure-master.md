# 退订链路补全 — 主索引与拆分说明

> 本文件**不是可执行计划**，是拆分决策与共享证据的索引。可执行计划见下方各子计划文件。
> 生成日期：2026-08-11
> 触发来源：Gmail 实际收信测试前的退订链路排查（会话内审计）

## 背景

准备用两个 Gmail 测试账号做介绍邮件实测。前置排查发现退订功能"两头缺"：核心链路（签 token → 校验 → 一键退订 POST → 幂等入库）是通的，但

1. **正文里没有退订链接** —— 变量链路通，模板内容里没有占位符；
2. **退订之后拦不住所有发送路径** —— `isSuppressed` 只在 4 个调用点被检查，3 条 `mailDeliveryService.send` 路径没有前置检查。

上一轮已修复的问题不在本次范围内：`List-Unsubscribe-Post` 的 `List=One-Click` 错误值已在提交 `f2916674` 修正并发布，当前输出严格为 `List-Unsubscribe=One-Click`（`SmtpMailDeliveryService.kt:61`），生产环境 `UNSUBSCRIBE_BASE_URL` / `UNSUBSCRIBE_SECRET` 均已注入。

## 共享现状证据（各子计划复用，不重复 grep）

### E-1 `unsubscribeUrl` 的唯一注入实现

`MailVariableService.buildVariables()`（`MailVariableService.kt:117-159`）是唯一产出 `unsubscribeUrl` 的地方，`:155-157`：

```kotlin
val unsubscribeVars = mapOf(
    "unsubscribeUrl" to unsubscribeUrl(unsubscribeEmail, previewFallbacks)
)
```

**能拿到 `unsubscribeUrl` 的路径（已验证）：**

| 路径 | 位置 | 说明 |
|---|---|---|
| `IntroductionMailComposer.compose()` | `IntroductionMailComposer.kt:18` → `:51-52` | INTRODUCTION，`unsubscribeEmail` 默认取 `expert?.email` |
| `ManualExpertMailService.composeComposeTemplate()` | `ManualExpertMailService.kt:197-199` | COMPOSE_TEMPLATE（含 MATERIAL_REMINDER），传 `contact.expertEmail` |
| `MailVariableService.renderContact()` / `renderHtmlForContact()` | `:187` / `:202` | 人工富文本、AI 草稿渲染 |
| `AutoMailReplyService` QA 自动回复 | `AutoMailReplyService.kt:562-565` 走 `renderForContact` | 间接经 `renderContact` |

**拿不到 `unsubscribeUrl` 的路径（已验证，全部是会议邮件族）：**

| 路径 | 位置 | 注入内容 |
|---|---|---|
| `AutoMailReplyService.sendMeetingInvitation()` | `:953-956` → `mailTemplateVariables()` `:990-998` | 仅 6 个 sender 变量 |
| `AutoReplyPreviewService` 会议邀请预览 | `:93-96` → `mailTemplateVariables()` `:205-213` | 仅 6 个 sender 变量 |
| `MeetingInvitationMailComposer.compose()` | `MeetingInvitationMailComposer.kt:16-20` | 仅 `senderDisplayName` |
| `MeetingScheduleService` MEETING_CONFIRMATION | `MeetingScheduleService.kt:118-131` | 会议变量 + 6 个 sender 变量 |
| `ManualExpertMailService` 测试兜底分支 | `:203-205` | `senderVariables + EXPERT_KEYS`，无 unsubscribeUrl |

**失败形态是字面量泄漏,不是空串。** `MailComposeTemplateService.renderText()` `:588-597`：

```kotlin
return variables.entries.fold(withFallback) { rendered, (key, value) ->
    rendered.replace("\${$key}", value)
}
```

fold 只替换 map 中**存在**的 key。map 里没有 `unsubscribeUrl` 时，`${unsubscribeUrl}` 会**原样出现在外发正文里**。这是 Plan 04 存在的理由。

### E-2 知识库过期条目（Phase 0 发现，已在 Phase 6 更正）

`docs/knowledge/mail/K-manual-expert-mail-sender-only-variables.md` 声称
`ManualExpertMailService.mailTemplateVariables()`（`:196-204`）只注入 6 个 sender 变量、零个专家变量。

**该条目已过期。** 当前 `ManualExpertMailService.kt:196-200` 是：

```kotlin
val variables = if (variableService != null) {
    val expert = variableService.resolveExpertProfileFor(contact)
    variableService.buildVariables(
        account, expert, contact.expertEmail, previewFallbacks = false, contact = contact
    )
} else { /* test-only fallback */ }
```

即生产路径走全量 `buildVariables`，含专家变量与 `unsubscribeUrl`。仅 `variableService == null` 的**测试兜底分支**（`:203-205`）保留旧行为。已按 Phase 6 规则就地更正条目。

### E-3 `mailDeliveryService.send` 的 7 个调用点与抑制检查覆盖

| # | 调用点 | 组装来源 | 前置 `isSuppressed` |
|---|---|---|---|
| 1 | `InitialOutreachService.kt:73` | `IntroductionMailComposer` | ✅ `:46` |
| 2 | `ManualInitialOutreachService.kt:688` | `IntroductionMailComposer`（`:297`） | ✅ `:266` |
| 3 | `AutoMailReplyService.kt:575` | QA/AI 自动回复 | ✅ `:825` |
| 4 | `AutoMailReplyService.kt:965` | `sendMeetingInvitation`（定义 `:946`） | ✅ `:445` `blockedByUnsubscribe(..., "MEETING_INVITATION")` 先于 `:469` 的调用 |
| 5 | `MeetingScheduleService.kt:141` | `ComposedMail(:135)` MEETING_CONFIRMATION | ❌ **无** |
| 6 | `ManualExpertMailService.kt:63` | `composeComposeTemplate` | ❌ **无** |
| 7 | `PendingMailOperationService.kt:270` | `ComposedMail(:258)` | ❌ **无** |

`isSuppressed` 的全部调用点（`grep isSuppressed src/main/kotlin`）：`InitialOutreachService:46`、`ManualInitialOutreachService:266/556/1171`、`AutoReplyPreviewService:149`、`AutoMailReplyService:825`。

### E-4 返回失败状态会污染账号健康度（决定 fail-closed 用异常而非状态码）

`ManualInitialOutreachService.kt:704-730` 对 `delivered.errorCategory` 做分支：`PERMANENT` → 把联系人写成 `operatorStatus = "EMAIL_INVALID"` 并同步 ES；`TRANSIENT` + 421/452 → `accountRateLimiter.recordThrottled()`。

因此**不能**用 `DeliveredMail(status = "SUPPRESSED", errorCategory = PERMANENT/TRANSIENT)` 表达"因退订跳过"——会误标邮箱无效或误限流账号。抑制拦截必须走异常路径。

`GlobalExceptionHandler` 把 `IllegalStateException` 映射为 400 `BAD_REQUEST`（`GlobalExceptionHandler.kt:18-20`），所以自定义异常继承 `IllegalStateException` 即可让运营看到可读错误（`K-custom-exception-http-status-mapping`）。

### E-5 Flyway 占位符替换在生产是开启状态（Plan 01 的硬前提）

`src/main/resources/application.yml:8-10`：

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
```

**未设** `placeholder-replacement`，Spring Boot 2.7.18 默认 `true`。而所有真正执行 Flyway 的测试都显式关闭它：

- `src/test/kotlin/.../auth/AuthFlowIntegrationTest.kt:53` — `"spring.flyway.placeholder-replacement=false"`
- `src/test/kotlin/.../campaign/repository/FlywayMigrationIntegrationTest.kt:343` — `.placeholderReplacement(false)`
- `src/test/kotlin/.../monitoring/repository/MailRecordRepositoryMonitoringIT.kt:30`
- `src/test/kotlin/.../llm/service/AiQaExtractionServiceTest.kt:203`

原因：`V2`/`V9`/`V56`/`V71` 的模板正文含 `${senderEmail}` 等（`grep -l '\${' src/main/resources/db/migration/*.sql`）。存量生产库已过这些版本故不重跑；**新增含 `${unsubscribeUrl}` 的迁移会在生产启动时被 Flyway 解析为占位符并抛 "No value provided for placeholder expressions"**。

### E-5b 三条未覆盖操作端路径的异常传播形态不一致（决定拦截点不能只放投递层）

初稿假设"3 条未覆盖路径全是操作端同步触发，异常自然冒泡成 400"。逐行复核后该假设**只对其中 2 条成立**：

| 路径 | send 行 | 是否被 try/catch 包裹 | 抛异常的实际后果 |
|---|---|---|---|
| `ManualExpertMailService.sendManualMail()` `:51` | `:63` | 否（方法内无 catch） | `@Transactional` 回滚 → `GlobalExceptionHandler` → 400 ✅ |
| `MeetingScheduleService.confirmMeetingAndEmail()` `:89` | `:141` | 否（唯一 catch 在 `:114`，只捕 `SenderAccountNotBoundException`） | 直达 `GlobalExceptionHandler` → 400 ✅ |
| `PendingMailOperationService.sendManualRichReply()` `:128` | `:270` | **是**，`:359 catch (deliveryEx: Exception)` | 落 `else` → `finalizeFailure(DELIVERY_UNKNOWN)` `:365-371` → 409「发送状态未知，请勿重复发送」`:376-379` ❌ |

第三条的后果三重：运营看到的文案暗示邮件**可能已发出**（实际一封没发）；attempt 被烧成 `DELIVERY_UNKNOWN`，按幂等 fail-closed 设计（`K-smtp-idempotency-reservation-before-delivery`）后续重试被阻断；且违反"拦截不得表达为投递失败"。

因此该路径必须在 `manualReplySendAttemptService.prepareAndClaim(payload)`（`:253`）**之前**单独前置拦截，不能依赖投递层兜底。见 Plan 02 的 I-5。

### E-6 `mail_template` 表已无代码读取

`grep -rn "mail_template\b\|MailTemplateRepository\|mailTemplateRepository" src/main/kotlin`（排除 compose）**零命中**。正文 SSOT 是 `mail_compose_template_block.custom_text`（`V61` 建表，`V62` 把 `mail_template.body` 搬进 `block_order = 0` 的 `CUSTOM_TEXT` 块）。

因此正文类迁移**只需**改 `mail_compose_template_block`，不必双写 `mail_template`。`V71` 的双写是历史做法，无需沿用。

### E-7 迁移可验证性：已有两种测试范式

- **文本断言**（无需 Docker）：`QaSeedEncodingRepairMigrationTest.kt` 用 `Files.readString(Path.of("src/main/resources/db/migration/V44__...sql"))` 断言 SQL 内容。本次沿用此范式。
- **真实执行**（需 Docker + `-DmigrationIt=true`）：`FlywayMigrationIntegrationTest.kt:21` 带 `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")`，默认跳过。

## 需求方决策（2026-08-11 确认）

| 决策点 | 结论 | 影响 |
|---|---|---|
| 退订 header 范围 | **收窄到冷外联**（INTRODUCTION + MATERIAL_REMINDER） | Plan 03：给 `ComposedMail` 加 bulk 字段 |
| 抑制拦截与人工发信 | **拦，但可显式 override** | Plan 02：`allowSuppressed` 默认 false，API 可传 true |
| token 有效期（exp） | **本轮不做，单列后续** | 记为 Plan 06（未排期） |
| 计划粒度 | **拆成顺序子计划** | 本索引 + 5 个子计划 |

关于"带 header 会把 1:1 会话邮件推向促销分类"：这是业内倾向性判断，**不是 Google 文档化规则**。Plan 03 的收益属预防性，验收标准只断言 header 有无，不断言 Gmail 分类结果。

## 拆分结果与依赖

create-p 硬限制：单计划 ≤10 文件、≤2 子系统、每个共享存储 ≤1 个新字段。本次 6 类问题横跨模板内容、变量注入、投递闸门、入站判定、消息元数据、HTTP 端点，必须拆。

```
Plan 01（P0，Gmail 实测前必做）  正文退订链接落地
        ↓ 无依赖，可独立部署
Plan 02（P1，Gmail 实测前必做）  抑制名单收口到投递层（fail-closed）
Plan 02b（P1，Gmail 实测前必做） mailto 退订通道生效
        ↑ 02 与 02b 无逻辑依赖，但都改 EmailSuppressionService.kt，建议顺序合并
        ↓
Plan 03（P2）  退订 header 收窄到冷外联
        ↑ 依赖 01（正文链接须先在位，否则收窄后冷外联以外的邮件既无 header 也无正文入口）
Plan 04（P2）  会议邮件族补 unsubscribeUrl 注入（消除字面量泄漏风险）
Plan 05（P2）  退订端点健壮性 + 人工 override 前端接线
Plan 06（未排期）  token 有效期 exp
```

| 计划 | 文件 | 状态 | 文档 |
|---|---|---|---|
| 01 正文退订链接 | 4 | ✅ 已展开 | [unsubscribe-01-body-link.md](unsubscribe-01-body-link.md) |
| 02 抑制收口（fail-closed） | 9 | ✅ 已展开（v2） | [unsubscribe-02-suppression-gate.md](unsubscribe-02-suppression-gate.md) |
| 02b mailto 通道 | 4 | ✅ 已展开 | [unsubscribe-02b-mailto-channel.md](unsubscribe-02b-mailto-channel.md) |
| 03 header 收窄 | ~6 | ⏳ 待 create-p 展开 | 见下方范围锚点 |
| 04 会议邮件变量注入 | ~7 | ⏳ 待 create-p 展开 | 见下方范围锚点 |
| 05 端点健壮性 + override UI | ~7（含前端，需样式契约） | ⏳ 待 create-p 展开 | 见下方范围锚点 |
| 06 token exp | ~4 | 未排期 | — |

**为什么有 02b**：初稿把 mailto 通道并入 Plan 02，复核发现 `PendingMailOperationService` 需要独立前置拦截（E-5b），补上后 Plan 02 会超出 10 文件上限，故按 create-p 的分解规则拆出 02b。两者子系统本就不同（投递层拦截 vs 入站判定）。

**Plan 03-05 未展开是刻意的**：它们的详细任务需要各自完整的 Phase 1b 审计与验收清单，塞进本索引会变成"示意性计划"（create-p 反模式）。执行到该阶段时单独跑一次 create-p 展开。下方仅锚定范围，防止范围漂移。

### Plan 03 范围锚点 — 退订 header 收窄到冷外联

- 目标：`List-Unsubscribe` / `List-Unsubscribe-Post` 只出现在 INTRODUCTION 与 MATERIAL_REMINDER 外发邮件上。
- 机制：`ComposedMail`（`IntroductionMailComposer.kt:69-78`）加 `bulk: Boolean = false`；`SmtpMailDeliveryService.kt:57` 的条件改为 `unsubscribeTokenService.enabled() && mail.bulk`。
- 只需两处置 true：`IntroductionMailComposer.kt:36`（覆盖 E-3 的 #1 #2 两条介绍邮件路径，依据 `K-introduction-compose-callers`）、`ManualExpertMailService.kt:244`（用已存在的 `:186` `isMaterialReminder` 判定）。
- 默认 false 意味着其余 6 个 `ComposedMail(` 构造点零改动，但**必须**为每个构造点写"header 不出现"的断言，否则默认值静默生效无法被验证。
- 待展开时须审计：`SmtpMailDeliveryServiceTest.kt` 现有 3 个 header 用例（`:126` `:152` `:176`）全部依赖"默认带 header"，需重写。

### Plan 04 范围锚点 — 会议邮件族补 unsubscribeUrl

- 目标：任何模板正文写 `${unsubscribeUrl}` 都不会外发字面量（E-1 的失败形态）。
- 4 个注入点补 `unsubscribeUrl`：`AutoMailReplyService.kt:990-998`、`AutoReplyPreviewService.kt:205-213`、`MeetingInvitationMailComposer.kt:16-20`、`MeetingScheduleService.kt:118-131`；外加 `ManualExpertMailService.kt:203-205` 测试兜底分支补空值。
- `MailVariableService` 已注入 `AutoMailReplyService`（`:62`）与 `AutoReplyPreviewService`（`:47`），无需新增依赖。
- 依据 `K-preview-mirrors-pipeline`：预览与发送必须同源同序，`AutoReplyPreviewService` 与 `AutoMailReplyService` 两处须同时改、同值。
- 是否**同时**把退订行加入会议模板正文，是运营内容决策，不在代码计划内。

### Plan 05 范围锚点 — 端点健壮性 + override 前端接线

- `UnsubscribeController.kt:49` 的 `action="/u/unsubscribe/confirm"` 是硬编码根路径。本项目 packaging 为 war 且 `application.yml` 未配 `server.servlet.context-path`；一旦部署到带 context path 的 Tomcat，GET 确认页可开（URL 来自配置 baseUrl）但 POST 会 404。改为相对路径或用 request context path 拼接。
- `UnsubscribeController.kt:50` `value="$token"` 未做 HTML 转义。仅 `verify` 通过才渲染，攻击者无 secret 无法伪造，**不可利用**；作为零成本加固项。
- `UnsubscribeTokenService.enabled()` 为 false 时两个 header 全省、正文 `${unsubscribeUrl}` 退化为空串（`MailVariableService.kt:251-263`），应用仍正常启动无告警。需加启动期校验或群发前置门禁。
- 人工发信 override 的前端勾选框（Plan 02 只做到 API 层）。**含前端文件 → 展开时必须执行 create-p Step 1b-fe 并产出 `## 样式契约`。**

## 不在任何计划范围内

- Gmail / Outlook 的实际分类与按钮展示行为 —— 不可由代码保证，属实测观察项。
- DKIM `h=` 是否覆盖两个退订头 —— 项目不自签，取决于 SMTP 服务商配置，属运维项（参考 `K-deliverability-dns-live-verification`、`K-ops-plan-code-scope-boundary`：运维项不得与代码计划混在一起）。
- 会议邮件正文是否加退订行 —— 运营内容决策。
- 抑制名单的运营管理界面改造 —— 已有 `EmailSuppressionController` 与前端入口，本次不动。

## 修正记录

由后续计划族 [`docs/plans/2026-08-12/unsubscribe-link-and-page-master.md`](../2026-08-12/unsubscribe-link-and-page-master.md) 产生的修订，记录于此以便后续验证轮次在源头看到：

| 日期 | 被修订项 | 修订内容 | 理由 | 决定它的计划 |
|---|---|---|---|---|
| 2026-08-12 | 本文件「Plan 06 token exp」 | **取消**，由新计划族 Plan 07「不透明随机 token」取代 | 改为服务端映射后，过期语义应落在 `unsubscribe_token.expires_at` 列上，而非自签载荷；exp 单列为 Plan 07 之后的后续计划 | `2026-08-12/unsubscribe-07-opaque-token.md` |
| 2026-08-12 | 本文件 `:202`（Plan 05 锚点：`action` 硬编码根路径） | **已修复，关闭** | `UnsubscribeControllerTest.kt:73` 已断言 `action="unsubscribe/confirm"`（相对路径） | 复核发现，非计划改动 |
| 2026-08-12 | 本文件 `:203`（Plan 05 锚点：`value="$token"` 未转义） | **移交** 新计划族 Plan 08 的 I-2 | Plan 08 重写整个页面渲染，转义在同一处顺带落地 | `2026-08-12/unsubscribe-08-branded-page.md` |
| 2026-08-12 | `unsubscribe-01-body-link.md:30`（out of scope：HTML 版正文退订链接样式） | **解除**，由新计划族 Plan 06 承接 | Gmail 实测确认裸 URL 展示问题成立，需求方决策改 HTML multipart | `2026-08-12/unsubscribe-06-html-anchor-body.md` |
| 2026-08-12 | 本文件 E-5（Flyway 占位符替换在生产是开启状态） | **状态更新**：Plan 01 已落地，`application.yml:8-13` 现为 `placeholder-replacement: false`，回归断言在 `UnsubscribeBodyLinkMigrationTest.kt:46` | 复核结果 | 复核发现，非计划改动 |

未受影响、状态不变：Plan 02 / 02b（抑制收口、mailto 通道）、Plan 03（header 收窄）、Plan 04（会议邮件变量注入）、Plan 05 剩余项（`enabled()` 启动期告警、人工发信 override 前端勾选框）。
